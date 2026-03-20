"""SQLite storage for historical channel statistics."""

import sqlite3
import os
from datetime import datetime, timedelta
from typing import Optional

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "channel_stats.db")


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_connection()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            channel_username TEXT NOT NULL,
            channel_title TEXT NOT NULL,
            subscribers INTEGER NOT NULL,
            collected_at TEXT NOT NULL
        )
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_snapshots_channel_time
        ON snapshots (channel_username, collected_at)
    """)
    conn.commit()
    conn.close()


def save_snapshot(channel_username: str, channel_title: str, subscribers: int, collected_at: datetime):
    conn = get_connection()
    conn.execute(
        "INSERT INTO snapshots (channel_username, channel_title, subscribers, collected_at) VALUES (?, ?, ?, ?)",
        (channel_username, channel_title, subscribers, collected_at.isoformat()),
    )
    conn.commit()
    conn.close()


def get_previous_snapshot(channel_username: str, before: datetime) -> Optional[dict]:
    """Get the most recent snapshot for a channel collected before the given timestamp."""
    conn = get_connection()
    window_start = (before - timedelta(hours=30)).isoformat()
    row = conn.execute(
        """
        SELECT channel_title, subscribers, collected_at
        FROM snapshots
        WHERE channel_username = ?
          AND collected_at < ?
          AND collected_at > ?
        ORDER BY collected_at DESC
        LIMIT 1
        """,
        (channel_username, before.isoformat(), window_start),
    ).fetchone()
    conn.close()

    if row:
        return {
            "channel_title": row["channel_title"],
            "subscribers": row["subscribers"],
            "collected_at": row["collected_at"],
        }
    return None


def cleanup_old_data(days_to_keep: int = 7):
    """Remove snapshots older than the specified number of days."""
    cutoff = (datetime.utcnow() - timedelta(days=days_to_keep)).isoformat()
    conn = get_connection()
    conn.execute("DELETE FROM snapshots WHERE collected_at < ?", (cutoff,))
    conn.commit()
    conn.close()
