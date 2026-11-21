"""Hybrid scenes: one tap gives each light in a room its own look (the rules are in hybrid_logic.py).

Kept in .storage/church_drive.hybrids for everyone. Playing one goes light by light: Off switches it off, a white or dynamic scene is played
with the scene library (the same code the scene tiles use), a colour is sent as that colour, and a gradient is played as a still colour
scene of its colours. "Leave" keeps the light as it is.
"""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.core import Context, HomeAssistant
from homeassistant.helpers.storage import Store

from .apply import async_apply
from .hybrid_logic import clean_all, clean_hybrid, clean_look, for_target, gradient_spec, kelvin_rgb, play_order, ring_is_colourful, rgb_of
from .library import Library, hex_to_xy, xy_to_hex

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_hybrids_changed"


class Hybrids:
    """The saved hybrid scenes, and which one is playing on each target."""

    def __init__(self, hass: HomeAssistant, library: Library) -> None:
        self.hass = hass
        self._library = library
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.hybrids")
        self._items: list[dict[str, Any]] = []
        self._active: dict[str, str] = {}

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self._items = clean_all(data.get("hybrids"))

    async def _async_save(self) -> None:
        await self._store.async_save({"hybrids": self._items})
        self.hass.bus.async_fire(EVENT_CHANGED, {})

    def listing(self, target: str | None = None) -> dict[str, Any]:
        return {"hybrids": for_target(self._items, target), "active": dict(self._active)}

    async def async_save_hybrid(self, value: dict[str, Any]) -> dict[str, Any]:
        """Add a hybrid, or change the one with the same id."""
        existing = next((h for h in self._items if h["id"] == value.get("id")), None)
        hybrid = clean_hybrid(value, existing)
        if existing is None and len(for_target(self._items, hybrid["target"])) >= 12:
            raise ValueError("A room can keep twelve hybrid scenes")
        self._items = [h for h in self._items if h["id"] != hybrid["id"]] + [hybrid]
        await self._async_save()
        return hybrid

    async def async_delete(self, hybrid_id: str) -> None:
        self._items = [h for h in self._items if h["id"] != hybrid_id]
        self._active = {t: i for t, i in self._active.items() if i != hybrid_id}
        await self._async_save()

    async def async_play(self, hybrid_id: str, context: Context | None = None) -> None:
        """Give every light its look."""
        hybrid = next((h for h in self._items if h["id"] == hybrid_id), None)
        if hybrid is None:
            raise ValueError("That hybrid scene is gone")
        looks = hybrid["looks"]
        rings = {e for e in looks if self._is_ring(e)}
        group = self.hass.states.get(hybrid["target"])
        siblings = set(group.attributes.get("entity_id") or []) if group is not None else set()
        # A Govee fitting in colour mode shows its own colour on the light beside the ring, so with a coloured ring that light is set first and
        # its white is sent as the same white in RGB (a white-mode command would switch the ring off).
        as_colour = {e for e in looks if e not in rings and e in siblings and rings & siblings} if ring_is_colourful(looks, rings) else set()
        for entity in play_order(list(looks), rings):
            look = looks[entity]
            if self.hass.states.get(entity) is None:
                continue
            try:
                await self._async_look(entity, look, context, as_colour=entity in as_colour)
            except Exception as err:  # noqa: BLE001 - one light that doesn't answer mustn't stop the others
                _LOGGER.warning("Hybrid scene %s: %s didn't take its look: %s", hybrid["name"], entity, err)
        self._active[hybrid["target"]] = hybrid["id"]
        self.hass.bus.async_fire(EVENT_CHANGED, {})

    async def async_gradient(self, entities: list[str], colours: list[str], context: Context | None = None) -> None:
        """Play a gradient of two to five colours (hex) on lights: along the strip of a gradient light, a still colour on any other."""
        look = clean_look({"t": "gradient", "v": colours})
        if look is None:
            raise ValueError("A gradient needs two to five colours")
        for entity in entities:
            if self.hass.states.get(entity) is not None:
                await self._async_look(entity, look, context)

    def _is_ring(self, entity: str) -> bool:
        """A ring of segments: a group made only of a fitting's segment lights."""
        state = self.hass.states.get(entity)
        members = (state.attributes.get("entity_id") if state is not None else None) or []
        return len(members) > 1 and all("_segment_" in m for m in members)

    async def _async_look(self, entity: str, look: dict[str, Any], context: Context | None, as_colour: bool = False) -> None:
        kind = look["t"]
        if kind == "leave":
            return
        if kind == "off":
            await self.hass.services.async_call("light", "turn_off", {"entity_id": entity}, blocking=True, context=context)
        elif kind in ("white", "dynamic"):
            found = self._library.find(look["v"])
            if found is None:
                _LOGGER.warning("Hybrid scene: no scene called %s", look["v"])
                return
            if as_colour and kind == "white":
                spec = found[1]
                rgb = kelvin_rgb(1_000_000 / spec["mirek"]) if "mirek" in spec else rgb_of(xy_to_hex(tuple(spec["xy"]))) if "xy" in spec else None
                if rgb is not None:
                    await self.hass.services.async_call(
                        "light", "turn_on", {"entity_id": entity, "rgb_color": rgb, "brightness": max(1, round(spec["brightness"] * 255 / 100))},
                        blocking=True, context=context,
                    )
                    return
            await async_apply(self.hass, [entity], found[0], found[1], context)
        elif kind == "colour":
            await self.hass.services.async_call("light", "turn_on", {"entity_id": entity, "rgb_color": rgb_of(look["v"])}, blocking=True, context=context)
        elif kind == "gradient":
            spec = gradient_spec([hex_to_xy(c) for c in look["v"]])
            await async_apply(self.hass, [entity], "hybrid", spec, context)
