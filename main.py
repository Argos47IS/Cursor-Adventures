#!/usr/bin/env python3
"""
Telegram Channel Analytics — daily collector & reporter.

Searches for IT / AI / tech / vibecoding Telegram channels (5k–40k subs),
filters those that purchased ads in the last 7 days, and writes results
to Google Sheets (or a local CSV fallback).

Runs every day at 16:00 MSK by default.
"""

import argparse
import logging
import sys
import time

import schedule

import config
from collector import run_collection
from google_sheets import save_report

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


def job() -> None:
    """Execute one collection cycle and save the report."""
    try:
        rows = run_collection()
        if rows:
            save_report(rows)
            logger.info("Report saved — %d channels.", len(rows))
        else:
            logger.info("Nothing to report this cycle.")
    except Exception:
        logger.exception("Unhandled error during collection cycle.")


def run_once() -> None:
    """Run the collection once and exit."""
    logger.info("Running single collection…")
    job()


def run_scheduled() -> None:
    """Run on a daily schedule (default 16:00 MSK)."""
    schedule_time = config.SCHEDULE_TIME
    logger.info("Scheduling daily run at %s MSK.", schedule_time)
    schedule.every().day.at(schedule_time).do(job)

    logger.info("Scheduler started. Press Ctrl+C to stop.")
    try:
        while True:
            schedule.run_pending()
            time.sleep(30)
    except KeyboardInterrupt:
        logger.info("Scheduler stopped.")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Telegram channel analytics collector"
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="Run collection once and exit (skip scheduling)",
    )
    args = parser.parse_args()

    if not config.TGSTAT_API_TOKEN:
        logger.error(
            "TGSTAT_API_TOKEN is not set. "
            "Copy .env.example to .env and fill in your token."
        )
        sys.exit(1)

    if args.once:
        run_once()
    else:
        run_scheduled()


if __name__ == "__main__":
    main()
