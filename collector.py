"""
Telegram Channel Statistics Collector

Collects subscriber counts from a list of Telegram channels,
calculates 24-hour growth, and saves results to CSV and SQLite.
"""

import asyncio
import csv
import json
import logging
import os
import sys
from datetime import datetime, timezone, timedelta

from telethon import TelegramClient
from telethon.tl.functions.channels import GetFullChannelRequest
from telethon.errors import (
    ChannelPrivateError,
    UsernameNotOccupiedError,
    UsernameInvalidError,
    FloodWaitError,
)
from dotenv import load_dotenv

import db

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "collector.log"),
            encoding="utf-8",
        ),
    ],
)
logger = logging.getLogger(__name__)

MSK = timezone(timedelta(hours=3))

MIN_SUBSCRIBERS = 5_000
MAX_SUBSCRIBERS = 40_000

CHANNELS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "channels.json")
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reports")
SESSION_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tg_session")


def load_channels() -> list[str]:
    with open(CHANNELS_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    channels = []
    for ch in data["channels"]:
        username = ch.strip().lstrip("@")
        if username:
            channels.append(username)
    return channels


async def fetch_channel_info(client: TelegramClient, username: str) -> dict | None:
    """Fetch subscriber count and title for a single channel."""
    try:
        entity = await client.get_entity(f"@{username}")
        full = await client(GetFullChannelRequest(entity))

        title = entity.title
        subscribers = full.full_chat.participants_count

        return {
            "username": username,
            "title": title,
            "subscribers": subscribers,
        }

    except (ChannelPrivateError, UsernameNotOccupiedError, UsernameInvalidError) as e:
        logger.warning("Канал @%s недоступен: %s", username, e)
        return None
    except FloodWaitError as e:
        logger.warning("Flood wait %d секунд для @%s, ждём...", e.seconds, username)
        await asyncio.sleep(e.seconds + 1)
        return await fetch_channel_info(client, username)
    except Exception as e:
        logger.error("Ошибка при получении данных @%s: %s", username, e)
        return None


def build_report_row(info: dict, now: datetime) -> dict:
    """Build a single report row with growth calculation."""
    prev = db.get_previous_snapshot(info["username"], now)

    growth_24h = None
    if prev:
        growth_24h = info["subscribers"] - prev["subscribers"]

    return {
        "Канал": info["title"],
        "Username": f"@{info['username']}",
        "Подписчики": info["subscribers"],
        "Прирост за 24ч": growth_24h if growth_24h is not None else "нет данных",
    }


def save_csv(rows: list[dict], now: datetime):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    date_str = now.strftime("%Y-%m-%d_%H-%M")
    filepath = os.path.join(OUTPUT_DIR, f"report_{date_str}.csv")

    with open(filepath, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=["Канал", "Username", "Подписчики", "Прирост за 24ч"])
        writer.writeheader()
        writer.writerows(rows)

    logger.info("Отчёт сохранён: %s", filepath)
    return filepath


def print_table(rows: list[dict]):
    if not rows:
        logger.info("Нет подходящих каналов для отображения.")
        return

    col_widths = {}
    for key in rows[0]:
        col_widths[key] = max(len(str(key)), max(len(str(r[key])) for r in rows))

    header = " | ".join(k.ljust(col_widths[k]) for k in rows[0])
    separator = "-+-".join("-" * col_widths[k] for k in rows[0])

    print(f"\n{header}")
    print(separator)
    for row in rows:
        line = " | ".join(str(row[k]).ljust(col_widths[k]) for k in row)
        print(line)
    print()


async def run_collection():
    """Main collection routine."""
    api_id = os.getenv("TELEGRAM_API_ID")
    api_hash = os.getenv("TELEGRAM_API_HASH")

    if not api_id or not api_hash:
        logger.error(
            "Установите TELEGRAM_API_ID и TELEGRAM_API_HASH в файле .env "
            "(получить на https://my.telegram.org/apps)"
        )
        sys.exit(1)

    db.init_db()

    now = datetime.now(MSK)
    logger.info("=== Сбор статистики каналов: %s МСК ===", now.strftime("%Y-%m-%d %H:%M"))

    channels = load_channels()
    logger.info("Каналов для проверки: %d", len(channels))

    client = TelegramClient(SESSION_FILE, int(api_id), api_hash)
    await client.start(phone=os.getenv("TELEGRAM_PHONE"))

    report_rows = []
    collected = 0
    skipped_range = 0

    for username in channels:
        info = await fetch_channel_info(client, username)
        if info is None:
            continue

        if info["subscribers"] < MIN_SUBSCRIBERS or info["subscribers"] > MAX_SUBSCRIBERS:
            logger.info(
                "  @%s (%s) — %d подписчиков, вне диапазона %d–%d, пропускаем",
                username, info["title"], info["subscribers"], MIN_SUBSCRIBERS, MAX_SUBSCRIBERS,
            )
            skipped_range += 1
            db.save_snapshot(info["username"], info["title"], info["subscribers"], now)
            continue

        db.save_snapshot(info["username"], info["title"], info["subscribers"], now)
        row = build_report_row(info, now)
        report_rows.append(row)
        collected += 1

        await asyncio.sleep(1.5)

    report_rows.sort(key=lambda r: r["Подписчики"], reverse=True)

    logger.info(
        "Собрано: %d каналов в диапазоне, %d вне диапазона",
        collected, skipped_range,
    )

    if report_rows:
        csv_path = save_csv(report_rows, now)
        print_table(report_rows)
        logger.info("CSV: %s", csv_path)
    else:
        logger.info("Подходящих каналов не найдено.")

    db.cleanup_old_data(days_to_keep=30)

    await client.disconnect()
    logger.info("=== Сбор завершён ===")


def main():
    asyncio.run(run_collection())


if __name__ == "__main__":
    main()
