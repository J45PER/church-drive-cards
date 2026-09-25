"""Church Drive: the house's custom dashboard cards and universal scenes.

- Cards: serves the bundled church-drive-cards.js and adds it to every
  frontend page, so no Lovelace resource is needed.
- Universal scenes: syncs the scene library (library.py) to the Hue rooms and
  zones chosen in the options, through HA's own Hue connection (hue.py).
"""

from __future__ import annotations

import json
from pathlib import Path

from homeassistant.components.frontend import add_extra_js_url, remove_extra_js_url
from homeassistant.components.http import StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import EVENT_HOMEASSISTANT_STARTED
from homeassistant.core import CoreState, Event, HomeAssistant, ServiceCall, ServiceResponse, SupportsResponse

from .const import CARDS_FILE, CONF_SCENE_GROUPS, DOMAIN, SERVICE_SYNC_SCENES, URL_BASE
from .hue import async_sync

FRONTEND_DIR = Path(__file__).parent / "frontend"


def _version() -> str:
    return json.loads((Path(__file__).parent / "manifest.json").read_text())["version"]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Serve the cards, load them on every page, and sync the scenes."""
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

    async def sync_scenes(call: ServiceCall | None = None) -> ServiceResponse:
        return await async_sync(hass, entry.options.get(CONF_SCENE_GROUPS, []))

    hass.services.async_register(
        DOMAIN, SERVICE_SYNC_SCENES, sync_scenes, supports_response=SupportsResponse.OPTIONAL
    )
    entry.async_on_unload(entry.add_update_listener(_options_updated))

    # Sync once the Hue integration is up: now if HA is already running,
    # otherwise when startup finishes.
    if hass.state is CoreState.running:
        entry.async_create_background_task(hass, sync_scenes(), "church_drive scene sync")
    else:

        async def started(_event: Event) -> None:
            await sync_scenes()

        hass.bus.async_listen_once(EVENT_HOMEASSISTANT_STARTED, started)
    return True


async def _options_updated(hass: HomeAssistant, entry: ConfigEntry) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Stop loading the cards on new pages."""
    hass.services.async_remove(DOMAIN, SERVICE_SYNC_SCENES)
    url = hass.data.get(DOMAIN, {}).get("cards_url")
    if url:
        remove_extra_js_url(hass, url)
    return True
