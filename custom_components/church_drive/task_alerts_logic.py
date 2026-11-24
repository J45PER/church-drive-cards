"""The plain rules behind task reminders ahead of a due time and overdue notices (no Home Assistant in here, so they can be tested alone).

A task with a due *time* can carry a lead: how many minutes before the due time to be reminded (0 is at the due time). The lead is
kept by the house with the task, not in its words. A task that has gone overdue is announced once when it passes its due moment and
then every morning until it is done or its due date moves. A task with only a due *day* is due at the end of that day.
"""

from __future__ import annotations

import re
from datetime import date, datetime, time, timedelta
from typing import Any

SEP = "|"
# The lengths the apps offer, in minutes: at the time, then before it.
LEADS = (0, 5, 15, 30, 60, 120, 1440)
MORNING = time(9, 0)  # when the daily overdue reminder goes
ALERT_LATE = timedelta(hours=3)  # a reminder this far past its moment (the house was off) is dropped, not sent late


def key(todo: str, uid: str) -> str:
    return f"{todo}{SEP}{uid}"


def valid_lead(value: Any) -> int | None:
    """The lead if it is one of the allowed lengths, else None."""
    try:
        minutes = int(value)
    except (TypeError, ValueError):
        return None
    return minutes if minutes in LEADS else None


_TIMER_WORDS = re.compile(
    r"^(once|every |(mon|tue|wed|thu|fri|sat|sun)[a-z]* \d{1,2}:\d{2}|monthly on |yearly on )", re.IGNORECASE
)


def has_timer(description: Any) -> bool:
    """Whether a task's words give it a repeat or "Once" reminder, which the repeating-tasks automation already sends at the due time."""
    first = str(description or "").split(" · ")[0].strip()
    return bool(_TIMER_WORDS.match(first))


def due_of(due: Any) -> tuple[datetime, bool] | None:
    """A task's due moment (naive, local) and whether it has a time. A day alone is due at the end of that day."""
    text = str(due or "").strip()
    if not text:
        return None
    try:
        if "T" in text or " " in text:
            return datetime.fromisoformat(text.replace(" ", "T")[:19]), True
        return datetime.combine(date.fromisoformat(text[:10]), time(23, 59)), False
    except ValueError:
        return None


def lead_moment(due: datetime, lead: int) -> datetime:
    return due - timedelta(minutes=lead)


def lead_text(lead: int) -> str:
    """How far ahead, in words: "now", "in 15 minutes", "in 1 hour", "tomorrow"."""
    if lead <= 0:
        return "now"
    if lead == 1440:
        return "tomorrow"
    if lead % 60 == 0:
        h = lead // 60
        return f"in {h} hour{'s' if h > 1 else ''}"
    return f"in {lead} minutes"


def reminders_due(tasks: list[dict[str, Any]], leads: dict[str, int], sent: dict[str, dict[str, str]], now: datetime) -> list[dict[str, Any]]:
    """The lead reminders to send now. [tasks] are open tasks: {"todo", "uid", "summary", "due"}. A task is reminded once for each due
    moment: when [now] has reached its lead moment, within [ALERT_LATE] of it, and not since its due moved."""
    out = []
    for t in tasks:
        lead = leads.get(key(t["todo"], t["uid"]))
        d = due_of(t.get("due"))
        if lead is None or d is None or not d[1] or (lead == 0 and has_timer(t.get("description"))):
            continue
        moment = lead_moment(d[0], lead)
        if moment <= now < moment + ALERT_LATE and sent.get(key(t["todo"], t["uid"]), {}).get("lead") != d[0].isoformat():
            out.append({**t, "lead": lead, "due_at": d[0]})
    return out


def overdue_now(tasks: list[dict[str, Any]], sent: dict[str, dict[str, str]], now: datetime) -> list[dict[str, Any]]:
    """The overdue notices to send now: each task once when it passes due, then once each day from the morning time."""
    out = []
    for t in tasks:
        d = due_of(t.get("due"))
        # A day-only task is overdue once its day is over (not in the last minute of it).
        if d is None or now <= d[0] or (not d[1] and now.date() <= d[0].date()):
            continue
        s = sent.get(key(t["todo"], t["uid"]), {})
        first = s.get("overdue_for") != d[0].isoformat()
        if first:
            # Just went overdue (not already long overdue when this ran): announce it. A day-only task passed at midnight is left to the morning.
            fresh = now - d[0] <= ALERT_LATE and d[1] and not has_timer(t.get("description"))
            if fresh:
                out.append({**t, "due_at": d[0], "first": True})
                continue
        today = now.date().isoformat()
        if now.time() >= MORNING and s.get("overdue_day") != today and (not first or not d[1] or now - d[0] > ALERT_LATE):
            out.append({**t, "due_at": d[0], "first": False})
    return out


def mark_lead(sent: dict[str, dict[str, str]], todo: str, uid: str, due_at: datetime) -> None:
    sent.setdefault(key(todo, uid), {})["lead"] = due_at.isoformat()


def mark_overdue(sent: dict[str, dict[str, str]], todo: str, uid: str, due_at: datetime, today: str) -> None:
    s = sent.setdefault(key(todo, uid), {})
    s["overdue_for"] = due_at.isoformat()
    s["overdue_day"] = today


def prune(sent: dict[str, dict[str, str]], open_keys: set[str]) -> dict[str, dict[str, str]]:
    """[sent] without tasks that are no longer open."""
    return {k: v for k, v in sent.items() if k in open_keys}


def overdue_words(due_at: datetime, now: datetime) -> str:
    """How late, in words: "due today at 14:00", "1 day overdue", "3 days overdue"."""
    days = (now.date() - due_at.date()).days
    if days <= 0:
        return f"was due at {due_at.strftime('%H:%M')}"
    return f"{days} day{'s' if days > 1 else ''} overdue"
