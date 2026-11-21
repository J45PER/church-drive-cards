"""Fittings: a light with a main light and a ring of segments (the Cloakroom's Govee), kept as one thing (rules in fitting_logic.py).

Home Assistant only reports one colour for the whole Govee device and the ring's segments are just the last thing we sent, so the house keeps its
own record of what the main light and each segment are set to, and sends the commands in the order that gives what was asked. The record is in
.storage/church_drive.fittings and goes out on websocket church_drive/fitting; church_drive/fitting/set and the service church_drive.set_fitting change it.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .fitting_logic import clean_main, clean_ring, new_fitting, plan, result

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
EVENT_CHANGED = "church_drive_fittings_changed"
COLOUR_MODES = {"hs", "rgb", "rgbw", "rgbww", "xy"}


class Fittings:
    """The record of each fitting (by its room's group light) and the commands that change it."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.fittings")
        self._items: dict[str, dict[str, Any]] = {}
        self._lock = asyncio.Lock()

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self._items = data.get("fittings") if isinstance(data.get("fittings"), dict) else {}

    async def _async_save(self) -> None:
        await self._store.async_save({"fittings": self._items})

    # ---- what is a fitting ------------------------------------------------------------

    def parts(self, room: str) -> dict[str, Any] | None:
        """The main light, the ring and the segments of a room that has a ring, or None."""
        state = self.hass.states.get(room)
        members = list((state.attributes.get("entity_id") or []) if state is not None else [])
        ring = main = None
        segments: list[str] = []
        for member in members:
            ms = self.hass.states.get(member)
            sub = list((ms.attributes.get("entity_id") or []) if ms is not None else [])
            if len(sub) > 1 and all("_segment_" in m for m in sub):
                ring, segments = member, sorted(sub)
            elif main is None:
                main = member
        if ring is None or main is None or not segments:
            return None
        return {"room": room, "main": main, "ring": ring, "segments": segments}

    def rooms(self) -> list[str]:
        out = []
        for state in self.hass.states.async_all("light"):
            if state.attributes.get("entity_id") and self.parts(state.entity_id) is not None:
                out.append(state.entity_id)
        return sorted(out)

    # ---- the record --------------------------------------------------------------------

    def _seed(self, parts: dict[str, Any]) -> dict[str, Any]:
        """A first record from what Home Assistant reports, until the house has set the fitting itself."""
        fit = new_fitting(len(parts["segments"]))
        main = self.hass.states.get(parts["main"])
        if main is not None and main.state == "on":
            colour = main.attributes.get("color_mode") in COLOUR_MODES
            fit["main"] = clean_main({
                "on": True, "mode": "colour" if colour else "white",
                "kelvin": main.attributes.get("color_temp_kelvin") or 2700, "rgb": main.attributes.get("rgb_color"),
                "brightness": round((main.attributes.get("brightness") or 255) * 100 / 255),
            }, fit["main"])
        ring = self.hass.states.get(parts["ring"])
        segments = []
        for sid in parts["segments"]:
            s = self.hass.states.get(sid)
            on = s is not None and s.state == "on"
            segments.append({
                "rgb": (s.attributes.get("rgb_color") if on else None) or [255, 200, 140],
                "brightness": round((s.attributes.get("brightness") or 255) * 100 / 255) if on else 100,
            })
        # With the device off nothing is lit, whatever the segments last reported: they only repeat the last command sent.
        ring_on = ring is not None and ring.state == "on" and fit["main"]["on"]
        fit["ring"] = clean_ring({"on": ring_on, "segments": segments}, count=len(parts["segments"]))
        return fit

    def get(self, room: str) -> dict[str, Any] | None:
        parts = self.parts(room)
        if parts is None:
            return None
        if room not in self._items:
            self._items[room] = self._seed(parts)
        return {**parts, "fitting": self._items[room]}

    def listing(self) -> dict[str, Any]:
        return {"fittings": {room: self.get(room) for room in self.rooms()}}

    # ---- changing it -------------------------------------------------------------------

    async def async_set(self, room: str, request: dict[str, Any], context=None) -> dict[str, Any]:
        """Give the fitting [request] (a main and/or a ring look, see fitting_logic) and return its record."""
        found = self.get(room)
        if found is None:
            raise ValueError("That light isn't a fitting with a ring")
        async with self._lock:
            current = self._items[room]
            ops = plan(current, request)
            after = result(current, request)
            # The record changes first and is announced at once, so the app shows the new setting while the slow commands (the Govee takes about a
            # second a segment) are still going out.
            self._items[room] = after
            await self._async_save()
            self.hass.bus.async_fire(EVENT_CHANGED, {"room": room})
            for op in ops:
                await self._run(op, found, context)
        return after

    async def _call(self, service: str, data: dict[str, Any], context) -> None:
        await self.hass.services.async_call("light", service, data, blocking=True, context=context)

    async def _run(self, op: dict[str, Any], found: dict[str, Any], context) -> None:
        kind = op["op"]
        if kind == "device_off":
            # The main light's own entity is the device's power: one command, where the room's group would switch the 13 segments off one by one first.
            await self._call("turn_off", {"entity_id": found["main"]}, context)
        elif kind == "main_off":
            await self._call("turn_off", {"entity_id": found["main"]}, context)
        elif kind == "main_white":
            await self._call("turn_on", {"entity_id": found["main"], "color_temp_kelvin": op["kelvin"], "brightness_pct": op["brightness"]}, context)
        elif kind == "main_colour":
            await self._call("turn_on", {"entity_id": found["main"], "rgb_color": op["rgb"], "brightness_pct": op["brightness"]}, context)
        elif kind == "ring_off":
            await self._call("turn_off", {"entity_id": found["ring"]}, context)
        elif kind == "ring":
            for group in op["groups"]:
                ids = [found["segments"][i] for i in group["indices"] if i < len(found["segments"])]
                if ids:
                    await self._call("turn_on", {"entity_id": ids, "rgb_color": group["rgb"], "brightness_pct": group["brightness"]}, context)
