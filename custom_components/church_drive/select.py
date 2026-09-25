"""Scene select entities: one per Hue room/zone ("Kitchen scene").

The state is the universal scene the room is showing: the last one applied
there while its lights still match it, else any white scene they match; none
when the lights are off or set by hand. For a few seconds after a scene is
applied it's shown regardless, while the lights change over (a Hue colour
scene can briefly report them off). The last scene is restored after a
restart. Choosing an option applies it.
"""

from __future__ import annotations

import time
from typing import Any

from homeassistant.components.select import SelectEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import ATTR_ENTITY_ID
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_call_later, async_track_state_change_event
from homeassistant.helpers.restore_state import RestoreEntity

from .apply import async_apply, members
from .const import DOMAIN, SIGNAL_ACTIVE, SIGNAL_LIBRARY
from .hue import async_get_bridge_api
from .library import Library

COLOUR_MODES = {"xy", "hs", "rgb", "rgbw", "rgbww"}
SETTLE_SECONDS = 15


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    """One select per Hue room/zone light entity."""
    api = async_get_bridge_api(hass)
    if api is None:
        return
    registry = er.async_get(hass)
    entities = []
    for grouped in api.groups.grouped_light:
        owner = grouped.owner.rid
        group = api.groups.room.get(owner) or api.groups.zone.get(owner)
        entity_id = registry.async_get_entity_id("light", "hue", grouped.id)
        if group is None or entity_id is None:
            continue
        entities.append(SceneSelect(hass.data[DOMAIN]["library"], grouped.id, entity_id, group.metadata.name))
    async_add_entities(entities)


def matches(hass: HomeAssistant, spec: dict, lights: list[str], playing_ok: bool) -> bool:
    """Whether the lit lights are showing a scene."""
    lit = [s for s in (hass.states.get(i) for i in lights) if s and s.state == "on"]
    if not lit:
        return False
    target = max(1, round(spec["brightness"] * 255 / 100))
    for st in lit:
        a = st.attributes
        modes = set(a.get("supported_color_modes") or [])
        if playing_ok and a.get("dynamics") == "dynamic_palette":
            continue
        if a.get("brightness") is not None and abs(a["brightness"] - target) > 4:
            return False
        if spec["kind"] == "colour" or "xy" in spec:
            xy = a.get("xy_color")
            colors = spec["colors"] if spec["kind"] == "colour" else [spec["xy"]]
            # 0.06: Hue clamps colours to what each bulb can show.
            if modes & COLOUR_MODES and not (
                xy and any(abs(xy[0] - c[0]) < 0.06 and abs(xy[1] - c[1]) < 0.06 for c in colors)
            ):
                return False
        elif "color_temp" in modes:
            kelvin = round(1_000_000 / spec["mirek"])
            if a.get("color_mode") != "color_temp" or a.get("color_temp_kelvin") is None:
                return False
            if abs(a["color_temp_kelvin"] - kelvin) > kelvin * 0.03:
                return False
    return True


class SceneSelect(SelectEntity, RestoreEntity):
    """The universal scene a Hue room/zone is showing."""

    _attr_should_poll = False
    _attr_icon = "mdi:palette"

    def __init__(self, library: Library, grouped_id: str, target: str, group_name: str) -> None:
        self._library = library
        self._target = target
        self._attr_unique_id = f"church_drive_scene_{grouped_id}"
        self._attr_name = f"{group_name} scene"
        self._key: str | None = None
        self._unsub_lights = None
        self._recheck = None
        self._attr_options = [spec["name"] for spec in library.all().values()]
        self._attr_current_option = None

    async def async_added_to_hass(self) -> None:
        # After a restart, pick up the scene the room was last set to.
        last_state = await self.async_get_last_state()
        found = self._library.find(last_state.state) if last_state else None
        active = self.hass.data[DOMAIN].setdefault("active", {})
        if found and self._target not in active:
            active[self._target] = found[0]
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_ACTIVE, self._applied))
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_LIBRARY, self._refresh))
        self._track()
        self.async_on_remove(lambda: self._unsub_lights and self._unsub_lights())
        self.async_on_remove(lambda: self._recheck and self._recheck())
        self._refresh()

    @callback
    def _track(self) -> None:
        if self._unsub_lights:
            self._unsub_lights()
        self._unsub_lights = async_track_state_change_event(
            self.hass, [self._target, *members(self.hass, self._target)], self._on_lights
        )

    @callback
    def _on_lights(self, event: Event) -> None:
        if event.data.get(ATTR_ENTITY_ID) == self._target:
            self._track()  # members may have changed
        self._refresh()

    @callback
    def _applied(self) -> None:
        """A scene was applied somewhere: show it now and look again once settled."""
        self._refresh()
        if self._recheck:
            self._recheck()
        self._recheck = async_call_later(self.hass, SETTLE_SECONDS + 1, lambda _now: self._refresh())

    @callback
    def _refresh(self) -> None:
        scenes = self._library.all()
        self._attr_options = [spec["name"] for spec in scenes.values()]
        lights = members(self.hass, self._target)
        data = self.hass.data[DOMAIN]
        last = data.get("active", {}).get(self._target)
        settling = time.monotonic() - data.get("applied_at", {}).get(self._target, -1e9) < SETTLE_SECONDS
        key = None
        if last in scenes and (settling or matches(self.hass, scenes[last], lights, playing_ok=True)):
            key = last
        else:
            key = next(
                (
                    k
                    for k, spec in scenes.items()
                    if spec["kind"] == "white" and matches(self.hass, spec, lights, False)
                ),
                None,
            )
        self._key = key
        self._attr_current_option = scenes[key]["name"] if key else None
        if self.hass and self.entity_id:
            self.async_write_ha_state()

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {"target": self._target, "scene_key": self._key}

    async def async_select_option(self, option: str) -> None:
        found = self._library.find(option)
        if found:
            await async_apply(self.hass, [self._target], found[0], found[1])
