"""Who changed the alarm, as Home Assistant knows it.

Ring only knows its own signed-in account, so the alarm entity says "Disarmed by Hayley" (the Ring account Home
Assistant uses) even when Jamie did it from the Church Drive app. Home Assistant does know: the state change carries the
login that asked for it. This remembers that, per change, in `sensor.church_drive_alarm_actor`:

- state: the person's first name, or "unknown" when the change did not come from a login (the keypad, the Ring app);
- attributes: `alarm` (the alarm entity), `alarm_state` (what it changed to) and `at` (when).

The cards and the app use it when it matches the alarm's current state and is the latest change; otherwise they show
Ring's name as before. It starts empty after a restart until the next change.
"""

from __future__ import annotations

from typing import Any, Callable

from homeassistant.const import EVENT_STATE_CHANGED
from homeassistant.core import Event, HomeAssistant, callback

ENTITY_ID = "sensor.church_drive_alarm_actor"
ALARM_STATES = {"disarmed", "armed_home", "armed_away", "armed_night", "armed_vacation", "armed_custom_bypass"}


class AlarmActor:
    def __init__(self, hass: HomeAssistant, first_name_for_user: Callable[[str | None], str]) -> None:
        self.hass = hass
        self._first_name_for_user = first_name_for_user
        self._unsub: Any = None

    @callback
    def async_start(self) -> None:
        self._unsub = self.hass.bus.async_listen(
            EVENT_STATE_CHANGED, self._on_state, event_filter=lambda data: str(data.get("entity_id", "")).startswith("alarm_control_panel.")
        )

    @callback
    def async_stop(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_state(self, event: Event) -> None:
        old, new = event.data.get("old_state"), event.data.get("new_state")
        if new is None or old is None or new.state == old.state or new.state not in ALARM_STATES:
            return
        who = self._first_name_for_user(event.context.user_id)
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
