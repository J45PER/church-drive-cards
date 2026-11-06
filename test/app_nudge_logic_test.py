import importlib.util
import pathlib
import unittest
from datetime import datetime, timedelta

base = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"
spec = importlib.util.spec_from_file_location("app_nudge_logic", base / "app_nudge_logic.py")
n = importlib.util.module_from_spec(spec)
spec.loader.exec_module(n)

NOW = datetime(2026, 11, 6, 12, 0)


class VersionTest(unittest.TestCase):
    def test_the_version_is_read_from_what_the_app_registers(self):
        self.assertEqual(n.parse_version("0.98.0 (123)"), (0, 98, 0))
        self.assertEqual(n.parse_version("0.98.0"), (0, 98, 0))
        self.assertEqual(n.parse_version("v1.2.3"), (1, 2, 3))

    def test_nothing_readable_is_no_version(self):
        for bad in (None, "", "build 42", "unknown", "1.2"):
            self.assertIsNone(n.parse_version(bad))

    def test_older_means_a_lower_version_not_a_lower_build(self):
        self.assertTrue(n.is_older("0.98.0 (200)", "0.99.0"))
        self.assertTrue(n.is_older("0.9.0", "0.10.0"), "compared as numbers, not text")
        self.assertFalse(n.is_older("0.99.0 (1)", "0.99.0"))
        self.assertFalse(n.is_older("1.0.0", "0.99.0"))

    def test_an_unreadable_version_is_never_older(self):
        self.assertFalse(n.is_older(None, "0.99.0"))
        self.assertFalse(n.is_older("0.98.0", None))


class NudgeTest(unittest.TestCase):
    def test_a_phone_never_told_is_told_in_the_day(self):
        self.assertTrue(n.should_nudge("0.98.0", "0.99.0", None, NOW, 12))

    def test_not_at_night(self):
        self.assertFalse(n.should_nudge("0.98.0", "0.99.0", None, NOW, 8))
        self.assertTrue(n.should_nudge("0.98.0", "0.99.0", None, NOW, 9))
        self.assertTrue(n.should_nudge("0.98.0", "0.99.0", None, NOW, 20))
        self.assertFalse(n.should_nudge("0.98.0", "0.99.0", None, NOW, 21))

    def test_up_to_date_is_never_told(self):
        self.assertFalse(n.should_nudge("0.99.0 (5)", "0.99.0", None, NOW, 12))

    def test_told_once_then_again_after_three_days(self):
        last = {"target": "0.99.0", "at": (NOW - timedelta(days=1)).isoformat()}
        self.assertFalse(n.should_nudge("0.98.0", "0.99.0", last, NOW, 12))
        last = {"target": "0.99.0", "at": (NOW - timedelta(days=3, minutes=1)).isoformat()}
        self.assertTrue(n.should_nudge("0.98.0", "0.99.0", last, NOW, 12))

    def test_a_newer_version_starts_again(self):
        last = {"target": "0.99.0", "at": NOW.isoformat()}
        self.assertTrue(n.should_nudge("0.98.0", "0.100.0", last, NOW, 12))

    def test_a_damaged_record_does_not_stop_the_nudge(self):
        self.assertTrue(n.should_nudge("0.98.0", "0.99.0", {"target": "0.99.0", "at": "nonsense"}, NOW, 12))


class PhonesTest(unittest.TestCase):
    def phone(self, **kw):
        return {"own_app": True, "on": True, "version": "0.98.0 (1)", **kw}

    def test_the_first_phone_that_needs_it_is_named(self):
        phones = [self.phone(version="0.99.0 (3)"), self.phone(version="0.97.0 (9)")]
        self.assertEqual(n.oldest_running(phones, "0.99.0"), "0.97.0 (9)")

    def test_the_home_assistant_app_and_phones_with_notices_off_are_left_alone(self):
        self.assertIsNone(n.oldest_running([self.phone(own_app=False)], "0.99.0"))
        self.assertIsNone(n.oldest_running([self.phone(on=False)], "0.99.0"))
        self.assertIsNone(n.oldest_running([], "0.99.0"))


if __name__ == "__main__":
    unittest.main()
