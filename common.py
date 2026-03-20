"""
Shared data structures and utility functions.
No heavy dependencies — safe to import on any platform (including Termux).
"""

import os
import re
from dataclasses import dataclass
from typing import Optional

DEBUG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "debug")


@dataclass
class ChannelData:
    title: str
    username: str
    subscribers: int
    growth_24h: Optional[int] = None
    source: str = ""


def save_debug_html(name: str, html: str):
    os.makedirs(DEBUG_DIR, exist_ok=True)
    path = os.path.join(DEBUG_DIR, f"{name}.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)


def parse_number(text: str) -> Optional[int]:
    """Parse subscriber count: '15.2K', '15 200', '15,200', '1.5M'."""
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

    clean = re.sub(r"[^\d]", "", text)
    if clean and clean.isdigit():
        return int(clean)

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

    clean = re.sub(r"[^\d]", "", text)
    if clean and clean.isdigit():
        return int(clean)

    return None
