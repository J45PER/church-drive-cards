import importlib.util
import pathlib
import unittest
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location(
    "purifier_schedule_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "purifier_schedule_logic.py"
)
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)

WEEKDAYS = {"id": "wk", "days": [0, 1, 2, 3, 4], "auto": "07:00", "sleep": "23:30"}
WEEKEND = {"id": "we", "days": [5, 6], "auto": "09:00", "sleep": "23:59"}


def at(day, hh, mm, ss=0):
    # 2026-11-02 is a Monday
    return datetime(2026, 11, 2 + day, hh, mm, ss, tzinfo=timezone.utc)


class Text(unittest.TestCase):
    def test_days_text(self):
        self.assertEqual(ps.days_text([0, 1, 2, 3, 4]), "Mon-Fri")
        self.assertEqual(ps.days_text([5, 6]), "Sat & Sun")
        self.assertEqual(ps.days_text([0, 3, 5]), "Mon, Thu & Sat")
        self.assertEqual(ps.days_text(list(range(7))), "Every day")
        self.assertEqual(ps.days_text([2]), "Wed")
        self.assertEqual(ps.days_text([0, 1]), "Mon & Tue")
        self.assertEqual(ps.days_text([0, 1, 2, 4]), "Mon-Wed & Fri")
        self.assertEqual(ps.days_text([]), "No days")

    def test_summary(self):
        self.assertEqual(ps.summary(WEEKDAYS), "Mon-Fri Auto: 07:00-23:30")
        self.assertEqual(ps.summary({"days": [0, 3, 5], "auto": "08:00", "sleep": "22:00"}), "Mon, Thu & Sat Auto: 08:00-22:00")


class Cleaning(unittest.TestCase):
    def test_times(self):
        self.assertEqual(ps.clean_time("7:05"), "07:05")
        self.assertEqual(ps.clean_time("07:05:00"), "07:05")
        self.assertIsNone(ps.clean_time("24:00"))
        self.assertIsNone(ps.clean_time("noon"))
        self.assertIsNone(ps.clean_time(None))

    def test_days(self):
        self.assertEqual(ps.clean_days([4, "2", 2, 9, -1, "x", None]), [2, 4])
        self.assertEqual(ps.clean_days("mon"), [])

    def test_bad_schedules_are_dropped(self):
        got = ps.clean_schedules([WEEKDAYS, {"days": [], "auto": "07:00", "sleep": "22:00"}, {"days": [1], "auto": "x", "sleep": "22:00"}, "rubbish"])
        self.assertEqual([s["id"] for s in got], ["wk"])

    def test_ids_are_made_unique_and_safe(self):
        got = ps.clean_schedules([{**WEEKDAYS, "id": "A b!"}, {**WEEKEND, "id": "ab"}, {**WEEKEND, "id": ""}, {**WEEKEND, "id": ""}])
        self.assertEqual([s["id"] for s in got], ["ab", "ab2", "s", "s2"])

    def test_at_most_twelve(self):
        many = [{"id": f"s{i}", "days": [0], "auto": "07:00", "sleep": "22:00"} for i in range(20)]
        self.assertEqual(len(ps.clean_schedules(many)), ps.MAX_SCHEDULES)

    def test_config_keeps_old_values_for_bad_input(self):
        current = ps.clean_config({"schedules": [WEEKDAYS]})
        self.assertEqual(ps.clean_config("rubbish", current), current)
        self.assertEqual(ps.clean_config({"entity": "light.x"}, current)["entity"], ps.DEFAULT_ENTITY)
        self.assertEqual(ps.clean_config({"enabled": False}, current)["schedules"], current["schedules"])

    def test_an_empty_list_clears_the_schedules(self):
        current = ps.clean_config({"schedules": [WEEKDAYS]})
        self.assertEqual(ps.clean_config({"schedules": []}, current)["schedules"], [])

    def test_default_has_a_schedule(self):
        self.assertTrue(ps.clean_config(None)["schedules"])


class Events(unittest.TestCase):
    def test_fires_on_its_days_only(self):
        both = [WEEKDAYS, WEEKEND]
        self.assertEqual(ps.events_at(both, 0, "07:00"), ["auto"])
        self.assertEqual(ps.events_at(both, 5, "07:00"), [])
        self.assertEqual(ps.events_at(both, 5, "09:00"), ["auto"])
        self.assertEqual(ps.events_at(both, 4, "23:30"), ["sleep"])
        self.assertEqual(ps.events_at(both, 6, "23:30"), [])

    def test_auto_wins_a_clash(self):
        a = {"id": "a", "days": [0], "auto": "22:00", "sleep": "23:00"}
        b = {"id": "b", "days": [0], "auto": "23:00", "sleep": "23:30"}
        self.assertEqual(ps.mode_for(ps.events_at([a, b], 0, "23:00")), "auto")
        self.assertIsNone(ps.mode_for([]))

    def test_catching_up_takes_the_latest_missed_event(self):
        both = [WEEKDAYS, WEEKEND]
        self.assertEqual(ps.due_between(both, at(0, 6, 50), at(0, 7, 5)), "auto")
        self.assertIsNone(ps.due_between(both, at(0, 7, 0), at(0, 7, 5)))  # 07:00 itself was already handled
        self.assertEqual(ps.due_between(both, at(0, 23, 29), at(0, 23, 31)), "sleep")
        self.assertIsNone(ps.due_between(both, at(0, 8, 0), at(0, 8, 0)))

    def test_next_change(self):
        both = [WEEKDAYS, WEEKEND]
        self.assertEqual(ps.next_change(both, at(0, 12, 0))["text"], "Sleep Mon 23:30")
        self.assertEqual(ps.next_change(both, at(4, 23, 45))["text"], "Auto Sat 09:00")
        self.assertEqual(ps.next_change(both, at(0, 7, 0, 30))["text"], "Sleep Mon 23:30")
        self.assertIsNone(ps.next_change([], at(0, 7, 0)))


if __name__ == "__main__":
    unittest.main()
