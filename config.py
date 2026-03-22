"""
Конфигурация проекта для сбора рекламодателей из Telegram sponsored messages.

Все настройки загружаются из переменных окружения (.env файл или GitHub Secrets).
Для корректной работы необходимо задать API_ID, API_HASH и SESSION_STRING.
"""

import os
import json
import base64
import logging
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# --- Telegram API (Telethon) ---
API_ID: int = int(os.getenv("API_ID", "0"))
API_HASH: str = os.getenv("API_HASH", "")
SESSION_STRING: str = os.getenv("SESSION_STRING", "")

# --- Telegram Bot (уведомления) ---
BOT_TOKEN: str = os.getenv("TELEGRAM_BOT_TOKEN", "")
CHAT_ID: str = os.getenv("TELEGRAM_CHAT_ID", "")

# --- Google Sheets ---
GOOGLE_SHEET_NAME: str = os.getenv("GOOGLE_SHEET_NAME", "TG_Advertisers")
# JSON-ключ: путь к файлу, base64-строка или сырой JSON
GOOGLE_SA_CREDENTIALS: str = os.getenv("GOOGLE_SA_CREDENTIALS", "")

# --- Каналы ---
CHANNELS_FILE: str = os.getenv("CHANNELS_FILE", "channels.txt")

# --- Задержки между запросами (секунды) ---
MIN_DELAY: float = float(os.getenv("MIN_DELAY", "3"))
MAX_DELAY: float = float(os.getenv("MAX_DELAY", "8"))

# --- Лимиты ---
MAX_CHANNELS: int = int(os.getenv("MAX_CHANNELS", "300"))

# --- Пути ---
BASE_DIR: Path = Path(__file__).parent
OUTPUT_DIR: Path = BASE_DIR / "output"
LOG_FILE: Path = BASE_DIR / "log.txt"


def get_monitored_channels() -> list[str]:
    """
    Загрузить список каналов для мониторинга.

    Приоритет:
    1. Переменная окружения MONITORED_CHANNELS (через запятую)
    2. Файл channels.txt (по одному каналу на строку)
    3. Дефолтный список популярных каналов
    """
    env_channels = os.getenv("MONITORED_CHANNELS", "")
    if env_channels:
        channels = [
            ch.strip().lstrip("@")
            for ch in env_channels.split(",")
            if ch.strip()
        ]
        if channels:
            logger.info("Загружено %d каналов из MONITORED_CHANNELS", len(channels))
            return channels[:MAX_CHANNELS]

    channels_path = BASE_DIR / CHANNELS_FILE
    if channels_path.exists():
        with open(channels_path, "r", encoding="utf-8") as f:
            channels = [
                line.strip().lstrip("@")
                for line in f
                if line.strip() and not line.strip().startswith("#")
            ]
        if channels:
            logger.info("Загружено %d каналов из %s", len(channels), CHANNELS_FILE)
            return channels[:MAX_CHANNELS]

    default = [
        "durov", "telegram", "tginfo", "habr_com", "vc_ru",
        "breakingmash", "lentachold", "varlamov", "banksta",
        "readovkanews", "rian_ru", "shot_shot", "rt_russian",
        "exploitex", "tproger", "kod_ru", "devby", "proglib",
        "nuancesprog", "frontend_info", "pythonl", "javascript_ru",
        "golang_ru", "openai_ru", "ailib",
    ]
    logger.info("Используется дефолтный список из %d каналов", len(default))
    return default


def get_google_credentials() -> Optional[dict]:
    """
    Получить credentials для Google Sheets.

    Поддерживает три формата GOOGLE_SA_CREDENTIALS:
    1. Путь к JSON-файлу сервисного аккаунта
    2. Base64-кодированная JSON-строка (удобно для GitHub Secrets)
    3. Сырая JSON-строка
    """
    if not GOOGLE_SA_CREDENTIALS:
        return None

    cred_path = Path(GOOGLE_SA_CREDENTIALS)
    if cred_path.exists():
        with open(cred_path, "r") as f:
            return json.load(f)

    try:
        decoded = base64.b64decode(GOOGLE_SA_CREDENTIALS)
        return json.loads(decoded)
    except Exception:
        pass

    try:
        return json.loads(GOOGLE_SA_CREDENTIALS)
    except Exception:
        logger.error("Не удалось разобрать GOOGLE_SA_CREDENTIALS")
        return None
