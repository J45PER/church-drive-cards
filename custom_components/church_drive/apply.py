"""Applying a universal scene to lights, rooms or zones.

White scenes: one light.turn_on on the targets.

Colour scenes on a Hue room/zone: the group has two working scenes on the
bridge ("Church Drive" and "Church Drive 2", tagged appdata "cd:live" and
"cd:live2", hidden in HA). The one not playing is rewritten with the palette -
colours spread round the lights (blended so no two match), several points on
gradient lights - and recalled animated (dynamic_palette) or still. Rewriting the scene that's
playing made the lights drop out for a few seconds, hence the pair. So colour
scenes never add more than two bridge scenes per room/zone. Anything else (a single
light, a non-Hue group, or if the bridge refuses) gets the colours spread
round its lights with light.turn_on.

The last scene applied to each target, and when, is remembered so the scene
select entities can show it (see select.py).
"""

from __future__ import annotations

import logging
import time
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

# (appdata tag, scene name) of each group's two working scenes.
LIVE_SCENES = (("cd:live", "Church Drive"), ("cd:live2", "Church Drive 2"))


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
    applied = hass.data[DOMAIN].setdefault("applied_at", {})
    applied_wall = hass.data[DOMAIN].setdefault("applied_wall", {})
    for entity_id in entity_ids:
        active[entity_id] = key
        applied[entity_id] = time.monotonic()
        applied_wall[entity_id] = time.time()
    async_dispatcher_send(hass, SIGNAL_ACTIVE)


async def _async_deal_colours(
    hass: HomeAssistant, lights: list[str], spec: dict, context: Context | None
) -> None:
    lights = sorted(lights)
    for light, (xy, level) in zip(lights, spread(spec["colors"], _levels(spec), len(lights)), strict=True):
        brightness = max(1, round(level * 255 / 100))
        await hass.services.async_call(
            "light",
            "turn_on",
            {ATTR_ENTITY_ID: light, "xy_color": list(xy), "brightness": brightness},
            blocking=True,
            context=context,
        )


def _xy(c: tuple[float, float]) -> dict:
    return {"xy": {"x": c[0], "y": c[1]}}


def _levels(spec: dict) -> list[float]:
    """Each palette colour's brightness (0-100)."""
    return spec.get("levels") or [spec["brightness"]] * len(spec["colors"])


def spread(colors: list, levels: list[float], count: int) -> list[tuple[tuple[float, float], float]]:
    """`count` (colour, brightness) pairs evenly along the palette, blending
    between neighbouring colours, so no two lights share a colour when there
    are more lights than colours."""
    if count <= len(colors):
        step = len(colors) / max(count, 1)
        return [(tuple(colors[int(i * step)]), levels[int(i * step)]) for i in range(count)]
    out = []
    for i in range(count):
        pos = i * (len(colors) - 1) / (count - 1) if count > 1 else 0
        a = min(int(pos), len(colors) - 1)
        b = min(a + 1, len(colors) - 1)
        t = pos - a
        xy = (colors[a][0] + (colors[b][0] - colors[a][0]) * t, colors[a][1] + (colors[b][1] - colors[a][1]) * t)
        out.append(((round(xy[0], 4), round(xy[1], 4)), round(levels[a] + (levels[b] - levels[a]) * t, 2)))
    return out


def _live_scene_body(lights: list, spec: dict) -> dict:
    colors = spec["colors"]
    levels = _levels(spec)
    lights = sorted(lights, key=lambda item: item.id)
    # Each light starts on its own colour; the animation palette gets at least
    # five colours (blends of a short one), like Hue's own, so the bridge has
    # enough to keep neighbouring lights apart.
    starts = spread(colors, levels, len(lights))
    palette = spread(colors, levels, max(5, len(colors)))[:9]
    actions = []
    for i, light in enumerate(lights):
        xy, level = starts[i]
        action: dict[str, Any] = {"on": {"on": True}}
        if light.dimming is not None:
            action["dimming"] = {"brightness": level}
        if light.color is not None:
            action["color"] = _xy(xy)
            gradient = getattr(light, "gradient", None)
            if gradient is not None and gradient.points_capable >= 2:
                count = min(gradient.points_capable, 5)
                action["gradient"] = {
                    "points": [{"color": _xy(palette[(i + n) % len(palette)][0])} for n in range(count)],
                    "mode": "interpolated_palette",
                }
        actions.append({"target": {"rid": light.id, "rtype": "light"}, "action": action})
    return {
        "actions": actions,
        "palette": {
            "color": [{"color": _xy(c), "dimming": {"brightness": level}} for c, level in palette],
            "dimming": [],
            "color_temperature": [],
            "effects": [],
        },
        "speed": spec.get("speed", 0.5),
    }


def _playing(scene: Any) -> bool:
    status = getattr(scene, "status", None) if scene is not None else None
    return bool(status and getattr(status.active, "value", "inactive") != "inactive")


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
        scenes = controller.get_scenes(group_id)
        slots = [next((sc for sc in scenes if sc.metadata.appdata == tag), None) for tag, _ in LIVE_SCENES]
        # Write to whichever working scene isn't the one playing.
        index = 1 if _playing(slots[0]) else 0
        live = slots[index]
        tag, name = LIVE_SCENES[index]
        if live is None:
            group = controller[group_id]
            created = await api.request(
                "post",
                "clip/v2/resource/scene",
                json={
                    "type": "scene",
                    "metadata": {"name": name, "appdata": tag},
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
