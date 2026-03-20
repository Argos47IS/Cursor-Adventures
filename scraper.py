"""
Web scraper for Telegram channel statistics.

Scrapes public pages of TGStat.ru and Telemetr.me without paid APIs.
Uses Playwright with stealth patches to bypass anti-bot protection.

TGStat structure:
  - div.peer-item-row cards
  - Link: /channel/@username/stat
  - Title: div.text-truncate.font-16
  - Subscribers: h4 inside stats columns
  - No daily growth on ratings page

Telemetr structure:
  - <table> with <tr> rows
  - td[0]: channel info (.catalog-table-cell__about-link for name, @username)
  - td[1]: subscriber count
  - td[4]: daily subscriber growth (when sorted by growth)
  - td[10]: category
"""

import asyncio
import logging
import os
import random
import re
from typing import Optional

from playwright.async_api import async_playwright, Page, BrowserContext
from bs4 import BeautifulSoup

from common import ChannelData, parse_number, parse_growth, save_debug_html, DEBUG_DIR

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Stealth helpers
# ---------------------------------------------------------------------------

STEALTH_JS = """
Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
Object.defineProperty(navigator, 'plugins', {
    get: () => [1, 2, 3, 4, 5]
});
Object.defineProperty(navigator, 'languages', {
    get: () => ['ru-RU', 'ru', 'en-US', 'en']
});
window.chrome = { runtime: {} };
const origQuery = window.navigator.permissions.query;
window.navigator.permissions.query = (parameters) =>
    parameters.name === 'notifications'
        ? Promise.resolve({ state: Notification.permission })
        : origQuery(parameters);
"""

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
]


async def create_stealth_context(playwright) -> tuple:
    browser = await playwright.chromium.launch(
        headless=True,
        args=[
            "--disable-blink-features=AutomationControlled",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-infobars",
            "--window-size=1920,1080",
        ],
    )
    context = await browser.new_context(
        user_agent=random.choice(USER_AGENTS),
        viewport={"width": 1920, "height": 1080},
        locale="ru-RU",
        timezone_id="Europe/Moscow",
        java_script_enabled=True,
    )
    return browser, context


async def new_stealth_page(context: BrowserContext) -> Page:
    page = await context.new_page()
    await page.add_init_script(STEALTH_JS)
    return page


def _save_debug(name: str, html: str):
    save_debug_html(name, html)


# ---------------------------------------------------------------------------
# TGStat scraper
# ---------------------------------------------------------------------------

TGSTAT_URLS = [
    "https://tgstat.ru/ratings/channels/tech?sort=ci",
    "https://tgstat.ru/ratings/channels/tech?sort=members",
]


async def _load_page_with_cf_wait(page: Page, url: str, label: str) -> Optional[str]:
    """Load a page, wait through CloudFlare challenge if needed."""
    try:
        resp = await page.goto(url, wait_until="domcontentloaded", timeout=45000)
    except Exception as e:
        logger.warning("%s: не удалось загрузить %s — %s", label, url, e)
        return None

    if resp and resp.status >= 400:
        logger.warning("%s: HTTP %s для %s", label, resp.status, url)
        return None

    await page.wait_for_timeout(4000)

    html = await page.content()
    if "cf-browser-verification" in html or "challenge-platform" in html:
        logger.info("%s: CloudFlare-проверка, ожидание 10с…", label)
        await page.wait_for_timeout(10000)
        html = await page.content()
        if "cf-browser-verification" in html:
            logger.warning("%s: не удалось пройти CloudFlare", label)
            _save_debug(f"{label}_cf_block", html)
            return None

    # Scroll to trigger lazy-loaded content
    for _ in range(3):
        await page.evaluate("window.scrollBy(0, window.innerHeight)")
        await page.wait_for_timeout(1200)

    return await page.content()


def _parse_tgstat_html(html: str) -> list[ChannelData]:
    """
    Parse TGStat ratings HTML.

    Each channel is in a div.peer-item-row card containing:
    - Link to /channel/@username/stat
    - div.text-truncate.font-16 = channel title
    - h4 tags = numbers (subscribers, reach, CI)
    """
    soup = BeautifulSoup(html, "lxml")
    channels = []

    cards = soup.find_all("div", class_="peer-item-row")
    if not cards:
        logger.warning("TGStat: не найдены div.peer-item-row карточки")
        return []

    for card in cards:
        link = card.find("a", href=re.compile(r"/channel/@"))
        if not link:
            continue

        m = re.search(r"@(\w+)", link["href"])
        if not m:
            continue
        username = m.group(1)

        title_el = card.find("div", class_=lambda c: c and "font-16" in c and "text-truncate" in c)
        title = title_el.get_text(strip=True) if title_el else username

        h4_tags = card.find_all("h4")
        subscribers = None
        if h4_tags:
            subscribers = parse_number(h4_tags[0].get_text(strip=True))

        if not subscribers:
            sub_div = card.find("div", class_=lambda c: c and "font-14" in c and "text-truncate" in c)
            if sub_div:
                subscribers = parse_number(sub_div.get_text(strip=True))

        if subscribers is None:
            continue

        channels.append(ChannelData(
            title=title,
            username=username,
            subscribers=subscribers,
            growth_24h=None,
            source="tgstat",
        ))

    return channels


async def scrape_tgstat(page: Page) -> list[ChannelData]:
    all_channels: list[ChannelData] = []
    for url in TGSTAT_URLS:
        logger.info("TGStat: загрузка %s", url)
        html = await _load_page_with_cf_wait(page, url, "tgstat")
        if not html:
            continue

        _save_debug("tgstat_page", html)
        channels = _parse_tgstat_html(html)
        if channels:
            logger.info("TGStat: получено %d каналов с %s", len(channels), url)
            all_channels.extend(channels)
        else:
            logger.warning("TGStat: 0 каналов из %s", url)

        await asyncio.sleep(random.uniform(2, 4))

    return all_channels


# ---------------------------------------------------------------------------
# Telemetr.me scraper
# ---------------------------------------------------------------------------

TELEMETR_URLS = [
    "https://telemetr.me/catalog/IT?sort=subscribers_growth_per_day_desc",
    "https://telemetr.me/catalog/IT?sort=subscribers_count_desc",
]


def _parse_telemetr_html(html: str) -> list[ChannelData]:
    """
    Parse Telemetr.me catalog HTML.

    Structure: <table> → <tr> rows → <td> cells:
      td[0]: channel info (rank, name, "Подписчиков X", @username)
             — name inside a.catalog-table-cell__about-link
      td[1]: subscriber count (plain number)
      td[4]: daily subscriber growth (plain number)
      td[10]: category
    """
    soup = BeautifulSoup(html, "lxml")
    channels = []

    rows = soup.find_all("tr")
    if not rows:
        logger.warning("Telemetr: не найдены <tr> строки")
        return []

    for tr in rows:
        tds = tr.find_all("td")
        if len(tds) < 5:
            continue

        # td[0]: channel info
        info_cell = tds[0]

        name_link = info_cell.find("a", class_=re.compile("about-link"))
        if not name_link:
            continue

        title = name_link.get_text(strip=True)
        href = name_link.get("href", "")
        username = href.strip("/").lstrip("@")
        if not username or not title:
            continue

        # td[1]: subscribers
        subs_text = tds[1].get_text(strip=True) if len(tds) > 1 else ""
        subscribers = parse_number(subs_text)
        if subscribers is None:
            info_text = info_cell.get_text(" ", strip=True)
            m = re.search(r"Подписчиков\s+([\d\s]+)", info_text)
            if m:
                subscribers = parse_number(m.group(1))

        if subscribers is None:
            continue

        # td[4]: daily subscriber growth
        growth = None
        if len(tds) > 4:
            growth_text = tds[4].get_text(strip=True)
            if growth_text and "Доступно" not in growth_text:
                growth = parse_number(growth_text)

        channels.append(ChannelData(
            title=title,
            username=username,
            subscribers=subscribers,
            growth_24h=growth,
            source="telemetr",
        ))

    return channels


async def scrape_telemetr(page: Page) -> list[ChannelData]:
    all_channels: list[ChannelData] = []
    for url in TELEMETR_URLS:
        logger.info("Telemetr: загрузка %s", url)
        html = await _load_page_with_cf_wait(page, url, "telemetr")
        if not html:
            continue

        _save_debug("telemetr_page", html)
        channels = _parse_telemetr_html(html)
        if channels:
            logger.info("Telemetr: получено %d каналов с %s", len(channels), url)
            all_channels.extend(channels)
        else:
            logger.warning("Telemetr: 0 каналов из %s", url)

        await asyncio.sleep(random.uniform(2, 4))

    return all_channels


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

async def scrape_all(min_subs: int = 5000, max_subs: int = 40000) -> list[ChannelData]:
    """
    Scrape channels from all available sources.
    Returns channels filtered to [min_subs, max_subs], sorted by subscribers desc.
    """
    async with async_playwright() as pw:
        browser, context = await create_stealth_context(pw)
        page = await new_stealth_page(context)

        all_channels: list[ChannelData] = []

        try:
            tg_channels = await scrape_tgstat(page)
            all_channels.extend(tg_channels)
        except Exception as e:
            logger.error("TGStat: ошибка — %s", e, exc_info=True)

        await asyncio.sleep(random.uniform(3, 6))

        try:
            tm_channels = await scrape_telemetr(page)
            all_channels.extend(tm_channels)
        except Exception as e:
            logger.error("Telemetr: ошибка — %s", e, exc_info=True)

        await browser.close()

    # Filter by subscriber range
    filtered = [
        ch for ch in all_channels
        if min_subs <= ch.subscribers <= max_subs
    ]

    # Deduplicate by username; prefer entry that has growth data
    seen: dict[str, ChannelData] = {}
    for ch in filtered:
        key = ch.username.lower()
        existing = seen.get(key)
        if existing is None:
            seen[key] = ch
        elif ch.growth_24h is not None and existing.growth_24h is None:
            seen[key] = ch

    result = sorted(seen.values(), key=lambda c: c.subscribers, reverse=True)
    logger.info(
        "Итого: %d каналов в диапазоне %d–%d (из %d найденных)",
        len(result), min_subs, max_subs, len(all_channels),
    )
    return result
