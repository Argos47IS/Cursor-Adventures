"""
Скрипт для генерации SESSION_STRING.

Запустите один раз локально:
    python generate_session.py

Скрипт попросит ввести API_ID, API_HASH, номер телефона и код подтверждения.
В результате вы получите строку, которую нужно сохранить в .env как SESSION_STRING.

ВАЖНО: Запускайте только на своём компьютере, НЕ в GitHub Actions!
"""

import asyncio
from telethon import TelegramClient
from telethon.sessions import StringSession


async def main():
    print("=" * 50)
    print("Генератор SESSION_STRING для Telethon")
    print("=" * 50)
    print()

    api_id = int(input("Введите API_ID: "))
    api_hash = input("Введите API_HASH: ")

    client = TelegramClient(StringSession(), api_id, api_hash)
    await client.start()

    session_string = client.session.save()

    print()
    print("=" * 50)
    print("Ваша SESSION_STRING (скопируйте в .env):")
    print("=" * 50)
    print()
    print(session_string)
    print()
    print("=" * 50)
    print("Сохраните эту строку в .env файл как:")
    print("SESSION_STRING=<ваша_строка>")
    print()
    print("НИКОМУ не передавайте SESSION_STRING!")
    print("Это эквивалент пароля от вашего Telegram аккаунта.")
    print("=" * 50)

    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
