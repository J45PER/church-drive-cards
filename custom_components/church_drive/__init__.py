"""Church Drive: the house's custom dashboard cards (and, later, scenes).

For now the integration's only job is to deliver the cards: it serves the
bundled church-drive-cards.js and adds it to every frontend page, so no
Lovelace resource is needed and HACS updates reach the dashboards directly.
"""

from __future__ import annotations

import json
from pathlib import Path

from homeassistant.components.frontend import add_extra_js_url, remove_extra_js_url
from homeassistant.components.http import StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import CARDS_FILE, DOMAIN, URL_BASE

FRONTEND_DIR = Path(__file__).parent / "frontend"


def _version() -> str:
    return json.loads((Path(__file__).parent / "manifest.json").read_text())["version"]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Serve the cards and load them on every page."""
    data = hass.data.setdefault(DOMAIN, {})
    if "cards_url" not in data:
        # Static paths can't be unregistered, so register once per run. The
        # version in the URL makes browsers fetch the new bundle after an
        # update instead of a cached copy.
        version = await hass.async_add_executor_job(_version)
        await hass.http.async_register_static_paths(
            [StaticPathConfig(URL_BASE, str(FRONTEND_DIR), cache_headers=False)]
        )
        data["cards_url"] = f"{URL_BASE}/{CARDS_FILE}?v={version}"
    add_extra_js_url(hass, data["cards_url"])
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Stop loading the cards on new pages."""
    url = hass.data.get(DOMAIN, {}).get("cards_url")
    if url:
        remove_extra_js_url(hass, url)
    return True
