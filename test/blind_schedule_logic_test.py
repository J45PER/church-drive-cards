import importlib.util
import pathlib
import unittest
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location(
    "blind_schedule_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "blind_schedule_logic.py"
)
bs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bs)

WEEKDAYS = {"id": "wk", "days": [0, 1, 2, 3, 4], "open": "07:30", "close": "20:00"}
WEEKEND = {"id": "we", "days": [5, 6], "open": "09:00", "close": "21:30"}


def at(day, hh, mm, ss=0):
    # 2026-11-02 is a Monday
    return datetime(2026, 11, 2 + day, hh, mm, ss, tzinfo=timezone.utc)


class Text(unittest.TestCase):
    def test_days_text(self):
        self.assertEqual(bs.days_text([0, 1, 2, 3, 4]), "Mon-Fri")
        self.assertEqual(bs.days_text([5, 6]), "Sat & Sun")
        self.assertEqual(bs.days_text([0, 3, 5]), "Mon, Thu & Sat")
        self.assertEqual(bs.days_text(list(range(7))), "Every day")
        self.assertEqual(bs.days_text([]), "No days")

    def test_summary(self):
        self.assertEqual(bs.summary(WEEKDAYS), "Mon-Fri Open 07:30 · Close 20:00")
        self.assertEqual(bs.summary({"days": [5, 6], "open": "09:00"}), "Sat & Sun Open 09:00")
        self.assertEqual(bs.summary({"days": [0, 1, 2, 3, 4, 5, 6], "close": "21:00"}), "Every day Close 21:00")


class Cleaning(unittest.TestCase):
    def test_times(self):
        self.assertEqual(bs.clean_time("7:05"), "07:05")
        self.assertEqual(bs.clean_time("07:05:00"), "07:05")
        self.assertIsNone(bs.clean_time("24:00"))
        self.assertIsNone(bs.clean_time(""))
        self.assertIsNone(bs.clean_time(None))

    def test_a_schedule_needs_days_and_one_time(self):
        out = bs.clean_schedules([
            {"id": "a", "days": [0, 1], "open": "7:30"},
            {"id": "b", "days": [2], "close": "20:00"},
            {"id": "c", "days": [], "open": "07:00"},
            {"id": "d", "days": [3]},
            {"id": "e", "days": [4], "open": "nope", "close": "nope"},
            "rubbish",
        ])
        self.assertEqual(out, [{"id": "a", "days": [0, 1], "open": "07:30"}, {"id": "b", "days": [2], "close": "20:00"}])

    def test_ids_are_made_unique_and_the_list_is_capped(self):
        out = bs.clean_schedules([{"id": "x", "days": [0], "open": "07:00"}, {"id": "x", "days": [1], "open": "07:00"}])
        self.assertEqual([s["id"] for s in out], ["x", "x2"])
        many = bs.clean_schedules([{"days": [0], "open": "07:00"}] * 20)
        self.assertEqual(len(many), bs.MAX_SCHEDULES)

    def test_a_blind_keeps_what_isnt_sent_and_an_empty_list_clears(self):
        cur = {"enabled": True, "schedules": [dict(WEEKDAYS)]}
        self.assertEqual(bs.clean_blind({"enabled": False}, cur), {"enabled": False, "schedules": [WEEKDAYS]})
        self.assertEqual(bs.clean_blind({"schedules": []}, cur), {"enabled": True, "schedules": []})
        self.assertEqual(bs.clean_blind("rubbish", cur), cur)

    def test_only_covers_are_kept(self):
        cfg = bs.clean_config({"blinds": {"cover.roller_blind": {"schedules": [WEEKDAYS]}, "light.kitchen": {"schedules": [WEEKDAYS]}}})
        self.assertEqual(list(cfg), ["cover.roller_blind"])
        self.assertTrue(cfg["cover.roller_blind"]["enabled"])
        self.assertEqual(bs.clean_config(None), {})


class Firing(unittest.TestCase):
    def test_events_at(self):
        self.assertEqual(bs.events_at([WEEKDAYS, WEEKEND], 0, "07:30"), ["open"])
        self.assertEqual(bs.events_at([WEEKDAYS, WEEKEND], 0, "20:00"), ["close"])
        self.assertEqual(bs.events_at([WEEKDAYS, WEEKEND], 5, "07:30"), [])
        self.assertEqual(bs.events_at([WEEKDAYS, WEEKEND], 5, "09:00"), ["open"])

    def test_close_wins_when_both_are_due(self):
        a = {"days": [0], "open": "07:00"}
        b = {"days": [0], "close": "07:00"}
        self.assertEqual(bs.action_for(bs.events_at([a, b], 0, "07:00")), "close")
        self.assertIsNone(bs.action_for([]))

    def test_due_between_takes_the_latest(self):
        self.assertEqual(bs.due_between([WEEKDAYS], at(0, 7, 0), at(0, 8, 0)), "open")
        self.assertIsNone(bs.due_between([WEEKDAYS], at(0, 8, 0), at(0, 9, 0)))
        self.assertEqual(bs.due_between([{"days": [0], "open": "07:00", "close": "07:10"}], at(0, 6, 55), at(0, 7, 20)), "close")
        self.assertIsNone(bs.due_between([WEEKDAYS], at(0, 8, 0), at(0, 7, 0)))

    def test_next_change(self):
        n = bs.next_change([WEEKDAYS, WEEKEND], at(0, 12, 0))
        self.assertEqual((n["action"], n["text"]), ("close", "Close Mon 20:00"))
        n = bs.next_change([WEEKDAYS, WEEKEND], at(4, 21, 0))  # Friday night
        self.assertEqual(n["text"], "Open Sat 09:00")
        self.assertIsNone(bs.next_change([], at(0, 12, 0)))
        self.assertEqual(bs.next_change([{"days": [2], "close": "18:00"}], at(3, 12, 0))["text"], "Close Wed 18:00")


if __name__ == "__main__":
    unittest.main()
