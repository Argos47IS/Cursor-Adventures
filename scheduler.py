"""
Scheduler: runs the collector every day at 16:00 MSK.

Usage:
    python scheduler.py          — start the scheduler (runs indefinitely)
    python collector.py          — run a single collection immediately
    python collector.py --dry-run — run without sending Telegram notification
"""

import logging
import sys
from datetime import timezone, timedelta

from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger

from collector import main as run_collector

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)

MSK = timezone(timedelta(hours=3))


def job():
    logger.info("Запуск сбора статистики по расписанию…")
    try:
        run_collector()
    except Exception as e:
        logger.error("Ошибка при сборе: %s", e, exc_info=True)


def main():
    scheduler = BlockingScheduler(timezone=MSK)

    scheduler.add_job(
        job,
        trigger=CronTrigger(hour=16, minute=0, timezone=MSK),
        id="daily_collection",
        name="Ежедневный сбор статистики в 16:00 МСК",
        replace_existing=True,
    )

    next_run = scheduler.get_job("daily_collection").next_run_time
    logger.info("Планировщик запущен. Следующий запуск: %s", next_run.strftime("%Y-%m-%d %H:%M %Z"))
    logger.info("Для остановки нажмите Ctrl+C")

    try:
        scheduler.start()
    except (KeyboardInterrupt, SystemExit):
        logger.info("Планировщик остановлен.")


if __name__ == "__main__":
    main()
