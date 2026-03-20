"""
Lightweight scraper — works without Playwright (Termux / low-resource systems).

Uses httpx + BeautifulSoup to parse Telemetr.me catalog pages.
Telemetr responds to regular HTTP requests without CloudFlare challenges.

TGStat is attempted but expected to fail (CloudFlare blocks non-browser requests).
"""

import asyncio
import logging
import os
import random
import re
from typing import Optional

import httpx
from bs4 import BeautifulSoup

from scraper import (
    ChannelData,
    parse_number,
    parse_growth,
    DEBUG_DIR,
    _save_debug,
)

logger = logging.getLogger(__name__)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/122.0.0.0 Mobile Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}

TELEMETR_URLS = [
    "https://telemetr.me/catalog/IT?sort=subscribers_growth_per_day_desc",
    "https://telemetr.me/catalog/IT?sort=subscribers_count_desc",
]

TGSTAT_URLS = [
    "https://tgstat.ru/ratings/channels/tech?sort=ci",
]


def _parse_telemetr_html(html: str) -> list[ChannelData]:
    """
    Parse Telemetr.me catalog table.
    td[0]: channel info (rank, name, subs text, @username)
    td[1]: subscriber count
    td[4]: daily subscriber growth
    """
    soup = BeautifulSoup(html, "html.parser")
    channels = []

    for tr in soup.find_all("tr"):
        tds = tr.find_all("td")
        if len(tds) < 5:
            continue

        info_cell = tds[0]
        name_link = info_cell.find("a", class_=re.compile("about-link"))
        if not name_link:
            continue

        title = name_link.get_text(strip=True)
        href = name_link.get("href", "")
        username = href.strip("/").lstrip("@")
        if not username or not title:
            continue

        subs_text = tds[1].get_text(strip=True) if len(tds) > 1 else ""
        subscribers = parse_number(subs_text)
        if subscribers is None:
            info_text = info_cell.get_text(" ", strip=True)
            m = re.search(r"Подписчиков\s+([\d\s]+)", info_text)
            if m:
                subscribers = parse_number(m.group(1))
        if subscribers is None:
            continue

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


def _parse_tgstat_html(html: str) -> list[ChannelData]:
    """Parse TGStat peer-item-row cards (same logic as scraper.py)."""
    soup = BeautifulSoup(html, "html.parser")
    channels = []

    for card in soup.find_all("div", class_="peer-item-row"):
        link = card.find("a", href=re.compile(r"/channel/@"))
        if not link:
            continue
        m = re.search(r"@(\w+)", link["href"])
        if not m:
            continue
        username = m.group(1)

        title_el = card.find(
            "div",
            class_=lambda c: c and "font-16" in c and "text-truncate" in c,
        )
        title = title_el.get_text(strip=True) if title_el else username

        h4_tags = card.find_all("h4")
        subscribers = parse_number(h4_tags[0].get_text(strip=True)) if h4_tags else None
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


async def _fetch(client: httpx.AsyncClient, url: str, label: str) -> Optional[str]:
    try:
        resp = await client.get(url)
        if resp.status_code == 200:
            logger.info("%s: HTTP 200, %d байт", label, len(resp.text))
            return resp.text
        logger.warning("%s: HTTP %s", label, resp.status_code)
    except Exception as e:
        logger.warning("%s: ошибка — %s", label, e)
    return None


async def scrape_all(min_subs: int = 5000, max_subs: int = 40000) -> list[ChannelData]:
    """
    Lightweight scrape using httpx (no browser needed).
    Primary: Telemetr.me (works without CloudFlare bypass).
    Bonus: TGStat (likely blocked, but attempted).
    """
    all_channels: list[ChannelData] = []

    async with httpx.AsyncClient(
        headers=HEADERS, follow_redirects=True, timeout=30
    ) as client:
        # Telemetr — primary source
        for url in TELEMETR_URLS:
            logger.info("Telemetr (lite): загрузка %s", url)
            html = await _fetch(client, url, "telemetr")
            if html:
                _save_debug("telemetr_lite", html)
                channels = _parse_telemetr_html(html)
                logger.info("Telemetr (lite): %d каналов с %s", len(channels), url)
                all_channels.extend(channels)
            await asyncio.sleep(random.uniform(1, 3))

        # TGStat — bonus attempt (will likely 403)
        for url in TGSTAT_URLS:
            logger.info("TGStat (lite): загрузка %s", url)
            html = await _fetch(client, url, "tgstat")
            if html and "peer-item-row" in html:
                _save_debug("tgstat_lite", html)
                channels = _parse_tgstat_html(html)
                logger.info("TGStat (lite): %d каналов с %s", len(channels), url)
                all_channels.extend(channels)
            await asyncio.sleep(random.uniform(1, 3))

    # Filter & deduplicate
    filtered = [ch for ch in all_channels if min_subs <= ch.subscribers <= max_subs]

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
        "Lite итого: %d каналов в диапазоне %d–%d (из %d найденных)",
        len(result), min_subs, max_subs, len(all_channels),
    )
    return result
