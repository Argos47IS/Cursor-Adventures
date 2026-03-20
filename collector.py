"""
Main orchestrator: scrape → filter → save → notify.

Usage:
    python collector.py              Run full collection cycle
    python collector.py --dry-run    Scrape only, no Telegram notification
    python collector.py --lite       Force lightweight mode (no Playwright)
"""

import asyncio
import csv
import logging
import os
import sys
from datetime import datetime, timezone, timedelta

from dotenv import load_dotenv

import db
from common import ChannelData
from notifier import send_report

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
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reports")


def save_csv(channels: list[ChannelData], now: datetime) -> str:
    """Save report to CSV, return file path."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    date_str = now.strftime("%Y-%m-%d_%H-%M")
    filepath = os.path.join(OUTPUT_DIR, f"report_{date_str}.csv")

    with open(filepath, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["Канал", "Username", "Подписчики", "Прирост за 24ч", "Источник"])
        for ch in channels:
            growth = ch.growth_24h if ch.growth_24h is not None else "нет данных"
            writer.writerow([ch.title, f"@{ch.username}", ch.subscribers, growth, ch.source])

    logger.info("CSV сохранён: %s", filepath)
    return filepath


def print_table(channels: list[ChannelData]):
    if not channels:
        return

    rows = []
    for i, ch in enumerate(channels, 1):
        growth = f"{ch.growth_24h:+d}" if ch.growth_24h is not None else "—"
        rows.append([str(i), ch.title[:30], f"@{ch.username}", f"{ch.subscribers:,}", growth])

    widths = [max(len(r[j]) for r in rows) for j in range(5)]
    headers = ["#", "Канал", "Username", "Подписчики", "Прирост"]
    widths = [max(w, len(h)) for w, h in zip(widths, headers)]

    fmt = " | ".join(f"{{:<{w}}}" for w in widths)
    sep = "-+-".join("-" * w for w in widths)

    print(f"\n{fmt.format(*headers)}")
    print(sep)
    for r in rows:
        print(fmt.format(*r))
    print()


def _pick_scraper(force_lite: bool = False):
    """Auto-detect available scraper: full (Playwright) or lite (httpx only)."""
    if force_lite:
        logger.info("Режим: lite (httpx, без Playwright)")
        from scraper_lite import scrape_all
        return scrape_all

    try:
        from playwright.async_api import async_playwright  # noqa: F401
        logger.info("Режим: full (Playwright + httpx)")
        from scraper import scrape_all
        return scrape_all
    except ImportError:
        logger.info("Playwright не установлен — переключение на lite-режим (httpx)")
        from scraper_lite import scrape_all
        return scrape_all


async def run_collection(dry_run: bool = False, force_lite: bool = False):
    """Main collection routine."""
    db.init_db()

    min_subs = int(os.getenv("MIN_SUBSCRIBERS", "5000"))
    max_subs = int(os.getenv("MAX_SUBSCRIBERS", "40000"))
    now = datetime.now(MSK)

    logger.info("=== Сбор статистики: %s МСК ===", now.strftime("%Y-%m-%d %H:%M"))
    logger.info("Диапазон подписчиков: %d – %d", min_subs, max_subs)

    scrape_all = _pick_scraper(force_lite)
    channels = await scrape_all(min_subs=min_subs, max_subs=max_subs)

    if not channels:
        logger.warning("Ни один канал не найден. Проверьте debug/ на предмет ошибок.")
        if not dry_run:
            await send_report([], now.strftime("%d.%m.%Y %H:%M"), None)
        return

    # Save snapshots to DB for historical tracking
    for ch in channels:
        db.save_snapshot(ch.username, ch.title, ch.subscribers, now)

    # Enrich growth from DB if scraper didn't provide it
    for ch in channels:
        if ch.growth_24h is None:
            prev = db.get_previous_snapshot(ch.username, now)
            if prev:
                ch.growth_24h = ch.subscribers - prev["subscribers"]

    # Print to console
    print_table(channels)

    # Save CSV
    csv_path = save_csv(channels, now)

    # Send Telegram notification
    if not dry_run:
        date_str = now.strftime("%d.%m.%Y %H:%M")
        await send_report(channels, date_str, csv_path)
    else:
        logger.info("Dry run — Telegram-уведомление пропущено")

    db.cleanup_old_data(days_to_keep=30)
    logger.info("=== Сбор завершён: %d каналов ===", len(channels))


def main():
    dry_run = "--dry-run" in sys.argv
    force_lite = "--lite" in sys.argv
    asyncio.run(run_collection(dry_run=dry_run, force_lite=force_lite))


if __name__ == "__main__":
    main()
