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


class Resolve(unittest.TestCase):
    KNOWN = {"cover.roller_blind": "Hayley's Bedroom Blind", "cover.living_room_blind": "Living Room Blind", "light.landing": "Landing light"}

    def test_slug(self):
        self.assertEqual(rl.slug("Hayley's Bedroom Blind"), "hayleys_bedroom_blind")
        self.assertEqual(rl.slug("Hayley\u2019s Room"), "hayleys_room")

    def test_a_real_id_is_kept(self):
        self.assertEqual(rl.resolve_entity("cover.roller_blind", self.KNOWN), ("cover.roller_blind", []))

    def test_a_guessed_id_becomes_the_real_one(self):
        self.assertEqual(rl.resolve_entity("cover.hayleys_bedroom_blind", self.KNOWN), ("cover.roller_blind", []))

    def test_an_unknown_one_gets_suggestions(self):
        real, near = rl.resolve_entity("cover.kitchen_blind", self.KNOWN)
        self.assertIsNone(real)
        self.assertEqual(near, ["Hayley's Bedroom Blind", "Living Room Blind"])

    def test_the_kind_must_match(self):
        self.assertIsNone(rl.resolve_entity("light.hayleys_bedroom_blind", self.KNOWN)[0])



class Sun(unittest.TestCase):
    @staticmethod
    def sun(day, event):
        # A sunset at 15:58 and a sunrise at 07:43 (UTC) on any day.
        return datetime(day.year, day.month, day.day, 15, 58, tzinfo=timezone.utc) if event == "sunset" else datetime(day.year, day.month, day.day, 7, 43, tzinfo=timezone.utc)

    def routine(self, **when):
        return rl.clean_routine({"entities": "light.front", "action": "theme", "value": "dusk", "when": {"kind": "repeat", **when}}, NOW)

    def test_a_routine_can_follow_the_sun(self):
        r = self.routine(sun="sunset", offset=-30)
        self.assertEqual(r["when"], {"kind": "repeat", "sun": "sunset", "offset": -30, "days": [0, 1, 2, 3, 4, 5, 6]})
        due = rl.occurrences(r, NOW.replace(hour=0, minute=0), NOW.replace(hour=23, minute=59), self.sun)
        self.assertEqual([d.strftime("%H:%M") for d in due], ["15:28"])
        self.assertEqual(rl.next_run(r, NOW.replace(hour=16), self.sun).strftime("%d %H:%M"), "21 15:28")

    def test_without_the_sun_it_is_never_due_and_a_bad_offset_is_clamped(self):
        r = self.routine(sun="sunrise", offset=9999)
        self.assertEqual(r["when"]["offset"], rl.MAX_SUN_OFFSET)
        self.assertEqual(rl.occurrences(r, NOW - timedelta(days=2), NOW + timedelta(days=2)), [])

    def test_how_it_reads(self):
        self.assertEqual(rl.describe_when(self.routine(sun="sunset")["when"], NOW), "Every day at sunset")
        self.assertEqual(rl.describe_when(self.routine(sun="sunset", offset=-30)["when"], NOW), "Every day 30 min before sunset")
        self.assertEqual(rl.describe_when(self.routine(sun="sunrise", offset=15, days=[5, 6])["when"], NOW), "Weekends 15 min after sunrise")

    def test_a_light_can_play_a_scene_or_a_theme(self):
        scene = rl.clean_routine({"entities": "light.front", "action": "scene", "value": "soho", "when": {"kind": "repeat", "time": "18:00"}}, NOW)
        self.assertEqual(rl.call_for("light.front", scene["action"], scene["value"]), ("church_drive", "apply_scene", {"entity_id": "light.front", "scene": "soho"}))
        theme = self.routine(sun="sunset")
        self.assertEqual(rl.call_for("light.front", theme["action"], theme["value"]), ("church_drive", "apply_hybrid", {"id": "dusk"}))
        with self.assertRaises(ValueError):
            rl.clean_routine({"entities": "light.front", "action": "theme", "when": {"kind": "repeat", "time": "18:00"}}, NOW)
        with self.assertRaises(ValueError):
            rl.clean_routine({"entities": "switch.porch", "action": "scene", "value": "soho", "when": {"kind": "repeat", "time": "18:00"}}, NOW)


if __name__ == "__main__":
    unittest.main()
