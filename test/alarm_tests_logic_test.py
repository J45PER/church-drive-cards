import importlib.util
import pathlib
import unittest
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location(
    "alarm_tests_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "alarm_tests_logic.py"
)
at = importlib.util.module_from_spec(spec)
spec.loader.exec_module(at)

ON = {"enabled": True, "day": 1, "time": "11:00"}


def t(y, m, d, hh, mm):
    return datetime(y, m, d, hh, mm, tzinfo=timezone.utc)


class Cleaning(unittest.TestCase):
    def test_every_test_is_there_and_off_until_set(self):
        cfg = at.clean_config(None)
        self.assertEqual(sorted(cfg), ["alarm", "co", "smoke"])
        self.assertFalse(cfg["alarm"]["enabled"])

    def test_only_valid_values_are_taken(self):
        cfg = at.clean_test({"enabled": True, "day": 40, "time": "9:5"}, {"enabled": False, "day": 3, "time": "10:00"})
        self.assertEqual(cfg, {"enabled": True, "day": 3, "time": "10:00"})
        self.assertEqual(at.clean_test({"day": "15", "time": "7:30"})["day"], 15)
        self.assertEqual(at.clean_test({"time": "7:30"})["time"], "07:30")


class Timing(unittest.TestCase):
    def test_the_next_run_is_this_month_or_next(self):
        self.assertEqual(at.next_run(ON, t(2026, 11, 17, 9, 0)), t(2026, 12, 1, 11, 0))
        self.assertEqual(at.next_run({**ON, "day": 20}, t(2026, 11, 17, 9, 0)), t(2026, 11, 20, 11, 0))
        self.assertIsNone(at.next_run({**ON, "enabled": False}, t(2026, 11, 17, 9, 0)))

    def test_a_day_the_month_lacks_is_its_last_day(self):
        self.assertEqual(at.next_run({**ON, "day": 31}, t(2026, 11, 1, 9, 0)), t(2026, 11, 30, 11, 0))
        self.assertEqual(at.next_run({**ON, "day": 30}, t(2027, 2, 1, 9, 0)), t(2027, 2, 28, 11, 0))

    def test_warnings_come_15_and_5_minutes_before(self):
        self.assertEqual(at.due(ON, t(2026, 12, 1, 10, 45)), "warn15")
        self.assertEqual(at.due(ON, t(2026, 12, 1, 10, 55)), "warn5")
        self.assertEqual(at.due(ON, t(2026, 12, 1, 11, 0)), "run")
        self.assertIsNone(at.due(ON, t(2026, 12, 1, 10, 50)))
        self.assertIsNone(at.due({**ON, "enabled": False}, t(2026, 12, 1, 11, 0)))

    def test_a_warning_before_midnight_on_the_1st(self):
        early = {**ON, "time": "00:05"}
        self.assertEqual(at.due(early, t(2026, 11, 30, 23, 50)), "warn15")


class Words(unittest.TestCase):
    def test_summary_and_ordinals(self):
        self.assertEqual(at.summary(ON), "The 1st of each month at 11:00")
        self.assertEqual(at.summary({**ON, "day": 22}), "The 22nd of each month at 11:00")
        self.assertEqual(at.ordinal(13), "13th")
        self.assertEqual(at.summary({**ON, "enabled": False}), "Off")

    def test_warning_text(self):
        title, message = at.warning("smoke", 15)
        self.assertEqual(title, "Smoke alarm test in 15 minutes")
        self.assertIn("only a test", message)
