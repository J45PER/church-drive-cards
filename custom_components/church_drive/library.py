"""The universal scene library: built-in scenes plus the user's own.

White scenes use the Hue app's values (read from the bridge) and are applied
with one light.turn_on. Colour scenes are palettes; they're played through one
working scene per Hue room/zone (see apply.py), animated or still, with
gradient lights getting several colours. Custom scenes come from the scene
builder and are kept in HA storage.

Spec fields: name, kind ("white" | "colour"), brightness (0-100),
white: mirek or xy; colour: colors [(x, y), ...], dynamic, speed (0-1).
"""

from __future__ import annotations

import re
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

WHITE: dict[str, dict] = {
    "bright": {"name": "Bright", "mirek": 370, "brightness": 100.0},
    "cool_bright": {"name": "Cool bright", "mirek": 250, "brightness": 100.0},
    "dimmed": {"name": "Dimmed", "mirek": 370, "brightness": 30.0},
    "read": {"name": "Read", "mirek": 346, "brightness": 100.0},
    "concentrate": {"name": "Concentrate", "mirek": 233, "brightness": 100.0},
    "energise": {"name": "Energise", "mirek": 156, "brightness": 100.0},
    "relax": {"name": "Relax", "mirek": 447, "brightness": 56.25},
    "rest": {"name": "Rest", "mirek": 447, "brightness": 34.0},
    "nightlight": {"name": "Nightlight", "xy": (0.5610, 0.4042), "mirek": 500, "brightness": 0.4},
}

# Hue's animated colour scenes: each palette colour is (x, y, brightness %)
# and the speed is Hue's own, all read from this home's bridge. Aqua wasn't
# on the bridge, so it's approximated.
COLOUR: dict[str, dict] = {
    "soho": {"name": "Soho", "speed": 0.627, "palette": [(0.5865, 0.2575, 62), (0.5115, 0.3625, 62), (0.5019, 0.2751, 62), (0.2215, 0.0822, 100), (0.1916, 0.3954, 62)]},
    "magneto": {"name": "Magneto", "speed": 0.611, "palette": [(0.1593, 0.1341, 88), (0.1574, 0.2113, 100), (0.171, 0.3389, 88), (0.4215, 0.4895, 88), (0.516, 0.4401, 88)]},
    "ruby_glow": {"name": "Ruby glow", "speed": 0.627, "palette": [(0.3826, 0.3117, 40), (0.4189, 0.3031, 40), (0.4557, 0.2951, 40), (0.4918, 0.2838, 100), (0.5321, 0.2758, 40)]},
    "emerald_isle": {"name": "Emerald isle", "speed": 0.603, "palette": [(0.2709, 0.3235, 75), (0.255, 0.4176, 75), (0.3133, 0.4141, 100), (0.3924, 0.4132, 75), (0.4648, 0.4254, 75)]},
    "dreamy_dusk": {"name": "Dreamy dusk", "speed": 0.603, "palette": [(0.5201, 0.392, 100), (0.5493, 0.3702, 50), (0.5579, 0.3308, 50), (0.5009, 0.2926, 100), (0.4416, 0.2813, 50)]},
    "lake_placid": {"name": "Lake Placid", "speed": 0.627, "palette": [(0.5135, 0.348, 30), (0.4656, 0.3479, 30), (0.4216, 0.3347, 30), (0.2436, 0.2523, 100), (0.194, 0.1927, 30)]},
    "toil_and_trouble": {
        "name": "Toil and trouble",
        "speed": 0.73,
        "palette": [(0.6899, 0.3075, 44.75), (0.6236, 0.3551, 44.75), (0.568, 0.389, 44.75), (0.2447, 0.1235, 44.75), (0.1972, 0.0689, 44.75)],
    },
    "spellbound": {"name": "Spellbound", "speed": 0.73, "palette": [(0.1993, 0.0703, 49.75), (0.2084, 0.1209, 49.75), (0.222, 0.5446, 49.75), (0.306, 0.568, 49.75), (0.5447, 0.4186, 49.75)]},
    "storybook": {"name": "Storybook", "speed": 0.627, "palette": [(0.4369, 0.4086, 69.58), (0.4601, 0.415, 69.58), (0.4833, 0.4186, 69.58), (0.5108, 0.4191, 69.58), (0.5396, 0.4118, 69.58)]},
    "arise": {"name": "Arise", "speed": 0.627, "palette": [(0.3472, 0.348, 100), (0.3671, 0.3629, 100), (0.385, 0.3743, 100), (0.4043, 0.3858, 100), (0.4271, 0.4052, 100)]},
    "shine": {"name": "Shine", "speed": 0.627, "palette": [(0.4364, 0.4087, 100), (0.4728, 0.4174, 100), (0.495, 0.4194, 100), (0.5208, 0.4183, 100)]},
    "unwind": {"name": "Unwind", "speed": 0.627, "palette": [(0.5796, 0.3787, 44.75), (0.5594, 0.4008, 44.75), (0.5369, 0.4148, 44.75), (0.5114, 0.4192, 44.75), (0.4849, 0.4189, 44.75)]},
    "pumpkin_patch": {
        "name": "Pumpkin patch",
        "speed": 0.627,
        "palette": [(0.2256, 0.3166, 44.83), (0.2694, 0.3467, 44.83), (0.4602, 0.4061, 44.83), (0.4734, 0.3664, 44.83), (0.5374, 0.3713, 44.83)],
    },
    "phantom": {"name": "Phantom", "speed": 0.73, "palette": [(0.1568, 0.1681, 40), (0.1597, 0.2673, 40), (0.2258, 0.3244, 40), (0.4281, 0.3412, 40), (0.5217, 0.3592, 40)]},
    "city_blue": {"name": "City Blue", "speed": 0.603, "palette": [(0.2861, 0.1162, 100), (0.1585, 0.2163, 100), (0.1655, 0.1766, 100), (0.1566, 0.1085, 100), (0.1633, 0.0583, 100)]},
    "motown": {"name": "Motown", "speed": 0.69, "palette": [(0.1532, 0.0476, 53), (0.1544, 0.0711, 100), (0.1561, 0.1586, 53), (0.1572, 0.202, 53), (0.1598, 0.3036, 53)]},
    "witching_hour": {"name": "Witching hour", "speed": 0.73, "palette": [(0.6615, 0.3268, 45), (0.5893, 0.3531, 45), (0.5259, 0.348, 45), (0.1689, 0.1281, 45), (0.1532, 0.0476, 45)]},
    "meriete": {"name": "Meriete", "speed": 0.603, "palette": [(0.1685, 0.055, 80), (0.1547, 0.1072, 80), (0.2092, 0.1108, 80), (0.2943, 0.1842, 80), (0.4636, 0.4138, 80)]},
    "aqua": {"name": "Aqua", "speed": 0.627, "hex": ["#00c9d6", "#0077b6", "#90e0ef"], "brightness": 75.0},
}

STORE_KEY = "church_drive.scenes"


def hex_to_xy(value: str) -> tuple[float, float]:
    """sRGB hex to CIE xy (the conversion Hue documents)."""
    v = value.lstrip("#")
    rgb = [int(v[i : i + 2], 16) / 255 for i in (0, 2, 4)]
    r, g, b = (((c + 0.055) / 1.055) ** 2.4 if c > 0.04045 else c / 12.92 for c in rgb)
    x = r * 0.664511 + g * 0.154324 + b * 0.162028
    y = r * 0.283881 + g * 0.668433 + b * 0.047685
    z = r * 0.000088 + g * 0.072310 + b * 0.986039
    total = x + y + z
    if total == 0:
        return (0.3127, 0.329)
    return (round(x / total, 4), round(y / total, 4))


def xy_to_hex(xy: tuple[float, float]) -> str:
    """CIE xy to an sRGB hex colour at full brightness (for tile previews)."""
    x, y = xy
    y = max(y, 1e-6)
    big_x, big_z = x / y, (1 - x - y) / y
    r = big_x * 1.656492 - 0.354851 - big_z * 0.255038
    g = -big_x * 0.707196 + 1.655397 + big_z * 0.036152
    b = big_x * 0.051713 - 0.121364 + big_z * 1.011530
    top = max(r, g, b, 1e-6)
    out = []
    for c in (r / top, g / top, b / top):
        c = max(c, 0.0)
        c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
        out.append(round(min(c, 1.0) * 255))
    return "#" + "".join(f"{c:02x}" for c in out)


def _slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_") or "scene"


class Library:
    """Built-in scenes plus custom ones from the scene builder."""

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store[dict] = Store(hass, 1, STORE_KEY)
        self.custom: list[dict] = []

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self.custom = data.get("scenes", [])

    async def _async_save(self) -> None:
        await self._store.async_save({"scenes": self.custom})

    def all(self) -> dict[str, dict]:
        """Every scene, keyed, as normalised specs."""
        out: dict[str, dict] = {}
        for key, spec in WHITE.items():
            out[key] = {**spec, "kind": "white"}
        for key, spec in COLOUR.items():
            if "palette" in spec:
                colors = [(x, y) for x, y, _ in spec["palette"]]
                levels = [b for _, _, b in spec["palette"]]
            else:
                colors = [hex_to_xy(h) for h in spec["hex"]]
                levels = [spec["brightness"]] * len(colors)
            out[key] = {
                "name": spec["name"],
                "kind": "colour",
                "brightness": round(sum(levels) / len(levels), 2),
                "colors": colors,
                "levels": levels,
                "dynamic": True,
                "speed": spec["speed"],
            }
        for scene in self.custom:
            out[scene["key"]] = normalise(scene)
        return out

    def find(self, scene: str) -> tuple[str, dict] | None:
        """Look a scene up by key ("cool_bright") or name ("Cool bright")."""
        wanted = str(scene).strip().lower()
        for key, spec in self.all().items():
            if wanted in (key, spec["name"].lower()):
                return key, spec
        return None

    async def async_save_scene(self, scene: dict) -> str:
        """Add or update a custom scene; returns its key."""
        key = scene.get("key")
        if not key:
            key = _slug(scene["name"])
            taken = set(self.all())
            base, n = key, 2
            while key in taken:
                key, n = f"{base}_{n}", n + 1
        elif key in WHITE or key in COLOUR:
            raise ValueError("Built-in scenes can't be changed")
        record = {**scene, "key": key}
        self.custom = [s for s in self.custom if s["key"] != key] + [record]
        await self._async_save()
        return key

    async def async_delete_scene(self, key: str) -> None:
        self.custom = [s for s in self.custom if s["key"] != key]
        await self._async_save()

    def as_list(self) -> list[dict[str, Any]]:
        """The library for the frontend."""
        return [frontend(key, spec) for key, spec in self.all().items()]


def normalise(scene: dict) -> dict:
    """A stored custom scene as a spec."""
    spec: dict[str, Any] = {
        "name": scene["name"],
        "kind": scene.get("kind", "white"),
        "brightness": float(scene.get("brightness", 100)),
        "custom": True,
    }
    if scene.get("icon"):
        spec["icon"] = scene["icon"]
    if spec["kind"] == "colour":
        spec["colors"] = [hex_to_xy(h) for h in scene.get("colors", [])] or [(0.3127, 0.329)]
        spec["dynamic"] = bool(scene.get("dynamic", False))
        spec["speed"] = float(scene.get("speed", 0.63))
    else:
        spec["mirek"] = round(1_000_000 / int(scene.get("kelvin", 2700)))
    return spec


def turn_on_data(spec: dict) -> dict[str, Any]:
    """light.turn_on data for a white scene (or a colour scene's first colour)."""
    data: dict[str, Any] = {"brightness": max(1, round(spec["brightness"] * 255 / 100))}
    if spec["kind"] == "colour":
        data["xy_color"] = list(spec["colors"][0])
    elif "xy" in spec:
        data["xy_color"] = list(spec["xy"])
    else:
        data["color_temp_kelvin"] = round(1_000_000 / spec["mirek"])
    return data


def frontend(key: str, spec: dict) -> dict[str, Any]:
    """One scene as the cards see it."""
    out: dict[str, Any] = {
        "key": key,
        "name": spec["name"],
        "kind": spec["kind"],
        "custom": bool(spec.get("custom")),
    }
    if spec.get("icon"):
        out["icon"] = spec["icon"]
    if spec["kind"] == "colour":
        out.update(
            brightness=max(1, round(spec["brightness"] * 255 / 100)),
            colors=[list(c) for c in spec["colors"]],
            hex=[xy_to_hex(c) for c in spec["colors"]],
            dynamic=spec["dynamic"],
            speed=spec["speed"],
        )
    else:
        out.update(turn_on_data(spec))
    return out
