"""
Утилиты для извлечения данных о рекламодателях из sponsored messages.

Главная функция — extract_advertiser(): извлекает канал-рекламодатель из
спонсированного сообщения с использованием нескольких стратегий с приоритетами.

Дополнительно: хелперы для работы с Google Sheets, CSV, Telegram Bot API.
"""

import csv
import logging
import os
import re
import asyncio
import hashlib
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

MSK = timezone(timedelta(hours=3))


# =============================================================================
# EXTRACT_ADVERTISER — самая важная функция
# =============================================================================

async def extract_advertiser(client, message) -> dict:
    """
    Извлечь информацию о рекламодателе из sponsored message.

    Стратегии (по приоритету):

    1. message.from_id → PeerChannel → получить entity → username
    2. Кнопки reply_markup (InlineKeyboardButton с t.me/ ссылками)
    3. Entities в тексте (MessageEntityMention, MessageEntityTextUrl, MessageEntityUrl)
    4. Регулярки по тексту (@username, t.me/username)
    5. Fallback: "unknown_[hash]"

    Возвращает dict:
        {
            'username': str,        — @username канала-рекламодателя
            'channel_id': int,      — числовой ID канала (0 если неизвестен)
            'full_link': str        — полная ссылка t.me/username
        }
    """
    from telethon.tl.types import (
        PeerChannel, PeerUser,
        MessageEntityMention, MessageEntityTextUrl, MessageEntityUrl,
        KeyboardButtonUrl,
    )

    username: Optional[str] = None
    channel_id: int = 0

    # --- Стратегия 1: from_id (PeerChannel) ---
    if hasattr(message, "from_id") and message.from_id:
        peer = message.from_id
        if isinstance(peer, PeerChannel):
            channel_id = peer.channel_id
            try:
                entity = await client.get_entity(peer)
                if hasattr(entity, "username") and entity.username:
                    username = entity.username
                    logger.debug("Стратегия 1 (from_id): @%s (id=%d)", username, channel_id)
            except Exception as e:
                logger.debug("Не удалось получить entity для channel_id=%d: %s", channel_id, e)
        elif isinstance(peer, PeerUser):
            try:
                entity = await client.get_entity(peer)
                if hasattr(entity, "username") and entity.username:
                    username = entity.username
                    logger.debug("Стратегия 1 (from_id/user): @%s", username)
            except Exception as e:
                logger.debug("Не удалось получить entity для user: %s", e)

    if username:
        return _make_result(username, channel_id)

    # --- Стратегия 1b: sponsor_info / channel_post (новые поля в API) ---
    for attr_name in ("sponsor_info", "channel_post", "chat_invite"):
        info = getattr(message, attr_name, None)
        if info and hasattr(info, "channel") and info.channel:
            ch = info.channel
            if hasattr(ch, "username") and ch.username:
                username = ch.username
                channel_id = getattr(ch, "id", 0)
                logger.debug("Стратегия 1b (%s): @%s", attr_name, username)
                return _make_result(username, channel_id)

    # --- Стратегия 2: reply_markup (кнопки с URL) ---
    if hasattr(message, "reply_markup") and message.reply_markup:
        rows = getattr(message.reply_markup, "rows", [])
        for row in rows:
            buttons = getattr(row, "buttons", [])
            for btn in buttons:
                if isinstance(btn, KeyboardButtonUrl) or hasattr(btn, "url"):
                    url = getattr(btn, "url", "")
                    if url:
                        extracted = _extract_username_from_url(url)
                        if extracted:
                            username = extracted
                            logger.debug("Стратегия 2 (кнопка URL): @%s из %s", username, url)
                            return _make_result(username, channel_id)

    # --- Стратегия 3: entities в тексте ---
    text = getattr(message, "message", "") or ""
    entities = getattr(message, "entities", []) or []

    for entity in entities:
        if isinstance(entity, MessageEntityMention):
            # @username прямо в тексте
            mention = text[entity.offset:entity.offset + entity.length]
            mention = mention.lstrip("@")
            if mention and _is_valid_username(mention):
                username = mention
                logger.debug("Стратегия 3 (MessageEntityMention): @%s", username)
                return _make_result(username, channel_id)

        elif isinstance(entity, MessageEntityTextUrl):
            # Текст со скрытой ссылкой
            url = entity.url or ""
            extracted = _extract_username_from_url(url)
            if extracted:
                username = extracted
                logger.debug("Стратегия 3 (MessageEntityTextUrl): @%s из %s", username, url)
                return _make_result(username, channel_id)

        elif isinstance(entity, MessageEntityUrl):
            # URL в тексте как есть
            url = text[entity.offset:entity.offset + entity.length]
            extracted = _extract_username_from_url(url)
            if extracted:
                username = extracted
                logger.debug("Стратегия 3 (MessageEntityUrl): @%s из %s", username, url)
                return _make_result(username, channel_id)

    # --- Стратегия 4: регулярки по тексту ---
    # t.me/username или @username
    tme_match = re.search(r"(?:https?://)?t\.me/([a-zA-Z_]\w{3,30})", text)
    if tme_match:
        candidate = tme_match.group(1).lower()
        if _is_valid_username(candidate) and candidate not in ("joinchat", "addstickers", "share"):
            username = candidate
            logger.debug("Стратегия 4 (regex t.me): @%s", username)
            return _make_result(username, channel_id)

    at_match = re.search(r"@([a-zA-Z_]\w{3,30})", text)
    if at_match:
        candidate = at_match.group(1)
        if _is_valid_username(candidate):
            username = candidate
            logger.debug("Стратегия 4 (regex @): @%s", username)
            return _make_result(username, channel_id)

    # --- Стратегия 5: fallback ---
    raw = f"{getattr(message, 'random_id', '')}{text[:50]}"
    hash_suffix = hashlib.md5(raw.encode()).hexdigest()[:8]
    username = f"unknown_{hash_suffix}"
    logger.debug("Стратегия 5 (fallback): %s", username)
    return _make_result(username, channel_id)


def _make_result(username: str, channel_id: int) -> dict:
    """Сформировать стандартный результат."""
    username = username.lstrip("@").strip()
    is_unknown = username.startswith("unknown_")
    link = f"https://t.me/{username}" if not is_unknown else ""
    return {
        "username": username,
        "channel_id": channel_id,
        "full_link": link,
    }


def _extract_username_from_url(url: str) -> Optional[str]:
    """Извлечь username канала из URL (t.me/..., telegram.me/...)."""
    patterns = [
        r"(?:https?://)?t\.me/([a-zA-Z_]\w{3,30})(?:\?|$|/)",
        r"(?:https?://)?telegram\.me/([a-zA-Z_]\w{3,30})(?:\?|$|/)",
        r"(?:https?://)?t\.me/([a-zA-Z_]\w{3,30})",
        r"(?:https?://)?telegram\.me/([a-zA-Z_]\w{3,30})",
    ]
    for pattern in patterns:
        m = re.search(pattern, url)
        if m:
            candidate = m.group(1).lower()
            skip = {"joinchat", "addstickers", "share", "proxy", "socks", "iv"}
            if candidate not in skip:
                return candidate
    return None


def _is_valid_username(username: str) -> bool:
    """Проверить, что строка похожа на валидный Telegram username."""
    if not username or len(username) < 4:
        return False
    return bool(re.match(r"^[a-zA-Z_]\w{3,30}$", username))


# =============================================================================
# CSV — резервное сохранение
# =============================================================================

def save_to_csv(records: list[dict], output_dir: Path) -> str:
    """
    Сохранить записи в CSV-файл.

    Возвращает путь к созданному файлу.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    today = datetime.now(MSK).strftime("%Y-%m-%d")
    filepath = output_dir / f"advertisers_{today}.csv"

    fieldnames = [
        "date", "advertiser_username", "advertiser_link",
        "host_channel", "ad_preview", "ad_date",
        "views", "post_link",
    ]

    file_exists = filepath.exists()
    mode = "a" if file_exists else "w"

    with open(filepath, mode, newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        if not file_exists:
            writer.writeheader()
        for rec in records:
            writer.writerow({k: rec.get(k, "") for k in fieldnames})

    logger.info("CSV сохранён: %s (%d записей)", filepath, len(records))
    return str(filepath)


# =============================================================================
# Google Sheets
# =============================================================================

def save_to_google_sheets(records: list[dict], credentials: Optional[dict], sheet_name: str) -> bool:
    """
    Сохранить записи в Google Sheets.

    Создаёт лист (worksheet) с текущей датой, если его нет.
    """
    if not credentials:
        logger.warning("Google credentials не заданы — пропуск сохранения в Sheets")
        return False

    try:
        import gspread
        from google.oauth2.service_account import Credentials
    except ImportError:
        logger.error("gspread/google-auth не установлены. pip install gspread google-auth")
        return False

    try:
        scopes = [
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive",
        ]
        creds = Credentials.from_service_account_info(credentials, scopes=scopes)
        gc = gspread.authorize(creds)

        try:
            spreadsheet = gc.open(sheet_name)
        except gspread.SpreadsheetNotFound:
            logger.error("Таблица '%s' не найдена. Создайте её и дайте доступ сервисному аккаунту.", sheet_name)
            return False

        today = datetime.now(MSK).strftime("%Y-%m-%d")

        try:
            worksheet = spreadsheet.worksheet(today)
        except gspread.WorksheetNotFound:
            worksheet = spreadsheet.add_worksheet(title=today, rows=500, cols=10)
            headers = [
                "Дата", "Рекламодатель", "Ссылка",
                "Хост-канал", "Превью рекламы", "Дата объявления",
                "Просмотры", "Ссылка на пост",
            ]
            worksheet.append_row(headers)

        rows = []
        for rec in records:
            rows.append([
                rec.get("date", ""),
                rec.get("advertiser_username", ""),
                rec.get("advertiser_link", ""),
                rec.get("host_channel", ""),
                rec.get("ad_preview", ""),
                rec.get("ad_date", ""),
                str(rec.get("views", "")),
                rec.get("post_link", ""),
            ])

        if rows:
            worksheet.append_rows(rows, value_input_option="USER_ENTERED")

        logger.info("Google Sheets: сохранено %d записей в лист '%s'", len(rows), today)
        return True

    except Exception as e:
        logger.error("Ошибка при сохранении в Google Sheets: %s", e, exc_info=True)
        return False


# =============================================================================
# Telegram Bot — отправка отчёта
# =============================================================================

async def send_telegram_report(
    bot_token: str,
    chat_id: str,
    new_advertisers: list[dict],
    stats: dict,
) -> bool:
    """
    Отправить красивый отчёт в Telegram через Bot API.

    stats: {
        'total_channels': int,
        'errors': int,
        'new_count': int,
        'total_found': int,
    }
    """
    if not bot_token or not chat_id:
        logger.warning("BOT_TOKEN или CHAT_ID не заданы — отчёт не отправлен")
        return False

    today = datetime.now(MSK).strftime("%d.%m.%Y")

    header = (
        f"📊 <b>Отчёт рекламодателей — {today}</b>\n"
        f"━━━━━━━━━━━━━━━━━━━━\n\n"
        f"🔍 Проверено каналов: <b>{stats.get('total_channels', 0)}</b>\n"
        f"📢 Найдено рекламодателей: <b>{stats.get('total_found', 0)}</b>\n"
        f"🆕 Новых за сегодня: <b>{stats.get('new_count', 0)}</b>\n"
        f"❌ Ошибок: <b>{stats.get('errors', 0)}</b>\n"
        f"\n━━━━━━━━━━━━━━━━━━━━\n"
    )

    # Топ 15 рекламодателей
    top = new_advertisers[:15]
    if top:
        header += "\n<b>Топ рекламодателей:</b>\n\n"
        for i, adv in enumerate(top, 1):
            uname = adv.get("advertiser_username", "unknown")
            link = adv.get("advertiser_link", "")
            host = adv.get("host_channel", "")
            preview = adv.get("ad_preview", "")[:80]

            if link:
                line = f"{i}. <a href=\"{link}\">@{uname}</a>"
            else:
                line = f"{i}. @{uname}"

            if host:
                line += f"  (в @{host})"
            if preview:
                line += f"\n   <i>{preview}…</i>"
            header += line + "\n\n"
    else:
        header += "\n<i>Рекламодателей не найдено.</i>\n"

    remaining = len(new_advertisers) - 15
    if remaining > 0:
        header += f"\n… и ещё {remaining} рекламодателей в полном отчёте.\n"

    header += f"\n━━━━━━━━━━━━━━━━━━━━\n⏰ {datetime.now(MSK).strftime('%H:%M МСК')}"

    try:
        async with httpx.AsyncClient(timeout=30) as http:
            resp = await http.post(
                f"https://api.telegram.org/bot{bot_token}/sendMessage",
                json={
                    "chat_id": chat_id,
                    "text": header,
                    "parse_mode": "HTML",
                    "disable_web_page_preview": True,
                },
            )
            if resp.status_code != 200:
                logger.error("Telegram API ошибка: %s %s", resp.status_code, resp.text)
                return False
            logger.info("Отчёт отправлен в Telegram (chat_id=%s)", chat_id)
            return True
    except Exception as e:
        logger.error("Ошибка отправки в Telegram: %s", e)
        return False


async def send_telegram_file(bot_token: str, chat_id: str, file_path: str, caption: str = "") -> bool:
    """Отправить файл (CSV) в Telegram."""
    if not bot_token or not chat_id:
        return False

    try:
        async with httpx.AsyncClient(timeout=60) as http:
            with open(file_path, "rb") as f:
                resp = await http.post(
                    f"https://api.telegram.org/bot{bot_token}/sendDocument",
                    data={"chat_id": chat_id, "caption": caption[:1024]},
                    files={"document": (os.path.basename(file_path), f)},
                )
                if resp.status_code != 200:
                    logger.error("Telegram API (file) ошибка: %s %s", resp.status_code, resp.text)
                    return False
                logger.info("CSV-файл отправлен в Telegram: %s", file_path)
                return True
    except Exception as e:
        logger.error("Ошибка отправки файла в Telegram: %s", e)
        return False
