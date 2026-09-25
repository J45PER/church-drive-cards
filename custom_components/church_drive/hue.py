"""Syncing the universal scene library to the Hue bridge.

It reuses the bridge connection of Home Assistant's own Hue integration (and
so its key), so there's no link-button pairing. Hue scenes created here are
tagged with metadata.appdata "cd:<key>". HA's Hue integration picks them up
as scene entities like any other Hue scene.

Per chosen room/zone and white library scene (colour scenes use one working
scene per group instead, see apply.py):
- a scene tagged as ours: its lights are brought in line with the library;
- an untagged scene with the same name (e.g. one the Hue app made): left
  untouched and used as-is;
- no scene of that name: created.
"""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.config_entries import ConfigEntryState
from homeassistant.core import HomeAssistant

from .library import WHITE

_LOGGER = logging.getLogger(__name__)

APPDATA_PREFIX = "cd:"


def async_get_bridge_api(hass: HomeAssistant) -> Any | None:
    """Return the aiohue v2 API of HA's Hue integration, if it's ready."""
    for entry in hass.config_entries.async_entries("hue"):
        if entry.state is not ConfigEntryState.LOADED:
            continue
        bridge = getattr(entry, "runtime_data", None)
        if bridge is not None and bridge.api_version == 2 and bridge.api is not None:
            return bridge.api
    return None


def async_list_groups(api: Any) -> dict[str, str]:
    """Rooms and zones on the bridge: {group id: label}."""
    groups = {}
    for room in api.groups.room:
        groups[room.id] = f"Room: {room.metadata.name}"
    for zone in api.groups.zone:
        groups[zone.id] = f"Zone: {zone.metadata.name}"
    return dict(sorted(groups.items(), key=lambda item: item[1].lower()))


def _group(api: Any, group_id: str) -> tuple[Any, Any] | None:
    if group_id in api.groups.room:
        return api.groups.room, api.groups.room[group_id]
    if group_id in api.groups.zone:
        return api.groups.zone, api.groups.zone[group_id]
    return None


def _light_action(light: Any, spec: dict) -> dict:
    """What one light does in a library scene, within what it supports."""
    action: dict[str, Any] = {"on": {"on": True}}
    if light.dimming is not None:
        action["dimming"] = {"brightness": spec["brightness"]}
    if "xy" in spec and light.color is not None:
        x, y = spec["xy"]
        action["color"] = {"xy": {"x": x, "y": y}}
    elif "mirek" in spec and light.color_temperature is not None:
        schema = light.color_temperature.mirek_schema
        mirek = min(max(spec["mirek"], schema.mirek_minimum), schema.mirek_maximum)
        action["color_temperature"] = {"mirek": mirek}
    return action


def _actions(lights: list, spec: dict) -> list[dict]:
    return [
        {"target": {"rid": light.id, "rtype": "light"}, "action": _light_action(light, spec)}
        for light in sorted(lights, key=lambda light: light.id)
    ]


def _existing_actions(scene: Any) -> list[dict]:
    """A scene's actions in the same shape _actions builds, for comparison."""
    result = []
    for item in sorted(scene.actions, key=lambda a: a.target.rid):
        act = item.action
        action: dict[str, Any] = {}
        if act.on is not None:
            action["on"] = {"on": act.on.on}
        if act.dimming is not None:
            action["dimming"] = {"brightness": act.dimming.brightness}
        if act.color is not None:
            action["color"] = {"xy": {"x": act.color.xy.x, "y": act.color.xy.y}}
        if act.color_temperature is not None and act.color_temperature.mirek is not None:
            action["color_temperature"] = {"mirek": act.color_temperature.mirek}
        result.append({"target": {"rid": item.target.rid, "rtype": "light"}, "action": action})
    return result


def _same(a: list[dict], b: list[dict]) -> bool:
    """Compare actions, allowing for the bridge rounding brightness and xy."""

    def norm(actions: list[dict]) -> list:
        out = []
        for item in actions:
            act = item["action"]
            out.append(
                (
                    item["target"]["rid"],
                    act.get("on", {}).get("on"),
                    round(act["dimming"]["brightness"]) if "dimming" in act else None,
                    tuple(round(v, 3) for v in act["color"]["xy"].values()) if "color" in act else None,
                    act.get("color_temperature", {}).get("mirek"),
                )
            )
        return out

    return norm(a) == norm(b)


async def async_sync(hass: HomeAssistant, group_ids: list[str]) -> dict[str, Any]:
    """Sync the library to the given rooms/zones. Returns a summary per group."""
    api = async_get_bridge_api(hass)
    if api is None:
        _LOGGER.warning("Hue bridge not ready; universal scenes not synced")
        return {"error": "hue_not_ready"}

    summary: dict[str, Any] = {}
    for group_id in group_ids:
        found = _group(api, group_id)
        if found is None:
            summary[group_id] = {"error": "group_not_found"}
            continue
        controller, group = found
        lights = controller.get_lights(group_id)
        scenes = controller.get_scenes(group_id)
        result: dict[str, str] = {}
        for key, spec in WHITE.items():
            tag = f"{APPDATA_PREFIX}{key}"
            wanted = _actions(lights, spec)
            ours = next((s for s in scenes if s.metadata.appdata == tag), None)
            same_name = next((s for s in scenes if s.metadata.name.lower() == spec["name"].lower()), None)
            try:
                if ours is not None:
                    if _same(_existing_actions(ours), wanted):
                        result[spec["name"]] = "up to date"
                    else:
                        await api.request(
                            "put", f"clip/v2/resource/scene/{ours.id}", json={"actions": wanted}
                        )
                        result[spec["name"]] = "updated"
                elif same_name is not None:
                    result[spec["name"]] = "using existing Hue scene"
                elif not lights:
                    result[spec["name"]] = "skipped (no lights)"
                else:
                    await api.request(
                        "post",
                        "clip/v2/resource/scene",
                        json={
                            "type": "scene",
                            "metadata": {"name": spec["name"], "appdata": tag},
                            "group": {"rid": group_id, "rtype": group.type.value},
                            "actions": wanted,
                        },
                    )
                    result[spec["name"]] = "created"
            except Exception as err:  # noqa: BLE001 - report and carry on with the rest
                _LOGGER.error("Syncing %s to %s failed: %s", spec["name"], group.metadata.name, err)
                result[spec["name"]] = f"failed: {err}"
        summary[group.metadata.name] = result
    _LOGGER.info("Universal scenes synced: %s", summary)
    return summary


def async_describe(hass: HomeAssistant, group_ids: list[str]) -> dict[str, Any]:
    """The chosen groups' scenes as the bridge has them (for diagnostics)."""
    api = async_get_bridge_api(hass)
    if api is None:
        return {"error": "hue_not_ready"}
    out: dict[str, Any] = {}
    for group_id in group_ids:
        found = _group(api, group_id)
        if found is None:
            continue
        controller, group = found
        out[group.metadata.name] = {
            scene.metadata.name: {
                "appdata": scene.metadata.appdata,
                "actions": _existing_actions(scene),
            }
            for scene in controller.get_scenes(group_id)
        }
    return out
