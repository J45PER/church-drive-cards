"""The universal scene library.

Each scene is defined once here and synced to every Hue room or zone chosen in
the integration's options. Light values follow the Hue app's own versions of
these scenes. `mirek` is colour temperature (1,000,000 / kelvin); `xy` is a
colour; `brightness` is 0-100.
"""

from __future__ import annotations

LIBRARY: dict[str, dict] = {
    "bright": {"name": "Bright", "mirek": 366, "brightness": 100.0},
    "relax": {"name": "Relax", "mirek": 447, "brightness": 56.3},
    "rest": {"name": "Rest", "mirek": 447, "brightness": 34.0},
    "nightlight": {"name": "Nightlight", "xy": (0.5610, 0.4042), "mirek": 500, "brightness": 0.4},
}
