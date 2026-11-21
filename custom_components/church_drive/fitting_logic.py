"""The plain rules for a fitting with a main light and a ring of segments (no Home Assistant in here, so they can be tested alone).

The Govee H60A1 (the Cloakroom light) is one fitting with two parts, and the controls Govee gives us don't match that: a whole-device colour
paints every LED (main light and ring), a white command lights the main light only and switches the ring off, and a segment command (slow, through
the cloud) touches one segment of the ring. So the house keeps its own record of what each part is set to, and works out which commands, in which
order, give a request: a white main light under a coloured ring, a colour on both, a ring on its own.

A *fitting* is ``{"main": {...}, "ring": {...}}``; a *request* is the same with either part left out to leave it alone.
``plan(current, request)`` returns the commands to send, in order, and ``result(current, request)`` the record afterwards.
"""

from __future__ import annotations

import copy
import math
from typing import Any

SEGMENTS = 13
DEFAULT_RGB = [255, 200, 140]


def kelvin_rgb(kelvin: float) -> list[int]:
    """The colour of a white of [kelvin] as red, green and blue (0 to 255)."""
    t = max(1000.0, min(40000.0, float(kelvin))) / 100.0
    red = 255.0 if t <= 66 else 329.698727446 * ((t - 60) ** -0.1332047592)
    green = 99.4708025861 * math.log(t) - 161.1195681661 if t <= 66 else 288.1221695283 * ((t - 60) ** -0.0755148492)
    blue = 255.0 if t >= 66 else 0.0 if t <= 19 else 138.5177312231 * math.log(t - 10) - 305.0447927307
    return [int(max(0, min(255, round(c)))) for c in (red, green, blue)]


def _level(value: Any, default: int = 100) -> int:
    try:
        return max(1, min(100, int(round(float(value)))))
    except (TypeError, ValueError):
        return default


def _rgb(value: Any, default: list[int] | None = None) -> list[int]:
    if isinstance(value, (list, tuple)) and len(value) == 3:
        try:
            return [max(0, min(255, int(c))) for c in value]
        except (TypeError, ValueError):
            pass
    return list(default or DEFAULT_RGB)


def clean_main(value: Any, base: dict[str, Any] | None = None) -> dict[str, Any]:
    """The main light's setting: on or off, white (a tone) or colour, and a level."""
    base = base or {"on": False, "mode": "white", "kelvin": 2700, "rgb": list(DEFAULT_RGB), "brightness": 100}
    value = value if isinstance(value, dict) else {}
    mode = value.get("mode", base["mode"])
    return {
        "on": bool(value.get("on", base["on"])),
        "mode": mode if mode in ("white", "colour") else "white",
        "kelvin": max(2000, min(6500, int(value.get("kelvin", base["kelvin"])))),
        "rgb": _rgb(value.get("rgb"), base["rgb"]),
        "brightness": _level(value.get("brightness", base["brightness"])),
    }


def clean_ring(value: Any, base: dict[str, Any] | None = None, count: int = SEGMENTS) -> dict[str, Any]:
    """The ring's setting: on or off, and each segment's colour and level (a colour for the whole ring is the same colour on every segment)."""
    base = base or {"on": False, "segments": [{"rgb": list(DEFAULT_RGB), "brightness": 100} for _ in range(count)]}
    value = value if isinstance(value, dict) else {}
    segments = [dict(s) for s in base["segments"]][:count]
    while len(segments) < count:
        segments.append({"rgb": list(DEFAULT_RGB), "brightness": 100})
    if isinstance(value.get("segments"), list):
        for i, s in enumerate(value["segments"][:count]):
            if isinstance(s, dict):
                segments[i] = {"rgb": _rgb(s.get("rgb"), segments[i]["rgb"]), "brightness": _level(s.get("brightness", segments[i]["brightness"]))}
    if "rgb" in value:
        segments = [{"rgb": _rgb(value["rgb"]), "brightness": _level(value.get("brightness", s["brightness"]))} for s in segments]
    elif "brightness" in value:
        segments = [{"rgb": s["rgb"], "brightness": _level(value["brightness"])} for s in segments]
    return {"on": bool(value.get("on", base["on"])), "segments": segments}


def ramp(colours: list[list[int]], count: int) -> list[list[int]]:
    """[count] colours spread along [colours] (two or more as red, green, blue), the first at one end and the last at the other."""
    if not colours:
        return [list(DEFAULT_RGB) for _ in range(count)]
    if len(colours) == 1 or count == 1:
        return [list(colours[0]) for _ in range(count)]
    out = []
    for i in range(count):
        pos = i * (len(colours) - 1) / (count - 1)
        a = min(int(pos), len(colours) - 2)
        t = pos - a
        out.append([int(round(colours[a][k] + (colours[a + 1][k] - colours[a][k]) * t)) for k in range(3)])
    return out


def new_fitting(count: int = SEGMENTS) -> dict[str, Any]:
    return {"main": clean_main({}), "ring": clean_ring({}, count=count)}


def groups(segments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Segments with the same colour and level share one command: a flat ring is one call, not thirteen."""
    by_look: dict[tuple, dict[str, Any]] = {}
    for index, s in enumerate(segments):
        key = (tuple(s["rgb"]), s["brightness"])
        by_look.setdefault(key, {"indices": [], "rgb": list(s["rgb"]), "brightness": s["brightness"]})["indices"].append(index)
    return list(by_look.values())


def _main_as_colour(main: dict[str, Any]) -> dict[str, Any]:
    """The main light's current look as a colour command: white is the same white in RGB."""
    rgb = main["rgb"] if main["mode"] == "colour" else kelvin_rgb(main["kelvin"])
    return {"op": "main_colour", "rgb": rgb, "brightness": main["brightness"]}


def result(current: dict[str, Any], request: dict[str, Any]) -> dict[str, Any]:
    """The record of the fitting after [request]. A whole-fitting colour (a colour on the main light with the ring left alone, unless the ring is
    asked to keep its own) paints the ring the same colour; a white main light with the ring left alone switches the ring off."""
    out = copy.deepcopy(current)
    main_asked = isinstance(request.get("main"), dict)
    ring_asked = isinstance(request.get("ring"), dict)
    if main_asked:
        out["main"] = clean_main(request["main"], current["main"])
    if ring_asked:
        out["ring"] = clean_ring(request["ring"], current["ring"], len(current["ring"]["segments"]))
    elif main_asked and out["main"]["on"]:
        if out["main"]["mode"] == "white":
            # A white tone while the ring has colour keeps the ring (the white goes as RGB and the ring's colours are put back); with the ring off it stays off.
            pass
        elif not request.get("keep_ring"):
            out["ring"] = {"on": True, "segments": [{"rgb": list(out["main"]["rgb"]), "brightness": 100} for _ in out["ring"]["segments"]]}
    if main_asked and not out["main"]["on"] and not ring_asked:
        out["ring"]["on"] = False
    if ring_asked and out["ring"]["on"] and not out["main"]["on"] and not (isinstance(request.get("main"), dict) and request["main"].get("on") is False):
        # The ring is one device with the main light: lighting it powers the device on, so the main light comes on with its own last look.
        out["main"]["on"] = True
    return out


def plan(current: dict[str, Any], request: dict[str, Any]) -> list[dict[str, Any]]:
    """The commands that give [request], in the order to send them."""
    after = result(current, request)
    main, ring = after["main"], after["ring"]
    ops: list[dict[str, Any]] = []
    main_asked = isinstance(request.get("main"), dict)
    ring_asked = isinstance(request.get("ring"), dict)

    if not main["on"] and not ring["on"]:
        return [{"op": "device_off"}] if (main_asked or ring_asked) else []

    if main_asked:
        if not main["on"]:
            ops.append({"op": "main_off"})
        elif main["mode"] == "white" and not ring["on"]:
            ops.append({"op": "main_white", "kelvin": main["kelvin"], "brightness": main["brightness"]})
        else:
            ops.append({"op": "main_colour", "rgb": _main_as_colour(main)["rgb"], "brightness": main["brightness"]})
            keep = main["mode"] == "white" or request.get("keep_ring")
            if not ring_asked and ring["on"] and keep:
                ops.append({"op": "ring", "groups": groups(current["ring"]["segments"])})
    elif ring_asked and ring["on"] and main["on"]:
        # The ring is about to put the fitting in colour mode, where the main light shows the fitting's colour: set it first, as its own look
        # (this also powers the device on if it was off).
        ops.append(_main_as_colour(main))

    if ring_asked:
        if not ring["on"]:
            ops.append({"op": "ring_off"})
        else:
            ops.append({"op": "ring", "groups": groups(ring["segments"])})
    return ops


def hex_rgb(value: str) -> list[int]:
    v = value.lstrip("#")
    return [int(v[i : i + 2], 16) for i in (0, 2, 4)]


def main_from_look(look: dict[str, Any] | None, white: dict[str, Any] | None = None) -> dict[str, Any] | None:
    """A hybrid scene's look for the main light as a request for it, or None when it isn't one this can do (leave it to the old way).

    [white] is the white scene's spec (a ``mirek`` or an ``xy`` colour, and a ``brightness``) when the look is a white scene."""
    if not isinstance(look, dict):
        return None
    kind = look.get("t")
    if kind == "off":
        return {"on": False}
    if kind == "colour" and isinstance(look.get("v"), str):
        return {"on": True, "mode": "colour", "rgb": hex_rgb(look["v"])}
    if kind == "white" and isinstance(white, dict):
        brightness = white.get("brightness", 100)
        if "mirek" in white:
            return {"on": True, "mode": "white", "kelvin": round(1_000_000 / white["mirek"]), "brightness": brightness}
    return None


def ring_from_look(look: dict[str, Any] | None, count: int = SEGMENTS) -> dict[str, Any] | None:
    """A hybrid scene's look for the ring as a request for it, or None when it isn't one this can do."""
    if not isinstance(look, dict):
        return None
    kind = look.get("t")
    if kind == "off":
        return {"on": False}
    if kind == "colour" and isinstance(look.get("v"), str):
        return {"on": True, "rgb": hex_rgb(look["v"])}
    if kind == "gradient" and isinstance(look.get("v"), list) and len(look["v"]) >= 2:
        return {"on": True, "segments": [{"rgb": rgb, "brightness": 100} for rgb in ramp([hex_rgb(c) for c in look["v"]], count)]}
    return None
