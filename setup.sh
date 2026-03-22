#!/data/data/com.termux/files/usr/bin/bash
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# setup.sh — Полная установка Telegram Ad Monitor в Termux
#
# Использование:
#   bash setup.sh
#
# Что делает:
#   1. Обновляет пакеты Termux
#   2. Устанавливает Python 3, git, nano
#   3. Устанавливает cronie + termux-services для cron
#   4. Устанавливает Python-зависимости (Telethon)
#   5. Настраивает cron-задачу на 16:00 МСК (13:00 UTC)
#   6. Создаёт скрипт автозапуска для Termux:Boot
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

set -euo pipefail

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Telegram Ad Monitor — Установка"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# ─── Шаг 1: Обновление Termux ───
echo "[1/6] Обновление пакетов Termux..."
pkg update -y
pkg upgrade -y
echo "  ✓ Пакеты обновлены"
echo ""

# ─── Шаг 2: Установка системных пакетов ───
echo "[2/6] Установка Python, Git, Nano..."
pkg install -y python git nano
echo "  ✓ Python $(python3 --version 2>&1 | awk '{print $2}')"
echo "  ✓ Git $(git --version 2>&1 | awk '{print $3}')"
echo ""

# ─── Шаг 3: Установка cronie для cron-задач ───
echo "[3/6] Установка cronie и termux-services..."
pkg install -y cronie termux-services
echo "  ✓ cronie установлен"
echo ""

# ─── Шаг 4: Установка Python-зависимостей ───
echo "[4/6] Установка Python-зависимостей..."
pip install --upgrade pip
pip install -r "$PROJECT_DIR/requirements.txt"
echo "  ✓ Telethon и зависимости установлены"
echo ""

# ─── Шаг 5: Настройка cron (16:00 МСК = 13:00 UTC) ───
echo "[5/6] Настройка cron-задачи (16:00 МСК ежедневно)..."
PYTHON_PATH="$(command -v python3 || command -v python)"
CRON_CMD="0 13 * * * cd $PROJECT_DIR && $PYTHON_PATH $PROJECT_DIR/main.py >> $PROJECT_DIR/log.txt 2>&1"

(crontab -l 2>/dev/null | grep -v "main.py" || true; echo "$CRON_CMD") | crontab -

echo "  ✓ Cron-задача установлена:"
echo "    $CRON_CMD"
echo ""

# ─── Шаг 6: Скрипт автозапуска для Termux:Boot ───
echo "[6/6] Настройка автозапуска (Termux:Boot)..."
BOOT_DIR="$HOME/.termux/boot"
BOOT_SCRIPT="$BOOT_DIR/start-ad-monitor.sh"

mkdir -p "$BOOT_DIR"
cat > "$BOOT_SCRIPT" << BOOTEOF
#!/data/data/com.termux/files/usr/bin/bash
# Автозапуск crond при загрузке телефона
sv-enable crond 2>/dev/null || true
sv up crond 2>/dev/null || true
BOOTEOF
chmod +x "$BOOT_SCRIPT"

echo "  ✓ Скрипт автозапуска создан: $BOOT_SCRIPT"
echo ""

# ─── Итог ───
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Установка завершена!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Следующие шаги:"
echo ""
echo "  1. Откройте config.py и заполните:"
echo "     - API_ID и API_HASH (с https://my.telegram.org)"
echo "     - MY_CHAT_ID (ваш числовой ID)"
echo ""
echo "     nano $PROJECT_DIR/config.py"
echo ""
echo "  2. Первый запуск (авторизация в Telegram):"
echo ""
echo "     python3 $PROJECT_DIR/main.py --login"
echo ""
echo "     Введите номер телефона и код из Telegram."
echo "     Сессия сохранится, больше логиниться не нужно."
echo ""
echo "  3. Отредактируйте список доноров:"
echo ""
echo "     nano $PROJECT_DIR/donors.txt"
echo ""
echo "  4. Тестовый запуск:"
echo ""
echo "     python3 $PROJECT_DIR/main.py --test"
echo ""
echo "  5. Запустите crond (один раз после перезапуска Termux):"
echo ""
echo "     sv-enable crond"
echo ""
echo "  Готово! Скрипт будет запускаться каждый день в 16:00 МСК."
echo ""
echo "  Для работы после перезагрузки установите Termux:Boot из F-Droid."
