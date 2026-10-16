"""Who changed the alarm, as Home Assistant knows it.

Ring only knows its own signed-in account, so the alarm entity says "Disarmed by Hayley" (the Ring account Home
Assistant uses) even when Jamie did it from the Church Drive app. Home Assistant does know, but only on the arm or
disarm *request* (the service call, which carries the login), not on the state change Ring confirms a moment later. So
the request is remembered (alarm_actor_logic.py) and the next change of that alarm is credited to it, in
`sensor.church_drive_alarm_actor`:

- state: the person's first name, or "unknown" when the change did not come from a login (the keypad, the Ring app);
- attributes: `alarm` (the alarm entity), `alarm_state` (what it changed to) and `at` (when).

The cards and the app use it when it matches the alarm's current state and is the latest change; otherwise they show
Ring's name as before. It starts empty after a restart until the next change.
"""

from __future__ import annotations

import time
from typing import Any, Callable

from homeassistant.const import EVENT_CALL_SERVICE, EVENT_STATE_CHANGED
from homeassistant.core import Event, HomeAssistant, callback

from .alarm_actor_logic import credit, remember

ENTITY_ID = "sensor.church_drive_alarm_actor"
ALARM_STATES = {"disarmed", "armed_home", "armed_away", "armed_night", "armed_vacation", "armed_custom_bypass"}


class AlarmActor:
    def __init__(self, hass: HomeAssistant, first_name_for_user: Callable[[str | None], str]) -> None:
        self.hass = hass
        self._first_name_for_user = first_name_for_user
        self._unsubs: list[Any] = []
        self._calls: dict[str, tuple[str, float]] = {}  # alarm entity -> (login, when it asked)

    # Home Assistant only accepts event filters marked as callbacks.
    @staticmethod
    @callback
    def _is_alarm(data: Any) -> bool:
        return str(data.get("entity_id", "")).startswith("alarm_control_panel.")

    @staticmethod
    @callback
    def _is_alarm_call(data: Any) -> bool:
        return data.get("domain") == "alarm_control_panel"

    @callback
    def async_start(self) -> None:
        self._unsubs = [
            self.hass.bus.async_listen(EVENT_CALL_SERVICE, self._on_call, event_filter=self._is_alarm_call),
            self.hass.bus.async_listen(EVENT_STATE_CHANGED, self._on_state, event_filter=self._is_alarm),
        ]

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs = []

    @callback
    def _on_call(self, event: Event) -> None:
        remember(self._calls, str(event.data.get("service", "")), event.data.get("service_data"), event.context.user_id, time.time())

    @callback
    def _on_state(self, event: Event) -> None:
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if new is None or old is None or new.state == old.state or new.state not in ALARM_STATES:
            return
        user_id = event.context.user_id or credit(self._calls, new.entity_id, time.time())
        who = self._first_name_for_user(user_id)
        self.hass.states.async_set(
            ENTITY_ID,
            who or "unknown",
            {
                "alarm": new.entity_id,
                "alarm_state": new.state,
                "at": new.last_changed.isoformat(),
                "friendly_name": "Alarm changed by",
                "icon": "mdi:account-key",
            },
        )
