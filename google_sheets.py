"""Google Sheets and CSV writers for channel reports."""

import csv
import logging
import os
from datetime import datetime

import config

logger = logging.getLogger(__name__)

HEADER = [
    "Дата",
    "Название канала",
    "Подписчики",
    "Ссылка",
    "Прирост за 24ч",
    "Упоминания (7д)",
]


def _google_sheets_available() -> bool:
    return bool(
        config.GOOGLE_SHEET_ID
        and os.path.exists(config.GOOGLE_CREDENTIALS_FILE)
    )


def write_to_google_sheets(rows: list[list]) -> None:
    """Append rows to a Google Sheets spreadsheet.

    Each row: [date, name, subscribers, link, growth_24h, mentions_7d]
    """
    import gspread
    from google.oauth2.service_account import Credentials

    scopes = [
        "https://www.googleapis.com/auth/spreadsheets",
        "https://www.googleapis.com/auth/drive",
    ]
    creds = Credentials.from_service_account_file(
        config.GOOGLE_CREDENTIALS_FILE, scopes=scopes
    )
    gc = gspread.authorize(creds)
    spreadsheet = gc.open_by_key(config.GOOGLE_SHEET_ID)

    try:
        worksheet = spreadsheet.worksheet(config.GOOGLE_SHEET_NAME)
    except gspread.WorksheetNotFound:
        worksheet = spreadsheet.add_worksheet(
            title=config.GOOGLE_SHEET_NAME, rows=1000, cols=len(HEADER)
        )

    existing = worksheet.get_all_values()
    if not existing:
        worksheet.append_row(HEADER, value_input_option="USER_ENTERED")

    for row in rows:
        worksheet.append_row(row, value_input_option="USER_ENTERED")

    logger.info("Wrote %d rows to Google Sheets.", len(rows))


def write_to_csv(rows: list[list]) -> None:
    """Append rows to a local CSV file."""
    path = config.CSV_OUTPUT_PATH
    file_exists = os.path.isfile(path)

    with open(path, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if not file_exists:
            writer.writerow(HEADER)
        writer.writerows(rows)

    logger.info("Wrote %d rows to %s.", len(rows), path)


def save_report(rows: list[list]) -> None:
    """Save the report to Google Sheets if configured, otherwise to CSV."""
    if _google_sheets_available():
        try:
            write_to_google_sheets(rows)
            return
        except Exception:
            logger.exception(
                "Failed to write to Google Sheets, falling back to CSV."
            )

    write_to_csv(rows)
