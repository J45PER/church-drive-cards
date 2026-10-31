"""The activity log's rules: what is worth noting, how it reads, how long it is kept and how it is filtered.

Administrators see a log of what people and the assistant did in the house: what a person switched from the app or a
dashboard (a light, the heating, the alarm...), what the assistant did (the changes his scripts made, and for whom), and
when people arrived or left. Pure logic, so it can be tested without Home Assistant (activity.py does the listening).
"""

from __future__ import annotations

from datetime import datetime, tzinfo
from typing import Any, Iterable

KINDS = ("user", "clive", "clive_auto", "presence", "overruled")
# `clive`: the assistant did it because someone asked him (a conversation, or a person running his script). `clive_auto`: one of the
# house's automations ran his script (the heating rules, a welcome home...), with nobody asking.
CLIVE_KINDS = ("clive", "clive_auto")
# `overruled`: something the assistant set (the heating target, a light on or off) was changed from outside the Church Drive app, with
# nothing in Home Assistant asking: the thermostat's own dial or app, the light's own app, a wall switch, a remote or a schedule in the
# device. Home Assistant can't say which of those it was. Only what he set recently counts; general device changes are not listed.
KEEP_DAYS = 30        # older entries are dropped
KEEP_MAX = 4000       # and never more than this many, newest kept
COALESCE_S = 8        # the same person doing the same thing to the same thing this soon after replaces it (a slider drag)
DEFAULT_LIMIT = 300
MAX_LIMIT = 1000

# The assistant's scripts and automations are named after him in Home Assistant (script.clive_*); that id is part of the house's
# own configuration (see ha/clive.md), not something to rename in code.
CLIVE_PREFIX = "clive"
# Scripts that only look something up or put something on a screen: asking is not worth a line in the log.
CLIVE_QUIET = {
    "answer", "show", "show_card", "scores", "fixture", "fixtures", "zappi_info", "car_status", "media_status", "who_is_home",
    "energy_now", "doors", "devices_attention", "home_safety_check", "house_secure", "house_climate", "air_quality", "my_tasks",
}

# What is watched for being overruled: light, switch, fan and humidifier (on or off) and climate (its mode and target temperature).
WATCH_DOMAINS = ("light", "switch", "fan", "humidifier", "climate")
WATCH_SETTLE_S = 90     # after Church Drive starts: Home Assistant is still catching up with its devices
WATCH_PROPAGATE_S = 6   # a device or room that follows a command from Home Assistant (a Hue room's lights) changes by itself just after it
WATCH_DUPLICATE_S = 5   # a room and its lights, or a group and its members, are one change, not several
OVERRULE_S = 6 * 3600   # a change only overrules the assistant if he set that device this recently

# What a person's own switching is noted for: domain -> the services that count (None = all of that domain's).
NOTED: dict[str, set[str] | None] = {
    "light": None, "switch": None, "fan": None, "cover": None, "lock": None, "climate": None, "humidifier": None,
    "valve": None, "water_heater": None, "siren": None, "vacuum": None, "scene": None, "input_boolean": None,
    "alarm_control_panel": None, "media_player": None, "script": None, "automation": {"turn_on", "turn_off", "toggle", "trigger"},
    "button": {"press"}, "todo": {"add_item", "update_item", "remove_item"}, "number": {"set_value"}, "select": {"select_option"},
    "church_drive": {"apply_scene", "shopping_add", "steps_create"},
}
# Even within those: not worth a line.
SKIPPED = {("media_player", "volume_set"), ("media_player", "volume_mute"), ("media_player", "select_source")}

_ICONS = {
    "light": "mdi:lightbulb", "switch": "mdi:toggle-switch", "fan": "mdi:fan", "cover": "mdi:blinds", "lock": "mdi:lock",
    "climate": "mdi:thermostat", "humidifier": "mdi:air-humidifier", "valve": "mdi:valve", "water_heater": "mdi:water-boiler",
    "siren": "mdi:bullhorn", "vacuum": "mdi:robot-vacuum", "scene": "mdi:palette", "input_boolean": "mdi:toggle-switch",
    "alarm_control_panel": "mdi:shield-home", "media_player": "mdi:speaker", "script": "mdi:script-text", "automation": "mdi:robot",
    "button": "mdi:gesture-tap-button", "todo": "mdi:clipboard-check", "number": "mdi:numeric", "select": "mdi:form-dropdown",
    "church_drive": "mdi:home-city",
}

_VERBS = {
    "turn_on": "turned on", "turn_off": "turned off", "toggle": "toggled",
    "lock": "locked", "unlock": "unlocked", "open": "unlocked and opened",
    "open_cover": "opened", "close_cover": "closed", "stop_cover": "stopped", "open_cover_tilt": "tilted open", "close_cover_tilt": "tilted shut",
    "set_cover_position": "moved", "set_temperature": "set", "set_hvac_mode": "set", "set_preset_mode": "set", "set_fan_mode": "set",
    "set_humidity": "set", "alarm_disarm": "disarmed", "alarm_arm_home": "armed (home)", "alarm_arm_away": "armed (away)",
    "alarm_arm_night": "armed (night)", "alarm_arm_vacation": "armed (vacation)", "alarm_trigger": "triggered",
    "start": "started", "stop": "stopped", "pause": "paused", "return_to_base": "sent home", "clean_spot": "cleaned a spot of",
    "locate": "located", "media_play": "played", "media_pause": "paused", "media_stop": "stopped", "media_next_track": "skipped",
    "media_previous_track": "went back on", "media_play_pause": "played or paused", "apply_scene": "applied a scene to",
    "press": "pressed", "trigger": "ran", "add_item": "added to", "update_item": "changed an item on", "remove_item": "removed from",
    "set_value": "set", "select_option": "set", "shopping_add": "added to the shopping list", "steps_create": "made a steps list",
}


def first_name(name: str) -> str:
    return (name or "").strip().split(" ")[0]


def is_clive(entity_id: str) -> bool:
    """Whether a script or automation is the assistant's own."""
    domain, _, object_id = (entity_id or "").partition(".")
    return domain in ("script", "automation") and object_id.startswith(CLIVE_PREFIX + "_")


def clive_is_quiet(entity_id: str) -> bool:
    """A lookup or a picture on a screen, rather than something done."""
    return (entity_id or "").partition(".")[2][len(CLIVE_PREFIX) + 1:] in CLIVE_QUIET


def noted(domain: str, service: str) -> bool:
    """Whether a person's service call is worth a line."""
    if (domain, service) in SKIPPED or domain not in NOTED:
        return False
    services = NOTED[domain]
    return services is None or service in services


def icon_for(domain: str) -> str:
    return _ICONS.get(domain, "mdi:home")


def _list(value: Any) -> list[str]:
    if value is None or value == "":
        return []
    if isinstance(value, str):
        return [v.strip() for v in value.split(",") if v.strip()]
    if isinstance(value, (list, tuple, set)):
        return [str(v) for v in value if v]
    return []


def entities_in(data: dict[str, Any] | None) -> list[str]:
    """The entities a service call was for."""
    data = data or {}
    out = _list(data.get("entity_id"))
    target = data.get("target")
    if isinstance(target, dict):
        out += _list(target.get("entity_id"))
    return list(dict.fromkeys(out))


def _join(names: list[str]) -> str:
    if len(names) <= 1:
        return "".join(names)
    if len(names) <= 3:
        return ", ".join(names[:-1]) + " and " + names[-1]
    return ", ".join(names[:2]) + f" and {len(names) - 2} more"


def describe(domain: str, service: str, data: dict[str, Any] | None, names: dict[str, str], areas: list[str] | None = None) -> str:
    """What was done, as the end of a sentence ("turned on Kitchen", "set Downstairs to 21°C"). [names] maps entity ids to names."""
    data = data or {}
    ids = entities_in(data)
    things = [names.get(e) or e.partition(".")[2].replace("_", " ").title() for e in ids]
    subject = _join(things) or _join(areas or []) or ("the house" if domain != "script" else "")
    if domain == "script":
        return f"ran {subject or service.replace('_', ' ')}"
    verb = _VERBS.get(service)
    if verb is None:
        return f"ran {domain}.{service}" + (f" on {subject}" if subject else "")
    if domain == "scene" and service == "turn_on":
        return f"set the scene {subject}"
    if domain == "church_drive" and service == "apply_scene":
        scene = str(data.get("scene") or "").replace("_", " ")
        return f"applied {scene or 'a scene'} to {subject}"
    if domain == "church_drive":
        return verb
    text = f"{verb} {subject}".strip()
    if service == "set_temperature" and data.get("temperature") is not None:
        text += f" to {_num(data['temperature'])}°"
    elif service == "set_hvac_mode" and data.get("hvac_mode"):
        text += f" to {str(data['hvac_mode']).replace('_', ' ')}"
    elif service in ("set_preset_mode", "set_fan_mode"):
        mode = data.get("preset_mode") or data.get("fan_mode")
        text += f" to {str(mode).replace('_', ' ')}" if mode else ""
    elif service == "set_cover_position" and data.get("position") is not None:
        text += f" to {_num(data['position'])}%"
    elif service == "set_humidity" and data.get("humidity") is not None:
        text += f" to {_num(data['humidity'])}%"
    elif service in ("set_value", "select_option"):
        value = data.get("value") if service == "set_value" else data.get("option")
        text += f" to {value}" if value is not None else ""
    elif domain == "light" and service == "turn_on":
        pct = data.get("brightness_pct")
        if pct is None and data.get("brightness") is not None:
            pct = round(float(data["brightness"]) / 255 * 100)
        if pct is not None:
            text += f" at {_num(pct)}%"
        elif data.get("scene") or data.get("effect"):
            text += f" ({str(data.get('scene') or data.get('effect')).replace('_', ' ')})"
    return text


def _num(value: Any) -> str:
    try:
        f = float(value)
    except (TypeError, ValueError):
        return str(value)
    return str(int(f)) if f == int(f) else f"{f:.1f}"


def presence_text(old: str | None, new: str | None) -> str:
    """"arrived home", "left home", "arrived at Work"... from a person's old and new place. "" when nothing changed that matters."""
    unknown = {None, "", "unknown", "unavailable"}
    if old in unknown or new in unknown or old == new:
        return ""
    old_home, new_home = old.lower() == "home", new.lower() == "home"
    old_away, new_away = old == "not_home", new == "not_home"
    if new_home:
        return "arrived home" if old_away else f"left {old} and arrived home"
    if old_home:
        return "left home" if new_away else f"left home for {new}"
    if old_away:
        return f"arrived at {new}"
    if new_away:
        return f"left {old}"
    return f"left {old} for {new}"


def rule_name(name: str) -> str:
    """An automation's name without the assistant's prefix ("Clive: keep the climate comfortable" -> "keep the climate comfortable")."""
    name = (name or "").strip()
    head, sep, rest = name.partition(":")
    return rest.strip() if sep and head.strip().lower() == CLIVE_PREFIX else name


def _float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def expected_from_call(domain: str, service: str, data: dict[str, Any] | None) -> dict[str, Any]:
    """What a request leaves a device as: {"state": "on"|"off"|a thermostat mode, "temperature": the target}. Only what the call says."""
    data = data or {}
    out: dict[str, Any] = {}
    if domain not in WATCH_DOMAINS:
        return out
    if service == "turn_on" and domain != "climate":
        out["state"] = "on"
    elif service == "turn_off":
        out["state"] = "off"
    elif domain == "climate":
        if service == "set_hvac_mode" and data.get("hvac_mode"):
            out["state"] = str(data["hvac_mode"])
        if service == "set_temperature":
            if _float(data.get("temperature")) is not None:
                out["temperature"] = _float(data["temperature"])
            if data.get("hvac_mode"):
                out["state"] = str(data["hvac_mode"])
    return out


def _deg(value: float) -> str:
    return f"{value:g}°"


def overrule_text(domain: str, old: str | None, old_attrs: dict[str, Any] | None, new: str | None, new_attrs: dict[str, Any] | None, expected: dict[str, Any]) -> str:
    """How a device was changed away from what the assistant set ("turned off", "target changed from 18° to 21°"); "" when it was not
    changed, was only changed to what he set (a device echoing his request later), or went through unavailable."""
    unknown = {None, "", "unknown", "unavailable"}
    if domain not in WATCH_DOMAINS or old in unknown or new in unknown:
        return ""
    old_attrs, new_attrs = old_attrs or {}, new_attrs or {}
    parts = []
    if domain == "climate":
        if old != new and expected.get("state") != new:
            parts.append("turned off" if new == "off" else f"turned on ({new.replace('_', ' ')})" if old == "off" else f"changed from {old.replace('_', ' ')} to {new.replace('_', ' ')}")
        before, after, wanted = _float(old_attrs.get("temperature")), _float(new_attrs.get("temperature")), expected.get("temperature")
        if before is not None and after is not None and before != after and not (wanted is not None and abs(after - wanted) < 0.05):
            parts.append(f"target changed from {_deg(before)} to {_deg(after)}")
    else:
        was, now = old == "on", new == "on"
        if was != now and expected.get("state") != ("on" if now else "off"):
            parts.append("turned on" if now else "turned off")
    return "; ".join(parts)


def related(a: str, a_members: Iterable[str], b: str, b_members: Iterable[str]) -> bool:
    """Whether two entities are the same device seen twice: one is a group that lists the other (a Hue room and its light)."""
    return a in set(b_members) or b in set(a_members)


def make_entry(ts: float, kind: str, who: str, text: str, icon: str, entity: str = "", by: str = "", user_id: str = "", rule: str = "", clive_ts: float = 0) -> dict[str, Any]:
    """One line of the log. [by] is who the assistant did it for (kind `clive`); [rule] is the automation that ran him (kind `clive_auto`);
    [user_id] is kept so a login with no person can be named later. For kind `overruled`, [who] is the device and [clive_ts] when he set it."""
    entry: dict[str, Any] = {"ts": round(float(ts), 3), "kind": kind, "who": who or "", "text": text, "icon": icon}
    if entity:
        entry["entity"] = entity
    if by:
        entry["by"] = by
    if rule:
        entry["rule"] = rule
    if clive_ts:
        entry["clive_ts"] = round(float(clive_ts), 3)
    if user_id:
        entry["user_id"] = user_id
    return entry


def add(entries: list[dict[str, Any]], entry: dict[str, Any]) -> None:
    """Add a line, replacing the last one when it's the same person doing the same kind of thing to the same thing a moment ago."""
    for i in range(len(entries) - 1, max(-1, len(entries) - 25), -1):
        prev = entries[i]
        if entry["ts"] - prev["ts"] > COALESCE_S:
            break
        if prev["kind"] == entry["kind"] and prev["who"] == entry["who"] and prev.get("entity") == entry.get("entity") and prev.get("entity") and prev.get("icon") == entry["icon"] and prev.get("by") == entry.get("by") and prev.get("rule") == entry.get("rule"):
            entries[i] = entry
            return
    entries.append(entry)


def prune(entries: list[dict[str, Any]], now: float) -> list[dict[str, Any]]:
    """Drop what is too old, and the oldest beyond the cap."""
    fresh = [e for e in entries if now - e["ts"] <= KEEP_DAYS * 86400]
    return fresh[-KEEP_MAX:]


def clean(raw: Any) -> list[dict[str, Any]]:
    """Stored lines made safe (a damaged file must not stop the log starting)."""
    out = []
    for e in raw if isinstance(raw, list) else []:
        if not isinstance(e, dict) or e.get("kind") not in KINDS or not isinstance(e.get("text"), str):
            continue
        try:
            ts = float(e["ts"])
        except (KeyError, TypeError, ValueError):
            continue
        out.append(make_entry(ts, e["kind"], str(e.get("who") or ""), e["text"], str(e.get("icon") or "mdi:home"), str(e.get("entity") or ""), str(e.get("by") or ""), str(e.get("user_id") or ""), str(e.get("rule") or ""), _float(e.get("clive_ts")) or 0))
    out.sort(key=lambda e: e["ts"])
    return out


def minutes_of(text: Any) -> int | None:
    """"HH:MM" as minutes into the day, or None."""
    try:
        h, m = str(text).split(":")[:2]
        h, m = int(h), int(m)
    except (TypeError, ValueError):
        return None
    return h * 60 + m if 0 <= h <= 24 and 0 <= m < 60 and (h < 24 or m == 0) else None


def in_window(ts: float, start: int | None, end: int | None, tz: tzinfo | None) -> bool:
    """Whether a time falls inside a daily window (minutes into the day, from start up to but not including end). A window can
    run over midnight (22:00 to 06:00). Both None = always."""
    if start is None and end is None:
        return True
    start = 0 if start is None else start
    end = 24 * 60 if end is None else end
    at = datetime.fromtimestamp(ts, tz)
    minute = at.hour * 60 + at.minute
    if start == end:
        return True
    return start <= minute < end if start < end else (minute >= start or minute < end)


def select(
    entries: Iterable[dict[str, Any]],
    *,
    since: float | None = None,
    until: float | None = None,
    kinds: Iterable[str] | None = None,
    who: str | None = None,
    start: str | None = None,
    end: str | None = None,
    tz: tzinfo | None = None,
    limit: int = DEFAULT_LIMIT,
    offset: int = 0,
) -> dict[str, Any]:
    """The lines that match, newest first. [since] and [until] are times (until is exclusive); [start] and [end] are a window in the
    day ("22:00" to "06:00") that applies to every day in the range; [who] is a first name. The total is before [limit]."""
    wanted = {k for k in (kinds or []) if k in KINDS} or set(KINDS)
    a, b = minutes_of(start), minutes_of(end)
    who_l = (who or "").strip().lower()
    hits = [
        e for e in entries
        if e["kind"] in wanted
        and (since is None or e["ts"] >= since)
        and (until is None or e["ts"] < until)
        and (not who_l or e["who"].lower() == who_l or e.get("by", "").lower() == who_l)
        and in_window(e["ts"], a, b, tz)
    ]
    hits.sort(key=lambda e: e["ts"], reverse=True)
    limit = max(1, min(int(limit or DEFAULT_LIMIT), MAX_LIMIT))
    return {"entries": hits[max(0, offset): max(0, offset) + limit], "total": len(hits)}


def who_list(entries: Iterable[dict[str, Any]]) -> list[str]:
    """Everyone named in the log (as the person or who the assistant acted for), A to Z. Devices are not people."""
    names = {e["who"] for e in entries if e["who"] and e["kind"] in ("user", "presence")} | {e["by"] for e in entries if e.get("by")}
    return sorted(names, key=str.lower)
