# Запуск парсера на Android (Termux) без root

Подробный пошаговый гайд. Root не нужен, всё работает из коробки.

---

## Что понадобится

- Телефон на Android 7+
- ~200 МБ свободного места
- Интернет-соединение
- 5 минут времени

---

## Шаг 1. Установка Termux

> Важно: ставьте Termux **из F-Droid**, а не из Google Play. Версия в Google Play устарела и не поддерживается.

1. Откройте в браузере: https://f-droid.org/packages/com.termux/
2. Скачайте и установите APK
3. Запустите Termux — откроется терминал

Сразу обновите пакеты:

```bash
pkg update && pkg upgrade -y
```

Если система спрашивает что-то про конфигурацию — просто жмите Enter (оставить по умолчанию).

---

## Шаг 2. Установка Python и Git

```bash
pkg install python git -y
```

Проверьте:

```bash
python --version
git --version
```

Должно показать Python 3.x и git 2.x.

---

## Шаг 3. Скачивание парсера

```bash
cd ~
git clone https://github.com/Argos47IS/Cursor-Adventures.git tg-stats
cd tg-stats
```

---

## Шаг 4. Установка Python-зависимостей

Для Termux есть отдельный облегчённый файл зависимостей (без Playwright):

```bash
pip install -r requirements-termux.txt
```

Это установит:
- `httpx` — HTTP-клиент для запросов к Telemetr.me
- `beautifulsoup4` — парсер HTML
- `apscheduler` — планировщик задач
- `python-dotenv` — чтение .env-конфигурации

---

## Шаг 5. Создание Telegram-бота

Бот будет присылать вам отчёты. Это бесплатно.

### 5.1. Получите токен бота

1. Откройте Telegram
2. Найдите бота **@BotFather**
3. Отправьте ему: `/newbot`
4. Введите имя бота (например: `Мой Парсер Каналов`)
5. Введите username бота (например: `my_channel_stats_bot`)
6. BotFather пришлёт токен вида: `7123456789:AAH1bGJkx9...`
7. **Скопируйте этот токен** — он нужен для настройки

### 5.2. Узнайте свой Chat ID

1. Найдите бота **@userinfobot** в Telegram
2. Отправьте ему любое сообщение
3. Он ответит вашим **Id** — это числовой Chat ID (например: `987654321`)
4. **Скопируйте его**

### 5.3. Активируйте бота

Важно: **напишите вашему новому боту** `/start` — без этого он не сможет вам отправлять сообщения.

---

## Шаг 6. Настройка конфигурации

```bash
cp .env.example .env
```

Откройте файл для редактирования:

```bash
nano .env
```

Замените значения на свои:

```
TELEGRAM_BOT_TOKEN=7123456789:AAH1bGJkx9...
TELEGRAM_CHAT_ID=987654321
MIN_SUBSCRIBERS=5000
MAX_SUBSCRIBERS=40000
```

Сохраните: `Ctrl+O` → `Enter` → `Ctrl+X`

> Если `nano` не установлен: `pkg install nano`

---

## Шаг 7. Тестовый запуск

Сначала проверьте без отправки в Telegram:

```bash
python collector.py --lite --dry-run
```

Вы увидите таблицу с каналами в терминале:

```
=== Сбор статистики: 2026-03-20 16:00 МСК ===
Диапазон подписчиков: 5000 – 40000
Режим: lite (httpx, без Playwright)
Telemetr (lite): 100 каналов ...
...
#  | Канал                    | Username           | Подписчики | Прирост
---+--------------------------+--------------------+------------+--------
1  | КиберПанк | Технологии   | @tech_cyberpunk    | 38,723     | +820
2  | UX Live                  | @uxlive            | 36,030     | +150
...
=== Сбор завершён: 32 каналов ===
```

Если таблица появилась — всё работает.

---

## Шаг 8. Запуск с отправкой в Telegram

```bash
python collector.py --lite
```

Бот пришлёт в Telegram:
- Форматированное сообщение со списком каналов
- CSV-файл с полными данными

---

## Шаг 9. Автоматический ежедневный запуск

Есть три варианта. Выберите один.

### Вариант A: Встроенный планировщик (самый простой)

```bash
python scheduler.py
```

Скрипт будет работать в фоне и запускать сбор каждый день в 16:00 МСК. Минус: если закрыть Termux — планировщик остановится.

Чтобы он работал в фоне:

```bash
nohup python scheduler.py > scheduler.log 2>&1 &
```

Проверить, работает ли:

```bash
ps aux | grep scheduler
```

### Вариант B: cronie (аналог cron)

```bash
pkg install cronie termux-services -y
sv-enable crond
```

Перезапустите Termux, затем:

```bash
crontab -e
```

Вставьте строку (13:00 UTC = 16:00 МСК):

```
0 13 * * * cd /data/data/com.termux/files/home/tg-stats && python collector.py --lite >> collector.log 2>&1
```

Сохраните и закройте редактор. Проверьте:

```bash
crontab -l
```

### Вариант C: Termux:Boot (запуск при старте телефона)

1. Установите приложение **Termux:Boot** из F-Droid
2. Запустите его один раз (для активации)
3. Создайте скрипт автозапуска:

```bash
mkdir -p ~/.termux/boot
nano ~/.termux/boot/start-parser.sh
```

Содержимое:

```bash
#!/data/data/com.termux/files/usr/bin/bash
cd /data/data/com.termux/files/home/tg-stats
nohup python scheduler.py > scheduler.log 2>&1 &
```

Сделайте исполняемым:

```bash
chmod +x ~/.termux/boot/start-parser.sh
```

Теперь планировщик будет запускаться автоматически при каждом включении/перезагрузке телефона.

---

## Шаг 10. Termux:Wake Lock (против засыпания)

Android может убить фоновый процесс Termux. Чтобы этого не произошло:

1. В Termux потяните панель уведомлений вниз
2. Нажмите на уведомление Termux → **Acquire wakelock**

Или из терминала:

```bash
termux-wake-lock
```

Также рекомендуется:
- Отключить оптимизацию батареи для Termux (Настройки → Приложения → Termux → Батарея → Без ограничений)
- На MIUI/Samsung: добавить Termux в автозапуск и отключить экономию батареи

---

## Полезные команды

```bash
# Посмотреть последний отчёт
ls -la reports/
cat reports/report_*.csv

# Посмотреть логи
cat collector.log

# Обновить парсер до последней версии
cd ~/tg-stats && git pull

# Остановить планировщик
pkill -f scheduler.py

# Запустить сбор вручную прямо сейчас
cd ~/tg-stats && python collector.py --lite
```

---

## Решение проблем

### «pip: command not found»

```bash
pkg install python
```

### «No module named 'httpx'»

```bash
pip install -r requirements-termux.txt
```

### «Permission denied»

```bash
chmod +x setup_cron.sh
```

### Бот не присылает сообщения

1. Проверьте, что написали боту `/start`
2. Проверьте токен и Chat ID в `.env`
3. Запустите тест:

```bash
python -c "
import os
from dotenv import load_dotenv
load_dotenv()
token = os.getenv('TELEGRAM_BOT_TOKEN', '')
chat = os.getenv('TELEGRAM_CHAT_ID', '')
print(f'Token: {token[:10]}...' if token else 'TOKEN НЕ ЗАДАН!')
print(f'Chat ID: {chat}' if chat else 'CHAT_ID НЕ ЗАДАН!')
"
```

### Нет прироста за 24ч (все «нет данных»)

При первом запуске прироста не будет — ему нужны данные за два дня. После второго запуска (на следующий день) прирост появится для каналов из TGStat (Telemetr даёт прирост сразу).

### «Connection error» или «timeout»

Проверьте интернет-соединение. Если вы за VPN — попробуйте без него (или наоборот).

### Termux убивает процесс в фоне

См. Шаг 10 про Wake Lock и отключение оптимизации батареи.

---

## Сравнение режимов

| | Lite (Termux) | Full (сервер/ПК) |
|---|---|---|
| Установка | 2 минуты | 5 минут |
| Размер зависимостей | ~20 МБ | ~200 МБ |
| Источники | Telemetr | TGStat + Telemetr |
| Каналов в отчёте | ~30 | ~55 |
| Время сбора | ~7 секунд | ~55 секунд |
| Прирост за 24ч | Сразу (Telemetr даёт) | Частичный + из БД |
| Потребление RAM | ~30 МБ | ~300 МБ |
