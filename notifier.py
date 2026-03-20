"""
Telegram Bot notifier.

Sends formatted channel statistics reports to a Telegram chat
using the free Bot API (no paid services).
"""

import logging
import os
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

MAX_MESSAGE_LENGTH = 4000  # Telegram limit is 4096, leave margin


def _format_number(n: int) -> str:
    """Format number with space separators: 15200 -> '15 200'."""
    return f"{n:,}".replace(",", " ")


def _format_growth(g: Optional[int]) -> str:
    if g is None:
        return "нет данных"
    sign = "+" if g >= 0 else ""
    return f"{sign}{_format_number(g)}"


def build_report_message(channels: list, date_str: str) -> list[str]:
    """
    Build formatted Telegram messages from channel data.
    Splits into multiple messages if too long.
    Returns list of HTML-formatted strings.
    """
    if not channels:
        return [
            f"<b>Отчёт за {date_str}</b>\n\n"
            "Подходящих каналов не найдено.\n"
            "Проверьте логи для деталей."
        ]

    header = (
        f"📊 <b>Статистика Telegram-каналов</b>\n"
        f"📅 {date_str}\n"
        f"🏷 IT / AI / Технологии / Вайбкодинг\n"
        f"👥 Диапазон: 5 000 – 40 000 подписчиков\n"
        f"━━━━━━━━━━━━━━━━━━━━\n\n"
    )

    footer = f"\n━━━━━━━━━━━━━━━━━━━━\nВсего каналов: {len(channels)}"

    messages = []
    current = header
    count = 0

    for ch in channels:
        count += 1
        username_display = f"@{ch.username}" if ch.username else ""
        growth_str = _format_growth(ch.growth_24h)

        entry = (
            f"<b>{count}. {ch.title}</b>  {username_display}\n"
            f"   👥 {_format_number(ch.subscribers)} подп. | "
            f"📈 {growth_str} за 24ч\n\n"
        )

        if len(current) + len(entry) > MAX_MESSAGE_LENGTH:
            messages.append(current.rstrip())
            current = entry
        else:
            current += entry

    current += footer
    messages.append(current.rstrip())
    return messages


async def send_telegram_message(bot_token: str, chat_id: str, text: str, parse_mode: str = "HTML"):
    """Send a text message via Telegram Bot API."""
    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": parse_mode,
        "disable_web_page_preview": True,
    }
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(url, json=payload)
        if resp.status_code != 200:
            logger.error("Telegram API ошибка: %s %s", resp.status_code, resp.text)
            return False
        logger.info("Сообщение отправлено в Telegram")
        return True


async def send_telegram_document(bot_token: str, chat_id: str, file_path: str, caption: str = ""):
    """Send a file (CSV report) via Telegram Bot API."""
    url = f"https://api.telegram.org/bot{bot_token}/sendDocument"
    data = {"chat_id": chat_id}
    if caption:
        data["caption"] = caption[:1024]

    async with httpx.AsyncClient(timeout=60) as client:
        with open(file_path, "rb") as f:
            files = {"document": (os.path.basename(file_path), f)}
            resp = await client.post(url, data=data, files=files)
            if resp.status_code != 200:
                logger.error("Telegram API ошибка (документ): %s %s", resp.status_code, resp.text)
                return False
            logger.info("CSV-файл отправлен в Telegram: %s", file_path)
            return True


async def send_report(channels: list, date_str: str, csv_path: Optional[str] = None):
    """
    Send the full report to Telegram: formatted messages + CSV file.
    Reads bot token and chat ID from environment variables.
    """
    bot_token = os.getenv("TELEGRAM_BOT_TOKEN", "")
    chat_id = os.getenv("TELEGRAM_CHAT_ID", "")

    if not bot_token or not chat_id:
        logger.warning(
            "TELEGRAM_BOT_TOKEN или TELEGRAM_CHAT_ID не заданы — "
            "отчёт не будет отправлен в Telegram"
        )
        return False

    messages = build_report_message(channels, date_str)

    for msg in messages:
        success = await send_telegram_message(bot_token, chat_id, msg)
        if not success:
            return False

    if csv_path and os.path.exists(csv_path):
        await send_telegram_document(
            bot_token, chat_id, csv_path,
            caption=f"📎 CSV-отчёт за {date_str}",
        )

    return True
