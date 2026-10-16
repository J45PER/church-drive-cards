"""The plain rules for who made an alarm change (no Home Assistant in here, so they can be tested alone).

Home Assistant knows which login asked for an arm or disarm only on the *service call* (`call_service` event); the alarm's
own state change, which Ring confirms a moment later, carries no login. So each call from a login is remembered for a short
time, and the next state change of that alarm within it is credited to that login.
"""

from __future__ import annotations

from typing import Any

ARM_SERVICES = ("alarm_arm_home", "alarm_arm_away", "alarm_arm_night", "alarm_arm_vacation", "alarm_arm_custom_bypass", "alarm_disarm")
WINDOW_SECONDS = 180  # a change this soon after a login's request is theirs


def entities_of(service_data: Any) -> list[str]:
    """The alarm entities a service call names (a string or a list), or none."""
    if not isinstance(service_data, dict):
        return []
    named = service_data.get("entity_id")
    if isinstance(named, str):
        return [named]
    if isinstance(named, (list, tuple)):
        return [str(e) for e in named]
    return []


def remember(calls: dict[str, tuple[str, float]], service: str, service_data: Any, user_id: str | None, now: float) -> None:
    """Note a login's arm or disarm request for each alarm it names (requests with no login, e.g. an automation's, are ignored)."""
    if service not in ARM_SERVICES or not user_id:
        return
    for entity in entities_of(service_data):
        calls[entity] = (user_id, now)


def credit(calls: dict[str, tuple[str, float]], entity_id: str, now: float, window: float = WINDOW_SECONDS) -> str | None:
    """The login to credit for a change of [entity_id] now: whoever last asked for one recently. Each request is credited once."""
    found = calls.pop(entity_id, None)
    if found is None:
        return None
    user_id, at = found
    return user_id if 0 <= now - at <= window else None
