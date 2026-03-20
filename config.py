import os
from dotenv import load_dotenv

load_dotenv()

TGSTAT_API_TOKEN = os.getenv("TGSTAT_API_TOKEN", "")
TGSTAT_BASE_URL = "https://api.tgstat.ru"

GOOGLE_CREDENTIALS_FILE = os.getenv("GOOGLE_CREDENTIALS_FILE", "credentials.json")
GOOGLE_SHEET_ID = os.getenv("GOOGLE_SHEET_ID", "")
GOOGLE_SHEET_NAME = os.getenv("GOOGLE_SHEET_NAME", "Telegram Channels")

SCHEDULE_TIME = os.getenv("SCHEDULE_TIME", "16:00")

MIN_SUBSCRIBERS = int(os.getenv("MIN_SUBSCRIBERS", "5000"))
MAX_SUBSCRIBERS = int(os.getenv("MAX_SUBSCRIBERS", "40000"))

AD_LOOKBACK_DAYS = int(os.getenv("AD_LOOKBACK_DAYS", "7"))

CSV_OUTPUT_PATH = os.getenv("CSV_OUTPUT_PATH", "channels_report.csv")

SEARCH_CATEGORIES = [
    "tech",
    "IT",
]

SEARCH_QUERIES = [
    "IT технологии",
    "искусственный интеллект",
    "нейросети",
    "вайбкодинг",
    "программирование AI",
    "технологии ИИ",
    "machine learning",
    "разработка",
]
