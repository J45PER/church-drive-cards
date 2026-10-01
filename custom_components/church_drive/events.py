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
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
import logging
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
DEFAULTS = {"keep_days": 5, "max_gb": 3.0, "dir": None}
PICTURE_DELAY = 8  # seconds: ring-mqtt's snapshot arrives a moment after the event
PAIR_WINDOW = 240  # seconds between an event and its recording
MAX_CLIP = 300 * 1024 * 1024
URL = "/api/church_drive/events"
SAFE = re.compile(r"^[a-z0-9_]+$")
SAFE_FILE = re.compile(r"^[0-9]{8}-[0-9]{6}_[a-z]+(_[0-9]+)?\.(jpg|mp4)$")


class CameraEvents:
    """Saves each camera event's picture and clip, and serves the list."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.camera_events")
        self._data: dict[str, Any] = {"settings": dict(DEFAULTS), "events": {}}
        self._unsubs: list = []
        self._tasks: set[asyncio.Task] = set()
        self._cams: list[str] = []

    # ---- start / stop -------------------------------------------------

    async def async_start(self) -> None:
        stored = await self._store.async_load()
        if isinstance(stored, dict):
            self._data["settings"].update(stored.get("settings") or {})
            self._data["events"] = stored.get("events") or {}
        await self.hass.async_add_executor_job(self._root().mkdir, 0o755, True, True)
        self._cams = self._find_cameras()
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

    async def async_set_settings(self, keep_days: int | None, max_gb: float | None, folder: str | None) -> None:
        s = self._data["settings"]
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
        return (eid.startswith("event.") and eid.endswith(("_ding", "_motion"))) or (
            eid.startswith("camera.") and eid.endswith("_live_view")
        )

    @callback
    def _on_state(self, event: Event) -> None:
        eid = event.data.get("entity_id", "")
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if new is None or old is None:
            return
        base = self.base_of(eid)
        if not base or base not in self._cams:
            return
        if eid.startswith("event."):
            if new.state in ("unknown", "unavailable") or old.state in ("unavailable",) or new.state == old.state:
                return
            kind = "ding" if eid.endswith("_ding") else "motion"
            when = dt_util.parse_datetime(new.state) or dt_util.utcnow()
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
        created = dt_util.parse_datetime(act.attributes.get("created_at") or act.state) if act else None
        kind = "ding" if act is not None and act.attributes.get("category") == "ding" else "motion"
        ts = (created or dt_util.utcnow()).timestamp()
        # The event this recording belongs to: same kind, close in time, no clip yet.
        entry = min(
            (e for e in events if not e.get("mp4") and e["kind"] == kind and abs(e["ts"] - ts) < PAIR_WINDOW),
            key=lambda e: abs(e["ts"] - ts),
            default=None,
        )
        if entry is None:
            entry = {"id": f"{int(ts)}{kind[0]}", "ts": ts, "kind": kind, "jpg": None, "mp4": None, "bytes": 0}
            events.append(entry)
        entry["video_id"] = video_id
        try:
            session = async_get_clientsession(self.hass)
            async with session.get(url, timeout=120) as resp:
                resp.raise_for_status()
                data = await resp.content.read(MAX_CLIP)
        except Exception as err:  # noqa: BLE001
            _LOGGER.warning("Couldn't download the %s recording: %s", base, err)
            self._save()
            return
        name = f"{self._stamp(entry['ts'], entry['kind'])}.mp4"
        size = await self.hass.async_add_executor_job(self._write, self._root() / base / name, data)
        entry["mp4"] = name
        entry["bytes"] = entry.get("bytes", 0) + size
        if not entry.get("jpg"):
            await self._async_picture(base, entry)
        self._save()
        await self._tidy()

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
            out.append({"id": e["id"], "ts": e["ts"], "kind": e["kind"], "jpg": e.get("jpg"), "mp4": e.get("mp4")})
        return out

    def path(self, base: str, name: str) -> Path | None:
        if not SAFE.match(base) or not SAFE_FILE.match(name):
            return None
        return self._root() / base / name


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
