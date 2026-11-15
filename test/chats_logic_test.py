"""Tests for the chats rules (custom_components/church_drive/chats_logic.py): turns from a chat log, upsert, prune, marking, selecting."""

import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("chats_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "chats_logic.py")
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)

LOG = {"conversation_id": "abc", "content": [
    {"role": "user", "content": "Turn the heating up", "created": "2026-11-14T10:00:00+00:00"},
    {"role": "assistant", "content": "", "tool_calls": [{"tool_name": "clive_climate_control", "tool_args": {}}, {"tool_name": "clive_show_card", "tool_args": {"kind": "climate"}}], "created": "2026-11-14T10:00:02+00:00"},
    {"role": "tool_result", "content": "ok"},
    {"role": "assistant", "content": "Done, the heating is at 21.", "created": "2026-11-14T10:00:03+00:00"},
    {"role": "user", "content": "Thanks", "created": "2026-11-14T10:01:00+00:00"},
]}


class TurnsTest(unittest.TestCase):
    def test_turns(self):
        turns = c.turns_from_chat_log(LOG)
        self.assertEqual(len(turns), 2)
        t = turns[0]
        self.assertEqual((t["asked"], t["answer"], t["abilities"], t["card"], t["seconds"]), ("Turn the heating up", "Done, the heating is at 21.", ["Climate control", "Show card"], "climate", 3.0))
        self.assertEqual(turns[1]["answer"], "")   # no answer yet
        self.assertIsNone(turns[1]["seconds"])

    def test_bad_input(self):
        self.assertEqual(c.turns_from_chat_log(None), [])
        self.assertEqual(c.turns_from_chat_log({"content": [{"role": "user", "content": "", "created": "2026-11-14T10:00:00Z"}]}), [])

    def test_ability_name(self):
        self.assertEqual(c.ability_name("clive_zappi_charge"), "Zappi charge")
        self.assertEqual(c.ability_name("script.clive_arm_alarm"), "Arm alarm")


class ToolInput:
    """Home Assistant's chat log gives tool calls as objects like this one (llm.ToolInput), not as dicts."""

    def __init__(self, tool_name, tool_args):
        self.tool_name, self.tool_args = tool_name, tool_args


class ObjectToolCallsTest(unittest.TestCase):
    def test_tool_calls_that_are_objects(self):
        log = {"conversation_id": "abc", "content": [
            {"role": "user", "content": "Open the blind", "created": "2026-11-15T09:47:50+00:00"},
            {"role": "assistant", "tool_calls": [ToolInput("clive_show_card", {"kind": "done"}), ToolInput("cover_set_position", None)], "created": "2026-11-15T09:47:52+00:00"},
            {"role": "assistant", "content": "Opening it.", "created": "2026-11-15T09:47:53+00:00"},
            {"role": "user", "content": "And the other one", "created": "2026-11-15T09:48:08+00:00"},
        ]}
        turns = c.turns_from_chat_log(log)
        self.assertEqual(len(turns), 2)
        self.assertEqual((turns[0]["abilities"], turns[0]["card"], turns[0]["answer"]), (["Show card", "Cover set position"], "done", "Opening it."))

    def test_odd_tool_calls_never_raise(self):
        log = {"conversation_id": "abc", "content": [
            {"role": "user", "content": "x", "created": "2026-11-15T09:47:50+00:00"},
            {"role": "assistant", "tool_calls": [None, 3, ToolInput("clive_show_card", "not a dict")], "created": "2026-11-15T09:47:52+00:00"},
        ]}
        self.assertEqual(c.turns_from_chat_log(log)[0]["card"], "card")


class StoreTest(unittest.TestCase):
    def test_upsert_keeps_marks(self):
        entries = []
        turns = c.turns_from_chat_log(LOG)
        self.assertTrue(c.upsert(entries, turns[1], who="Jamie"))
        c.rate(entries, turns[1]["id"], "bad", "too slow")
        turns[1]["answer"] = "You're welcome"
        self.assertTrue(c.upsert(entries, turns[1]))
        self.assertEqual((entries[0]["answer"], entries[0]["rating"], entries[0]["note"], entries[0]["who"]), ("You're welcome", "bad", "too slow", "Jamie"))
        self.assertFalse(c.upsert(entries, turns[1]))

    def test_prune_and_clean(self):
        now = 1_000_000_000.0
        old = {"id": "a:1", "ts": now - 31 * 86400, "asked": "x", "answer": "y"}
        new = {"id": "a:2", "ts": now - 86400, "asked": "x", "answer": "y", "rating": "weird"}
        kept = c.prune(c.clean([old, new, {"id": "bad"}]), now)
        self.assertEqual([e["id"] for e in kept], ["a:2"])
        self.assertIsNone(kept[0]["rating"])

    def test_rate_errors(self):
        entries = c.clean([{"id": "a:2", "ts": 5.0, "asked": "x"}])
        with self.assertRaises(ValueError):
            c.rate(entries, "nope", "good")
        with self.assertRaises(ValueError):
            c.rate(entries, "a:2", "meh")
        c.rate(entries, "a:2", "good", "  nice  ")
        self.assertEqual((entries[0]["rating"], entries[0]["note"]), ("good", "nice"))
        c.rate(entries, "a:2", None)
        self.assertIsNone(entries[0]["rating"])


class SelectTest(unittest.TestCase):
    def setUp(self):
        self.entries = c.clean([
            {"id": "a:1", "ts": 100.0, "asked": "one", "rating": "good"}, {"id": "a:2", "ts": 200.0, "asked": "two", "rating": "bad", "note": "slow"},
            {"id": "a:3", "ts": 300.0, "asked": "three"}, {"id": "a:4", "ts": 400.0, "asked": "four", "note": "wrong room"}])

    def test_marks(self):
        self.assertEqual([e["id"] for e in c.select(self.entries)["entries"]], ["a:4", "a:3", "a:2", "a:1"])
        self.assertEqual([e["id"] for e in c.select(self.entries, mark="bad")["entries"]], ["a:2"])
        self.assertEqual([e["id"] for e in c.select(self.entries, mark="unmarked")["entries"]], ["a:3"])
        self.assertEqual([e["id"] for e in c.select(self.entries, mark="noted")["entries"]], ["a:4", "a:2"])
        self.assertEqual(c.select(self.entries)["counts"], {"all": 4, "good": 1, "bad": 1, "unmarked": 1, "noted": 2, "to_send": 2})

    def test_window_and_paging(self):
        self.assertEqual([e["id"] for e in c.select(self.entries, since=200.0, until=400.0)["entries"]], ["a:3", "a:2"])
        page = c.select(self.entries, limit=2, offset=2)
        self.assertEqual(([e["id"] for e in page["entries"]], page["total"]), (["a:2", "a:1"], 4))


class SendTest(unittest.TestCase):
    def test_to_send_and_marking(self):
        entries = c.clean([{"id": "a:1", "ts": 1.0, "asked": "x", "rating": "good"}, {"id": "a:2", "ts": 2.0, "asked": "y", "rating": "bad"}, {"id": "a:3", "ts": 3.0, "asked": "z", "note": "wrong"}])
        self.assertEqual([e["id"] for e in c.select(entries, mark="tosend")["entries"]], ["a:3", "a:2"])
        self.assertEqual(c.mark_sent(entries, ["a:2", "a:3", "a:1"]), 3)
        self.assertEqual(c.select(entries, mark="tosend")["total"], 0)
        c.rate(entries, "a:2", "bad", "still wrong")   # changing a chat makes it sendable again
        self.assertEqual([e["id"] for e in c.select(entries, mark="tosend")["entries"]], ["a:2"])


class NotesTest(unittest.TestCase):
    def test_notes(self):
        ok = c.normalise_notes_change({"notes": [{"id": "a:2", "rating": "bad", "note": " too long ", "asked": "q", "answer": "a"}, {"id": "a:3", "rating": "weird", "note": "hm"}]})
        self.assertEqual([(n["rating"], n["note"]) for n in ok["notes"]], [("bad", "too long"), (None, "hm")])
        for bad in (None, {}, {"notes": []}, {"notes": [{"rating": "bad"}]}, {"notes": [{"id": "x"}]}):
            with self.assertRaises(ValueError):
                c.normalise_notes_change(bad)


if __name__ == "__main__":
    unittest.main()
