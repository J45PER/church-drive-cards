"""The pure parts of sending a notification to the Church Drive app through Google's push service (Firebase Cloud Messaging)."""

from __future__ import annotations

import json
from typing import Any

KEY_FILE = "church_drive_fcm.json"  # the service account key, in Home Assistant's config folder (never in the repo)
SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
TTL_SECONDS = 1800  # a doorbell press that can't be delivered for half an hour isn't worth showing


def endpoint(project_id: str) -> str:
    return f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"


def build_message(token: str, event: dict[str, Any]) -> dict[str, Any]:
    """A data-only, high priority message: the app builds the notification itself, so its styles, buttons and mutes apply."""
    return {
        "message": {
            "token": token,
            "data": {"event": json.dumps(event, separators=(",", ":"))},
            "android": {"priority": "HIGH", "ttl": f"{TTL_SECONDS}s"},
        }
    }


def token_is_dead(status: int, body: Any) -> bool:
    """Whether Google says this token will never work again (the app was uninstalled or its data cleared)."""
    if status == 404:
        return True
    text = json.dumps(body) if not isinstance(body, str) else body
    return (status == 400 and "UNREGISTERED" in text) or "registration-token-not-registered" in text


def clean_service(service: Any) -> Any:
    """The app names its phone as `notify.mobile_app_x`; the house keeps the service part, `mobile_app_x`."""
    return service.removeprefix("notify.") if isinstance(service, str) else service


def valid_token_event(service: Any, token: Any) -> bool:
    """A token the app reports: a phone's notify service name and a plausible FCM token."""
    return (
        isinstance(service, str) and service.startswith("mobile_app_") and service.replace("_", "").isalnum()
        and isinstance(token, str) and 20 <= len(token) <= 4096 and " " not in token
    )
