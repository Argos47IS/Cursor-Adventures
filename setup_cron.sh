#!/usr/bin/env bash
#
# Устанавливает cron-задачу для ежедневного запуска сбора статистики
# в 16:00 по МСК (= 13:00 UTC).
#
# Использование:  bash setup_cron.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PYTHON="$(command -v python3 || command -v python)"
COLLECTOR="$SCRIPT_DIR/collector.py"
LOG_FILE="$SCRIPT_DIR/collector_cron.log"

CRON_LINE="0 13 * * * cd $SCRIPT_DIR && $PYTHON $COLLECTOR >> $LOG_FILE 2>&1"

(crontab -l 2>/dev/null | grep -v "$COLLECTOR"; echo "$CRON_LINE") | crontab -

echo "Cron-задача установлена:"
echo "  $CRON_LINE"
echo ""
echo "Проверить: crontab -l"
echo "Логи:      $LOG_FILE"
