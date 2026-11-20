"""The plain rules behind the saved colours (no Home Assistant in here, so they can be tested alone).

The colours people keep for their lights (up to eight, as hue 0-360 and saturation 0-100), shared by everyone and managed in the apps.
"""

from __future__ import annotations

from typing import Any

MAX_COLOURS = 8


def same(a: tuple[float, float], b: tuple[float, float]) -> bool:
    """Whether two colours look the same: within 3 degrees of hue (round the wheel) and 3 points of saturation."""
    dh = abs(a[0] - b[0])
    if dh > 180:
        dh = 360 - dh
    return dh < 3 and abs(a[1] - b[1]) < 3


def clean(value: Any) -> list[list[float]]:
    """The list from something sent in: only valid pairs, hue taken round the wheel, saturation held to 0-100, no two alike, at most eight."""
    out: list[tuple[float, float]] = []
    for item in value if isinstance(value, (list, tuple)) else []:
        if not isinstance(item, (list, tuple)) or len(item) != 2:
            continue
        try:
            hue, sat = float(item[0]), float(item[1])
        except (TypeError, ValueError):
            continue
        if hue != hue or sat != sat:  # NaN
            continue
        colour = (round(hue % 360, 1), round(min(100.0, max(0.0, sat)), 1))
        if any(same(colour, other) for other in out):
            continue
        out.append(colour)
        if len(out) == MAX_COLOURS:
            break
    return [[h, s] for h, s in out]
