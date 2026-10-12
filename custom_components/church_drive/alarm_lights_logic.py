"""The rules for what the lights do when an alarm goes off (no Home Assistant in here, so it's tested on its own).

Two kinds of alarm, two answers:

- **fire**: a smoke, heat or carbon monoxide alarm. Red and amber pulses (two pulses of half a second, each
  followed by half a second dark). By day only the strobe lights pulse; at night the constant lights come on
  bright white as well. When it stops the strobe lights show green for a minute.
- **security**: the house alarm going off. Set to home (or night): red and blue strobes and the constant lights
  on. Set to away: red strobes only, the constant lights left alone. When it's turned off the strobe lights show
  blue for a few seconds.

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


def pulses(colours: list[str], on: float = 0.5, off: float = 0.5) -> list[Frame]:
    """Each colour as a pulse: `on` seconds lit, then `off` seconds dark."""
    frames: list[Frame] = []
    for colour in colours:
        frames.append((colour, on))
        frames.append((None, off))
    return frames


def profile(kind: str, mode: str, daytime: bool) -> dict[str, Any]:
    """What to do for a kind of alarm: whether the constant lights come on, and the strobes' repeating pattern.

    kind is "fire" or "security"; mode is "home" or "away" (security only); daytime is the whole house's answer."""
    if kind == "fire":
        return {"constant": not daytime, "cycle": pulses(["red", "amber"])}
    if mode == "away":
        return {"constant": False, "cycle": pulses(["red"])}
    return {"constant": True, "cycle": pulses(["red", "blue"])}


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
    if test == "fire":
        return {**profile("fire", "home", day), "daytime": day}
    return {**profile("security", "home" if test == "security_home" else "away", day), "daytime": day}


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
