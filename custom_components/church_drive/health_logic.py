"""Pure helpers for Device health (no Home Assistant needed, so they are tested)."""

from __future__ import annotations

from typing import Any


def fan_choice(attrs: dict[str, Any]) -> tuple[str, dict[str, Any]] | None:
    """The service that puts a fan back on the mode or speed it reports, or None when it reports neither."""
    if attrs.get("preset_mode"):
        return "set_preset_mode", {"preset_mode": attrs["preset_mode"]}
    if attrs.get("percentage"):
        return "set_percentage", {"percentage": attrs["percentage"]}
    return None


def keep_fan_choice(state: str, attrs: dict[str, Any], previous: dict[str, Any] | None) -> dict[str, Any]:
    """A fan that is on but reports neither a mode nor a speed keeps the last ones it reported (so a re-sync never loses them)."""
    if state in ("off", "unavailable", "unknown") or fan_choice(attrs) or not previous:
        return attrs
    kept = {k: previous[k] for k in ("preset_mode", "percentage") if previous.get(k)}
    return {**attrs, **kept}
