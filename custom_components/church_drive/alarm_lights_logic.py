"""The rules for what the lights do when an alarm goes off (no Home Assistant in here, so it's tested on its own).

Two kinds of alarm, two answers:

- **fire**: a smoke, heat or carbon monoxide alarm. The strobe lights alternate red and amber. By day only the
  strobe lights flash; at night the constant lights come on bright white as well. When it stops the strobe
  lights show green for a minute.
- **security**: the house alarm going off. Set to home (or night): the strobes alternate red and blue and the
  constant lights come on. Set to away: red strobes only, the constant lights left alone. When it's turned off
  the strobe lights show blue for a few seconds.

How the strobes flash: Hue lights are flashed by the Hue bridge itself (one command per light, which the bridge
repeats on its own: Hue's "signaling"), because a stream of commands from Home Assistant is throttled by the
bridge and doesn't strobe. Any other light, or Hue if chosen, is pulsed by Home Assistant (quick on/off pulses).

Fire wins if both are going. Day or night is a reading of the whole house: it is day when enough of the chosen
light sensors are bright, and if the readings can't be had it is night, which is the safer answer (more lights).

The lights are picked per role: **strobe** lights pulse, **constant** lights just come on bright.
"""

from __future__ import annotations

from typing import Any

# What the strobes show, as (red, green, blue). Fire is red and amber, the house alarm red and blue.
COLOURS: dict[str, tuple[int, int, int]] = {
    "red": (255, 0, 0),
    "amber": (255, 140, 0),
    "blue": (0, 60, 255),
    "green": (0, 255, 40),
}

# The constant lights: bright and cool, like the Hue app's Energise (156 mirek is about 6400 K).
CONSTANT_KELVIN = 6000

# The same colours as Hue CIE xy points (Hue's own gamut C primaries), for the bridge's own flashing.
XY: dict[str, tuple[float, float]] = {
    "red": (0.6915, 0.3083),
    "amber": (0.5614, 0.4156),
    "blue": (0.1532, 0.0475),
    "green": (0.17, 0.70),
}

METHODS = ("hue", "pulses")
RULES = ("any", "half", "all")
KINDS = ("fire", "security_home", "security_away", "fire_ended", "disarmed")
WHEN = ("auto", "day", "night")
SCENE_ID = "church_drive_before_alarm"

DEFAULTS: dict[str, Any] = {
    "enabled": True,
    "strobe": [],  # light entities that pulse
    "constant": [],  # light entities that just come on bright
    "sensors": [],  # light sensors (lux) that say whether it is day
    "lux": 50,  # at or above this a sensor says "day"
    "rule": "half",  # how many sensors must say day: any, half or all
    "green_seconds": 60,  # strobes show green this long after a fire stops
    "blue_seconds": 10,  # strobes show blue this long after the alarm is turned off
    "method": "hue",  # hue: Hue lights flash on the bridge itself; pulses: Home Assistant pulses every light
    "pulse_ms": 250,  # how long each pulse is lit, and dark, when Home Assistant does the pulsing
}

Frame = tuple[str | None, float]  # (colour name, or None for dark; seconds)


def _ids(value: Any, domain: str) -> list[str]:
    out: list[str] = []
    for item in value if isinstance(value, (list, tuple)) else []:
        if isinstance(item, str) and item.startswith(f"{domain}.") and item not in out:
            out.append(item)
    return out


def _int(value: Any, low: int, high: int, default: int) -> int:
    try:
        number = int(float(value))
    except (TypeError, ValueError):
        return default
    return max(low, min(high, number))


def clean_config(raw: Any, base: dict[str, Any] | None = None) -> dict[str, Any]:
    """A safe config from whatever was sent: unknown keys are dropped, bad values keep the old/default one.

    A light picked as both strobe and constant is a strobe (it can't be both)."""
    cfg = {**DEFAULTS, **(base or {})}
    raw = raw if isinstance(raw, dict) else {}
    if "enabled" in raw:
        cfg["enabled"] = bool(raw["enabled"])
    for key in ("strobe", "constant"):
        if key in raw:
            cfg[key] = _ids(raw[key], "light")
    if "sensors" in raw:
        cfg["sensors"] = _ids(raw["sensors"], "sensor")
    if "lux" in raw:
        cfg["lux"] = _int(raw["lux"], 1, 100000, cfg["lux"])
    if raw.get("rule") in RULES:
        cfg["rule"] = raw["rule"]
    if "green_seconds" in raw:
        cfg["green_seconds"] = _int(raw["green_seconds"], 0, 600, cfg["green_seconds"])
    if "blue_seconds" in raw:
        cfg["blue_seconds"] = _int(raw["blue_seconds"], 0, 600, cfg["blue_seconds"])
    if raw.get("method") in METHODS:
        cfg["method"] = raw["method"]
    if "pulse_ms" in raw:
        cfg["pulse_ms"] = _int(raw["pulse_ms"], 100, 1000, cfg["pulse_ms"])
    cfg["constant"] = [e for e in cfg["constant"] if e not in cfg["strobe"]]
    return {k: cfg[k] for k in DEFAULTS}


def is_daytime(readings: list[float | None], lux: float, rule: str) -> bool:
    """Day or night for the whole house. `readings` are the chosen sensors' lux (None = no reading).

    Sensors with no reading don't count. With no readings at all it is night: more lights is the safer mistake."""
    have = [r for r in readings if r is not None]
    if not have:
        return False
    bright = sum(1 for r in have if r >= lux)
    if rule == "any":
        return bright >= 1
    if rule == "all":
        return bright == len(have)
    return bright * 2 >= len(have)  # half: at least half of them


def security_mode(armed_before: str | None, last_armed: str | None = None, target: str | None = None) -> str:
    """"home" or "away" for an alarm that has just gone off.

    Looks at the mode just before it went off, then the last mode seen (the panel passes through "pending"
    first), then what the panel says it is set to. Night counts as home (people are in). If nothing says,
    home: it lights more of the house."""
    for state in (armed_before, last_armed, target):
        if state in ("armed_home", "armed_night"):
            return "home"
        if state in ("armed_away", "armed_vacation", "armed_custom_bypass"):
            return "away"
    return "home"


def pulses(colours: list[str], on: float = 0.25, off: float = 0.25) -> list[Frame]:
    """Each colour as a pulse: `on` seconds lit, then `off` seconds dark."""
    frames: list[Frame] = []
    for colour in colours:
        frames.append((colour, on))
        frames.append((None, off))
    return frames


def profile(kind: str, mode: str, daytime: bool, pulse: float = 0.25) -> dict[str, Any]:
    """What to do for a kind of alarm: whether the constant lights come on, and how the strobes flash.

    kind is "fire" or "security"; mode is "home" or "away" (security only); daytime is the whole house's answer;
    `pulse` is how long a pulse is lit (and dark) when Home Assistant does the pulsing.

    `signal` is the Hue bridge's own flashing (alternate two colours, or flash one); `cycle` is the same thing
    as Home Assistant pulses, for lights that aren't Hue."""
    if kind == "fire":
        return {"constant": not daytime, "cycle": pulses(["red", "amber"], pulse, pulse), "signal": ("alternating", ["red", "amber"])}
    if mode == "away":
        return {"constant": False, "cycle": pulses(["red"], pulse, pulse), "signal": ("on_off_color", ["red"])}
    return {"constant": True, "cycle": pulses(["red", "blue"], pulse, pulse), "signal": ("alternating", ["red", "blue"])}


def ending(kind: str, cfg: dict[str, Any]) -> tuple[str | None, int]:
    """What the strobes show once an alarm is over, and for how many seconds: green for a fire, blue for disarmed."""
    if kind == "fire":
        return "green", cfg["green_seconds"]
    return "blue", cfg["blue_seconds"]


def plan_for(test: str, when: str, daytime_now: bool, mode_cfg: dict[str, Any]) -> dict[str, Any]:
    """The light-only test for `test` (one of KINDS): the same profile a real alarm would use.

    `when` is "day", "night" or "auto" (the house's reading right now)."""
    if test not in KINDS:
        raise ValueError(f"Unknown test: {test}")
    day = daytime_now if when == "auto" else when == "day"
    if test in ("fire_ended", "disarmed"):
        kind = "fire" if test == "fire_ended" else "security"
        colour, seconds = ending(kind, mode_cfg)
        return {"end": colour, "seconds": seconds, "daytime": day}
    pulse = mode_cfg["pulse_ms"] / 1000
    if test == "fire":
        return {**profile("fire", "home", day, pulse), "daytime": day}
    return {**profile("security", "home" if test == "security_home" else "away", day, pulse), "daytime": day}


def roles(cfg: dict[str, Any], expand) -> tuple[list[str], list[str]]:
    """(strobe lights, constant lights) as single lights: `expand` turns a group into the lights in it.

    A light in both ends up a strobe."""
    strobe: list[str] = []
    for entity in cfg["strobe"]:
        for light in expand(entity):
            if light not in strobe:
                strobe.append(light)
    constant: list[str] = []
    for entity in cfg["constant"]:
        for light in expand(entity):
            if light not in strobe and light not in constant:
                constant.append(light)
    return strobe, constant
