"""Task inbox: turn text (a message, a note, something said) into to-do tasks.

Text arrives from the phone app's share sheet and highlight menu, from a voice
assistant, from an automation, or from the Tasks card, all through
`church_drive.add_tasks`. An AI Task entity (any Home Assistant AI Task
provider) reads it and splits it into tasks, each with the to-do list it
belongs on, a due date if the text gives one, and a short note. Nothing goes on
a list yet: the tasks wait in the inbox as a **batch** for the person to check
(change the list, edit the wording, drop one), then `confirm` adds them with
`todo.add_item`. The `auto_add` option skips the check, for voice.

Batches are kept across restarts. A batch not looked at for a few days is
dropped. If no AI Task entity is set up, or it fails, the whole text becomes one
task on the sender's own list so nothing is lost.
"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, timedelta
from typing import Any

from homeassistant.core import Context, HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

_LOGGER = logging.getLogger(__name__)

STORE_VERSION = 1
SIGNAL_INBOX = "church_drive_inbox"
EVENT_INBOX = "church_drive_inbox_changed"
KEEP = timedelta(days=7)
MAX_TEXT = 6000
MAX_ITEMS = 20
AI_TIMEOUT = 60

INSTRUCTIONS = """You turn a message into to-do tasks for a family home.

Today is {today} ({weekday}). The message was sent by {sender}.
The to-do lists you may use (use the list_id exactly as written):
{lists}

Rules:
- Extract only real actions someone has to do. Skip chat, greetings and things already done.
- One task per action. Keep each task short, starting with a verb ("Book dentist", "Buy milk").
- Choose the list that fits the task: shopping goes on the shopping list, cleaning jobs on the cleaning list, a task for a named person on that person's list, a task for the whole household on the "everyone" list. If nobody is named and it isn't shopping or cleaning, use the sender's own list.
- If the text gives a day or time ("by Friday", "tomorrow 5pm") set due as an ISO date (YYYY-MM-DD) or date-time (YYYY-MM-DDTHH:MM:SS). Otherwise leave due empty.
- note holds useful detail (an address, amount, who asked). Leave it empty if there is none.
- If there are no tasks, return an empty list.

Message:
\"\"\"
{text}
\"\"\""""

STRUCTURE = {
    "tasks": {
        "description": "The tasks found in the message, in the order they appear",
        "required": True,
        "selector": {
            "object": {
                "multiple": True,
                "fields": {
                    "summary": {"required": True, "selector": {"text": {}}},
                    "list_id": {"required": True, "selector": {"text": {}}},
                    "due": {"selector": {"text": {}}},
                    "note": {"selector": {"text": {}}},
                },
            }
        },
    }
}


def _clean(text: Any, limit: int = 255) -> str:
    return " ".join(str(text or "").split())[:limit]


class Inbox:
    """Batches of proposed tasks waiting to be checked."""

    def __init__(self, hass: HomeAssistant, people: Any) -> None:
        self.hass = hass
        self._people = people
        self._store: Store = Store(hass, STORE_VERSION, "church_drive.inbox")
        self._batches: list[dict[str, Any]] = []

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self._batches = list(data.get("batches", []))
        self._expire()

    def _expire(self) -> None:
        cutoff = (dt_util.utcnow() - KEEP).isoformat()
        self._batches = [b for b in self._batches if b["created"] >= cutoff]

    async def _async_save(self) -> None:
        await self._store.async_save({"batches": self._batches})

    def _changed(self, context: Context | None = None) -> None:
        async_dispatcher_send(self.hass, SIGNAL_INBOX)
        self.hass.bus.async_fire(
            EVENT_INBOX, {"open": len(self.batches())}, context=context
        )

    # ---- what the AI may choose from ----------------------------------

    def lists(self) -> list[dict[str, str]]:
        """Every to-do list, with a friendly name. Automatic lists are left out: the house fills those."""
        out = []
        for st in self.hass.states.async_all("todo"):
            name = st.name or st.entity_id
            if "automatic" in st.entity_id:
                continue
            out.append({"id": st.entity_id, "name": name})
        return out

    def _person(self, who: str | None) -> dict[str, Any] | None:
        """The person called [who] (a name, first name or person entity), if known."""
        if not who or self._people is None:
            return None
        key = who.strip().lower()
        for p in self._people.people():
            if key in (p["entity_id"].lower(), p["name"].lower(), p["first"].lower()):
                return p
        return None

    def _own_list(self, person: dict[str, Any] | None) -> str:
        if person and self.hass.states.get(person["list"]):
            return person["list"]
        return "todo.priorities_everyone"

    # ---- reading the text ---------------------------------------------

    def _ai_entity(self) -> str | None:
        reg = er.async_get(self.hass)
        configured = [e.entity_id for e in reg.entities.values() if e.domain == "ai_task" and not e.disabled]
        return configured[0] if configured else None

    async def _async_extract(
        self, text: str, sender: str, own: str, lists: list[dict[str, str]], entity_id: str | None, context: Context | None
    ) -> list[dict[str, str]]:
        entity = entity_id or self._ai_entity()
        if entity is None:
            raise RuntimeError("No AI Task entity is set up")
        now = dt_util.now()
        prompt = INSTRUCTIONS.format(
            today=now.date().isoformat(),
            weekday=now.strftime("%A"),
            sender=sender or "someone in the house",
            lists="\n".join(f"- {l['id']}: {l['name']}" for l in lists),
            text=text,
        )
        result = await self.hass.services.async_call(
            "ai_task",
            "generate_data",
            {"task_name": "Church Drive tasks", "instructions": prompt, "entity_id": entity, "structure": STRUCTURE},
            blocking=True,
            return_response=True,
            context=context,
        )
        found = ((result or {}).get("data") or {}).get("tasks") or []
        valid = {l["id"] for l in lists}
        tasks = []
        for t in found[:MAX_ITEMS]:
            summary = _clean(t.get("summary"))
            if not summary:
                continue
            list_id = t.get("list_id") if t.get("list_id") in valid else own
            tasks.append(
                {
                    "summary": summary,
                    "list": list_id,
                    "due": self._due(t.get("due")),
                    "note": _clean(t.get("note"), 1000),
                }
            )
        return tasks

    @staticmethod
    def _due(value: Any) -> str:
        """A date or date-time the To-do service accepts, or empty."""
        text = str(value or "").strip()
        if not text:
            return ""
        try:
            if "T" in text or " " in text:
                return datetime.fromisoformat(text.replace(" ", "T")).replace(microsecond=0).isoformat()
            return datetime.strptime(text, "%Y-%m-%d").date().isoformat()
        except ValueError:
            return ""

    # ---- submitting ----------------------------------------------------

    async def async_submit(
        self,
        text: str,
        source: str = "",
        person: str | None = None,
        auto_add: bool = False,
        ai_entity: str | None = None,
        context: Context | None = None,
    ) -> dict[str, Any]:
        text = (text or "").strip()[:MAX_TEXT]
        if not text:
            return {"batch": None, "tasks": [], "added": 0}
        who = self._person(person)
        own = self._own_list(who)
        lists = self.lists()
        ai_failed = ""
        try:
            tasks = await self._async_extract(text, who["first"] if who else (person or ""), own, lists, ai_entity, context)
        except Exception as err:  # noqa: BLE001 - never lose what was sent
            _LOGGER.warning("Couldn't read tasks from the text with AI (%s); keeping it as one task", err)
            ai_failed = str(err) or err.__class__.__name__
            tasks = [{"summary": _clean(text.splitlines()[0]), "list": own, "due": "", "note": _clean(text, 1000)}]
        batch = {
            "id": uuid.uuid4().hex[:12],
            "created": dt_util.utcnow().isoformat(),
            "source": _clean(source, 60),
            "person": who["first"] if who else (person or ""),
            "text": text,
            "ai_failed": ai_failed,
            "tasks": [{"id": uuid.uuid4().hex[:8], **t} for t in tasks],
        }
        if not batch["tasks"]:
            return {"batch": None, "tasks": [], "added": 0}
        if auto_add:
            added = await self._async_add(batch["tasks"], context)
            return {"batch": None, "tasks": batch["tasks"], "added": added}
        self._batches.append(batch)
        self._expire()
        await self._async_save()
        self._changed(context)
        await self._async_tell(batch, who, context)
        return {"batch": batch["id"], "tasks": batch["tasks"], "added": 0}

    async def _async_tell(self, batch: dict[str, Any], who: dict[str, Any] | None, context: Context | None) -> None:
        """Tell the sender their tasks are ready to check, on their phones."""
        if self._people is None or who is None:
            return
        n = len(batch["tasks"])
        try:
            await self._people.async_notify(
                None, [who["first"]], "Tasks to check",
                f"{n} task{'s' if n != 1 else ''} found: {batch['tasks'][0]['summary']}" + (" …" if n > 1 else ""),
                tag=f"inbox-{batch['id']}", link="/dashboard-mobile/todo", context=context,
            )
        except Exception:  # noqa: BLE001 - the notice is a courtesy
            _LOGGER.debug("Couldn't tell %s about their tasks", who["first"], exc_info=True)

    # ---- checking ------------------------------------------------------

    @callback
    def batches(self, person: str | None = None) -> list[dict[str, Any]]:
        self._expire()
        mine = self._person(person)
        out = [b for b in self._batches if not mine or b["person"] in ("", mine["first"])]
        return sorted(out, key=lambda b: b["created"], reverse=True)

    async def _async_add(self, tasks: list[dict[str, Any]], context: Context | None) -> int:
        valid = {l["id"] for l in self.lists()}
        added = 0
        for t in tasks:
            if t.get("list") not in valid:
                raise ValueError(f"Unknown to-do list: {t.get('list')}")
            data: dict[str, Any] = {"entity_id": t["list"], "item": _clean(t["summary"])}
            if t.get("note"):
                data["description"] = t["note"]
            due = t.get("due") or ""
            if "T" in due:
                data["due_datetime"] = due
            elif due:
                data["due_date"] = due
            await self.hass.services.async_call("todo", "add_item", data, blocking=True, context=context)
            added += 1
        return added

    async def async_confirm(
        self, batch_id: str, tasks: list[dict[str, Any]] | None, context: Context | None = None
    ) -> int:
        """Add the checked tasks. [tasks] are the person's edits (a task left out is dropped);
        None adds the batch as it was proposed."""
        batch = next((b for b in self._batches if b["id"] == batch_id), None)
        if batch is None:
            raise KeyError(batch_id)
        chosen = batch["tasks"] if tasks is None else [
            {"summary": t["summary"], "list": t["list"], "due": self._due(t.get("due")), "note": t.get("note", "")}
            for t in tasks
            if _clean(t.get("summary"))
        ]
        added = await self._async_add(chosen, context)
        await self.async_dismiss(batch_id, context)
        return added

    async def async_dismiss(self, batch_id: str, context: Context | None = None) -> None:
        self._batches = [b for b in self._batches if b["id"] != batch_id]
        await self._async_save()
        self._changed(context)
