"""TGStat API client for Telegram channel analytics."""

import time
import logging
from datetime import datetime, timedelta
from typing import Optional

import requests

import config

logger = logging.getLogger(__name__)

API_RATE_LIMIT_PAUSE = 1.0


class TGStatAPIError(Exception):
    pass


class TGStatClient:
    """Wrapper around the TGStat API (https://api.tgstat.ru)."""

    def __init__(self, token: Optional[str] = None):
        self.token = token or config.TGSTAT_API_TOKEN
        if not self.token:
            raise TGStatAPIError(
                "TGSTAT_API_TOKEN is not set. "
                "Get your token at https://api.tgstat.ru/"
            )
        self.base_url = config.TGSTAT_BASE_URL
        self.session = requests.Session()

    def _request(self, endpoint: str, params: Optional[dict] = None) -> dict:
        """Make an authenticated GET request to TGStat API with retry logic."""
        url = f"{self.base_url}/{endpoint}"
        if params is None:
            params = {}
        params["token"] = self.token

        for attempt in range(3):
            try:
                resp = self.session.get(url, params=params, timeout=30)
                data = resp.json()

                if resp.status_code == 429:
                    wait = 2 ** (attempt + 1)
                    logger.warning("Rate limited, waiting %ds…", wait)
                    time.sleep(wait)
                    continue

                if data.get("status") == "error":
                    raise TGStatAPIError(
                        f"API error on {endpoint}: {data.get('error', 'unknown')}"
                    )

                return data.get("response", data)

            except requests.RequestException as exc:
                if attempt < 2:
                    time.sleep(2 ** attempt)
                    continue
                raise TGStatAPIError(f"Network error on {endpoint}: {exc}") from exc

        raise TGStatAPIError(f"Max retries exceeded for {endpoint}")

    # ------------------------------------------------------------------
    # Channel search
    # ------------------------------------------------------------------

    def search_channels(
        self,
        query: str = "",
        category: str = "",
        language: str = "ru",
        country: str = "ru",
        limit: int = 100,
    ) -> list[dict]:
        """Search for channels by keyword and/or category.

        Returns a list of channel dicts from the API.
        """
        params: dict = {
            "limit": limit,
            "peer_type": "channel",
        }
        if query:
            params["q"] = query
        if category:
            params["category"] = category
        if language:
            params["language"] = language
        if country:
            params["country"] = country

        time.sleep(API_RATE_LIMIT_PAUSE)
        result = self._request("channels/search", params)
        return result.get("items", []) if isinstance(result, dict) else []

    # ------------------------------------------------------------------
    # Channel statistics
    # ------------------------------------------------------------------

    def get_channel_stat(self, channel_id: str) -> dict:
        """Get main statistics for a channel."""
        time.sleep(API_RATE_LIMIT_PAUSE)
        return self._request("channels/stat", {"channelId": channel_id})

    # ------------------------------------------------------------------
    # Subscriber dynamics
    # ------------------------------------------------------------------

    def get_subscribers_history(
        self,
        channel_id: str,
        days: int = 2,
        group: str = "day",
    ) -> list[dict]:
        """Get subscriber count over time.

        Returns list of {period, participants_count} dicts.
        """
        end = datetime.utcnow()
        start = end - timedelta(days=days)
        params = {
            "channelId": channel_id,
            "startDate": int(start.timestamp()),
            "endDate": int(end.timestamp()),
            "group": group,
        }
        time.sleep(API_RATE_LIMIT_PAUSE)
        result = self._request("channels/subscribers", params)
        return result if isinstance(result, list) else []

    def get_subscriber_growth_24h(self, channel_id: str) -> int:
        """Calculate subscriber growth over the last 24 hours."""
        history = self.get_subscribers_history(channel_id, days=2, group="day")
        if len(history) >= 2:
            today = history[-1].get("participants_count", 0)
            yesterday = history[-2].get("participants_count", 0)
            return today - yesterday
        return 0

    # ------------------------------------------------------------------
    # Mentions (proxy for ad purchases)
    # ------------------------------------------------------------------

    def get_channel_mentions(
        self,
        channel_id: str,
        days: int = 7,
        limit: int = 50,
    ) -> list[dict]:
        """Get mentions of the channel in other channels.

        When a channel buys advertising on another channel, the ad post
        contains a link/mention of the buyer.  So recent mentions serve
        as a proxy for ad purchase activity.
        """
        end = datetime.utcnow()
        start = end - timedelta(days=days)
        params = {
            "channelId": channel_id,
            "startDate": int(start.timestamp()),
            "endDate": int(end.timestamp()),
            "limit": limit,
            "extended": 1,
        }
        time.sleep(API_RATE_LIMIT_PAUSE)
        result = self._request("channels/mentions", params)
        if isinstance(result, dict):
            return result.get("items", [])
        return result if isinstance(result, list) else []

    def has_recent_ad_purchases(
        self,
        channel_id: str,
        days: int = 7,
        min_mentions: int = 1,
    ) -> bool:
        """Return True if the channel was mentioned in other channels recently.

        Mentions in other channels indicate that the channel purchased
        advertising (the ad post links back to the buyer).
        """
        mentions = self.get_channel_mentions(channel_id, days=days)
        return len(mentions) >= min_mentions
