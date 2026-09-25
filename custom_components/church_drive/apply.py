"""Applying a universal scene to lights, rooms or zones.

White scenes: one light.turn_on on the targets.

Colour scenes on a Hue room/zone: the group's one working scene on the bridge
(named "Church Drive", tagged appdata "cd:live", hidden in HA) is rewritten
with the palette - colours dealt round the lights, several points on gradient
lights - and recalled animated (dynamic_palette) or still. So colour scenes
never add more than one bridge scene per room/zone. Anything else (a single
light, a non-Hue group, or if the bridge refuses) gets the colours dealt
round its lights with light.turn_on.

The last scene applied to each target is remembered so the scene select
entities can show it (see select.py).
"""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.const import ATTR_ENTITY_ID
from homeassistant.core import Context, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_call_later

from .const import DOMAIN, SIGNAL_ACTIVE
from .hue import async_get_bridge_api
from .library import turn_on_data

_LOGGER = logging.getLogger(__name__)

LIVE_TAG = "cd:live"
LIVE_NAME = "Church Drive"


def hue_group_for(hass: HomeAssistant, entity_id: str) -> tuple[Any, Any, str] | None:
    """(api, controller, group id) for a Hue room/zone light entity."""
    api = async_get_bridge_api(hass)
    entry = er.async_get(hass).async_get(entity_id)
    if api is None or entry is None or entry.platform != "hue":
        return None
    grouped = api.groups.grouped_light.get(entry.unique_id)
    if grouped is None:
        return None
    owner = grouped.owner.rid
    if owner in api.groups.room:
        return api, api.groups.room, owner
    if owner in api.groups.zone:
        return api, api.groups.zone, owner
    return None


def members(hass: HomeAssistant, entity_id: str) -> list[str]:
    """A group's member lights, or the light itself."""
    st = hass.states.get(entity_id)
    ids = st.attributes.get(ATTR_ENTITY_ID) if st else None
    return [i for i in ids if i.startswith("light.")] if isinstance(ids, list) else [entity_id]


async def async_apply(
    hass: HomeAssistant, entity_ids: list[str], key: str, spec: dict, context: Context | None = None
) -> None:
    """Apply a scene spec to each target."""
    if spec["kind"] != "colour":
        await hass.services.async_call(
            "light",
            "turn_on",
            {ATTR_ENTITY_ID: entity_ids, **turn_on_data(spec)},
            blocking=True,
            context=context,
        )
    else:
        for entity_id in entity_ids:
            if not await _async_play_on_bridge(hass, entity_id, spec):
                await _async_deal_colours(hass, members(hass, entity_id), spec, context)
    active = hass.data[DOMAIN].setdefault("active", {})
    for entity_id in entity_ids:
        active[entity_id] = key
    async_dispatcher_send(hass, SIGNAL_ACTIVE)


async def _async_deal_colours(
    hass: HomeAssistant, lights: list[str], spec: dict, context: Context | None
) -> None:
    colors = spec["colors"]
    brightness = max(1, round(spec["brightness"] * 255 / 100))
    for i, light in enumerate(sorted(lights)):
        await hass.services.async_call(
            "light",
            "turn_on",
            {ATTR_ENTITY_ID: light, "xy_color": list(colors[i % len(colors)]), "brightness": brightness},
            blocking=True,
            context=context,
        )


def _xy(c: tuple[float, float]) -> dict:
    return {"xy": {"x": c[0], "y": c[1]}}


def _live_scene_body(lights: list, spec: dict) -> dict:
    colors = spec["colors"]
    brightness = spec["brightness"]
    actions = []
    for i, light in enumerate(sorted(lights, key=lambda item: item.id)):
        action: dict[str, Any] = {"on": {"on": True}}
        if light.dimming is not None:
            action["dimming"] = {"brightness": brightness}
        if light.color is not None:
            action["color"] = _xy(colors[i % len(colors)])
            gradient = getattr(light, "gradient", None)
            if gradient is not None and gradient.points_capable >= 2:
                count = min(gradient.points_capable, 5, max(2, len(colors)))
                action["gradient"] = {
                    "points": [{"color": _xy(colors[(i + n) % len(colors)])} for n in range(count)]
                }
        actions.append({"target": {"rid": light.id, "rtype": "light"}, "action": action})
    return {
        "actions": actions,
        "palette": {
            "color": [{"color": _xy(c), "dimming": {"brightness": brightness}} for c in colors[:9]],
            "dimming": [],
            "color_temperature": [],
            "effects": [],
        },
        "speed": spec.get("speed", 0.5),
    }


async def _async_play_on_bridge(hass: HomeAssistant, entity_id: str, spec: dict) -> bool:
    found = hue_group_for(hass, entity_id)
    if found is None:
        return False
    api, controller, group_id = found
    lights = controller.get_lights(group_id)
    if not lights:
        return False
    body = _live_scene_body(lights, spec)
    try:
        live = next((s for s in controller.get_scenes(group_id) if s.metadata.appdata == LIVE_TAG), None)
        if live is None:
            group = controller[group_id]
            created = await api.request(
                "post",
                "clip/v2/resource/scene",
                json={
                    "type": "scene",
                    "metadata": {"name": LIVE_NAME, "appdata": LIVE_TAG},
                    "group": {"rid": group_id, "rtype": group.type.value},
                    **body,
                },
            )
            scene_id = created[0]["rid"]
        else:
            scene_id = live.id
            await api.request("put", f"clip/v2/resource/scene/{scene_id}", json=body)
        action = "dynamic_palette" if spec.get("dynamic") else "active"
        await api.request("put", f"clip/v2/resource/scene/{scene_id}", json={"recall": {"action": action}})
    except Exception as err:  # noqa: BLE001 - fall back to per-light colours
        _LOGGER.warning("Couldn't play %s on the Hue bridge (%s); setting lights directly", spec["name"], err)
        return False
    _hide_live_scene_later(hass, scene_id)
    return True


def _hide_live_scene_later(hass: HomeAssistant, scene_id: str) -> None:
    """Hide the working scene's HA entity so it doesn't appear in scene lists."""

    @callback
    def hide(_now: Any = None) -> None:
        registry = er.async_get(hass)
        entity_id = registry.async_get_entity_id("scene", "hue", scene_id)
        if entity_id and registry.async_get(entity_id).hidden_by is None:
            registry.async_update_entity(entity_id, hidden_by=er.RegistryEntryHider.INTEGRATION)

    hide()
    async_call_later(hass, 5, hide)
