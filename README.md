# Telegram Sponsored Messages Scraper

Автоматический ежедневный сбор каналов-рекламодателей из Telegram.

Скрипт проверяет **50–300 каналов**, извлекает **sponsored messages** через Telethon, определяет рекламодателей и сохраняет результаты в **Google Sheets** и **CSV**, а также отправляет отчёт в **Telegram**.

## Структура проекта

```
├── main.py                         # Главный скрипт: обход каналов + сбор данных
├── config.py                       # Конфигурация: загрузка .env, списки каналов
├── utils.py                        # extract_advertiser() + CSV + Sheets + Telegram Bot
├── generate_session.py             # Одноразовый скрипт для генерации SESSION_STRING
├── channels.txt                    # Список каналов для мониторинга
├── requirements.txt                # Зависимости Python
├── .env.example                    # Шаблон переменных окружения
├── .github/workflows/daily.yml     # GitHub Actions cron (16:00 МСК ежедневно)
└── README.md                       # Этот файл
```

## Что собирается

| Поле | Описание |
|------|----------|
| `date` | Дата сбора (YYYY-MM-DD) |
| `advertiser_username` | @username рекламодателя |
| `advertiser_link` | Ссылка t.me/username |
| `host_channel` | Канал, в котором показана реклама |
| `ad_preview` | Первые 150 символов рекламного текста |
| `ad_date` | Дата и время обнаружения |
| `views` | Просмотры (если доступны) |
| `post_link` | Ссылка на канал рекламодателя |

---

## Быстрый старт

### 1. Клонирование и установка

```bash
git clone https://github.com/YOUR_USER/YOUR_REPO.git
cd YOUR_REPO
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows
pip install -r requirements.txt
```

### 2. Получение API_ID и API_HASH

1. Откройте [my.telegram.org/apps](https://my.telegram.org/apps)
2. Войдите с номером телефона Telegram-аккаунта
3. Создайте приложение (любое название)
4. Скопируйте **App api_id** и **App api_hash**

### 3. Генерация SESSION_STRING

```bash
python generate_session.py
```

Скрипт попросит:
- **API_ID** — числовой ID вашего приложения
- **API_HASH** — хеш вашего приложения
- **Номер телефона** — номер Telegram-аккаунта (формат: +79991234567)
- **Код подтверждения** — придёт в Telegram
- **Пароль 2FA** — если включена двухфакторная аутентификация

В результате вы получите длинную строку — это ваша `SESSION_STRING`.

> **ВАЖНО:** `SESSION_STRING` — это эквивалент пароля! Никому не передавайте.

### 4. Настройка .env

```bash
cp .env.example .env
```

Заполните `.env` файл:

```env
API_ID=12345678
API_HASH=0123456789abcdef0123456789abcdef
SESSION_STRING=1BVtsOH...ваша_строка...
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
TELEGRAM_CHAT_ID=987654321
```

### 5. Создание Telegram-бота для уведомлений

1. Откройте [@BotFather](https://t.me/BotFather) в Telegram
2. Отправьте `/newbot`
3. Придумайте имя и username бота
4. Скопируйте **токен** → `TELEGRAM_BOT_TOKEN`
5. Напишите боту `/start`
6. Откройте [@userinfobot](https://t.me/userinfobot), чтобы узнать ваш **Chat ID** → `TELEGRAM_CHAT_ID`

### 6. Настройка Google Sheets (опционально)

#### Создание Service Account:

1. Откройте [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте проект (или выберите существующий)
3. Включите **Google Sheets API** и **Google Drive API**
4. Перейдите в **IAM & Admin** → **Service Accounts**
5. Создайте новый сервисный аккаунт
6. Скачайте JSON-ключ

#### Настройка таблицы:

1. Создайте Google Таблицу с названием `TG_Advertisers` (или своим)
2. Дайте доступ на редактирование email-адресу сервисного аккаунта (из JSON-ключа, поле `client_email`)

#### Указание credentials:

**Вариант A** — путь к файлу:
```env
GOOGLE_SA_CREDENTIALS=service_account.json
```

**Вариант B** — base64 (для GitHub Secrets):
```bash
base64 -w 0 service_account.json
# Скопируйте результат в GOOGLE_SA_CREDENTIALS
```

### 7. Настройка списка каналов

Отредактируйте `channels.txt` — по одному каналу на строку (без `@`):

```
durov
telegram
habr_com
vc_ru
tproger
```

Или задайте через переменную окружения:
```env
MONITORED_CHANNELS=durov,telegram,habr_com,vc_ru
```

### 8. Запуск

```bash
python main.py
```

---

## GitHub Actions (автоматический запуск)

### Настройка Secrets

В репозитории: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Добавьте следующие секреты:

| Secret | Описание |
|--------|----------|
| `API_ID` | Telegram API ID |
| `API_HASH` | Telegram API Hash |
| `SESSION_STRING` | Строка сессии Telethon |
| `TELEGRAM_BOT_TOKEN` | Токен бота для уведомлений |
| `TELEGRAM_CHAT_ID` | Chat ID для отправки отчётов |
| `GOOGLE_SA_CREDENTIALS` | Base64 JSON-ключ Google SA (опционально) |
| `GOOGLE_SHEET_NAME` | Название Google Таблицы (опционально) |

### Расписание

Workflow `.github/workflows/daily.yml` запускается:
- **Автоматически**: каждый день в 13:00 UTC (16:00 МСК)
- **Вручную**: через вкладку Actions → Run workflow

### Ручной запуск

1. Откройте **Actions** в репозитории
2. Выберите **Daily Telegram Advertisers Scraper**
3. Нажмите **Run workflow**
4. При необходимости укажите `max_channels`

---

## Как работает extract_advertiser()

Функция извлекает рекламодателя из sponsored message по приоритетам:

1. **`message.from_id`** → если это `PeerChannel` → получение entity → username
2. **Кнопки `reply_markup`** → `KeyboardButtonUrl` с t.me/ ссылкой
3. **Entities** → `MessageEntityMention`, `MessageEntityTextUrl`, `MessageEntityUrl`
4. **Регулярные выражения** → поиск `@username` и `t.me/username` в тексте
5. **Fallback** → `unknown_[hash]`

---

## Обработка ошибок

- **FloodWaitError** — автоматическое ожидание указанного времени + retry
- **UserDeactivatedBanError** — остановка с сообщением об ошибке
- **ChannelPrivateError** — пропуск канала
- **ConnectionError** — retry с экспоненциальной задержкой (5с → 10с → 20с)
- Между запросами к каналам — случайная пауза 3–8 секунд

---

## FAQ

**Q: Может ли Telegram заблокировать аккаунт?**
A: Скрипт использует консервативные задержки (3–8 секунд) и обрабатывает FloodWait. Риск минимален, но используйте отдельный аккаунт.

**Q: Не все каналы возвращают sponsored messages?**
A: Это нормально. Sponsored messages показываются не во всех каналах и не всегда. Чем больше каналов в списке — тем выше покрытие.

**Q: Как добавить свои каналы?**
A: Отредактируйте `channels.txt` или задайте `MONITORED_CHANNELS` в .env.

**Q: CSV не создаётся?**
A: Проверьте, что директория `output/` создаётся автоматически. Посмотрите лог `log.txt`.

---

## Лицензия

MIT License. Используйте на свой страх и риск. Автор не несёт ответственности за последствия использования.
