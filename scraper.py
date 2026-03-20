"""
Web scraper for Telegram channel statistics.

Scrapes public pages of TGStat.ru and Telemetr.me without paid APIs.
Uses Playwright with stealth patches to bypass anti-bot protection.
"""

import asyncio
import json
import logging
import os
import random
import re
from dataclasses import dataclass, asdict
from typing import Optional

from playwright.async_api import async_playwright, Page, BrowserContext
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

DEBUG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "debug")


@dataclass
class ChannelData:
    title: str
    username: str
    subscribers: int
    growth_24h: Optional[int] = None
    source: str = ""


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
    """Launch Chromium with anti-detection settings."""
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
    os.makedirs(DEBUG_DIR, exist_ok=True)
    path = os.path.join(DEBUG_DIR, f"{name}.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)
    logger.debug("Debug HTML saved: %s", path)


# ---------------------------------------------------------------------------
# Number parsing helpers
# ---------------------------------------------------------------------------

def parse_number(text: str) -> Optional[int]:
    """Parse subscriber count from various formats: '15.2K', '15 200', '15,200'."""
    if not text:
        return None
    text = text.strip().replace("\xa0", " ").replace("\u202f", " ")

    m = re.search(r"([\d\s.,]+)\s*[KkКк]", text)
    if m:
        num = m.group(1).replace(" ", "").replace(",", ".")
        try:
            return int(float(num) * 1000)
        except ValueError:
            pass

    m = re.search(r"([\d\s.,]+)\s*[MmМм]", text)
    if m:
        num = m.group(1).replace(" ", "").replace(",", ".")
        try:
            return int(float(num) * 1_000_000)
        except ValueError:
            pass

    m = re.search(r"[\d\s,]+", text)
    if m:
        num = m.group(0).replace(" ", "").replace(",", "")
        if num.isdigit():
            return int(num)

    return None


def parse_growth(text: str) -> Optional[int]:
    """Parse growth: '+320', '-15', '↑320', '↓15', '▲ 320'."""
    if not text:
        return None
    text = text.strip().replace("\xa0", " ").replace("\u202f", " ")

    m = re.search(r"([+\-−↑↓▲▼])\s*([\d\s,]+)", text)
    if m:
        sign = -1 if m.group(1) in ("-", "−", "↓", "▼") else 1
        num = m.group(2).replace(" ", "").replace(",", "")
        if num.isdigit():
            return sign * int(num)

    m = re.search(r"(\d[\d\s,]*)", text)
    if m:
        num = m.group(1).replace(" ", "").replace(",", "")
        if num.isdigit():
            return int(num)

    return None


def extract_username(href: str) -> Optional[str]:
    """Extract Telegram username from various URL formats."""
    if not href:
        return None
    patterns = [
        r"t\.me/([A-Za-z_]\w{3,})",
        r"tgstat\.ru/channel/@?([A-Za-z_]\w{3,})",
        r"tgstat\.com/channel/@?([A-Za-z_]\w{3,})",
        r"telemetr\.me/content/([A-Za-z_]\w{3,})",
    ]
    for p in patterns:
        m = re.search(p, href)
        if m:
            return m.group(1)
    return None


# ---------------------------------------------------------------------------
# TGStat scraper
# ---------------------------------------------------------------------------

TGSTAT_URLS = [
    "https://tgstat.ru/ratings/channels/tech?sort=members",
    "https://tgstat.ru/ratings/channels/tech",
]


async def _scrape_tgstat_page(page: Page, url: str) -> list[ChannelData]:
    """Attempt to scrape a single TGStat ratings page."""
    logger.info("TGStat: загрузка %s", url)

    api_data: list[dict] = []

    async def intercept(response):
        ct = response.headers.get("content-type", "")
        if "application/json" in ct:
            try:
                body = await response.json()
                api_data.append({"url": response.url, "body": body})
            except Exception:
                pass

    page.on("response", intercept)

    try:
        resp = await page.goto(url, wait_until="domcontentloaded", timeout=45000)
    except Exception as e:
        logger.warning("TGStat: не удалось загрузить %s — %s", url, e)
        return []

    if resp and resp.status >= 400:
        logger.warning("TGStat: HTTP %s для %s", resp.status, url)
        return []

    await page.wait_for_timeout(4000)

    # Scroll to trigger lazy loading
    for _ in range(3):
        await page.evaluate("window.scrollBy(0, window.innerHeight)")
        await page.wait_for_timeout(1500)

    html = await page.content()

    # Check for CloudFlare challenge
    if "cf-browser-verification" in html or "challenge-platform" in html:
        logger.warning("TGStat: обнаружена CloudFlare-проверка, ожидание…")
        await page.wait_for_timeout(8000)
        html = await page.content()
        if "cf-browser-verification" in html:
            logger.error("TGStat: не удалось пройти CloudFlare")
            _save_debug("tgstat_cf_block", html)
            return []

    _save_debug("tgstat_page", html)

    # Strategy 1: JSON from intercepted API responses
    channels = _parse_tgstat_json(api_data)
    if channels:
        logger.info("TGStat: получено %d каналов из JSON API", len(channels))
        return channels

    # Strategy 2: Parse HTML
    channels = _parse_tgstat_html(html)
    if channels:
        logger.info("TGStat: получено %d каналов из HTML", len(channels))
        return channels

    logger.warning("TGStat: не удалось извлечь каналы из %s", url)
    return []


def _parse_tgstat_json(api_data: list[dict]) -> list[ChannelData]:
    """Try to extract channel data from intercepted JSON responses."""
    channels = []
    for entry in api_data:
        body = entry.get("body")
        if not body:
            continue

        items = []
        if isinstance(body, list):
            items = body
        elif isinstance(body, dict):
            for key in ("items", "channels", "data", "response"):
                if key in body and isinstance(body[key], list):
                    items = body[key]
                    break

        for item in items:
            if not isinstance(item, dict):
                continue
            title = item.get("title") or item.get("name") or item.get("channel_name", "")
            username = item.get("username") or item.get("link", "")
            subs = item.get("participants_count") or item.get("subscribers") or item.get("members_count", 0)
            growth = item.get("daily_reach") or item.get("members_growth") or item.get("growth")

            if title and subs:
                if isinstance(username, str):
                    username = username.lstrip("@").split("/")[-1]
                channels.append(ChannelData(
                    title=str(title),
                    username=str(username),
                    subscribers=int(subs),
                    growth_24h=int(growth) if growth else None,
                    source="tgstat",
                ))
    return channels


def _parse_tgstat_html(html: str) -> list[ChannelData]:
    """Parse TGStat ratings HTML with multiple selector strategies."""
    soup = BeautifulSoup(html, "lxml")
    channels = []

    # Strategy A: look for links to tgstat.ru/channel/@ and extract surrounding data
    for link in soup.find_all("a", href=True):
        href = link["href"]
        username = extract_username(href)
        if not username:
            continue

        title = link.get_text(strip=True)
        if not title or len(title) < 2:
            parent = link.find_parent(["div", "tr", "li", "article"])
            if parent:
                name_el = parent.find(["h3", "h4", "h5", "span", "b", "strong"])
                if name_el:
                    title = name_el.get_text(strip=True)

        if not title or len(title) < 2:
            continue

        container = link.find_parent(["div", "tr", "li", "article", "section"])
        if not container:
            continue

        all_text = container.get_text(" ", strip=True)

        subs = None
        growth = None

        nums = re.findall(r"[\d\s]{3,}[KkКк]?", all_text)
        for n in nums:
            parsed = parse_number(n)
            if parsed and parsed >= 100 and subs is None:
                subs = parsed
                break

        growth_patterns = re.findall(r"[+\-−↑↓▲▼]\s*[\d\s,]+", all_text)
        for g in growth_patterns:
            parsed = parse_growth(g)
            if parsed is not None:
                growth = parsed
                break

        if subs and subs >= 100:
            channels.append(ChannelData(
                title=title,
                username=username,
                subscribers=subs,
                growth_24h=growth,
                source="tgstat",
            ))

    seen = set()
    unique = []
    for ch in channels:
        if ch.username.lower() not in seen:
            seen.add(ch.username.lower())
            unique.append(ch)
    return unique


async def scrape_tgstat(page: Page) -> list[ChannelData]:
    """Try all TGStat URLs, return first successful result."""
    for url in TGSTAT_URLS:
        result = await _scrape_tgstat_page(page, url)
        if result:
            return result
        await asyncio.sleep(random.uniform(2, 4))
    return []


# ---------------------------------------------------------------------------
# Telemetr.me scraper
# ---------------------------------------------------------------------------

TELEMETR_URLS = [
    "https://telemetr.me/catalog/IT?sort=subscribers_growth_per_day_desc",
    "https://telemetr.me/catalog/IT",
]


async def _scrape_telemetr_page(page: Page, url: str) -> list[ChannelData]:
    """Scrape a single Telemetr.me catalog page."""
    logger.info("Telemetr: загрузка %s", url)

    api_data: list[dict] = []

    async def intercept(response):
        ct = response.headers.get("content-type", "")
        if "application/json" in ct:
            try:
                body = await response.json()
                api_data.append({"url": response.url, "body": body})
            except Exception:
                pass

    page.on("response", intercept)

    try:
        resp = await page.goto(url, wait_until="domcontentloaded", timeout=45000)
    except Exception as e:
        logger.warning("Telemetr: не удалось загрузить %s — %s", url, e)
        return []

    if resp and resp.status >= 400:
        logger.warning("Telemetr: HTTP %s для %s", resp.status, url)
        return []

    await page.wait_for_timeout(4000)

    for _ in range(3):
        await page.evaluate("window.scrollBy(0, window.innerHeight)")
        await page.wait_for_timeout(1500)

    html = await page.content()
    _save_debug("telemetr_page", html)

    # Strategy 1: intercepted JSON
    channels = _parse_telemetr_json(api_data)
    if channels:
        logger.info("Telemetr: получено %d каналов из JSON API", len(channels))
        return channels

    # Strategy 2: HTML parsing
    channels = _parse_telemetr_html(html)
    if channels:
        logger.info("Telemetr: получено %d каналов из HTML", len(channels))
        return channels

    logger.warning("Telemetr: не удалось извлечь каналы из %s", url)
    return []


def _parse_telemetr_json(api_data: list[dict]) -> list[ChannelData]:
    channels = []
    for entry in api_data:
        body = entry.get("body")
        if not body:
            continue

        items = []
        if isinstance(body, list):
            items = body
        elif isinstance(body, dict):
            for key in ("items", "channels", "data", "results", "list"):
                if key in body and isinstance(body[key], list):
                    items = body[key]
                    break

        for item in items:
            if not isinstance(item, dict):
                continue
            title = item.get("title") or item.get("name") or item.get("channel_name", "")
            username = item.get("username") or item.get("tg_id") or item.get("link", "")
            subs = item.get("subscribers") or item.get("participants_count") or item.get("members", 0)
            growth = (
                item.get("subscribers_growth_per_day")
                or item.get("growth")
                or item.get("daily_growth")
            )

            if title and subs:
                if isinstance(username, str):
                    username = username.lstrip("@").split("/")[-1]
                channels.append(ChannelData(
                    title=str(title),
                    username=str(username),
                    subscribers=int(subs),
                    growth_24h=int(growth) if growth else None,
                    source="telemetr",
                ))
    return channels


def _parse_telemetr_html(html: str) -> list[ChannelData]:
    """Parse Telemetr.me catalog HTML."""
    soup = BeautifulSoup(html, "lxml")
    channels = []

    for link in soup.find_all("a", href=True):
        href = link["href"]
        username = None

        if "t.me/" in href or "/content/" in href or "/channel/" in href:
            username = extract_username(href)
        if not username:
            continue

        title = link.get_text(strip=True)
        if not title or len(title) < 2:
            parent = link.find_parent(["div", "tr", "li", "article"])
            if parent:
                name_el = parent.find(["h3", "h4", "h5", "span", "b", "strong"])
                if name_el:
                    title = name_el.get_text(strip=True)

        if not title or len(title) < 2:
            continue

        container = link.find_parent(["div", "tr", "li", "article", "section"])
        if not container:
            continue

        all_text = container.get_text(" ", strip=True)

        subs = None
        growth = None

        nums = re.findall(r"[\d\s]{3,}[KkКк]?", all_text)
        for n in nums:
            parsed = parse_number(n)
            if parsed and parsed >= 100 and subs is None:
                subs = parsed
                break

        growth_patterns = re.findall(r"[+\-−↑↓▲▼]\s*[\d\s,]+", all_text)
        for g in growth_patterns:
            parsed = parse_growth(g)
            if parsed is not None:
                growth = parsed
                break

        if subs and subs >= 100:
            channels.append(ChannelData(
                title=title,
                username=username,
                subscribers=subs,
                growth_24h=growth,
                source="telemetr",
            ))

    seen = set()
    unique = []
    for ch in channels:
        if ch.username.lower() not in seen:
            seen.add(ch.username.lower())
            unique.append(ch)
    return unique


async def scrape_telemetr(page: Page) -> list[ChannelData]:
    for url in TELEMETR_URLS:
        result = await _scrape_telemetr_page(page, url)
        if result:
            return result
        await asyncio.sleep(random.uniform(2, 4))
    return []


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

async def scrape_all(min_subs: int = 5000, max_subs: int = 40000) -> list[ChannelData]:
    """
    Scrape channels from all available sources.
    Returns channels filtered to the given subscriber range, sorted by subscribers desc.
    """
    async with async_playwright() as pw:
        browser, context = await create_stealth_context(pw)
        page = await new_stealth_page(context)

        all_channels: list[ChannelData] = []

        # Source 1: TGStat
        try:
            tg_channels = await scrape_tgstat(page)
            all_channels.extend(tg_channels)
            logger.info("TGStat: итого %d каналов", len(tg_channels))
        except Exception as e:
            logger.error("TGStat: ошибка — %s", e, exc_info=True)

        await asyncio.sleep(random.uniform(3, 6))

        # Source 2: Telemetr.me
        try:
            tm_channels = await scrape_telemetr(page)
            all_channels.extend(tm_channels)
            logger.info("Telemetr: итого %d каналов", len(tm_channels))
        except Exception as e:
            logger.error("Telemetr: ошибка — %s", e, exc_info=True)

        await browser.close()

    # Filter by subscriber range
    filtered = [
        ch for ch in all_channels
        if min_subs <= ch.subscribers <= max_subs
    ]

    # Deduplicate (prefer tgstat data when duplicated)
    seen = {}
    for ch in filtered:
        key = ch.username.lower()
        if key not in seen:
            seen[key] = ch

    result = sorted(seen.values(), key=lambda c: c.subscribers, reverse=True)
    logger.info(
        "Итого: %d каналов в диапазоне %d–%d (из %d найденных)",
        len(result), min_subs, max_subs, len(all_channels),
    )
    return result
