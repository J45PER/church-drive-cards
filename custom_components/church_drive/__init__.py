"""Church Drive: the house's custom dashboard cards and universal scenes.

- Cards: serves the bundled church-drive-cards.js and adds it to every
  frontend page, so no Lovelace resource is needed.
- Universal scenes: a scene library (library.py; built-in white and colour
  scenes plus the user's own from the scene builder) that the cards read over
  the websocket and apply to any room, zone or light (apply.py); a scene
  select entity per Hue room/zone (select.py); church_drive.apply_scene; and
  an optional sync of the white scenes to chosen Hue rooms (hue.py).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.components.frontend import add_extra_js_url, remove_extra_js_url
from homeassistant.components.http import StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import ATTR_ENTITY_ID, EVENT_HOMEASSISTANT_STARTED, Platform
from homeassistant.core import (
    CoreState,
    Event,
    HomeAssistant,
    ServiceCall,
    ServiceResponse,
    SupportsResponse,
    callback,
)
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .apply import async_apply
from .const import (
    CARDS_FILE,
    CONF_SCENE_GROUPS,
    DOMAIN,
    SERVICE_APPLY_SCENE,
    SERVICE_SYNC_SCENES,
    SIGNAL_LIBRARY,
    URL_BASE,
    WS_LIBRARY,
    WS_SCENE_DELETE,
    WS_SCENE_PREVIEW,
    WS_SCENE_SAVE,
)
from .hue import async_sync
from .library import Library, normalise

FRONTEND_DIR = Path(__file__).parent / "frontend"
PLATFORMS = [Platform.SELECT]

APPLY_SCENE_SCHEMA = vol.Schema(
    {vol.Required(ATTR_ENTITY_ID): cv.entity_ids, vol.Required("scene"): cv.string}
)

HEX = vol.Match(r"^#[0-9a-fA-F]{6}$")
CUSTOM_SCENE = vol.Schema(
    {
        vol.Optional("key"): cv.string,
        vol.Required("name"): vol.All(cv.string, vol.Length(min=1, max=32)),
        vol.Required("kind"): vol.In(["white", "colour"]),
        vol.Optional("kelvin", default=2700): vol.All(vol.Coerce(int), vol.Range(min=2000, max=6500)),
        vol.Optional("brightness", default=100): vol.All(vol.Coerce(float), vol.Range(min=1, max=100)),
        vol.Optional("colors", default=[]): vol.All([HEX], vol.Length(max=9)),
        vol.Optional("dynamic", default=False): cv.boolean,
        vol.Optional("speed", default=0.5): vol.All(vol.Coerce(float), vol.Range(min=0, max=1)),
        vol.Optional("icon"): cv.string,
    }
)


def _library(hass: HomeAssistant) -> Library:
    return hass.data[DOMAIN]["library"]


@websocket_api.websocket_command({vol.Required("type"): WS_LIBRARY})
@callback
def ws_library(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Send the scene library (built-in and custom) to the cards."""
    connection.send_result(msg["id"], {"scenes": _library(hass).as_list(), "custom": _library(hass).custom})


@websocket_api.websocket_command({vol.Required("type"): WS_SCENE_SAVE, vol.Required("scene"): CUSTOM_SCENE})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_scene_save(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Add or update a custom scene from the scene builder."""
    scene = msg["scene"]
    if scene["kind"] == "colour" and not scene["colors"]:
        connection.send_error(msg["id"], "invalid", "A colour scene needs at least one colour")
        return
    try:
        key = await _library(hass).async_save_scene(scene)
    except ValueError as err:
        connection.send_error(msg["id"], "invalid", str(err))
        return
    async_dispatcher_send(hass, SIGNAL_LIBRARY)
    connection.send_result(msg["id"], {"key": key})


@websocket_api.websocket_command({vol.Required("type"): WS_SCENE_DELETE, vol.Required("key"): cv.string})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_scene_delete(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Delete a custom scene."""
    await _library(hass).async_delete_scene(msg["key"])
    async_dispatcher_send(hass, SIGNAL_LIBRARY)
    connection.send_result(msg["id"], {})


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_SCENE_PREVIEW,
        vol.Required("entity_id"): cv.entity_ids,
        vol.Required("scene"): CUSTOM_SCENE,
    }
)
@websocket_api.async_response
async def ws_scene_preview(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Try an unsaved scene from the builder on real lights."""
    scene = msg["scene"]
    if scene["kind"] == "colour" and not scene["colors"]:
        connection.send_error(msg["id"], "invalid", "A colour scene needs at least one colour")
        return
    await async_apply(hass, msg["entity_id"], scene.get("key") or "preview", normalise(scene))
    connection.send_result(msg["id"], {})


def _version() -> str:
    return json.loads((Path(__file__).parent / "manifest.json").read_text())["version"]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Serve the cards, load them on every page, and set up the scenes."""
    data = hass.data.setdefault(DOMAIN, {})
    if "cards_url" not in data:
        # Static paths and websocket commands can't be unregistered, so do
        # them once per run. The version in the URL makes browsers fetch the
        # new bundle after an update instead of a cached copy.
        version = await hass.async_add_executor_job(_version)
        await hass.http.async_register_static_paths(
            [StaticPathConfig(URL_BASE, str(FRONTEND_DIR), cache_headers=False)]
        )
        data["cards_url"] = f"{URL_BASE}/{CARDS_FILE}?v={version}"
        for command in (ws_library, ws_scene_save, ws_scene_delete, ws_scene_preview):
            websocket_api.async_register_command(hass, command)
    add_extra_js_url(hass, data["cards_url"])

    library = Library(hass)
    await library.async_load()
    data["library"] = library

    async def sync_scenes(call: ServiceCall | None = None) -> ServiceResponse:
        return await async_sync(hass, entry.options.get(CONF_SCENE_GROUPS, []))

    async def apply_scene(call: ServiceCall) -> None:
        found = library.find(call.data["scene"])
        if found is None:
            raise ServiceValidationError(f"Unknown scene: {call.data['scene']}")
        await async_apply(hass, call.data[ATTR_ENTITY_ID], found[0], found[1], call.context)

    hass.services.async_register(
        DOMAIN, SERVICE_SYNC_SCENES, sync_scenes, supports_response=SupportsResponse.OPTIONAL
    )
    hass.services.async_register(DOMAIN, SERVICE_APPLY_SCENE, apply_scene, schema=APPLY_SCENE_SCHEMA)
    entry.async_on_unload(entry.add_update_listener(_options_updated))

    async def start(_event: Event | None = None) -> None:
        # The Hue integration is up once HA has started.
        await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
        await sync_scenes()

    if hass.state is CoreState.running:
        entry.async_create_background_task(hass, start(), "church_drive start")
    else:
        hass.bus.async_listen_once(EVENT_HOMEASSISTANT_STARTED, start)
    return True


async def _options_updated(hass: HomeAssistant, entry: ConfigEntry) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Stop loading the cards on new pages and remove the scene entities."""
    hass.services.async_remove(DOMAIN, SERVICE_SYNC_SCENES)
    hass.services.async_remove(DOMAIN, SERVICE_APPLY_SCENE)
    url = hass.data.get(DOMAIN, {}).get("cards_url")
    if url:
        remove_extra_js_url(hass, url)
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
