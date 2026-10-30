import importlib.util
import pathlib
import unittest
from datetime import timezone, timedelta
from zoneinfo import ZoneInfo

spec = importlib.util.spec_from_file_location(
    "activity_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "activity_logic.py"
)
al = importlib.util.module_from_spec(spec)
spec.loader.exec_module(al)

LONDON = ZoneInfo("Europe/London")
SRC = (pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "activity.py").read_text()
INIT = (pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "__init__.py").read_text()


def at(day, hh, mm=0):
    """Epoch seconds for 2026-10-<day> hh:mm in London (BST ended on the 25th, so both offsets get used)."""
    from datetime import datetime

    return datetime(2026, 10, day, hh, mm, tzinfo=LONDON).timestamp()


def entry(ts, kind="user", who="Jamie", text="turned on Kitchen", **kw):
    return al.make_entry(ts, kind, who, text, "mdi:lightbulb", **kw)


class DescribeTest(unittest.TestCase):
    names = {"light.kitchen": "Kitchen", "climate.downstairs": "Downstairs", "light.hall": "Hall", "cover.lounge": "Lounge blind"}

    def test_lights(self):
        self.assertEqual(al.describe("light", "turn_on", {"entity_id": "light.kitchen"}, self.names), "turned on Kitchen")
        self.assertEqual(al.describe("light", "turn_on", {"entity_id": ["light.kitchen", "light.hall"], "brightness_pct": 40}, self.names), "turned on Kitchen and Hall at 40%")
        self.assertEqual(al.describe("light", "turn_on", {"entity_id": "light.kitchen", "brightness": 128}, self.names), "turned on Kitchen at 50%")
        self.assertEqual(al.describe("light", "turn_off", {"target": {"entity_id": "light.hall"}}, self.names), "turned off Hall")

    def test_heating_blinds_alarm(self):
        self.assertEqual(al.describe("climate", "set_temperature", {"entity_id": "climate.downstairs", "temperature": 21.5}, self.names), "set Downstairs to 21.5°")
        self.assertEqual(al.describe("climate", "set_hvac_mode", {"entity_id": "climate.downstairs", "hvac_mode": "heat_cool"}, self.names), "set Downstairs to heat cool")
        self.assertEqual(al.describe("cover", "set_cover_position", {"entity_id": "cover.lounge", "position": 30}, self.names), "moved Lounge blind to 30%")
        self.assertEqual(al.describe("alarm_control_panel", "alarm_arm_away", {"entity_id": "alarm_control_panel.ring"}, {"alarm_control_panel.ring": "Alarm"}), "armed (away) Alarm")

    def test_unknown_names_fall_back_to_the_entity_id_and_unknown_services_say_so(self):
        self.assertEqual(al.describe("light", "turn_off", {"entity_id": "light.back_porch"}, {}), "turned off Back Porch")
        self.assertEqual(al.describe("light", "frobnicate", {"entity_id": "light.hall"}, self.names), "ran light.frobnicate on Hall")

    def test_many_things_are_shortened(self):
        ids = [f"light.l{i}" for i in range(6)]
        text = al.describe("light", "turn_off", {"entity_id": ids}, {i: i[-2:] for i in ids})
        self.assertEqual(text, "turned off l0, l1 and 4 more")

    def test_areas_are_used_when_there_are_no_entities(self):
        self.assertEqual(al.describe("light", "turn_off", {}, {}, ["Kitchen"]), "turned off Kitchen")

    def test_scripts(self):
        self.assertEqual(al.describe("script", "goodnight", {"entity_id": ["script.goodnight"]}, {"script.goodnight": "Goodnight"}), "ran Goodnight")


class WhatIsNotedTest(unittest.TestCase):
    def test_noted(self):
        self.assertTrue(al.noted("light", "turn_on"))
        self.assertTrue(al.noted("alarm_control_panel", "alarm_disarm"))
        self.assertFalse(al.noted("media_player", "volume_set"))
        self.assertFalse(al.noted("homeassistant", "update_entity"))
        self.assertFalse(al.noted("notify", "mobile_app_x"))
        self.assertTrue(al.noted("automation", "trigger"))
        self.assertFalse(al.noted("automation", "reload"))
        self.assertFalse(al.noted("number", "increment"))

    def test_the_assistants_scripts(self):
        self.assertTrue(al.is_clive("script.clive_arm_alarm"))
        self.assertTrue(al.is_clive("automation.clive_keep_the_climate_comfortable"))
        self.assertFalse(al.is_clive("script.goodnight"))
        self.assertFalse(al.is_clive("light.clive_lamp"))
        self.assertFalse(al.is_clive("script.cliven_thing"))
        self.assertTrue(al.clive_is_quiet("script.clive_zappi_info"))
        self.assertTrue(al.clive_is_quiet("script.clive_show_card"))
        self.assertFalse(al.clive_is_quiet("script.clive_arm_alarm"))
        self.assertFalse(al.clive_is_quiet("script.clive_zappi_charge"))


class PresenceTest(unittest.TestCase):
    def test_coming_and_going(self):
        self.assertEqual(al.presence_text("not_home", "home"), "arrived home")
        self.assertEqual(al.presence_text("home", "not_home"), "left home")
        self.assertEqual(al.presence_text("not_home", "Work"), "arrived at Work")
        self.assertEqual(al.presence_text("Work", "not_home"), "left Work")
        self.assertEqual(al.presence_text("Work", "home"), "left Work and arrived home")
        self.assertEqual(al.presence_text("home", "Work"), "left home for Work")
        self.assertEqual(al.presence_text("Work", "Gym"), "left Work for Gym")

    def test_nothing_happened(self):
        for old, new in [("unknown", "home"), ("home", "unavailable"), (None, "home"), ("home", "home"), ("", "not_home")]:
            self.assertEqual(al.presence_text(old, new), "", (old, new))


class KeepingTest(unittest.TestCase):
    def test_a_slider_drag_is_one_line(self):
        log = []
        for i, pct in enumerate((10, 20, 30, 40)):
            al.add(log, entry(1000 + i, entity="light.kitchen", text=f"turned on Kitchen at {pct}%"))
        self.assertEqual([e["text"] for e in log], ["turned on Kitchen at 40%"])

    def test_different_people_things_or_times_are_kept_apart(self):
        log = []
        al.add(log, entry(1000, entity="light.kitchen"))
        al.add(log, entry(1002, entity="light.kitchen", who="Hayley"))
        al.add(log, entry(1003, entity="light.hall"))
        al.add(log, entry(1003 + al.COALESCE_S + 1, entity="light.hall"))
        al.add(log, entry(1004, kind="clive", who="", entity="light.hall"))
        self.assertEqual(len(log), 5)

    def test_no_entity_never_merges(self):
        log = []
        al.add(log, entry(1000))
        al.add(log, entry(1001))
        self.assertEqual(len(log), 2)

    def test_prune_by_age_and_count(self):
        now = 100 * 86400
        old = entry(now - (al.KEEP_DAYS + 1) * 86400)
        new = entry(now - 3600)
        self.assertEqual(al.prune([old, new], now), [new])
        many = [entry(now - 100 + i / 10) for i in range(al.KEEP_MAX + 50)]
        kept = al.prune(many, now)
        self.assertEqual(len(kept), al.KEEP_MAX)
        self.assertEqual(kept[-1], many[-1])

    def test_a_damaged_store_is_cleaned_not_fatal(self):
        raw = [{"ts": "5", "kind": "user", "text": "ok"}, {"ts": "x", "kind": "user", "text": "bad"}, {"kind": "nope", "text": "x", "ts": 1}, 7, None, {"ts": 1, "kind": "user", "text": 3}]
        self.assertEqual([e["text"] for e in al.clean(raw)], ["ok"])
        self.assertEqual(al.clean(None), [])
        self.assertEqual(al.clean({"a": 1}), [])


class SelectTest(unittest.TestCase):
    def setUp(self):
        self.log = [
            entry(at(26, 7, 30), who="Hayley", text="turned on Kitchen"),
            entry(at(26, 22, 45), kind="presence", who="Jamie", text="arrived home"),
            entry(at(27, 2, 10), kind="clive", who="", text="turned off Downstairs", by="Hayley"),
            entry(at(27, 3, 0), kind="clive_auto", who="", text="turned on Downstairs", rule="take over again when a pause ends"),
            entry(at(27, 9, 0), who="Jamie", text="armed (away) Alarm"),
            entry(at(28, 18, 0), kind="presence", who="Ian", text="left home"),
        ]

    def texts(self, **kw):
        return [e["text"] for e in al.select(self.log, tz=LONDON, **kw)["entries"]]

    def test_newest_first_and_total(self):
        r = al.select(self.log, tz=LONDON)
        self.assertEqual(r["total"], 6)
        self.assertEqual(r["entries"][0]["text"], "left home")
        self.assertEqual(r["entries"][-1]["text"], "turned on Kitchen")

    def test_days(self):
        self.assertEqual(self.texts(since=at(27, 0), until=at(28, 0)), ["armed (away) Alarm", "turned on Downstairs", "turned off Downstairs"])
        self.assertEqual(self.texts(since=at(28, 0)), ["left home"])

    def test_until_is_exclusive(self):
        self.assertEqual(self.texts(since=at(27, 9, 0), until=at(27, 9, 0)), [])

    def test_kinds(self):
        self.assertEqual(self.texts(kinds=["presence"]), ["left home", "arrived home"])
        self.assertEqual(self.texts(kinds=["clive"]), ["turned off Downstairs"])  # asked of him
        self.assertEqual(self.texts(kinds=["clive_auto"]), ["turned on Downstairs"])  # run by an automation
        self.assertEqual(self.texts(kinds=["clive", "clive_auto"]), ["turned on Downstairs", "turned off Downstairs"])
        self.assertEqual(len(self.texts(kinds=["nonsense"])), 6)  # unknown kinds mean "no choice made"
        self.assertEqual(len(self.texts(kinds=[])), 6)

    def test_who_matches_the_person_or_who_the_assistant_did_it_for(self):
        self.assertEqual(self.texts(who="hayley"), ["turned off Downstairs", "turned on Kitchen"])  # not what an automation did
        self.assertEqual(self.texts(who="Ian"), ["left home"])

    def test_time_of_day_window(self):
        self.assertEqual(self.texts(start="07:00", end="10:00"), ["armed (away) Alarm", "turned on Kitchen"])
        self.assertEqual(self.texts(start="09:00", end="09:01"), ["armed (away) Alarm"])  # start inclusive
        self.assertEqual(self.texts(start="07:00", end="07:30"), [])  # end exclusive

    def test_a_window_over_midnight(self):
        self.assertEqual(self.texts(start="22:00", end="06:00"), ["turned on Downstairs", "turned off Downstairs", "arrived home"])

    def test_open_ended_windows(self):
        self.assertEqual(self.texts(start="18:00"), ["left home", "arrived home"])
        self.assertEqual(self.texts(end="03:00"), ["turned off Downstairs"])  # end is exclusive

    def test_bad_times_are_ignored(self):
        self.assertEqual(len(self.texts(start="soon", end="25:99")), 6)

    def test_window_uses_the_houses_clock_across_the_clock_change(self):
        # 2026-10-25 01:30 GMT is 02:30 BST-less; the same wall time either side of the change is the same window.
        a, b = entry(at(24, 8, 15)), entry(at(26, 8, 15))
        r = al.select([a, b], start="08:00", end="09:00", tz=LONDON)
        self.assertEqual(r["total"], 2)
        r = al.select([a, b], start="08:00", end="09:00", tz=timezone(timedelta(hours=0)))
        self.assertEqual(r["total"], 1)  # in UTC the first one (BST) is 07:15

    def test_limit_and_offset(self):
        log = [entry(1000 + i, text=str(i)) for i in range(10)]
        r = al.select(log, limit=3)
        self.assertEqual([e["text"] for e in r["entries"]], ["9", "8", "7"])
        self.assertEqual(r["total"], 10)
        r = al.select(log, limit=3, offset=9)
        self.assertEqual([e["text"] for e in r["entries"]], ["0"])
        self.assertEqual(len(al.select(log, limit=10**6)["entries"]), 10)
        self.assertEqual(len(al.select(log, limit=0)["entries"]), 10)

    def test_people_list(self):
        self.assertEqual(al.who_list(self.log), ["Hayley", "Ian", "Jamie"])


class AutoOrAskedTest(unittest.TestCase):
    def test_rule_names_lose_the_assistants_prefix(self):
        self.assertEqual(al.rule_name("Clive: keep the climate comfortable"), "keep the climate comfortable")
        self.assertEqual(al.rule_name("clive : take over again"), "take over again")
        self.assertEqual(al.rule_name("Security alerts: night"), "Security alerts: night")
        self.assertEqual(al.rule_name(""), "")

    def test_the_rule_is_kept_and_survives_storage(self):
        e = al.make_entry(5, "clive_auto", "", "turned off Downstairs", "mdi:thermostat", rule="keep the climate comfortable")
        self.assertEqual(al.clean([e]), [e])
        self.assertNotIn("rule", al.make_entry(5, "clive", "", "x", "mdi:home", by="Hayley"))

    def test_asked_and_automatic_are_not_merged(self):
        log = []
        al.add(log, al.make_entry(1000, "clive", "", "x", "mdi:thermostat", entity="climate.a", by="Hayley"))
        al.add(log, al.make_entry(1001, "clive_auto", "", "x", "mdi:thermostat", entity="climate.a", rule="r"))
        al.add(log, al.make_entry(1002, "clive_auto", "", "y", "mdi:thermostat", entity="climate.a", rule="other"))
        self.assertEqual(len(log), 3)

    def test_the_people_list_has_who_he_did_things_for_but_never_the_assistant(self):
        log = [al.make_entry(1, "clive", "", "x", "i", by="Ian"), al.make_entry(2, "clive_auto", "", "y", "i", rule="r")]
        self.assertEqual(al.who_list(log), ["Ian"])


class WiringTest(unittest.TestCase):
    # activity.py needs Home Assistant, so it can't be imported here; these check what broke before elsewhere.
    def test_event_filters_are_marked_callbacks(self):
        self.assertNotIn("event_filter=lambda", SRC)
        self.assertRegex(SRC, r"@staticmethod\n    @callback\n    def _is_person")
        self.assertRegex(SRC, r"@staticmethod\n    @callback\n    def _is_script_run")

    def test_the_command_is_for_administrators_only(self):
        start = INIT.index("async def ws_activity")
        before = INIT[max(0, start - 200):start]
        self.assertIn("@websocket_api.require_admin", before)

    def test_a_failure_to_start_cannot_stop_notifications_starting(self):
        start = INIT.index("activity = Activity(")
        self.assertIn("try:", INIT[max(0, start - 200):start])
        self.assertRegex(INIT[start:start + 500], r"except Exception")

    def test_the_automations_runs_are_followed_to_tell_automatic_from_asked(self):
        self.assertIn('"automation_triggered"', SRC)
        self.assertIn('"clive_auto" if followed[2] else "clive"', SRC)

    def test_user_logins_are_never_sent_out(self):
        self.assertIn('if k != "user_id"', SRC)


if __name__ == "__main__":
    unittest.main()
