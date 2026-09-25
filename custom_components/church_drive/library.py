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

# Hue's animated colour scenes. The first five are read from the bridge; the
# rest approximate the Hue app's palettes.
COLOUR: dict[str, dict] = {
    "soho": {
        "name": "Soho",
        "brightness": 70.0,
        "colors": [(0.5865, 0.2575), (0.5115, 0.3625), (0.2215, 0.0822), (0.1916, 0.3954), (0.5019, 0.2751)],
    },
    "magneto": {
        "name": "Magneto",
        "brightness": 88.0,
        "colors": [(0.171, 0.3389), (0.1575, 0.2128), (0.516, 0.4401), (0.4215, 0.4895), (0.1593, 0.1341)],
    },
    "ruby_glow": {
        "name": "Ruby glow",
        "brightness": 50.0,
        "colors": [(0.3826, 0.3117), (0.4189, 0.3031), (0.4918, 0.2838), (0.4557, 0.2951), (0.5321, 0.2758)],
    },
    "emerald_isle": {
        "name": "Emerald isle",
        "brightness": 75.0,
        "colors": [(0.4648, 0.4254), (0.255, 0.4176), (0.3133, 0.4141), (0.2709, 0.3235), (0.3924, 0.4132)],
    },
    "dreamy_dusk": {
        "name": "Dreamy dusk",
        "brightness": 60.0,
        "colors": [(0.5493, 0.3702), (0.4996, 0.293), (0.5201, 0.392), (0.4416, 0.2813), (0.5579, 0.3308)],
    },
    "lake_placid": {"name": "Lake Placid", "brightness": 70.0, "hex": ["#0f5e9c", "#35baf6", "#9fe2bf"]},
    "toil_and_trouble": {
        "name": "Toil and trouble",
        "brightness": 70.0,
        "hex": ["#6a0dad", "#2e8b57", "#ff7f00"],
    },
    "spellbound": {"name": "Spellbound", "brightness": 70.0, "hex": ["#3a0ca3", "#f72585", "#4cc9f0"]},
    "storybook": {"name": "Storybook", "brightness": 70.0, "hex": ["#ffadad", "#ffd6a5", "#9bf6ff"]},
    "arise": {"name": "Arise", "brightness": 80.0, "hex": ["#ff7b39", "#ffd27f"]},
    "unwind": {"name": "Unwind", "brightness": 60.0, "hex": ["#ff9966", "#ff5e62"]},
    "pumpkin_patch": {"name": "Pumpkin patch", "brightness": 70.0, "hex": ["#ff7518", "#8b4513", "#ffb347"]},
    "phantom": {"name": "Phantom", "brightness": 60.0, "hex": ["#2d0a4e", "#6c2bd9", "#0f0f2e"]},
    "city_blue": {"name": "City Blue", "brightness": 70.0, "hex": ["#0b1d51", "#2f6fd6", "#89c2ff"]},
    "aqua": {"name": "Aqua", "brightness": 75.0, "hex": ["#00c9d6", "#0077b6", "#90e0ef"]},
    "motown": {"name": "Motown", "brightness": 75.0, "hex": ["#7b2cbf", "#ff6d00", "#ffd60a"]},
    "witching_hour": {"name": "Witching hour", "brightness": 60.0, "hex": ["#240046", "#5a189a", "#ff7900"]},
    "meriete": {"name": "Meriete", "brightness": 70.0, "hex": ["#ff9e7a", "#c86b98", "#5f4b8b"]},
}

# The Hue app's own speed and brightness (%) for its animated scenes, read
# from the bridge (scene entity attributes). Aqua wasn't on the bridge.
HUE_TIMING: dict[str, tuple[float, float]] = {
    "soho": (0.627, 62.0),
    "magneto": (0.611, 88.0),
    "ruby_glow": (0.627, 40.0),
    "emerald_isle": (0.603, 75.0),
    "dreamy_dusk": (0.603, 50.0),
    "lake_placid": (0.627, 30.0),
    "toil_and_trouble": (0.730, 45.0),
    "spellbound": (0.730, 50.0),
    "storybook": (0.627, 69.0),
    "arise": (0.627, 100.0),
    "unwind": (0.627, 45.0),
    "pumpkin_patch": (0.627, 45.0),
    "phantom": (0.730, 40.0),
    "city_blue": (0.603, 50.0),
    "aqua": (0.627, 75.0),
    "motown": (0.690, 53.0),
    "witching_hour": (0.730, 45.0),
    "meriete": (0.603, 80.0),
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
            colors = spec.get("colors") or [hex_to_xy(h) for h in spec["hex"]]
            speed, brightness = HUE_TIMING.get(key, (0.627, spec["brightness"]))
            out[key] = {
                "name": spec["name"],
                "kind": "colour",
                "brightness": brightness,
                "colors": colors,
                "dynamic": True,
                "speed": speed,
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
