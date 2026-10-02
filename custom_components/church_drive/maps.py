"""Google map tiles, fetched by Home Assistant for the browser.

The Map Tiles key is locked to Home Assistant's web addresses, which Google
reads from the Referer. Home Assistant's pages send no referrer to other
sites, and its own maps (MapLibre) can't be told to, so the browser asks Home
Assistant (/api/church_drive/maptile/<style>/<z>/<x>/<y>?t=<token>) and Home
Assistant asks Google, giving its own external address as the Referer. The
token comes from the church_drive/maps websocket command (signed-in users only)
and changes each time Home Assistant starts.
"""

from __future__ import annotations

import asyncio
import logging
import secrets
import time
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.network import NoURLAvailableError, get_url

from .const import DOMAIN

_LOGGER = logging.getLogger(__name__)

GOOGLE = "https://tile.googleapis.com/v1"
URL = "/api/church_drive/maptile"
SESSIONS = {
    "satellite": {"mapType": "satellite", "language": "en-GB", "region": "GB", "layerTypes": ["layerRoadmap"]},
    "street": {"mapType": "roadmap", "language": "en-GB", "region": "GB"},
}


class GoogleTiles:
    """Google Map Tiles sessions and tile fetching for one key."""

    def __init__(self, hass: HomeAssistant, key: str) -> None:
        self.hass = hass
        self.key = key
        self.token = secrets.token_urlsafe(18)
        self._sessions: dict[str, tuple[str, float]] = {}
        self._lock = asyncio.Lock()
        self.error: str | None = None

    def _referer(self) -> str:
        try:
            return get_url(self.hass, allow_internal=False, prefer_cloud=True).rstrip("/") + "/"
        except NoURLAvailableError:
            try:
                return get_url(self.hass).rstrip("/") + "/"
            except NoURLAvailableError:
                return "http://homeassistant.local:8123/"

    def url(self) -> str:
        return f"{URL}/{{style}}/{{z}}/{{x}}/{{y}}?t={self.token}"

    async def _session(self, style: str, fresh: bool = False) -> str:
        async with self._lock:
            have = self._sessions.get(style)
            if have and not fresh and have[1] > time.time() + 3600:
                return have[0]
            resp = await async_get_clientsession(self.hass).post(
                f"{GOOGLE}/createSession",
                params={"key": self.key},
                json=SESSIONS[style],
                headers={"Referer": self._referer()},
                timeout=15,
            )
            data: dict[str, Any] = await resp.json(content_type=None)
            if resp.status != 200 or "session" not in data:
                self.error = ((data or {}).get("error") or {}).get("message") or f"HTTP {resp.status}"
                raise web.HTTPBadGateway(text=f"Google refused the Map Tiles key: {self.error}")
            self.error = None
            self._sessions[style] = (data["session"], float(data.get("expiry") or time.time() + 86400))
            return data["session"]

    async def tile(self, style: str, z: int, x: int, y: int) -> tuple[bytes, str]:
        for attempt in range(2):
            session = await self._session(style, fresh=attempt > 0)
            resp = await async_get_clientsession(self.hass).get(
                f"{GOOGLE}/2dtiles/{z}/{x}/{y}",
                params={"session": session, "key": self.key},
                headers={"Referer": self._referer()},
                timeout=15,
            )
            if resp.status == 200:
                return await resp.read(), resp.headers.get("Content-Type", "image/png")
            if resp.status == 404:
                raise web.HTTPNotFound  # no imagery that deep here
            if attempt == 0 and resp.status in (400, 401, 403):
                continue  # session expired: a new one, once
            raise web.HTTPBadGateway(text=f"Google tile error {resp.status}")
        raise web.HTTPBadGateway


class MapTileView(HomeAssistantView):
    """A Google map tile, for a browser holding the current token."""

    url = URL + "/{style}/{z}/{x}/{y}"
    name = "api:church_drive:maptile"
    # Map engines load tiles as plain image requests (no bearer token); the
    # t= token from the signed-in websocket stands in for it.
    requires_auth = False

    def __init__(self, hass: HomeAssistant) -> None:
        self._hass = hass

    async def get(self, request: web.Request, style: str, z: str, x: str, y: str) -> web.StreamResponse:
        tiles: GoogleTiles | None = self._hass.data.get(DOMAIN, {}).get("tiles")
        if tiles is None or not secrets.compare_digest(request.query.get("t", ""), tiles.token):
            raise web.HTTPForbidden
        if style not in SESSIONS or not (z.isdigit() and x.isdigit() and y.isdigit()) or int(z) > 22:
            raise web.HTTPNotFound
        body, ctype = await tiles.tile(style, int(z), int(x), int(y))
        return web.Response(body=body, content_type=ctype.split(";")[0], headers={"Cache-Control": "private, max-age=86400"})
