"""Sends notifications to the Church Drive app through Google's push service, so they arrive without the app keeping its own
connection (and without the "connected" notice). The phone's token comes from the app; the key that signs the requests is a
service account file in Home Assistant's config folder. Without the file, or without a token for a phone, nothing changes:
the app is sent its notifications over its own connection, as before."""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.storage import Store

from .fcm_logic import KEY_FILE, SCOPE, build_message, endpoint, token_is_dead, valid_token_event

_LOGGER = logging.getLogger(__name__)

EVENT_FCM_TOKEN = "church_drive_fcm_token"  # fired by the Church Drive app through its webhook


class Fcm:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, 1, "church_drive.fcm")
        self._tokens: dict[str, str] = {}
        self._creds: Any = None
        self._project = ""
        self._unsub: Any = None

    @property
    def configured(self) -> bool:
        return self._creds is not None

    def token_for(self, service: str) -> str | None:
        return self._tokens.get(service) if self.configured else None

    async def async_start(self) -> None:
        self._tokens = (await self._store.async_load()) or {}
        await self.hass.async_add_executor_job(self._load_key)
        self._unsub = self.hass.bus.async_listen(EVENT_FCM_TOKEN, self._on_token)

    @callback
    def async_stop(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    def _load_key(self) -> None:
        path = self.hass.config.path(KEY_FILE)
        try:
            from google.oauth2 import service_account  # imported here: it is only needed when the key is there

            self._creds = service_account.Credentials.from_service_account_file(path, scopes=[SCOPE])
            self._project = self._creds.project_id
            _LOGGER.info("Google push is on (project %s)", self._project)
        except FileNotFoundError:
            self._creds = None
        except Exception as err:  # noqa: BLE001 - a bad key file must not stop Church Drive
            _LOGGER.warning("Google push is off: the key in %s couldn't be read (%s)", path, err)
            self._creds = None

    async def _on_token(self, event: Event) -> None:
        service, token = event.data.get("service"), event.data.get("token")
        if not valid_token_event(service, token) or not self.hass.services.has_service("notify", service):
            return
        if self._tokens.get(service) != token:
            self._tokens[service] = token
            await self._store.async_save(self._tokens)
            _LOGGER.info("Google push token saved for %s", service)

    def _access_token(self) -> str:
        from google.auth.transport.requests import Request

        if not self._creds.valid:
            self._creds.refresh(Request())
        return self._creds.token

    async def async_send(self, service: str, event: dict[str, Any]) -> bool:
        """Sends a notification event to a phone's token. False if it couldn't be (the caller then uses the app's own connection)."""
        token = self.token_for(service)
        if not token:
            return False
        try:
            access = await self.hass.async_add_executor_job(self._access_token)
            async with async_get_clientsession(self.hass).post(
                endpoint(self._project), json=build_message(token, event), headers={"Authorization": f"Bearer {access}"},
            ) as resp:
                if resp.status == 200:
                    return True
                body = await resp.text()
                if token_is_dead(resp.status, body):
                    self._tokens.pop(service, None)
                    await self._store.async_save(self._tokens)
                    _LOGGER.info("Google push token for %s is no longer valid; it will register again", service)
                else:
                    _LOGGER.warning("Google push to %s failed: %s %s", service, resp.status, body[:200])
        except Exception as err:  # noqa: BLE001 - fall back to the app's own connection
            _LOGGER.warning("Google push to %s failed: %s", service, err)
        return False
