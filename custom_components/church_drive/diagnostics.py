"""Diagnostics: the chosen rooms' scenes as the Hue bridge has them."""

from __future__ import annotations

from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import CONF_SCENE_GROUPS
from .hue import async_describe


async def async_get_config_entry_diagnostics(
    hass: HomeAssistant, entry: ConfigEntry
) -> dict[str, Any]:
    """Return the synced groups' scenes and their light settings."""
    return {
        "options": dict(entry.options),
        "scenes": async_describe(hass, entry.options.get(CONF_SCENE_GROUPS, [])),
    }
