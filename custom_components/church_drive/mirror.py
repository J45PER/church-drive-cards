"""Copies the Manager's design data to Firestore (rules and shapes in mirror_logic.py). Without the key file, or when Google is out of reach, nothing
changes: Home Assistant still serves the same data itself, so the apps and the website keep working."""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.dispatcher import async_dispatcher_connect

from .colours import SIGNAL_COLOURS
from .mirror_logic import DEBOUNCE_SECONDS, KEY_FILE, SCOPE, build_documents, doc_url, document_body, fingerprint
from .proposals import SIGNAL_PROPOSALS

_LOGGER = logging.getLogger(__name__)


class Mirror:
    def __init__(self, hass: HomeAssistant, data: dict[str, Any], app_version: str) -> None:
        self.hass = hass
        self._data = data
        self._version = app_version
        self._creds: Any = None
        self._project = ""
        self._written: dict[str, str] = {}
        self._timer: Any = None
        self._unsubs: list[Any] = []
        self.last_ok: float | None = None
        self.last_error: str | None = None

    @property
    def configured(self) -> bool:
        return self._creds is not None

    async def async_start(self) -> None:
        await self.hass.async_add_executor_job(self._load_key)
        if not self.configured:
            return
        for signal in (SIGNAL_COLOURS, SIGNAL_PROPOSALS):
            self._unsubs.append(async_dispatcher_connect(self.hass, signal, self._changed))
        self._changed()  # bring Firestore up to date after a start

    @callback
    def async_stop(self) -> None:
        for u in self._unsubs:
            u()
        self._unsubs.clear()
        if self._timer:
            self._timer.cancel()
            self._timer = None

    def _load_key(self) -> None:
        path = self.hass.config.path(KEY_FILE)
        try:
            from google.oauth2 import service_account

            self._creds = service_account.Credentials.from_service_account_file(path, scopes=[SCOPE])
            self._project = self._creds.project_id
            _LOGGER.info("The Firestore mirror is on (project %s)", self._project)
        except FileNotFoundError:
            self._creds = None
        except Exception as err:  # noqa: BLE001 - a bad key file must not stop Church Drive
            _LOGGER.warning("The Firestore mirror is off: the key in %s couldn't be read (%s)", path, err)
            self._creds = None

    @callback
    def _changed(self) -> None:
        """Waits a few seconds so a burst of changes is sent once."""
        if self._timer:
            self._timer.cancel()
        self._timer = self.hass.loop.call_later(DEBOUNCE_SECONDS, lambda: self.hass.async_create_task(self.async_sync()))

    def _access_token(self) -> str:
        from google.auth.transport.requests import Request

        if not self._creds.valid:
            self._creds.refresh(Request())
        return self._creds.token

    def _documents(self) -> list[tuple[str, str, dict[str, Any]]]:
        colours, proposals = self._data.get("colours"), self._data.get("proposals")
        if colours is None:
            return []
        return build_documents(colours.config, colours.overrides, proposals.cards if proposals else {}, proposals.versions if proposals else {}, self._version, time.time())

    async def async_sync(self, force: bool = False) -> int:
        """Writes the documents that changed. Returns how many were written; a failure is logged and the next change tries again."""
        if not self.configured:
            return 0
        sent = 0
        try:
            token = await self.hass.async_add_executor_job(self._access_token)
            session = async_get_clientsession(self.hass)
            for collection, doc_id, data in self._documents():
                key, fp = f"{collection}/{doc_id}", fingerprint(data)
                if not force and self._written.get(key) == fp:
                    continue
                async with session.patch(doc_url(self._project, collection, doc_id), json=document_body(data), headers={"Authorization": f"Bearer {token}"}, timeout=20) as resp:
                    if resp.status != 200:
                        raise RuntimeError(f"{resp.status} {(await resp.text())[:200]}")
                self._written[key] = fp
                sent += 1
            self.last_ok, self.last_error = time.time(), None
        except (asyncio.CancelledError, GeneratorExit):
            raise
        except Exception as err:  # noqa: BLE001 - Home Assistant keeps serving the same data
            self.last_error = str(err)
            _LOGGER.warning("The Firestore mirror couldn't write (%s); it will try again at the next change", err)
        return sent
