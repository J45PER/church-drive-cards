"""Church Drive: the house's custom dashboard cards and universal scenes.

- Cards: serves the bundled church-drive-cards.js and adds it to every
  frontend page, so no Lovelace resource is needed.
- Universal scenes: a scene library (library.py; built-in white and colour
  scenes plus the user's own from the scene builder) that the cards read over
  the websocket and apply to any room, zone or light (apply.py); a scene
  select entity per Hue room/zone (select.py); church_drive.apply_scene; and
  an optional sync of the white scenes to chosen Hue rooms (hue.py).
- Device health (health.py): watches chosen devices, spots ones whose state
  has gone stale (e.g. old readings after a restart), fixes them
  automatically, and reports on sensor.church_drive_device_health for the
  cards; church_drive.health_fix runs a fix on demand.
- People and notifications (people.py, kinds.py): the house's people and
  their phones, picked up from Home Assistant's people; who gets each kind
  of notification (set in Manager); church_drive.notify to send one; and
  sensor.church_drive_people for automations and the cards. Each person's
  places (their names for the zones they go to) are set in Manager.
- Camera events (events.py): a picture and the recording of every Ring
  doorbell press and motion, kept for a few days, for the cards' events
  viewer.
"""

from __future__ import annotations

import asyncio
from datetime import timedelta
import json
import logging
from pathlib import Path
from typing import Any

import aiohttp
import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.components.frontend import add_extra_js_url, remove_extra_js_url
from homeassistant.components.http import StaticPathConfig
from homeassistant.components.http.auth import async_sign_path
from homeassistant.components.lovelace.const import LOVELACE_DATA
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
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .apply import async_apply
from .const import (
    CONF_MAPS_PLACES_KEY,
    CONF_MAPS_TILES_KEY,
    WS_MAPS,
    WS_MAPS_SEARCH,
    CARDS_FILE,
    CONF_HEALTH_ENTITIES,
    CONF_SCENE_GROUPS,
    DOMAIN,
    SERVICE_APPLY_SCENE,
    SERVICE_HEALTH_FIX,
    SERVICE_NOTIFY,
    SERVICE_SYNC_SCENES,
    SIGNAL_LIBRARY,
    URL_BASE,
    EVENT_ICONS,
    WS_ICON_SET,
    WS_ICONS,
    WS_LIBRARY,
    WS_PEOPLE,
    WS_PEOPLE_ASSIGN,
    WS_CAMERA_EVENTS,
    WS_CAMERA_LINK_SET,
    WS_CAMERA_LINKS,
    WS_CAMERA_SETTINGS,
    WS_PEOPLE_PHONE,
    WS_PEOPLE_PLACES,
    WS_PEOPLE_CARS,
    WS_SCENE_DELETE,
    WS_SCENE_PREVIEW,
    WS_SCENE_SAVE,
)
from .events import URL as EVENTS_URL, CameraEvents, EventFileView
from .maps import GoogleTiles, MapTileView
from .health import DeviceHealth
from .hue import async_sync
from .library import Library, normalise
from .icons import Icons
from .people import People

_LOGGER = logging.getLogger(__name__)
FRONTEND_DIR = Path(__file__).parent / "frontend"
PLATFORMS = [Platform.SELECT, Platform.SENSOR]

HEALTH_FIX_SCHEMA = vol.Schema(
    {
        vol.Required(ATTR_ENTITY_ID): cv.entity_ids,
        vol.Optional("action", default="resync"): vol.In(["refresh", "resync", "nudge", "reconnect"]),
    }
)

NOTIFY_SCHEMA = vol.Schema(
    {
        vol.Optional("kind"): cv.string,
        vol.Optional("people"): vol.All(cv.ensure_list, [cv.string]),
        vol.Required("message"): cv.string,
        vol.Optional("title", default=""): cv.string,
        vol.Optional("admin_message", default=""): cv.string,
        vol.Optional("tag", default=""): cv.string,
        vol.Optional("link", default=""): cv.string,
        vol.Optional("image", default=""): cv.string,
        vol.Optional("critical"): cv.boolean,
        vol.Optional("data"): dict,
    }
)

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
        vol.Optional("speed", default=0.63): vol.All(vol.Coerce(float), vol.Range(min=0, max=1)),
        vol.Optional("icon"): cv.string,
    }
)


def _library(hass: HomeAssistant) -> Library:
    return hass.data[DOMAIN]["library"]


def _people(hass: HomeAssistant) -> People | None:
    return hass.data.get(DOMAIN, {}).get("people")


@websocket_api.websocket_command({vol.Required("type"): WS_PEOPLE})
@callback
def ws_people(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """People, their phones, and every kind of notification with who's assigned."""
    people = _people(hass)
    if people is None:
        connection.send_error(msg["id"], "not_ready", "People and notifications aren't running")
        return
    connection.send_result(msg["id"], {"people": people.people(), "kinds": people.kinds(), "rev": people.rev})


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_PEOPLE_ASSIGN,
        vol.Required("kind"): cv.string,
        vol.Optional("person"): cv.entity_id,
        vol.Required("on"): cv.boolean,
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_people_assign(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Tick or untick a person (or "everyone") for a kind."""
    people = _people(hass)
    if people is None:
        connection.send_error(msg["id"], "not_ready", "People and notifications aren't running")
        return
    await people.async_set(msg["kind"], msg.get("person"), msg["on"])
    connection.send_result(msg["id"], {"kinds": people.kinds()})


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_PEOPLE_PHONE,
        vol.Required("person"): cv.entity_id,
        vol.Required("service"): cv.string,
        vol.Required("on"): cv.boolean,
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_people_phone(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Switch one of a person's phones on or off for notifications."""
    people = _people(hass)
    if people is None:
        connection.send_error(msg["id"], "not_ready", "People and notifications aren't running")
        return
    await people.async_set_phone(msg["person"], msg["service"], msg["on"])
    connection.send_result(msg["id"], {"people": people.people()})


@websocket_api.websocket_command({vol.Required("type"): WS_LIBRARY})
@callback
def ws_library(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Send the scene library (built-in and custom) to the cards."""
    connection.send_result(msg["id"], {"scenes": _library(hass).as_list(), "custom": _library(hass).custom})


@websocket_api.websocket_command({vol.Required("type"): WS_ICONS})
@callback
def ws_icons(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Send the mode icons (built-in and changed) to the cards and the app."""
    icons = hass.data.get(DOMAIN, {}).get("icons")
    if icons is None:
        connection.send_error(msg["id"], "not_ready", "Icons aren't running")
        return
    connection.send_result(msg["id"], icons.as_dict())


@websocket_api.websocket_command(
    {vol.Required("type"): WS_ICON_SET, vol.Required("group"): cv.string, vol.Required("key"): cv.string, vol.Optional("icon"): vol.Any(None, cv.string)}
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_icon_set(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Change one mode's icon (or put the built-in one back with no icon)."""
    icons = hass.data.get(DOMAIN, {}).get("icons")
    if icons is None:
        connection.send_error(msg["id"], "not_ready", "Icons aren't running")
        return
    try:
        await icons.async_set(msg["group"], msg["key"], msg.get("icon"))
    except ValueError as err:
        connection.send_error(msg["id"], "invalid", str(err))
        return
    hass.bus.async_fire(EVENT_ICONS)
    connection.send_result(msg["id"], icons.as_dict())


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


async def _async_ensure_resource(hass: HomeAssistant, url: str) -> None:
    """Keep a dashboard resource for the cards, at the current version.

    Pages always get the stored resource list, so the cards load even when a
    page is opened while HA is still starting, before this integration has
    added its extra module URL. Both use the same URL, so the browser loads
    the bundle once.
    """
    try:
        resources = hass.data[LOVELACE_DATA].resources
        if getattr(resources, "async_create_item", None) is None:
            return  # resources are managed in YAML
        await resources.async_get_info()  # loads the stored list
        base = f"{URL_BASE}/{CARDS_FILE}"
        ours = [item for item in resources.async_items() if str(item.get("url", "")).startswith(base)]
        if not ours:
            await resources.async_create_item({"res_type": "module", "url": url})
            return
        if ours[0]["url"] != url:
            await resources.async_update_item(ours[0]["id"], {"res_type": "module", "url": url})
        for extra in ours[1:]:
            await resources.async_delete_item(extra["id"])
    except Exception as err:  # noqa: BLE001 - the extra module URL still loads the cards
        _LOGGER.warning("Couldn't register the cards as a dashboard resource: %s", err)


async def _async_remove_resource(hass: HomeAssistant) -> None:
    try:
        resources = hass.data[LOVELACE_DATA].resources
        if getattr(resources, "async_delete_item", None) is None:
            return
        await resources.async_get_info()
        base = f"{URL_BASE}/{CARDS_FILE}"
        for item in list(resources.async_items()):
            if str(item.get("url", "")).startswith(base):
                await resources.async_delete_item(item["id"])
    except Exception as err:  # noqa: BLE001
        _LOGGER.warning("Couldn't remove the cards' dashboard resource: %s", err)


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_PEOPLE_PLACES,
        vol.Required("person"): cv.entity_id,
        vol.Required("places"): [
            vol.Schema({vol.Required("zone"): cv.entity_id, vol.Optional("name", default=""): cv.string})
        ],
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_people_places(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Set a person's places: the zones they go to, each with their name for it."""
    people = _people(hass)
    if people is None:
        connection.send_error(msg["id"], "not_ready", "People and notifications aren't running")
        return
    await people.async_set_places(msg["person"], msg["places"])
    connection.send_result(msg["id"], {"people": people.people()})


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_PEOPLE_CARS,
        vol.Required("person"): cv.entity_id,
        vol.Required("cars"): [cv.string],
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_people_cars(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Set a person's cars (car device ids)."""
    people = _people(hass)
    if people is None:
        connection.send_error(msg["id"], "not_ready", "People and notifications aren't running")
        return
    await people.async_set_cars(msg["person"], msg["cars"])
    connection.send_result(msg["id"], {"people": people.people()})


def _events(hass: HomeAssistant) -> CameraEvents | None:
    return hass.data.get(DOMAIN, {}).get("events")


@websocket_api.websocket_command({vol.Required("type"): WS_CAMERA_EVENTS, vol.Required("camera"): cv.string})
@callback
def ws_camera_events(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """A camera's saved events (newest first), with signed links to each picture and clip."""
    events = _events(hass)
    if events is None:
        connection.send_error(msg["id"], "not_ready", "Camera events aren't running")
        return
    cam = msg["camera"]
    base = CameraEvents.base_of(cam) or cam
    expires = timedelta(hours=2)

    def sign(name: str | None) -> str | None:
        if not name:
            return None
        return async_sign_path(hass, f"{EVENTS_URL}/{base}/{name}", expires, refresh_token_id=connection.refresh_token_id)

    out = [{**e, "picture": sign(e.pop("jpg")), "clip": sign(e.pop("mp4"))} for e in events.events(base)]
    connection.send_result(msg["id"], {"camera": base, "events": out, "settings": events.settings()})


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_CAMERA_SETTINGS,
        vol.Optional("keep_days"): vol.All(vol.Coerce(int), vol.Range(min=1, max=365)),
        vol.Optional("max_gb"): vol.All(vol.Coerce(float), vol.Range(min=0.5, max=10000)),
        vol.Optional("folder"): cv.string,
        vol.Optional("cooldown"): vol.All(vol.Coerce(int), vol.Range(min=30, max=3600)),
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_camera_settings(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """How long camera events are kept, the space they may use, and where (e.g. a NAS share)."""
    events = _events(hass)
    if events is None:
        connection.send_error(msg["id"], "not_ready", "Camera events aren't running")
        return
    await events.async_set_settings(msg.get("keep_days"), msg.get("max_gb"), msg.get("folder"), msg.get("cooldown"))
    connection.send_result(msg["id"], events.settings())


@websocket_api.websocket_command({vol.Required("type"): WS_CAMERA_LINKS})
@callback
def ws_camera_links(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Camera links per alarm mode, the cameras, the current mode and the cooldown."""
    events = _events(hass)
    if events is None:
        connection.send_error(msg["id"], "not_ready", "Camera events aren't running")
        return
    connection.send_result(msg["id"], events.links())


@websocket_api.websocket_command(
    {
        vol.Required("type"): WS_CAMERA_LINK_SET,
        vol.Required("mode"): vol.In(["disarmed", "home", "away"]),
        vol.Required("trigger"): cv.entity_id,
        vol.Optional("cams"): vol.Any(None, [cv.string]),
        vol.Optional("secs"): vol.All(vol.Coerce(int), vol.Range(min=5, max=120)),
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_camera_link_set(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Which cameras a trigger records in a mode (no `cams`: take the trigger out of that mode)."""
    events = _events(hass)
    if events is None:
        connection.send_error(msg["id"], "not_ready", "Camera events aren't running")
        return
    await events.async_set_link(msg["mode"], msg["trigger"], msg.get("cams"), msg.get("secs"))
    connection.send_result(msg["id"], events.links())


def _maps_option(hass: HomeAssistant, key: str) -> str:
    entries = hass.config_entries.async_entries(DOMAIN)
    return (entries[0].options.get(key) or "").strip() if entries else ""


@websocket_api.websocket_command({vol.Required("type"): WS_MAPS})
@callback
def ws_maps(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Where browsers get Google's map tiles (through Home Assistant), and whether Places search is set up."""
    tiles: GoogleTiles | None = hass.data.get(DOMAIN, {}).get("tiles")
    connection.send_result(
        msg["id"],
        {"tile_url": tiles.url() if tiles else None, "places": bool(_maps_option(hass, CONF_MAPS_PLACES_KEY))},
    )


PLACES_URL = "https://places.googleapis.com/v1/places:searchText"


@websocket_api.websocket_command(
    {vol.Required("type"): WS_MAPS_SEARCH, vol.Required("query"): vol.All(cv.string, vol.Length(min=2, max=200))}
)
@websocket_api.async_response
async def ws_maps_search(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]) -> None:
    """Search Google Places (shops, businesses, addresses) near home, here so the key stays private."""
    key = _maps_option(hass, CONF_MAPS_PLACES_KEY)
    if not key:
        connection.send_error(msg["id"], "no_key", "No Google Places key set")
        return
    body = {
        "textQuery": msg["query"],
        "regionCode": "gb",
        "languageCode": "en-GB",
        "pageSize": 6,
        "locationBias": {"circle": {"center": {"latitude": hass.config.latitude, "longitude": hass.config.longitude}, "radius": 50000.0}},
    }
    headers = {"X-Goog-Api-Key": key, "X-Goog-FieldMask": "places.displayName,places.formattedAddress,places.location"}
    try:
        async with asyncio.timeout(10):
            resp = await async_get_clientsession(hass).post(PLACES_URL, json=body, headers=headers)
            result = await resp.json(content_type=None)
    except (aiohttp.ClientError, TimeoutError, ValueError) as err:
        connection.send_error(msg["id"], "unavailable", f"Google search didn't answer: {err}")
        return
    if resp.status != 200:
        message = ((result or {}).get("error") or {}).get("message") or f"HTTP {resp.status}"
        connection.send_error(msg["id"], "google_error", message)
        return
    places = []
    for place in (result or {}).get("places", []):
        where = place.get("location") or {}
        if "latitude" not in where:
            continue
        places.append(
            {
                "name": (place.get("displayName") or {}).get("text", ""),
                "address": place.get("formattedAddress", ""),
                "lat": where["latitude"],
                "lon": where["longitude"],
            }
        )
    connection.send_result(msg["id"], places)


def _version() -> str:
    return json.loads((Path(__file__).parent / "manifest.json").read_text())["version"]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Serve the cards, load them on every page, and set up the scenes."""
    data = hass.data.setdefault(DOMAIN, {})
    if "cards_url" not in data:
        # Static paths and websocket commands can't be unregistered, so do
        # them once per run.
        await hass.http.async_register_static_paths(
            [StaticPathConfig(URL_BASE, str(FRONTEND_DIR), cache_headers=False)]
        )
        for command in (
            ws_library, ws_icons, ws_icon_set, ws_scene_save, ws_scene_delete, ws_scene_preview, ws_people, ws_people_assign, ws_people_phone,
            ws_people_places, ws_people_cars, ws_camera_events, ws_camera_settings, ws_camera_links, ws_camera_link_set, ws_maps, ws_maps_search,
        ):
            websocket_api.async_register_command(hass, command)
    # The version in the URL makes browsers fetch the new bundle after an
    # update. It's read on every setup, so reloading Church Drive after a
    # card-only update is enough: no Home Assistant restart (restarts are
    # when the Philips devices can come back stale).
    version = await hass.async_add_executor_job(_version)
    data["cards_url"] = f"{URL_BASE}/{CARDS_FILE}?v={version}"
    add_extra_js_url(hass, data["cards_url"])
    await _async_ensure_resource(hass, data["cards_url"])

    library = Library(hass)
    await library.async_load()
    data["library"] = library

    # The mode icons are optional extras: if they can't load, the cards use their built-in ones.
    icons = Icons(hass)
    try:
        await icons.async_load()
        data["icons"] = icons
    except Exception:  # noqa: BLE001
        _LOGGER.exception("Icons couldn't load; the cards use their built-in icons")

    # Device health must never stop the cards and scenes from loading.
    health = DeviceHealth(hass, entry.options.get(CONF_HEALTH_ENTITIES, []))
    try:
        await health.async_start()
        entry.async_on_unload(health.async_stop)
        data["health"] = health
    except Exception:  # noqa: BLE001
        _LOGGER.exception("Device health couldn't start; the cards and scenes still work")
        data["health"] = None

    # People and notifications mustn't stop anything else loading either.
    people = People(hass)
    try:
        await people.async_start()
        entry.async_on_unload(people.async_stop)
        data["people"] = people
    except Exception:  # noqa: BLE001
        _LOGGER.exception("People and notifications couldn't start; everything else still works")
        data["people"] = None

    # Camera events mustn't stop anything else loading either.
    events = CameraEvents(hass)
    try:
        await events.async_start()
        entry.async_on_unload(events.async_stop)
        data["events"] = events
        if "events_view" not in data:
            hass.http.register_view(EventFileView(hass))
            data["events_view"] = True
    except Exception:  # noqa: BLE001
        _LOGGER.exception("Camera events couldn't start; everything else still works")
        data["events"] = None

    # Google map tiles through Home Assistant (maps.py), with a Map Tiles key.
    key = (entry.options.get(CONF_MAPS_TILES_KEY) or "").strip()
    data["tiles"] = GoogleTiles(hass, key) if key else None
    if "tiles_view" not in data:
        hass.http.register_view(MapTileView(hass))
        data["tiles_view"] = True

    async def notify(call: ServiceCall) -> ServiceResponse:
        if data.get("people") is None:
            raise ServiceValidationError("People and notifications aren't running")
        d = call.data
        result = await data["people"].async_notify(
            d.get("kind"), d.get("people"), d.get("title", ""), d["message"], d.get("admin_message", ""),
            d.get("tag", ""), d.get("link", ""), d.get("image", ""), d.get("critical"), d.get("data"), call.context,
        )
        return result if call.return_response else None

    hass.services.async_register(
        DOMAIN, SERVICE_NOTIFY, notify, schema=NOTIFY_SCHEMA, supports_response=SupportsResponse.OPTIONAL
    )

    async def health_fix(call: ServiceCall) -> None:
        if data.get("health") is None:
            raise ServiceValidationError("Device health isn't running")
        for entity_id in call.data[ATTR_ENTITY_ID]:
            await data["health"].async_fix(entity_id, call.data["action"])

    hass.services.async_register(DOMAIN, SERVICE_HEALTH_FIX, health_fix, schema=HEALTH_FIX_SCHEMA)

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
    hass.services.async_remove(DOMAIN, SERVICE_HEALTH_FIX)
    hass.services.async_remove(DOMAIN, SERVICE_NOTIFY)
    url = hass.data.get(DOMAIN, {}).get("cards_url")
    if url:
        remove_extra_js_url(hass, url)
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)


async def async_remove_entry(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Integration removed: drop the cards' dashboard resource too."""
    await _async_remove_resource(hass)
