import importlib.util
import pathlib
import unittest
from datetime import datetime, timedelta, timezone

spec = importlib.util.spec_from_file_location(
    "routines_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "routines_logic.py"
)
rl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rl)

NOW = datetime(2026, 11, 20, 20, 30, tzinfo=timezone.utc)  # a Friday
BLIND = {"entities": "cover.hayleys_bedroom_blind", "action": "open", "when": {"kind": "once", "at": "2026-11-21T08:15"}}


class Clean(unittest.TestCase):
    def test_a_one_off(self):
        r = rl.clean_routine(BLIND, NOW)
        self.assertEqual(r["entities"], ["cover.hayleys_bedroom_blind"])
        self.assertEqual(r["name"], "Open hayleys bedroom blind")
        self.assertEqual(r["when"], {"kind": "once", "at": "2026-11-21T08:15"})
        self.assertTrue(r["enabled"])

    def test_a_repeat_with_no_days_is_every_day(self):
        r = rl.clean_routine({"entities": ["light.landing"], "action": "off", "when": {"kind": "repeat", "time": "23:00"}}, NOW)
        self.assertEqual(r["when"]["days"], list(range(7)))

    def test_things_that_cant_be_scheduled(self):
        for entity, action in (("lock.front_door", "off"), ("alarm_control_panel.house", "on"), ("cover.b", "on"), ("light.a", "open")):
            with self.assertRaises(ValueError):
                rl.clean_routine({"entities": entity, "action": action, "when": BLIND["when"]}, NOW)

    def test_set_needs_a_number(self):
        with self.assertRaises(ValueError):
            rl.clean_routine({"entities": "light.a", "action": "set", "value": "bright", "when": BLIND["when"]}, NOW)
        r = rl.clean_routine({"entities": "light.a", "action": "set", "value": "40", "when": BLIND["when"]}, NOW)
        self.assertEqual(r["value"], 40)

    def test_when_is_needed(self):
        with self.assertRaises(ValueError):
            rl.clean_routine({"entities": "cover.b", "action": "open"}, NOW)


class Calls(unittest.TestCase):
    def test_services(self):
        self.assertEqual(rl.call_for("cover.b", "open", None), ("cover", "open_cover", {"entity_id": "cover.b"}))
        self.assertEqual(rl.call_for("cover.b", "set", 30)[2]["position"], 30)
        self.assertEqual(rl.call_for("light.a", "set", 40)[2]["brightness_pct"], 40)
        self.assertEqual(rl.call_for("climate.h", "set", 19.5)[1:], ("set_temperature", {"entity_id": "climate.h", "temperature": 19.5}))
        self.assertEqual(rl.call_for("switch.s", "off", None)[:2], ("switch", "turn_off"))


class Times(unittest.TestCase):
    def test_one_off_is_due_once(self):
        r = rl.clean_routine(BLIND, NOW)
        at = datetime(2026, 11, 21, 8, 15, tzinfo=timezone.utc)
        self.assertEqual(rl.occurrences(r, at - timedelta(minutes=1), at), [at])
        self.assertEqual(rl.occurrences(r, at, at + timedelta(minutes=1)), [])
        r["done"] = "x"
        self.assertEqual(rl.occurrences(r, at - timedelta(minutes=1), at), [])
        self.assertIsNone(rl.next_run(r, NOW))

    def test_weekday_repeat(self):
        r = rl.clean_routine({"entities": "light.a", "action": "on", "when": {"kind": "repeat", "time": "07:00", "days": [0, 1, 2, 3, 4]}}, NOW)
        # Friday evening: next is Monday 07:00
        self.assertEqual(rl.next_run(r, NOW), datetime(2026, 11, 23, 7, 0, tzinfo=timezone.utc))
        self.assertEqual(len(rl.occurrences(r, NOW, NOW + timedelta(days=7))), 5)

    def test_words(self):
        r = rl.clean_routine(BLIND, NOW)
        self.assertEqual(rl.describe_when(r["when"], NOW), "Tomorrow at 08:15")
        self.assertEqual(rl.describe_days([0, 1, 2, 3, 4]), "Mon-Fri")
        self.assertEqual(rl.describe_days([5, 6]), "Weekends")
        self.assertEqual(rl.describe_days(list(range(7))), "Every day")


if __name__ == "__main__":
    unittest.main()
