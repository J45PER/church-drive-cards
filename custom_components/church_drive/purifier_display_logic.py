"""The plain rules behind the air purifier's display brightness by mode (no Home Assistant in here, so they can be tested alone).

The purifier's display ("Display backlight") is a light with only three real steps: off, dim (128) and bright (255). Each of the
purifier's modes can be given one of those, or "keep" (leave the display as it is). The display is set when the mode changes, so
it can still be changed by hand afterwards and stays until the next mode change.

Modes are kept by their lower-case name ("sleep", "auto"). Until something is saved, Sleep turns the display off and the rest are left alone.
"""

from __future__ import annotations

from typing import Any

DEFAULT_ENTITY = "fan.hayleys_bedroom_air_purifier"  # the same purifier as purifier_schedule_logic.DEFAULT_ENTITY
CHOICES = ("keep", "off", "dim", "bright")
BRIGHTNESS = {"dim": 128, "bright": 255}
MAX_MODES = 20
DEFAULTS: dict[str, Any] = {"enabled": True, "entity": DEFAULT_ENTITY, "modes": {"sleep": "off"}}


def mode_key(mode: Any) -> str | None:
    """A mode's name as kept: trimmed and lower case, or None if it isn't a usable name."""
    if not isinstance(mode, str):
        return None
    key = mode.strip().lower()
    return key if key and len(key) <= 40 else None


def clean_config(value: Any, base: dict[str, Any] | None = None) -> dict[str, Any]:
    """A saved or sent config, made safe: only known choices, at most MAX_MODES modes; what isn't sent stays as in `base` (or the defaults)."""
    start = base if base is not None else DEFAULTS
    cfg: dict[str, Any] = {"enabled": bool(start["enabled"]), "entity": start["entity"], "modes": dict(start["modes"])}
    if not isinstance(value, dict):
        return cfg
    if "enabled" in value:
        cfg["enabled"] = bool(value["enabled"])
    entity = value.get("entity")
    if isinstance(entity, str) and entity.startswith("fan."):
        cfg["entity"] = entity
    modes = value.get("modes")
    if isinstance(modes, dict):
        for mode, choice in modes.items():
            key = mode_key(mode)
            if key is None or choice not in CHOICES:
                continue
            if key in cfg["modes"] or len(cfg["modes"]) < MAX_MODES:
                cfg["modes"][key] = choice
    return cfg


def choice_for(cfg: dict[str, Any], mode: Any) -> str:
    """What the display should do for a mode: keep, off, dim or bright (keep for a mode that isn't listed)."""
    key = mode_key(mode)
    return cfg["modes"].get(key, "keep") if key else "keep"


def display_light(entity_ids: list[str], fan: str) -> str | None:
    """The display light that belongs to a purifier fan: the light that starts with the fan's name and has "display" in it (a "backlight" first)."""
    base = fan.split(".", 1)[1] if "." in fan else ""
    if not base:
        return None
    found = sorted(i for i in entity_ids if i.startswith(f"light.{base}_") and "display" in i)
    return next((i for i in found if "backlight" in i), found[0] if found else None)


def call_for(choice: str, light: str) -> tuple[str, dict[str, Any]] | None:
    """The light service call for a choice: (service, data), or None for keep."""
    if choice == "off":
        return "turn_off", {"entity_id": light}
    if choice in BRIGHTNESS:
        return "turn_on", {"entity_id": light, "brightness": BRIGHTNESS[choice]}
    return None


def should_apply(old_state: str | None, old_mode: Any, new_state: str | None, new_mode: Any) -> bool:
    """Whether a change of the purifier is a mode taking effect: it is on now, and it was just switched on or its mode changed.
    A change in something else (a reading, a filter) or while it is unavailable is not."""
    if new_state != "on" or old_state in (None, "unavailable", "unknown"):
        return False
    return old_state != "on" or mode_key(old_mode) != mode_key(new_mode)


def summary(cfg: dict[str, Any]) -> str:
    """"Sleep: Off" / "Sleep: Off · Auto: Dim", or "Left as it is" when nothing is set."""
    words = {"off": "Off", "dim": "Dim", "bright": "Bright"}
    parts = [f"{mode.capitalize()}: {words[c]}" for mode, c in cfg["modes"].items() if c in words]
    return " · ".join(parts) if parts else "Left as it is"
