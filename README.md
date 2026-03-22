# Cursor-Adventures

## File Extension Changer (Android App)

An Android application for changing file extensions. See [FileExtensionChanger/README.md](FileExtensionChanger/README.md) for full documentation and usage guide.

---

# Telegram Ad Monitor — Мониторинг рекламы в Telegram-каналах

Автоматический скрипт для Android (Termux), который отслеживает рекламные размещения в Telegram-каналах. Работает как инструмент рекламного менеджера — находит, кто и где покупает рекламу, собирает статистику покупателей.

## Что делает

Каждый день в **16:00 МСК** скрипт:

1. Подключается к Telegram через ваш аккаунт (Telethon)
2. Проверяет список каналов-доноров за последние 24 часа
3. Находит рекламные посты (по маркерам: «Реклама», «Sponsored», erid и т.д.)
4. Извлекает username покупателя из ссылок, кнопок, упоминаний, форвардов
5. Собирает статистику покупателя: подписчики, средние просмотры, ER
6. Сохраняет всё в `ads_today.csv`
7. Отправляет CSV + текстовый отчёт вам в личный Telegram

## Требования

- Телефон на **Android 7+**
- **Termux** (версия из F-Droid, НЕ из Google Play)
- Аккаунт Telegram
- ~100 МБ свободного места
- Интернет

**Не нужно:** ПК, root, платные API, TGStat, Telemetr, VPN.

## Структура проекта

```
├── main.py             # Основной скрипт (async, Telethon)
├── config.py           # Конфигурация (api_id, api_hash, chat_id)
├── donors.txt          # Список каналов-доноров
├── setup.sh            # Скрипт полной установки в Termux
├── requirements.txt    # Python-зависимости
├── ads_today.csv       # Выходной CSV (создаётся автоматически)
├── last_checked.txt    # Метка последней проверки
└── log.txt             # Лог работы
```

---

# Пошаговая инструкция по установке

## Шаг 1. Установка Termux

> **Важно:** ставьте Termux **из F-Droid**, а не из Google Play. Версия в Play Store устарела.

1. Откройте в браузере: https://f-droid.org/packages/com.termux/
2. Скачайте APK и установите
3. Запустите Termux — откроется терминал

Также установите **Termux:Boot** (для автозапуска после перезагрузки):

https://f-droid.org/packages/com.termux.boot/

## Шаг 2. Скачивание проекта и установка

Скопируйте и вставьте в Termux (одна команда):

```bash
pkg update -y && pkg upgrade -y && pkg install -y python git nano && cd ~ && git clone https://github.com/Argos47IS/Cursor-Adventures.git ad-monitor && cd ad-monitor && bash setup.sh
```

Или по шагам:

```bash
pkg update && pkg upgrade -y
pkg install python git nano -y
cd ~
git clone https://github.com/Argos47IS/Cursor-Adventures.git ad-monitor
cd ad-monitor
bash setup.sh
```

Установка займёт 2-3 минуты.

## Шаг 3. Получение API ID и API Hash

Это нужно для подключения к Telegram через Telethon (userbot).

1. Откройте в браузере: **https://my.telegram.org**
2. Войдите по номеру телефона (тому же, что в Telegram)
3. Введите код подтверждения из Telegram
4. Нажмите **«API development tools»**
5. Заполните форму:
   - **App title:** любое (например `Ad Monitor`)
   - **Short name:** любое (например `admon`)
   - **Platform:** Other
   - **Description:** пустое или любое
6. Нажмите **«Create application»**
7. Скопируйте:
   - **App api_id** — число (например `12345678`)
   - **App api_hash** — строка (например `a1b2c3d4e5f6g7h8i9j0k1l2`)

> **Эти данные создаются один раз и не меняются.** Никому не передавайте api_hash!

## Шаг 4. Узнать свой Chat ID

1. Откройте Telegram
2. Найдите бота **@userinfobot**
3. Отправьте ему любое сообщение
4. Он ответит вашим **Id** — это числовой Chat ID (например `987654321`)
5. Скопируйте его

## Шаг 5. Настройка конфигурации

Откройте config.py:

```bash
cd ~/ad-monitor
nano config.py
```

Заполните три поля:

```python
API_ID = 12345678          # ваш api_id (число)
API_HASH = "a1b2c3d4..."   # ваш api_hash (строка в кавычках)
MY_CHAT_ID = 987654321     # ваш chat_id (число)
```

Сохраните: `Ctrl+O` → `Enter` → `Ctrl+X`

## Шаг 6. Первый запуск (авторизация)

```bash
python3 main.py --login
```

Скрипт попросит:
1. **Номер телефона** — введите с кодом страны (например `+79161234567`)
2. **Код из Telegram** — придёт в приложение Telegram
3. **Пароль 2FA** — если включена двухфакторная аутентификация

После успешной авторизации создастся файл `ad_monitor.session` — он хранит сессию. **Больше логиниться не нужно.**

## Шаг 7. Настройка списка доноров

```bash
nano donors.txt
```

Добавьте каналы, рекламу в которых хотите отслеживать:

```
@breakingmash
@rian_ru
@durov
@varlamov
```

По одному username на строку, с `@` или без. Строки с `#` — комментарии.

## Шаг 8. Тестовый запуск

```bash
python3 main.py --test
```

Скрипт покажет результаты в терминале, но **не отправит** отчёт в Telegram. Это для проверки, что всё работает.

## Шаг 9. Полный запуск

```bash
python3 main.py
```

Скрипт проверит доноров, соберёт статистику и отправит отчёт + CSV вам в Telegram.

## Шаг 10. Автоматический запуск (16:00 МСК ежедневно)

### Вариант A: cron (рекомендуется)

Cron уже настроен скриптом `setup.sh`. Активируйте его:

```bash
sv-enable crond
```

Проверьте задачу:

```bash
crontab -l
```

Должно показать строку с `0 13 * * *` (13:00 UTC = 16:00 МСК).

### Вариант B: Ручной cron

```bash
crontab -e
```

Вставьте:

```
0 13 * * * cd /data/data/com.termux/files/home/ad-monitor && python3 main.py >> log.txt 2>&1
```

### Автозапуск после перезагрузки

1. Установите **Termux:Boot** из F-Droid
2. Запустите его один раз (для активации)
3. Скрипт `setup.sh` уже создал файл автозапуска в `~/.termux/boot/`

При каждой перезагрузке телефона crond запустится автоматически.

### Termux Wake Lock (обязательно!)

Чтобы Android не убивал Termux в фоне:

```bash
termux-wake-lock
```

Также:
- Потяните уведомление Termux вниз → **Acquire wakelock**
- Настройки → Приложения → Termux → Батарея → **Без ограничений**
- На MIUI/Samsung: добавьте Termux в автозапуск

---

# Формат CSV-отчёта

Файл `ads_today.csv` содержит колонки:

| Колонка | Описание |
|---------|----------|
| `date` | Дата размещения рекламы (МСК) |
| `buyer_username` | Username канала-покупателя |
| `subs` | Подписчики покупателя |
| `avg_views` | Средние просмотры (последние 10 постов) |
| `er` | ER% (avg_views / subscribers × 100) |
| `ad_link` | Ссылка на рекламный пост |
| `donor_channel` | Канал-донор, где найдена реклама |

## Формат отчёта в Telegram

```
📊 Отчёт о рекламе — 22.03.2026 16:00 МСК

Найдено размещений: 5

1. @crypto_news
   👥 45,200 подп. | 👁 12,300 avg views | 📈 ER 27.21%
   📎 Донор: @breakingmash | 📅 2026-03-22 14:30
   🔗 https://t.me/breakingmash/12345

2. @tech_insider
   👥 23,100 подп. | 👁 8,400 avg views | 📈 ER 36.36%
   📎 Донор: @durov | 📅 2026-03-22 11:15
   🔗 https://t.me/durov/98765
```

---

# Полезные команды

```bash
# Запуск вручную (полный цикл + отчёт)
cd ~/ad-monitor && python3 main.py

# Тестовый запуск (без отправки)
python3 main.py --test

# Только авторизация
python3 main.py --login

# Проверить cron-задачу
crontab -l

# Посмотреть логи
cat log.txt

# Посмотреть последний CSV
cat ads_today.csv

# Обновить список доноров
nano donors.txt

# Обновить проект до последней версии
cd ~/ad-monitor && git pull

# Проверить, запущен ли crond
sv status crond
```

---

# Как обновлять список доноров

```bash
cd ~/ad-monitor
nano donors.txt
```

Добавьте или удалите каналы. Формат:

```
# Это комментарий — будет пропущен
@channel_username
another_channel
https://t.me/third_channel
```

Поддерживаются:
- `@username`
- `username` (без @)
- `https://t.me/username` (полная ссылка)

Изменения вступят в силу при следующем запуске.

---

# Решение проблем

## FloodWaitError (Telegram rate limit)

**Симптом:** в логах `FloodWait X сек`

**Причина:** слишком частые запросы к API Telegram.

**Решение:** скрипт автоматически ждёт указанное время и повторяет запрос. Если проблема повторяется:
- Увеличьте паузу в `config.py`: `REQUEST_DELAY_MIN = 2.0`, `REQUEST_DELAY_MAX = 4.0`
- Уменьшите количество доноров в `donors.txt`

## Сессия истекла / просит логин повторно

**Решение:**

```bash
rm ad_monitor.session
python3 main.py --login
```

## «ChannelPrivateError» — канал недоступен

**Причина:** канал приватный или вы не подписаны.

**Решение:** подпишитесь на канал в Telegram или удалите его из `donors.txt`.

## «Permission denied» при запуске setup.sh

```bash
chmod +x setup.sh
bash setup.sh
```

## crond не запускается

```bash
pkg install cronie termux-services -y
sv-enable crond
sv up crond
sv status crond
```

## Скрипт не находит рекламу

Не все каналы размечают рекламу маркерами. Скрипт ищет:
- Атрибут `message.sponsored`
- Текстовые маркеры: «Реклама», «Sponsored», «#реклама», «erid:» и др.

Если в канале реклама не помечена — она не будет обнаружена. Это ограничение подхода.

## Termux убивает процесс в фоне

1. `termux-wake-lock`
2. Настройки → Приложения → Termux → Батарея → **Без ограничений**
3. На Xiaomi/Samsung: Настройки → Батарея → Автозапуск → включите Termux

## «No module named telethon»

```bash
pip install -r requirements.txt
```

## Код подтверждения не приходит при --login

- Подождите 1-2 минуты
- Проверьте Telegram — код может прийти в «Saved Messages»
- Попробуйте ещё раз: `python3 main.py --login`

## Как сменить аккаунт Telegram

```bash
rm ad_monitor.session
python3 main.py --login
```

---

# Безопасность

- **api_hash** — никогда никому не передавайте
- **Файл сессии** (`ad_monitor.session`) — содержит ключи авторизации. Не копируйте его на другие устройства
- Скрипт работает от вашего аккаунта — Telegram может заблокировать за подозрительную активность при слишком частых запросах. Паузы между запросами (`REQUEST_DELAY_*`) защищают от этого.
- Не добавляйте `config.py` с вашими данными в публичные репозитории

---

# Технические детали

- **Асинхронный код** на `asyncio` + Telethon
- **Сессия** сохраняется в файл — повторный логин не нужен
- **FloodWait** обрабатывается автоматически (ждёт + повторяет)
- **Rate-limit** защита: пауза 1-2.5 сек между каждым запросом
- **last_checked** timestamp — предотвращает дублирование старых постов
- **Timezone** МСК (`Europe/Moscow`) через `zoneinfo`
- **Логирование** в файл `log.txt` + консоль
