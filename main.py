#!/usr/bin/env python3
"""
Telegram Ad Monitor — мониторинг рекламных размещений в Telegram-каналах.

Скрипт подключается через Telethon (userbot API), проверяет каналы-доноры
за последние 24 часа, находит рекламные посты, собирает статистику
покупателей и отправляет CSV-отчёт в личный чат.

Запуск:
    python main.py              — полный цикл (проверка + отчёт)
    python main.py --test       — тестовый режим (без отправки)
    python main.py --login      — только авторизация (первый раз)
"""

import asyncio
import csv
import logging
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from typing import Optional

from telethon import TelegramClient, errors, types, functions
from telethon.tl.types import (
    MessageEntityUrl,
    MessageEntityTextUrl,
    MessageEntityMention,
    KeyboardButtonUrl,
    MessageMediaWebPage,
    PeerChannel,
)

import config

# ─────────────────────────────────────────────
# Часовой пояс МСК
# ─────────────────────────────────────────────
try:
    from zoneinfo import ZoneInfo
except ImportError:
    from backports.zoneinfo import ZoneInfo

MSK = ZoneInfo(config.TIMEZONE)

# ─────────────────────────────────────────────
# Логирование
# ─────────────────────────────────────────────
LOG_FORMAT = "%(asctime)s [%(levelname)s] %(message)s"
LOG_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

script_dir = os.path.dirname(os.path.abspath(__file__))

logger = logging.getLogger("ad_monitor")
logger.setLevel(logging.INFO)

console_handler = logging.StreamHandler(sys.stdout)
console_handler.setFormatter(logging.Formatter(LOG_FORMAT, LOG_DATE_FORMAT))

file_handler = logging.FileHandler(
    os.path.join(script_dir, config.LOG_FILE), encoding="utf-8"
)
file_handler.setFormatter(logging.Formatter(LOG_FORMAT, LOG_DATE_FORMAT))

logger.addHandler(console_handler)
logger.addHandler(file_handler)


# ─────────────────────────────────────────────
# Вспомогательные функции
# ─────────────────────────────────────────────

def load_donors() -> list[str]:
    """Загружает список каналов-доноров из donors.txt."""
    path = os.path.join(script_dir, config.DONORS_FILE)
    if not os.path.exists(path):
        logger.error("Файл %s не найден!", config.DONORS_FILE)
        return []

    donors = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            username = line.lstrip("@").split("/")[-1].strip()
            if username:
                donors.append(username)

    logger.info("Загружено доноров: %d", len(donors))
    return donors


def load_last_checked() -> datetime:
    """Загружает метку времени последней проверки. По умолчанию — 24 часа назад."""
    path = os.path.join(script_dir, config.LAST_CHECKED_FILE)
    default = datetime.now(timezone.utc) - timedelta(hours=24)
    if not os.path.exists(path):
        return default
    try:
        with open(path, "r") as f:
            ts = f.read().strip()
            return datetime.fromisoformat(ts)
    except (ValueError, OSError):
        return default


def save_last_checked(dt: datetime):
    """Сохраняет метку времени последней проверки."""
    path = os.path.join(script_dir, config.LAST_CHECKED_FILE)
    with open(path, "w") as f:
        f.write(dt.isoformat())


async def safe_sleep(seconds: float):
    """Пауза между запросами для защиты от rate-limit."""
    import random
    delay = random.uniform(
        max(seconds, config.REQUEST_DELAY_MIN),
        max(seconds, config.REQUEST_DELAY_MAX),
    )
    await asyncio.sleep(delay)


def normalize_username(raw: str) -> Optional[str]:
    """Извлекает username канала из строки/ссылки."""
    if not raw:
        return None
    raw = raw.strip()

    patterns = [
        r"(?:https?://)?t\.me/(?:joinchat/)?([a-zA-Z]\w{3,})",
        r"(?:https?://)?telegram\.me/([a-zA-Z]\w{3,})",
        r"@([a-zA-Z]\w{3,})",
        r"^([a-zA-Z]\w{3,})$",
    ]
    for pattern in patterns:
        match = re.search(pattern, raw)
        if match:
            username = match.group(1)
            skip = {"joinchat", "addstickers", "share", "proxy", "socks", "iv"}
            if username.lower() not in skip:
                return username
    return None


# ─────────────────────────────────────────────
# Извлечение покупателей из поста
# ─────────────────────────────────────────────

def extract_buyer_usernames(message: types.Message) -> list[str]:
    """
    Извлекает username'ы покупателей рекламы из сообщения.
    Проверяет: entities, inline-кнопки, forward-заголовок, media (web preview).
    """
    found = set()

    if message.entities:
        for entity in message.entities:
            if isinstance(entity, MessageEntityMention):
                mention = message.text[entity.offset:entity.offset + entity.length]
                uname = normalize_username(mention)
                if uname:
                    found.add(uname.lower())

            elif isinstance(entity, MessageEntityTextUrl):
                uname = normalize_username(entity.url)
                if uname:
                    found.add(uname.lower())

            elif isinstance(entity, MessageEntityUrl):
                url_text = message.text[entity.offset:entity.offset + entity.length]
                uname = normalize_username(url_text)
                if uname:
                    found.add(uname.lower())

    if message.reply_markup:
        rows = getattr(message.reply_markup, "rows", [])
        for row in rows:
            for button in row.buttons:
                if isinstance(button, KeyboardButtonUrl):
                    uname = normalize_username(button.url)
                    if uname:
                        found.add(uname.lower())

    if message.forward:
        if hasattr(message.forward, "chat") and message.forward.chat:
            chat = message.forward.chat
            if hasattr(chat, "username") and chat.username:
                found.add(chat.username.lower())

    if message.media and isinstance(message.media, MessageMediaWebPage):
        webpage = message.media.webpage
        if hasattr(webpage, "url") and webpage.url:
            uname = normalize_username(webpage.url)
            if uname:
                found.add(uname.lower())

    if message.text:
        links = re.findall(r"https?://t\.me/([a-zA-Z]\w{3,})", message.text)
        for link in links:
            uname = normalize_username(link)
            if uname:
                found.add(uname.lower())

    return list(found)


def is_sponsored_post(message: types.Message) -> bool:
    """
    Определяет, является ли пост рекламным.
    Проверяет: атрибут sponsored, маркеры в тексте, наличие кнопок с внешними ссылками.
    """
    if getattr(message, "sponsored", False):
        return True

    if not message.text:
        return False

    text_lower = message.text.lower()

    ad_markers = [
        "реклама", "sponsored", "#реклама", "#ad", "#sponsored",
        "рекламный пост", "партнёрский пост", "партнерский пост",
        "на правах рекламы", "промо", "#промо", "#promo",
        "erid:", "erid :", "токен: ", "рекламодатель:",
    ]
    for marker in ad_markers:
        if marker in text_lower:
            return True

    return False


# ─────────────────────────────────────────────
# Сбор статистики канала-покупателя
# ─────────────────────────────────────────────

async def get_channel_stats(
    client: TelegramClient, username: str
) -> Optional[dict]:
    """
    Собирает статистику канала-покупателя:
    - количество подписчиков
    - средние просмотры последних N постов
    - ER (engagement rate) = avg_views / subscribers * 100
    """
    try:
        entity = await client.get_entity(username)
    except (ValueError, errors.UsernameNotOccupiedError):
        logger.warning("Канал @%s не найден", username)
        return None
    except errors.FloodWaitError as e:
        logger.warning("FloodWait %d сек при get_entity(@%s)", e.seconds, username)
        await asyncio.sleep(e.seconds + 5)
        try:
            entity = await client.get_entity(username)
        except Exception:
            return None
    except Exception as e:
        logger.warning("Ошибка при get_entity(@%s): %s", username, e)
        return None

    if not isinstance(entity, (types.Channel, types.Chat)):
        logger.info("@%s — не канал, пропуск", username)
        return None

    # Получаем количество подписчиков
    subscribers = 0
    try:
        full = await client(functions.channels.GetFullChannelRequest(entity))
        subscribers = full.full_chat.participants_count
    except errors.FloodWaitError as e:
        logger.warning("FloodWait %d сек при GetFullChannel(@%s)", e.seconds, username)
        await asyncio.sleep(e.seconds + 5)
        try:
            full = await client(functions.channels.GetFullChannelRequest(entity))
            subscribers = full.full_chat.participants_count
        except Exception:
            pass
    except errors.ChatAdminRequiredError:
        subscribers = getattr(entity, "participants_count", 0) or 0
    except Exception as e:
        logger.warning("Не удалось получить подписчиков @%s: %s", username, e)
        subscribers = getattr(entity, "participants_count", 0) or 0

    await safe_sleep(1.0)

    # Собираем просмотры последних постов
    views_list = []
    try:
        async for msg in client.iter_messages(entity, limit=config.POSTS_FOR_STATS):
            if msg.views is not None:
                views_list.append(msg.views)
    except errors.FloodWaitError as e:
        logger.warning("FloodWait %d сек при iter_messages(@%s)", e.seconds, username)
        await asyncio.sleep(e.seconds + 5)
    except Exception as e:
        logger.warning("Ошибка при чтении постов @%s: %s", username, e)

    avg_views = 0
    if views_list:
        avg_views = int(sum(views_list) / len(views_list))

    er = 0.0
    if subscribers > 0 and avg_views > 0:
        er = round((avg_views / subscribers) * 100, 2)

    return {
        "username": username,
        "subscribers": subscribers,
        "avg_views": avg_views,
        "er": er,
    }


# ─────────────────────────────────────────────
# Основной цикл обхода доноров
# ─────────────────────────────────────────────

async def scan_donors(client: TelegramClient, donors: list[str], since: datetime) -> list[dict]:
    """
    Сканирует каналы-доноры за указанный период.
    Возвращает список найденных рекламных размещений.
    """
    results = []
    excluded = {x.lower() for x in config.EXCLUDED_BUYERS}

    for donor in donors:
        logger.info("━━━ Проверяю донора: @%s ━━━", donor)

        try:
            donor_entity = await client.get_entity(donor)
        except errors.FloodWaitError as e:
            logger.warning("FloodWait %d сек, жду...", e.seconds)
            await asyncio.sleep(e.seconds + 5)
            try:
                donor_entity = await client.get_entity(donor)
            except Exception as ex:
                logger.error("Не удалось получить @%s: %s", donor, ex)
                continue
        except Exception as e:
            logger.error("Не удалось получить @%s: %s", donor, e)
            continue

        await safe_sleep(1.0)

        msg_count = 0
        ad_count = 0

        try:
            async for message in client.iter_messages(
                donor_entity,
                offset_date=None,
                reverse=False,
                limit=200,
            ):
                if message.date.replace(tzinfo=timezone.utc) < since:
                    break

                msg_count += 1

                if not is_sponsored_post(message):
                    continue

                ad_count += 1
                logger.info(
                    "  Рекламный пост #%d (id=%d, дата=%s)",
                    ad_count, message.id,
                    message.date.strftime("%Y-%m-%d %H:%M"),
                )

                buyer_usernames = extract_buyer_usernames(message)
                if not buyer_usernames:
                    logger.info("    Покупатель не определён, пропуск")
                    continue

                for buyer_uname in buyer_usernames:
                    if buyer_uname in excluded:
                        logger.info("    @%s в исключениях, пропуск", buyer_uname)
                        continue

                    if buyer_uname.lower() == donor.lower():
                        continue

                    logger.info("    Покупатель: @%s — собираю статистику...", buyer_uname)
                    await safe_sleep(1.0)

                    stats = await get_channel_stats(client, buyer_uname)
                    if not stats:
                        logger.info("    Не удалось получить статистику @%s", buyer_uname)
                        continue

                    ad_link = f"https://t.me/{donor}/{message.id}"

                    result = {
                        "date": message.date.astimezone(MSK).strftime("%Y-%m-%d %H:%M"),
                        "buyer_username": f"@{buyer_uname}",
                        "subs": stats["subscribers"],
                        "avg_views": stats["avg_views"],
                        "er": stats["er"],
                        "ad_link": ad_link,
                        "donor_channel": f"@{donor}",
                    }
                    results.append(result)

                    logger.info(
                        "    ✓ @%s: %d подп, %d avg views, ER=%.2f%%",
                        buyer_uname,
                        stats["subscribers"],
                        stats["avg_views"],
                        stats["er"],
                    )

        except errors.FloodWaitError as e:
            logger.warning("FloodWait %d сек при чтении @%s", e.seconds, donor)
            await asyncio.sleep(e.seconds + 5)
        except errors.ChannelPrivateError:
            logger.error("Канал @%s приватный или недоступен", donor)
        except Exception as e:
            logger.error("Ошибка при чтении @%s: %s", donor, e)

        logger.info(
            "  @%s: проверено %d сообщ., найдено %d рекл. постов",
            donor, msg_count, ad_count,
        )
        await safe_sleep(1.5)

    return results


# ─────────────────────────────────────────────
# Сохранение CSV
# ─────────────────────────────────────────────

def save_csv(results: list[dict]) -> str:
    """Сохраняет результаты в CSV-файл. Возвращает путь к файлу."""
    path = os.path.join(script_dir, config.CSV_FILE)

    fieldnames = [
        "date", "buyer_username", "subs", "avg_views", "er",
        "ad_link", "donor_channel",
    ]

    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in results:
            writer.writerow(row)

    logger.info("CSV сохранён: %s (%d записей)", path, len(results))
    return path


# ─────────────────────────────────────────────
# Формирование и отправка отчёта
# ─────────────────────────────────────────────

def build_report_text(results: list[dict], now: datetime) -> str:
    """Формирует текстовый отчёт для отправки в Telegram."""
    date_str = now.strftime("%d.%m.%Y %H:%M МСК")

    if not results:
        return (
            f"📊 **Отчёт о рекламе** — {date_str}\n\n"
            "Рекламных размещений за последние 24 часа не найдено."
        )

    lines = [f"📊 **Отчёт о рекламе** — {date_str}\n"]
    lines.append(f"Найдено размещений: **{len(results)}**\n")

    for i, r in enumerate(results, 1):
        lines.append(
            f"{i}. {r['buyer_username']}\n"
            f"   👥 {r['subs']:,} подп. | 👁 {r['avg_views']:,} avg views | "
            f"📈 ER {r['er']}%\n"
            f"   📎 Донор: {r['donor_channel']} | 📅 {r['date']}\n"
            f"   🔗 {r['ad_link']}\n"
        )

    return "\n".join(lines)


async def send_report(client: TelegramClient, results: list[dict], now: datetime):
    """Отправляет текстовый отчёт + CSV-файл в личный чат."""
    if not config.MY_CHAT_ID:
        logger.warning("MY_CHAT_ID не задан в config.py — отчёт не отправлен")
        return

    report_text = build_report_text(results, now)

    try:
        await client.send_message(config.MY_CHAT_ID, report_text, parse_mode="md")
        logger.info("Текстовый отчёт отправлен в чат %s", config.MY_CHAT_ID)
    except errors.FloodWaitError as e:
        logger.warning("FloodWait %d сек при отправке отчёта", e.seconds)
        await asyncio.sleep(e.seconds + 5)
        await client.send_message(config.MY_CHAT_ID, report_text, parse_mode="md")
    except Exception as e:
        logger.error("Ошибка отправки отчёта: %s", e)

    if results:
        csv_path = os.path.join(script_dir, config.CSV_FILE)
        if os.path.exists(csv_path):
            try:
                await client.send_file(
                    config.MY_CHAT_ID,
                    csv_path,
                    caption=f"📎 Рекламные размещения за {now.strftime('%d.%m.%Y')}",
                )
                logger.info("CSV-файл отправлен")
            except Exception as e:
                logger.error("Ошибка отправки CSV: %s", e)


# ─────────────────────────────────────────────
# Точка входа
# ─────────────────────────────────────────────

async def main():
    """Основная функция: подключение, сканирование, отчёт."""
    logger.info("=" * 50)
    now = datetime.now(MSK)
    logger.info("Запуск Ad Monitor: %s", now.strftime("%Y-%m-%d %H:%M МСК"))
    logger.info("=" * 50)

    if not config.API_ID or not config.API_HASH:
        logger.error(
            "API_ID и API_HASH не заданы в config.py!\n"
            "Получите их на https://my.telegram.org → API development tools"
        )
        sys.exit(1)

    session_path = os.path.join(script_dir, config.SESSION_NAME)
    client = TelegramClient(session_path, config.API_ID, config.API_HASH)

    await client.start()
    me = await client.get_me()
    logger.info("Авторизован как: %s (@%s)", me.first_name, me.username or "—")

    if "--login" in sys.argv:
        logger.info("Режим --login: авторизация прошла успешно, выход")
        await client.disconnect()
        return

    test_mode = "--test" in sys.argv
    if test_mode:
        logger.info("⚠ Тестовый режим: отчёт НЕ будет отправлен в Telegram")

    donors = load_donors()
    if not donors:
        logger.error("Список доноров пуст! Добавьте каналы в %s", config.DONORS_FILE)
        await client.disconnect()
        return

    since = load_last_checked()
    logger.info(
        "Проверяю сообщения с: %s",
        since.astimezone(MSK).strftime("%Y-%m-%d %H:%M МСК"),
    )

    results = await scan_donors(client, donors, since)

    if results:
        csv_path = save_csv(results)
        logger.info("Найдено %d рекламных размещений", len(results))
    else:
        logger.info("Рекламных размещений не найдено")

    if not test_mode:
        await send_report(client, results, now)
    else:
        report = build_report_text(results, now)
        logger.info("Тестовый отчёт:\n%s", report)

    save_last_checked(datetime.now(timezone.utc))
    logger.info("Метка last_checked обновлена")

    await client.disconnect()
    logger.info("Готово! Сессия закрыта.")


if __name__ == "__main__":
    asyncio.run(main())
