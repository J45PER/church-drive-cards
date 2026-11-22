"""Clive's chats, recorded from Home Assistant's own chat log (what was asked, what he answered, which abilities he used, which card he showed).

Home Assistant's conversation integration tells subscribers whenever a chat log is created or changes; this listens, keeps each turn, and
lets an administrator mark it good or bad with a note. Kept in .storage/church_drive.chats for 30 days, administrators only (chats_logic.py
has the rules). If this Home Assistant has no chat log to subscribe to, `available` is False and `reason` says so; nothing else is affected.
"""

from __future__ import annotations

import logging
import time
from typing import Any, Callable

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.storage import Store

from .chats_logic import clean, mark_sent, prune, rate, select, turns_from_chat_log, upsert

_LOGGER = logging.getLogger(__name__)
STORE_KEY = "church_drive.chats"
SAVE_S = 30
WHO_WINDOW_S = 30


class Chats:
    def __init__(self, hass: HomeAssistant, who_near: Callable[[float], str | None] | None = None) -> None:
        self.hass = hass
        self._who_near = who_near or (lambda ts: None)
        self._store: Store[list] = Store(hass, 1, STORE_KEY)
        self.entries: list[dict[str, Any]] = []
        self._unsub: Callable[[], None] | None = None
        self.available = False
        self.reason = "not started"

    async def async_start(self) -> None:
        self.entries = prune(clean(await self._store.async_load()), time.time())
        try:
            from homeassistant.components.conversation.chat_log import async_subscribe_chat_logs  # noqa: PLC0415
        except ImportError:
            self.reason = "this Home Assistant has no chat log to listen to"
            _LOGGER.warning("Chats are not recorded: %s", self.reason)
            return
        try:
            self._unsub = async_subscribe_chat_logs(self.hass, self._on_chat_log)
        except Exception as err:  # noqa: BLE001
            self.reason = f"the chat log could not be listened to ({err})"
            _LOGGER.warning("Chats are not recorded: %s", self.reason)
            return
        self.available, self.reason = True, ""

    @callback
    def async_stop(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None
        self._store.async_delay_save(lambda: self.entries, 0)

    @callback
    def _on_chat_log(self, conversation_id: str, event_type: Any, data: Any) -> None:
        """A chat log was created, changed or deleted: take its turns (a deleted one keeps what was recorded)."""
        if str(getattr(event_type, "value", event_type)).lower() == "deleted":
            return
        # Home Assistant calls this from inside Clive's own request and does not catch an error here: a failure would turn the answer into
        # "Sorry, I didn't understand that" for every turn of that chat. Recording a chat must never be able to do that.
        try:
            log = (data or {}).get("chat_log") if isinstance(data, dict) else None
            if isinstance(log, dict) and "conversation_id" not in log:
                log = {**log, "conversation_id": conversation_id}
            changed = False
            for turn in turns_from_chat_log(log):
                changed |= upsert(self.entries, turn, who=self._who_near(turn["ts"]))
            if changed:
                self.entries = prune(self.entries, time.time())
                self._store.async_delay_save(lambda: self.entries, SAVE_S)
        except Exception:  # noqa: BLE001
            _LOGGER.warning("A chat with Clive could not be recorded", exc_info=True)

    def asking(self, now: float) -> bool:
        """Whether someone is talking to Clive right now: a turn begun a moment ago that is still being answered, or was answered a moment ago."""
        for entry in self.entries[-30:]:
            end = entry["ts"] + (entry.get("seconds") or 10)
            if entry["ts"] - 1 <= now <= end + 3:
                return True
        return False

    async def async_query(self, **filters: Any) -> dict[str, Any]:
        out = select(self.entries, **filters)
        return {**out, "available": self.available, "reason": self.reason, "retention_days": 30}

    async def async_rate(self, chat_id: str, rating: Any, note: Any = None) -> dict[str, Any]:
        entry = rate(self.entries, chat_id, rating, note)
        self._store.async_delay_save(lambda: self.entries, 1)
        return entry

    async def async_mark_sent(self, ids: list[str]) -> int:
        n = mark_sent(self.entries, ids)
        if n:
            self._store.async_delay_save(lambda: self.entries, 1)
        return n
