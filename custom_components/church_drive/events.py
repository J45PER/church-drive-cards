"""Camera events: a picture and the recording of every doorbell press and motion.

Ring only keeps a handful of recordings within reach, so each one is saved
here as it happens, for the camera card's events viewer:
- a picture, a few seconds after a camera's motion or doorbell event (from
  ring-mqtt's snapshot camera, else the camera itself);
- the clip, when Ring finishes the recording (the Ring integration's camera
  gets a new `last_video_id` and a `video_url` to download, with Ring Protect).

The two are paired by camera, kind and time. Files go in
<media>/church_drive/events/<camera>/ and are kept for `keep_days` (5), with
the oldest removed first if they pass `max_gb` (3). Both, and the folder
(e.g. a NAS share later), are settings in the store.

Cameras are found by themselves: every Ring `camera.<x>_live_view`, with its
`event.<x>_ding` / `event.<x>_motion` and `camera.<x>_snapshot`.

The card asks for a camera's events over the websocket and gets short-lived
signed links to each picture and clip (served by EventFileView).

Camera links (set in Manager, like Ring's Linked Devices but for any sensor
and per alarm mode): when a trigger fires (a camera's motion or doorbell, a
motion sensor, a door), the linked cameras record for a few seconds, by
turning on ring-mqtt's live stream (Ring saves live views as recordings with
Ring Protect). A live view someone watched (not a link) is kept as "live".
Links are kept per mode: disarmed, home (and night) and away
(and while the alarm is going off). Each camera records a linked clip at most
once per `cooldown` seconds. These show in the events viewer as "linked".
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
import logging
import os
from pathlib import Path
import re
import time
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import DOMAIN

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
DEFAULTS = {"keep_days": 5, "max_gb": 3.0, "dir": None, "cooldown": 120}
MODES = ("disarmed", "home", "away")
ALARM_MODE = {"disarmed": "disarmed", "armed_home": "home", "armed_night": "home", "armed_vacation": "away",
              "armed_away": "away", "armed_custom_bypass": "home", "arming": "disarmed", "pending": "away",
              "triggered": "away"}
ON_STATES = ("on", "open", "detected", "home")
PICTURE_DELAY = 8  # seconds: ring-mqtt's snapshot arrives a moment after the event
PAIR_WINDOW = 240  # seconds between an event and its recording
MAX_CLIP = 300 * 1024 * 1024
URL = "/api/church_drive/events"
SAFE = re.compile(r"^[a-z0-9_]+$")
SAFE_FILE = re.compile(r"^[0-9]{8}-[0-9]{6}_[a-z]+(_[0-9]+)?\.(jpg|mp4)$")


def _when(value: Any) -> datetime | None:
    """A time from a state or attribute: Ring gives datetimes in attributes, text in states."""
    if isinstance(value, datetime):
        return value if value.tzinfo else dt_util.as_utc(value)
    try:
        return dt_util.parse_datetime(str(value)) if value else None
    except (TypeError, ValueError):
        return None


class CameraEvents:
    """Saves each camera event's picture and clip, and serves the list."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.camera_events")
        self._convert_lock = asyncio.Lock()
        self._data: dict[str, Any] = {"settings": dict(DEFAULTS), "events": {}, "links": None}
        self._linked_at: dict[str, float] = {}
        self._unsubs: list = []
        self._tasks: set[asyncio.Task] = set()
        self._cams: list[str] = []

    # ---- start / stop -------------------------------------------------

    async def async_start(self) -> None:
        stored = await self._store.async_load()
        if isinstance(stored, dict):
            self._data["settings"].update(stored.get("settings") or {})
            self._data["events"] = stored.get("events") or {}
            self._data["links"] = stored.get("links")
            # Before v0.30.2 a watched live view was saved as "linked".
            for events in self._data["events"].values():
                for e in events:
                    if e.get("kind") == "linked" and not e.get("trigger"):
                        e["kind"] = "live"
            # Before v0.31.1 only the start of each clip was saved, which won't
            # play: drop those clips (the pictures stay).
            broken = [
                (base, e.pop("mp4"))
                for base, events in self._data["events"].items()
                for e in events
                if e.get("mp4") and not e.get("whole")
            ]
            if broken:
                await self.hass.async_add_executor_job(self._remove_files, broken)
                self._save()
            # Once (v0.31.2): events from before the clip fix never got a clip
            # that plays; remove them, pictures too.
            if not stored.get("cleared_old"):
                old: list[tuple[str, str]] = []
                for base, events in self._data["events"].items():
                    keep = []
                    for e in events:
                        if e.get("whole"):
                            keep.append(e)
                        else:
                            old += [(base, e[k]) for k in ("jpg", "mp4") if e.get(k)]
                    events[:] = keep
                await self.hass.async_add_executor_job(self._remove_files, old)
                self._data["cleared_old"] = True
                self._save()
        await self.hass.async_add_executor_job(self._root().mkdir, 0o755, True, True)
        self._cams = self._find_cameras()
        if self._data["links"] is None:
            self._data["links"] = self._seed_links()
            self._save()
        self._unsubs.append(self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state, self._wanted))
        self._unsubs.append(async_track_time_interval(self.hass, self._tidy, timedelta(hours=1)))
        self.hass.async_create_task(self._tidy())

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()
        for task in list(self._tasks):
            task.cancel()

    def _save(self) -> None:
        self._store.async_delay_save(lambda: self._data, 5)

    def _root(self) -> Path:
        folder = self._data["settings"].get("dir")
        if folder:
            return Path(folder)
        media = (getattr(self.hass.config, "media_dirs", None) or {}).get("local") or self.hass.config.path("media")
        return Path(media) / "church_drive" / "events"

    def settings(self) -> dict[str, Any]:
        return {**self._data["settings"], "folder": str(self._root())}

    async def async_set_settings(
        self, keep_days: int | None, max_gb: float | None, folder: str | None, cooldown: int | None = None
    ) -> None:
        s = self._data["settings"]
        if cooldown is not None:
            s["cooldown"] = max(30, int(cooldown))
        if keep_days is not None:
            s["keep_days"] = max(1, int(keep_days))
        if max_gb is not None:
            s["max_gb"] = max(0.5, float(max_gb))
        if folder is not None:
            s["dir"] = folder or None
            await self.hass.async_add_executor_job(self._root().mkdir, 0o755, True, True)
        self._save()
        await self._tidy()

    # ---- which cameras ------------------------------------------------

    def cameras(self) -> list[str]:
        """Every Ring camera, by its base name (front_door for camera.front_door_live_view)."""
        return self._cams

    def _find_cameras(self) -> list[str]:
        reg = er.async_get(self.hass)
        out = []
        for entry in reg.entities.values():
            if entry.domain == "camera" and entry.platform == "ring" and entry.entity_id.endswith("_live_view"):
                out.append(entry.entity_id[len("camera."):-len("_live_view")])
        return sorted(out)

    @staticmethod
    def base_of(entity_id: str) -> str | None:
        m = re.match(r"^(camera|event)\.([a-z0-9_]+?)(_live_view|_snapshot|_last_recording|_ding|_motion)$", entity_id)
        return m.group(2) if m else None

    @callback
    def _wanted(self, event_data: Any) -> bool:
        eid = event_data.get("entity_id", "") if hasattr(event_data, "get") else ""
        return (
            (eid.startswith("event.") and eid.endswith(("_ding", "_motion")))
            or (eid.startswith("camera.") and eid.endswith("_live_view"))
            or eid in self._triggers()
        )

    @callback
    def _on_state(self, event: Event) -> None:
        eid = event.data.get("entity_id", "")
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if new is None or old is None:
            return
        if eid in self._triggers() and self._fired(eid, old.state, new.state):
            self._link(eid)
        base = self.base_of(eid)
        if not base or base not in self._cams:
            return
        if eid.startswith("event."):
            if new.state in ("unknown", "unavailable") or old.state in ("unavailable",) or new.state == old.state:
                return
            kind = "ding" if eid.endswith("_ding") else "motion"
            when = _when(new.state) or dt_util.utcnow()
            self._spawn(self._async_event(base, kind, when))
        else:
            vid, was = new.attributes.get("last_video_id"), old.attributes.get("last_video_id")
            url = new.attributes.get("video_url")
            if vid and url and vid != was:
                self._spawn(self._async_clip(base, str(vid), url))

    def _spawn(self, coro) -> None:
        task = self.hass.async_create_background_task(coro, "church_drive camera event")
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    # ---- saving -------------------------------------------------------

    def _list(self, base: str) -> list[dict[str, Any]]:
        return self._data["events"].setdefault(base, [])

    @staticmethod
    def _stamp(ts: float, kind: str) -> str:
        return f"{dt_util.as_local(dt_util.utc_from_timestamp(ts)).strftime('%Y%m%d-%H%M%S')}_{kind}"

    def _remove_files(self, files: list[tuple[str, str]]) -> None:
        for base, name in files:
            (self._root() / base / name).unlink(missing_ok=True)

    def _write(self, path: Path, data: bytes) -> int:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return len(data)

    async def _async_event(self, base: str, kind: str, when: datetime) -> None:
        ts = when.timestamp()
        events = self._list(base)
        # The same event reported twice (e.g. after a reconnect).
        if any(e["kind"] == kind and abs(e["ts"] - ts) < 5 for e in events):
            return
        entry = {"id": f"{int(ts)}{kind[0]}", "ts": ts, "kind": kind, "jpg": None, "mp4": None, "bytes": 0}
        events.append(entry)
        self._save()
        await asyncio.sleep(PICTURE_DELAY)
        await self._async_picture(base, entry)

    async def _async_picture(self, base: str, entry: dict[str, Any]) -> None:
        from homeassistant.components.camera import async_get_image  # noqa: PLC0415

        for source in (f"camera.{base}_snapshot", f"camera.{base}_live_view"):
            if self.hass.states.get(source) is None:
                continue
            try:
                image = await async_get_image(self.hass, source, timeout=20)
            except Exception as err:  # noqa: BLE001
                _LOGGER.debug("No picture from %s: %s", source, err)
                continue
            name = f"{self._stamp(entry['ts'], entry['kind'])}.jpg"
            size = await self.hass.async_add_executor_job(self._write, self._root() / base / name, image.content)
            entry["jpg"] = name
            entry["bytes"] = entry.get("bytes", 0) + size
            self._save()
            return

    async def _async_clip(self, base: str, video_id: str, url: str) -> None:
        events = self._list(base)
        if any(e.get("video_id") == video_id for e in events):
            return
        act = self.hass.states.get(f"sensor.{base}_last_activity")
        created = _when(act.attributes.get("created_at") or act.state) if act else None
        category = act.attributes.get("category") if act is not None else None
        kind = {"ding": "ding", "on_demand": "linked", "live": "linked"}.get(category, "motion")
        ts = (created or dt_util.utcnow()).timestamp()
        # The event this recording belongs to: same kind, close in time, no clip yet.
        entry = min(
            (e for e in events if not e.get("mp4") and e["kind"] == kind and abs(e["ts"] - ts) < PAIR_WINDOW),
            key=lambda e: abs(e["ts"] - ts),
            default=None,
        )
        if entry is None:
            # A live view nobody linked: someone watched the camera live.
            if kind == "linked":
                kind = "live"
            entry = {"id": f"{int(ts)}{kind[0]}", "ts": ts, "kind": kind, "jpg": None, "mp4": None, "bytes": 0}
            events.append(entry)
        entry["video_id"] = video_id
        try:
            session = async_get_clientsession(self.hass)
            async with session.get(url, timeout=120) as resp:
                resp.raise_for_status()
                # Read to the end: content.read(n) gives back only what has
                # arrived so far, which saved just the start of each clip.
                chunks: list[bytes] = []
                total = 0
                async for chunk in resp.content.iter_chunked(1 << 16):
                    total += len(chunk)
                    if total > MAX_CLIP:
                        raise ValueError("recording is too big")
                    chunks.append(chunk)
                data = b"".join(chunks)
        except Exception as err:  # noqa: BLE001
            _LOGGER.warning("Couldn't download the %s recording: %s", base, err)
            self._save()
            return
        name = f"{self._stamp(entry['ts'], entry['kind'])}.mp4"
        size = await self.hass.async_add_executor_job(self._write, self._root() / base / name, data)
        entry["mp4"] = name
        entry["whole"] = True
        entry["bytes"] = entry.get("bytes", 0) + size
        await self._async_remux(base, entry)
        # The clip's own first moments make the best thumbnail.
        if not await self._async_thumb(base, entry) and not entry.get("jpg"):
            await self._async_picture(base, entry)
        self._save()
        await self._tidy()

    async def _async_remux(self, base: str, entry: dict[str, Any]) -> None:
        """Make the clip play on phones. Ring records HEVC, which phones' Home
        Assistant apps show as sound over a black picture: convert it to H.264
        (one at a time, at low priority, at most 1280 px). Anything else is
        just repackaged with its index first, so it starts at once. If
        converting fails, the HEVC is kept, labelled hvc1 (enough for iPhones).
        """
        try:
            from homeassistant.components.ffmpeg import get_ffmpeg_manager  # noqa: PLC0415

            binary = get_ffmpeg_manager(self.hass).binary
        except Exception:  # noqa: BLE001
            return
        clip = self._root() / base / entry["mp4"]
        tmp = clip.with_name(clip.stem + ".tmp.mp4")

        async def run(args: list[str], limit: float) -> None:
            proc = await asyncio.create_subprocess_exec(
                binary, "-y", "-loglevel", "error", "-i", str(clip), *args, "-movflags", "+faststart", str(tmp),
                stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE, preexec_fn=_low_priority,
            )
            try:
                _, err = await asyncio.wait_for(proc.communicate(), limit)
            except TimeoutError:
                proc.kill()
                raise
            if proc.returncode != 0:
                raise RuntimeError(err.decode(errors="replace").strip()[-300:])
            await self.hass.async_add_executor_job(tmp.replace, clip)

        async with self._convert_lock:
            try:
                probe = await asyncio.create_subprocess_exec(
                    binary, "-hide_banner", "-i", str(clip), stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE
                )
                _, err = await asyncio.wait_for(probe.communicate(), 30)
                found = re.search(r"Video: (\w+)", err.decode(errors="replace"))
                entry["codec"] = codec = found.group(1) if found else None
                if codec == "hevc":
                    try:
                        await run(
                            ["-map", "0:v:0", "-map", "0:a?", "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
                             "-vf", "scale='min(1280,iw)':-2", "-pix_fmt", "yuv420p", "-threads", "2", "-c:a", "aac", "-b:a", "64k"],
                            900,
                        )
                        entry["codec"] = "h264"
                        return
                    except Exception as err:  # noqa: BLE001
                        _LOGGER.warning("Couldn't convert the %s clip %s to H.264, keeping HEVC: %s", base, entry["mp4"], err)
                await run(["-map", "0", "-c", "copy", *(["-tag:v", "hvc1"] if codec == "hevc" else [])], 120)
            except Exception as err:  # noqa: BLE001
                _LOGGER.warning("Couldn't repackage the %s clip %s (kept as it came): %s", base, entry["mp4"], err)
            finally:
                await self.hass.async_add_executor_job(lambda: tmp.unlink(missing_ok=True))

    async def _async_thumb(self, base: str, entry: dict[str, Any]) -> bool:
        """A picture from 1 second into the clip (with Home Assistant's ffmpeg)."""
        try:
            from homeassistant.components.ffmpeg import get_ffmpeg_manager  # noqa: PLC0415

            binary = get_ffmpeg_manager(self.hass).binary
        except Exception:  # noqa: BLE001
            return False
        clip = self._root() / base / entry["mp4"]
        name = f"{self._stamp(entry['ts'], entry['kind'])}.jpg"
        out = self._root() / base / name
        try:
            proc = await asyncio.create_subprocess_exec(
                binary, "-y", "-loglevel", "error", "-ss", "1", "-i", str(clip), "-frames:v", "1", "-vf", "scale=640:-2", str(out),
                stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL,
            )
            await asyncio.wait_for(proc.wait(), 30)
        except Exception as err:  # noqa: BLE001
            _LOGGER.debug("No thumbnail for %s: %s", clip, err)
            return False
        size = await self.hass.async_add_executor_job(lambda: out.stat().st_size if out.is_file() else 0)
        if not size:
            return False
        if entry.get("jpg") and entry["jpg"] != name:
            await self.hass.async_add_executor_job(lambda: (self._root() / base / entry["jpg"]).unlink(missing_ok=True))
        entry["jpg"] = name
        entry["bytes"] = entry.get("bytes", 0) + size
        return True

    # ---- camera links -------------------------------------------------

    def _seed_links(self) -> dict[str, Any]:
        """To start with: what Ring did (Front Door motion and the doorbell
        also record the Driveway), in every mode, when those cameras exist."""
        links: dict[str, Any] = {m: {} for m in MODES}
        if "front_door" in self._cams and "driveway" in self._cams:
            for trig in ("event.front_door_motion", "event.front_door_ding"):
                for m in MODES:
                    links[m][trig] = {"cams": ["driveway"], "secs": 30}
        return links

    def _triggers(self) -> set[str]:
        return {t for m in MODES for t in (self._data.get("links") or {}).get(m, {})}

    def mode(self) -> str:
        alarms = sorted(self.hass.states.async_all("alarm_control_panel"), key=lambda s: s.entity_id)
        return ALARM_MODE.get(alarms[0].state, "disarmed") if alarms else "disarmed"

    @staticmethod
    def _fired(entity_id: str, old: str, new: str) -> bool:
        if new in ("unknown", "unavailable") or old in ("unavailable",) or new == old:
            return False
        if entity_id.startswith("event."):
            return True  # a new event (its state is the time)
        return new in ON_STATES and old not in ON_STATES

    @callback
    def _link(self, trigger: str) -> None:
        rule = (self._data.get("links") or {}).get(self.mode(), {}).get(trigger)
        if not rule:
            return
        source = self.base_of(trigger)
        name = (self.hass.states.get(trigger).name if self.hass.states.get(trigger) else trigger)
        cooldown = float(self._data["settings"].get("cooldown") or 120)
        now = time.time()
        for cam in rule.get("cams", []):
            if cam == source or cam not in self._cams or now - self._linked_at.get(cam, 0) < cooldown:
                continue
            self._linked_at[cam] = now
            self._spawn(self._async_record(cam, int(rule.get("secs") or 30), trigger, name))

    async def _async_record(self, cam: str, secs: int, trigger: str, source: str) -> None:
        switch = f"switch.{cam}_live_stream"
        if self.hass.states.get(switch) is None:
            _LOGGER.debug("No live stream switch for %s", cam)
            return
        ts = time.time()
        self._list(cam).append(
            {"id": f"{int(ts)}l", "ts": ts, "kind": "linked", "source": source, "trigger": trigger,
             "jpg": None, "mp4": None, "bytes": 0}
        )
        self._save()
        try:
            await self.hass.services.async_call("switch", "turn_on", {"entity_id": switch}, blocking=True)
            await asyncio.sleep(max(5, min(secs, 120)))
        finally:
            await self.hass.services.async_call("switch", "turn_off", {"entity_id": switch}, blocking=True)

    def links(self) -> dict[str, Any]:
        """Everything Manager's Camera links needs."""
        cams = []
        for base in self._cams:
            st = self.hass.states.get(f"camera.{base}_live_view")
            name = (st.name if st else base.replace("_", " ").title()).replace(" Live view", "")
            cams.append({"base": base, "name": name, "battery": self.hass.states.get(f"sensor.{base}_battery") is not None})
        return {
            "modes": list(MODES),
            "mode": self.mode(),
            "links": self._data.get("links") or {m: {} for m in MODES},
            "cameras": cams,
            "cooldown": self._data["settings"].get("cooldown", 120),
        }

    async def async_set_link(self, mode: str, trigger: str, cams: list[str] | None, secs: int | None) -> None:
        links = self._data.setdefault("links", {m: {} for m in MODES})
        table = links.setdefault(mode, {})
        if cams is None:
            table.pop(trigger, None)  # removed from this mode
        else:
            rule = table.setdefault(trigger, {"cams": [], "secs": 30})
            rule["cams"] = [c for c in dict.fromkeys(cams) if c in self._cams]
            if secs is not None:
                rule["secs"] = max(5, min(int(secs), 120))
        self._save()

    # ---- tidying ------------------------------------------------------

    def _remove(self, base: str, entry: dict[str, Any]) -> None:
        for key in ("jpg", "mp4"):
            if entry.get(key):
                try:
                    (self._root() / base / entry[key]).unlink(missing_ok=True)
                except OSError as err:
                    _LOGGER.debug("Couldn't remove %s: %s", entry[key], err)

    async def _tidy(self, _now: Any = None) -> None:
        """Remove events older than keep_days, then the oldest while over max_gb."""
        self._cams = self._find_cameras()  # a camera added since
        s = self._data["settings"]
        cutoff = time.time() - float(s.get("keep_days") or 5) * 86400
        limit = float(s.get("max_gb") or 3) * 1024**3
        drop: list[tuple[str, dict[str, Any]]] = []
        for base, events in self._data["events"].items():
            for e in events:
                if e["ts"] < cutoff:
                    drop.append((base, e))
        kept = sorted(
            ((b, e) for b, evs in self._data["events"].items() for e in evs if (b, e) not in drop),
            key=lambda x: x[1]["ts"],
        )
        total = sum(e.get("bytes", 0) for _, e in kept)
        for b, e in kept:
            if total <= limit:
                break
            drop.append((b, e))
            total -= e.get("bytes", 0)
        if not drop:
            return
        for base, e in drop:
            await self.hass.async_add_executor_job(self._remove, base, e)
            events = self._data["events"].get(base, [])
            if e in events:
                events.remove(e)
        self._save()

    # ---- reading ------------------------------------------------------

    def events(self, base: str) -> list[dict[str, Any]]:
        """A camera's saved events, newest first."""
        out = []
        for e in sorted(self._data["events"].get(base, []), key=lambda e: e["ts"], reverse=True):
            out.append(
                {"id": e["id"], "ts": e["ts"], "kind": e["kind"], "source": e.get("source"), "jpg": e.get("jpg"), "mp4": e.get("mp4"), "codec": e.get("codec")}
            )
        return out

    def path(self, base: str, name: str) -> Path | None:
        if not SAFE.match(base) or not SAFE_FILE.match(name):
            return None
        return self._root() / base / name


def _low_priority() -> None:
    """Run ffmpeg below Home Assistant (in the child, before it starts)."""
    try:
        os.nice(10)
    except OSError:
        pass


class EventFileView(HomeAssistantView):
    """A saved picture or clip (the card asks for signed links to these)."""

    url = URL + "/{base}/{name}"
    name = "api:church_drive:events"
    requires_auth = True

    def __init__(self, hass: HomeAssistant) -> None:
        self._hass = hass

    async def get(self, request: web.Request, base: str, name: str) -> web.StreamResponse:
        # The current CameraEvents (Church Drive may have been reloaded since).
        events = self._hass.data.get(DOMAIN, {}).get("events")
        path = events.path(base, name) if events is not None else None
        if path is None:
            raise web.HTTPNotFound
        exists = await self._hass.async_add_executor_job(path.is_file)
        if not exists:
            raise web.HTTPNotFound
        return web.FileResponse(path, headers={"Cache-Control": "private, max-age=86400"})
