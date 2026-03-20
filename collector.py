"""Core logic: discover channels, gather stats, filter, and produce a report."""

import logging
from datetime import datetime

import config
from tgstat_api import TGStatClient, TGStatAPIError

logger = logging.getLogger(__name__)


def _channel_link(username: str) -> str:
    if username.startswith("@"):
        username = username[1:]
    return f"https://t.me/{username}"


def collect_channels(client: TGStatClient) -> dict[str, dict]:
    """Search for channels across configured queries and deduplicate."""
    channels: dict[str, dict] = {}  # keyed by channel id to dedup

    for query in config.SEARCH_QUERIES:
        logger.info("Searching channels: q=%r", query)
        try:
            results = client.search_channels(query=query)
        except TGStatAPIError:
            logger.exception("Search failed for q=%r", query)
            continue

        for ch in results:
            ch_id = str(ch.get("id", ch.get("channel_id", "")))
            if not ch_id or ch_id in channels:
                continue
            participants = ch.get("participants_count", 0)
            if config.MIN_SUBSCRIBERS <= participants <= config.MAX_SUBSCRIBERS:
                channels[ch_id] = ch

    logger.info(
        "Found %d unique channels in %d–%d subscriber range.",
        len(channels),
        config.MIN_SUBSCRIBERS,
        config.MAX_SUBSCRIBERS,
    )
    return channels


def enrich_and_filter(
    client: TGStatClient,
    channels: dict[str, dict],
) -> list[dict]:
    """For each channel, get 24h growth and filter by recent ad purchases."""
    qualified: list[dict] = []

    for ch_id, ch in channels.items():
        title = ch.get("title", ch.get("name", "N/A"))
        username = ch.get("username", "")
        participants = ch.get("participants_count", 0)
        link = _channel_link(username) if username else ch.get("link", "")

        identifier = username or ch_id

        logger.info("Checking channel: %s (%s)", title, identifier)

        try:
            has_ads = client.has_recent_ad_purchases(
                identifier, days=config.AD_LOOKBACK_DAYS
            )
        except TGStatAPIError:
            logger.warning("Could not check mentions for %s, skipping.", title)
            continue

        if not has_ads:
            logger.debug("  → no recent ad purchases, skipping.")
            continue

        try:
            growth = client.get_subscriber_growth_24h(identifier)
        except TGStatAPIError:
            logger.warning("Could not get growth for %s.", title)
            growth = 0

        qualified.append(
            {
                "title": title,
                "subscribers": participants,
                "link": link,
                "growth_24h": growth,
                "channel_id": ch_id,
                "username": username,
            }
        )
        logger.info(
            "  ✓ qualified: subs=%d, growth=%+d", participants, growth
        )

    qualified.sort(key=lambda c: c["subscribers"], reverse=True)
    logger.info("%d channels qualified after ad-purchase filter.", len(qualified))
    return qualified


def build_report_rows(channels: list[dict]) -> list[list]:
    """Convert enriched channel dicts into spreadsheet rows."""
    today = datetime.now().strftime("%Y-%m-%d")
    rows: list[list] = []
    for ch in channels:
        rows.append([
            today,
            ch["title"],
            ch["subscribers"],
            ch["link"],
            ch["growth_24h"],
            "",  # mentions column filled by enrich step if needed
        ])
    return rows


def run_collection() -> list[list]:
    """Full pipeline: search → enrich → filter → report rows."""
    logger.info("=== Starting channel collection ===")
    client = TGStatClient()

    channels = collect_channels(client)
    if not channels:
        logger.warning("No channels found matching criteria.")
        return []

    qualified = enrich_and_filter(client, channels)
    if not qualified:
        logger.warning("No channels passed the ad-purchase filter.")
        return []

    rows = build_report_rows(qualified)
    logger.info("=== Collection complete: %d channels ===", len(rows))
    return rows
