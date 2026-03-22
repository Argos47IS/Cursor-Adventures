"""
Главный скрипт: ежедневный сбор рекламодателей из Telegram sponsored messages.

Логика:
1. Подключение к Telegram через Telethon (session_string).
2. Обход списка хост-каналов (50–300 штук).
3. Для каждого канала — запрос GetSponsoredMessagesRequest.
4. Извлечение рекламодателей через extract_advertiser().
5. Дедупликация.
6. Сохранение: Google Sheets + CSV.
7. Отправка отчёта через Telegram Bot.
"""

import asyncio
import logging
import random
import sys
from datetime import datetime, timezone, timedelta

from telethon import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import (
    FloodWaitError,
    UserDeactivatedBanError,
    AuthKeyUnregisteredError,
    ChannelInvalidError,
    ChannelPrivateError,
    ChatAdminRequiredError,
)
from telethon.tl.functions.channels import GetSponsoredMessagesRequest
from telethon.tl.types import InputChannel

import config
from utils import (
    extract_advertiser,
    save_to_csv,
    save_to_google_sheets,
    send_telegram_report,
    send_telegram_file,
    MSK,
)

# --- Логирование: консоль + файл ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(str(config.LOG_FILE), encoding="utf-8"),
    ],
)
logger = logging.getLogger("main")


# =============================================================================
# Retry-декоратор с экспоненциальной задержкой
# =============================================================================

async def retry_with_backoff(coro_factory, max_retries: int = 3, base_delay: float = 5.0):
    """
    Выполнить корутину с retry и экспоненциальной задержкой.
    coro_factory — callable, возвращающий корутину (чтобы можно было вызвать заново).
    """
    for attempt in range(max_retries + 1):
        try:
            return await coro_factory()
        except FloodWaitError as e:
            wait_time = e.seconds + 5
            logger.warning(
                "FloodWaitError: ожидание %d секунд (попытка %d/%d)",
                wait_time, attempt + 1, max_retries + 1,
            )
            if attempt < max_retries:
                await asyncio.sleep(wait_time)
            else:
                raise
        except (ConnectionError, OSError) as e:
            delay = base_delay * (2 ** attempt) + random.uniform(0, 2)
            logger.warning(
                "Ошибка соединения: %s. Retry через %.1f сек (попытка %d/%d)",
                e, delay, attempt + 1, max_retries + 1,
            )
            if attempt < max_retries:
                await asyncio.sleep(delay)
            else:
                raise


# =============================================================================
# Получение sponsored messages для одного канала
# =============================================================================

async def get_sponsored_messages(client: TelegramClient, channel_username: str) -> list:
    """
    Получить sponsored messages для канала.

    Возвращает список sponsored message объектов или пустой список при ошибке.
    """
    try:
        entity = await client.get_entity(channel_username)
    except (ValueError, ChannelInvalidError):
        logger.warning("Канал @%s не найден", channel_username)
        return []
    except ChannelPrivateError:
        logger.warning("Канал @%s приватный", channel_username)
        return []
    except FloodWaitError as e:
        logger.warning("FloodWait при get_entity @%s: %d сек", channel_username, e.seconds)
        await asyncio.sleep(e.seconds + 3)
        return []
    except Exception as e:
        logger.warning("Ошибка get_entity @%s: %s", channel_username, e)
        return []

    try:
        input_channel = InputChannel(entity.id, entity.access_hash)

        async def _fetch():
            return await client(GetSponsoredMessagesRequest(channel=input_channel))

        result = await retry_with_backoff(_fetch, max_retries=2, base_delay=5.0)

        messages = getattr(result, "messages", []) or []
        if messages:
            logger.info("@%s: найдено %d sponsored messages", channel_username, len(messages))
        return messages

    except FloodWaitError as e:
        logger.warning("FloodWait при GetSponsored @%s: %d сек", channel_username, e.seconds)
        await asyncio.sleep(e.seconds + 3)
        return []
    except ChatAdminRequiredError:
        logger.debug("@%s: нет прав для sponsored messages", channel_username)
        return []
    except Exception as e:
        logger.warning("Ошибка GetSponsored @%s: %s", channel_username, type(e).__name__)
        return []


# =============================================================================
# Обработка результатов
# =============================================================================

def format_record(
    advertiser: dict,
    host_channel: str,
    message,
    now: datetime,
) -> dict:
    """Сформировать запись для сохранения в таблицу/CSV."""
    text = getattr(message, "message", "") or ""
    preview = text[:150].replace("\n", " ").strip()

    views = getattr(message, "views", None) or 0

    post_link = ""
    if advertiser["channel_id"] and not advertiser["username"].startswith("unknown_"):
        post_link = f"https://t.me/{advertiser['username']}"

    return {
        "date": now.strftime("%Y-%m-%d"),
        "advertiser_username": advertiser["username"],
        "advertiser_link": advertiser["full_link"],
        "host_channel": host_channel,
        "ad_preview": preview,
        "ad_date": now.strftime("%Y-%m-%d %H:%M"),
        "views": views,
        "post_link": post_link,
    }


# =============================================================================
# Главная функция
# =============================================================================

async def main():
    """Основной цикл сбора рекламодателей."""
    now = datetime.now(MSK)
    logger.info("=" * 60)
    logger.info("Запуск сбора рекламодателей: %s МСК", now.strftime("%Y-%m-%d %H:%M"))
    logger.info("=" * 60)

    # --- Валидация конфигурации ---
    if not config.API_ID or not config.API_HASH:
        logger.error("API_ID и API_HASH не заданы. Задайте их в .env или GitHub Secrets.")
        sys.exit(1)

    if not config.SESSION_STRING:
        logger.error("SESSION_STRING не задана. Сгенерируйте её (см. README).")
        sys.exit(1)

    # --- Подключение к Telegram ---
    logger.info("Подключение к Telegram...")
    client = TelegramClient(
        StringSession(config.SESSION_STRING),
        config.API_ID,
        config.API_HASH,
    )

    try:
        await client.connect()
        if not await client.is_user_authorized():
            logger.error("Сессия невалидна. Пересоздайте SESSION_STRING.")
            sys.exit(1)
        me = await client.get_me()
        logger.info("Авторизован как: %s (@%s)", me.first_name, me.username or "no_username")
    except (UserDeactivatedBanError, AuthKeyUnregisteredError) as e:
        logger.error("Аккаунт заблокирован или ключ невалиден: %s", e)
        sys.exit(1)
    except Exception as e:
        logger.error("Ошибка подключения к Telegram: %s", e)
        sys.exit(1)

    # --- Загрузка списка каналов ---
    channels = config.get_monitored_channels()
    logger.info("Каналов для мониторинга: %d", len(channels))

    # --- Обход каналов ---
    all_records: list[dict] = []
    seen_advertisers: set[str] = set()
    errors_count: int = 0
    total_sponsored: int = 0

    for idx, channel in enumerate(channels, 1):
        logger.info("[%d/%d] Проверяю @%s...", idx, len(channels), channel)

        try:
            messages = await get_sponsored_messages(client, channel)
        except Exception as e:
            logger.error("Критическая ошибка для @%s: %s", channel, e, exc_info=True)
            errors_count += 1
            continue

        if not messages:
            logger.debug("@%s: нет sponsored messages", channel)
        else:
            total_sponsored += len(messages)

        for msg in messages:
            try:
                advertiser = await extract_advertiser(client, msg)
                uname = advertiser["username"]

                record = format_record(advertiser, channel, msg, now)

                if uname not in seen_advertisers:
                    seen_advertisers.add(uname)
                    all_records.append(record)
                    logger.info(
                        "  → Новый рекламодатель: @%s (в @%s)",
                        uname, channel,
                    )
                else:
                    logger.debug("  → Дубликат: @%s", uname)

            except Exception as e:
                logger.warning("Ошибка извлечения рекламодателя из @%s: %s", channel, e)
                errors_count += 1

        # Задержка между каналами (3–8 секунд)
        if idx < len(channels):
            delay = random.uniform(config.MIN_DELAY, config.MAX_DELAY)
            logger.debug("Задержка %.1f сек...", delay)
            await asyncio.sleep(delay)

    # --- Отключение от Telegram ---
    await client.disconnect()

    # --- Статистика ---
    stats = {
        "total_channels": len(channels),
        "errors": errors_count,
        "new_count": len(all_records),
        "total_found": total_sponsored,
    }

    logger.info("=" * 60)
    logger.info("Итоги:")
    logger.info("  Проверено каналов: %d", stats["total_channels"])
    logger.info("  Найдено sponsored messages: %d", stats["total_found"])
    logger.info("  Уникальных рекламодателей: %d", stats["new_count"])
    logger.info("  Ошибок: %d", stats["errors"])
    logger.info("=" * 60)

    if not all_records:
        logger.info("Рекламодатели не найдены. Возможно, в каналах нет sponsored messages.")

    # --- Сохранение в CSV ---
    csv_path = save_to_csv(all_records, config.OUTPUT_DIR)

    # --- Сохранение в Google Sheets ---
    google_creds = config.get_google_credentials()
    if google_creds:
        save_to_google_sheets(all_records, google_creds, config.GOOGLE_SHEET_NAME)
    else:
        logger.info("Google Sheets: credentials не заданы, пропускаю.")

    # --- Отправка отчёта в Telegram ---
    await send_telegram_report(
        config.BOT_TOKEN,
        config.CHAT_ID,
        all_records,
        stats,
    )

    # Отправка CSV-файла
    if all_records:
        await send_telegram_file(
            config.BOT_TOKEN,
            config.CHAT_ID,
            csv_path,
            caption=f"📎 CSV-отчёт рекламодателей за {now.strftime('%d.%m.%Y')}",
        )

    logger.info("Сбор завершён. Всего уникальных рекламодателей: %d", len(all_records))


if __name__ == "__main__":
    asyncio.run(main())
