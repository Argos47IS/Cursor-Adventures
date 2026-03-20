# Telegram Channel Analytics

Автоматический сбор статистики Telegram-каналов в нишах IT, ИИ, технологии и вайбкодинг.

## Что делает

1. Ищет каналы по заданным ключевым словам через TGStat API
2. Фильтрует каналы с 5 000–40 000 подписчиков
3. Оставляет только каналы, которые **закупали рекламу** на других каналах за последние 7 дней (определяется по упоминаниям канала в других каналах)
4. Собирает прирост подписчиков за 24 часа
5. Записывает результат в Google Sheets (или в локальный CSV)

### Формат таблицы

| Дата | Название канала | Подписчики | Ссылка | Прирост за 24ч |
|------|----------------|------------|--------|----------------|
| 2026-03-20 | AI News | 12 500 | https://t.me/ainews | +340 |

## Быстрый старт

### 1. Установка зависимостей

```bash
pip install -r requirements.txt
```

### 2. Настройка

Скопируйте `.env.example` в `.env` и заполните:

```bash
cp .env.example .env
```

**Обязательно:**
- `TGSTAT_API_TOKEN` — API-токен TGStat ([получить здесь](https://api.tgstat.ru/))

**Опционально (для Google Sheets):**
- `GOOGLE_CREDENTIALS_FILE` — путь к JSON-ключу сервисного аккаунта Google
- `GOOGLE_SHEET_ID` — ID таблицы из URL (`https://docs.google.com/spreadsheets/d/<ID>/...`)
- `GOOGLE_SHEET_NAME` — название листа в таблице

Если Google Sheets не настроен, результаты сохраняются в CSV-файл (`channels_report.csv`).

### 3. Запуск

**Одноразовый запуск** (собрать данные и выйти):

```bash
python main.py --once
```

**Ежедневный запуск** (по расписанию в 16:00 МСК):

```bash
python main.py
```

## Настройка Google Sheets

1. Создайте проект в [Google Cloud Console](https://console.cloud.google.com/)
2. Включите Google Sheets API и Google Drive API
3. Создайте сервисный аккаунт и скачайте JSON-ключ
4. Поделитесь таблицей с email сервисного аккаунта (права на редактирование)
5. Укажите путь к ключу и ID таблицы в `.env`

## Настройка расписания

По умолчанию скрипт запускается каждый день в 16:00 по МСК.

Для изменения — установите `SCHEDULE_TIME` в `.env` (формат `HH:MM`).

### Через systemd (рекомендуется для сервера)

Создайте файл `/etc/systemd/system/tg-analytics.service`:

```ini
[Unit]
Description=Telegram Channel Analytics
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/path/to/project
ExecStart=/usr/bin/python3 main.py
Restart=always
RestartSec=60
Environment=TZ=Europe/Moscow

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable tg-analytics
sudo systemctl start tg-analytics
```

### Через cron (альтернатива)

```bash
# Запуск каждый день в 16:00 МСК
0 16 * * * cd /path/to/project && TZ=Europe/Moscow /usr/bin/python3 main.py --once
```

## Параметры конфигурации

| Переменная | Описание | По умолчанию |
|---|---|---|
| `TGSTAT_API_TOKEN` | API-токен TGStat | — |
| `GOOGLE_CREDENTIALS_FILE` | Путь к JSON-ключу Google | `credentials.json` |
| `GOOGLE_SHEET_ID` | ID Google-таблицы | — |
| `GOOGLE_SHEET_NAME` | Название листа | `Telegram Channels` |
| `SCHEDULE_TIME` | Время запуска (МСК) | `16:00` |
| `MIN_SUBSCRIBERS` | Минимум подписчиков | `5000` |
| `MAX_SUBSCRIBERS` | Максимум подписчиков | `40000` |
| `AD_LOOKBACK_DAYS` | Период проверки рекламы (дни) | `7` |
| `CSV_OUTPUT_PATH` | Путь к CSV-файлу | `channels_report.csv` |

## Структура проекта

```
├── main.py             # Точка входа + планировщик
├── collector.py        # Основная логика сбора данных
├── tgstat_api.py       # Клиент TGStat API
├── google_sheets.py    # Запись в Google Sheets / CSV
├── config.py           # Конфигурация
├── requirements.txt    # Зависимости Python
├── .env.example        # Шаблон переменных окружения
└── .gitignore
```

## Как работает определение рекламных закупок

Скрипт использует эндпоинт `channels/mentions` TGStat API. Когда канал закупает рекламу на другом канале, рекламный пост содержит ссылку/упоминание канала-покупателя. Таким образом, наличие упоминаний канала в других каналах за последние 7 дней является индикатором рекламной активности.

## Требования

- Python 3.10+
- API-токен TGStat (тариф API Stat S или выше)
- (Опционально) Google Cloud сервисный аккаунт для записи в Sheets
