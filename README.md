# Telegram Channel Stats — Автопарсер

Автоматический парсер статистики Telegram-каналов из **TGStat.ru** и **Telemetr.me** — без платных API.

Каждый день в 16:00 МСК скрипт:
1. Парсит рейтинги каналов с TGStat и Telemetr (категория: IT/технологии)
2. Фильтрует каналы с 5 000 – 40 000 подписчиков
3. Отправляет отчёт в Telegram-бот: список каналов + CSV-файл

## Как это работает

- **Playwright** (headless Chromium) открывает публичные страницы рейтингов
- Stealth-настройки обходят CloudFlare и антибот-защиту
- Данные парсятся из HTML и перехваченных JSON-ответов API
- Никаких платных API — только публичные веб-страницы
- Отчёт отправляется через бесплатный Telegram Bot API

## Быстрый старт

### 1. Создайте Telegram-бота

1. Напишите `@BotFather` в Telegram → `/newbot`
2. Скопируйте **токен бота**
3. Напишите `@userinfobot` → скопируйте свой **Chat ID**
4. Напишите своему новому боту `/start` (иначе он не сможет вам писать)

### 2. Установите зависимости

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
playwright install chromium
```

### 3. Настройте `.env`

```bash
cp .env.example .env
```

Заполните:

```
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
TELEGRAM_CHAT_ID=123456789
```

### 4. Запустите

**Одноразовый запуск (тест):**

```bash
python collector.py --dry-run
```

**Одноразовый запуск с уведомлением в Telegram:**

```bash
python collector.py
```

**Ежедневно в 16:00 МСК (планировщик):**

```bash
python scheduler.py
```

**Через cron (альтернатива):**

```bash
bash setup_cron.sh
```

## Структура проекта

```
├── collector.py       # Оркестратор: парсинг → фильтрация → CSV → Telegram
├── scraper.py         # Playwright-парсер TGStat + Telemetr.me
├── notifier.py        # Отправка отчёта через Telegram Bot API
├── scheduler.py       # Планировщик (APScheduler) — 16:00 МСК
├── db.py              # SQLite — история для расчёта прироста
├── setup_cron.sh      # Установка cron-задачи
├── requirements.txt   # Python-зависимости
├── .env.example       # Шаблон конфигурации
├── reports/           # CSV-отчёты (создаётся автоматически)
└── debug/             # HTML-дампы для отладки парсера
```

## Формат отчёта в Telegram

```
📊 Статистика Telegram-каналов
📅 20.03.2026 16:00
🏷 IT / AI / Технологии / Вайбкодинг
👥 Диапазон: 5 000 – 40 000 подписчиков
━━━━━━━━━━━━━━━━━━━━

1. AI News  @ai_newz
   👥 15 200 подп. | 📈 +320 за 24ч

2. Python Scripts  @python_scripts
   👥 8 400 подп. | 📈 +45 за 24ч

━━━━━━━━━━━━━━━━━━━━
Всего каналов: 25
```

Плюс CSV-файл с полными данными во вложении.

## Настройки (.env)

| Переменная | Описание | По умолчанию |
|-----------|----------|-------------|
| `TELEGRAM_BOT_TOKEN` | Токен бота от @BotFather | — |
| `TELEGRAM_CHAT_ID` | ID чата для отправки отчётов | — |
| `MIN_SUBSCRIBERS` | Минимум подписчиков | 5000 |
| `MAX_SUBSCRIBERS` | Максимум подписчиков | 40000 |

## Источники данных

| Источник | URL | Что парсится |
|----------|-----|-------------|
| TGStat | `tgstat.ru/ratings/channels/tech` | Рейтинг IT-каналов |
| Telemetr | `telemetr.me/catalog/IT` | Каталог IT-каналов |

Скрипт автоматически пробует оба источника. Если один недоступен — использует другой.

## Решение проблем

| Проблема | Решение |
|----------|---------|
| CloudFlare блокирует | Попробуйте через VPN/прокси; проверьте `debug/*.html` |
| Бот не отправляет | Убедитесь, что написали боту `/start` |
| Нет каналов в отчёте | Проверьте `debug/` — там HTML-дампы страниц |
| Нет прироста | Прирост появится со второго дня (нужна история в БД) |
| Playwright не запускается | Выполните `playwright install chromium` |
