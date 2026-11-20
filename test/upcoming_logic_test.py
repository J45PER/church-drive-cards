import importlib
import pathlib
import sys
import types
import unittest
from datetime import datetime, timezone

# upcoming_logic imports its sibling, so load the integration's folder as a package without running its __init__.
_pkg = types.ModuleType("cd_pkg")
_pkg.__path__ = [str(pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive")]
sys.modules["cd_pkg"] = _pkg
up = importlib.import_module("cd_pkg.upcoming_logic")
rl = importlib.import_module("cd_pkg.routines_logic")

NOW = datetime(2026, 11, 20, 20, 30, tzinfo=timezone.utc)  # a Friday


def routine(entity, action, when, **extra):
    return rl.clean_routine({"entities": entity, "action": action, "when": when, **extra}, NOW)


def words(entity):
    return ("Open", "Close") if entity.startswith("cover.") else ("On", "Off")


def build(**kw):
    args = dict(routines=[], names={}, blinds={}, words=words, purifier=None, tests=[], overnight=None, tasks=[])
    args.update(kw)
    return up.build(NOW, 7, **args)


class Sources(unittest.TestCase):
    def test_a_clive_one_off_is_a_one_off_you_can_open(self):
        r = routine("cover.roller_blind", "open", {"kind": "once", "at": "2026-11-21T08:15"}, by="Jamie", name="Open Hayley's bedroom blind")
        [item] = build(routines=[r])
        self.assertEqual((item["date"], item["time"], item["kind"], item["src"], item["by"]), ("2026-11-21", "08:15", "blind", "clive", "Jamie"))
        self.assertEqual(item["tap"], {"to": "oneoff", "id": r["id"]})

    def test_a_repeating_routine_appears_each_time_and_opens_the_lights_card(self):
        r = routine("light.landing", "off", {"kind": "repeat", "time": "23:00", "days": [0, 1, 2, 3, 4, 5, 6]}, src="user", by="Diane")
        items = build(routines=[r])
        self.assertEqual([i["date"] for i in items], [f"2026-11-{d}" for d in range(20, 27)])
        self.assertEqual(items[0]["src"], "user")
        self.assertEqual(items[0]["tap"]["to"], "routine")

    def test_a_blind_schedule_gives_open_and_close(self):
        blinds = {"cover.roller_blind": {"enabled": True, "name": "Hayley's Bedroom Blind", "schedules": [{"days": [0, 1, 2, 3, 4], "open": "07:00", "close": "21:00"}, {"days": [5, 6], "open": "09:45", "close": "22:00"}]}}
        items = build(blinds=blinds)
        self.assertEqual([(i["date"], i["time"], i["title"]) for i in items[:3]], [("2026-11-20", "21:00", "Close Hayley's Bedroom Blind"), ("2026-11-21", "09:45", "Open Hayley's Bedroom Blind"), ("2026-11-21", "22:00", "Close Hayley's Bedroom Blind")])
        self.assertEqual(items[0]["src"], "device")
        self.assertEqual(items[0]["tap"], {"to": "device", "entity": "cover.roller_blind"})

    def test_a_switched_off_device_schedule_is_left_out(self):
        blinds = {"cover.b": {"enabled": False, "name": "B", "schedules": [{"days": [0, 1, 2, 3, 4, 5, 6], "open": "07:00"}]}}
        self.assertEqual(build(blinds=blinds), [])

    def test_the_purifier_goes_to_auto_and_sleep(self):
        p = {"enabled": True, "entity": "fan.p", "name": "Air Purifier ", "schedules": [{"days": [4], "auto": "07:00", "sleep": "22:30"}]}
        items = build(purifier=p)
        self.assertEqual([(i["date"], i["time"], i["title"]) for i in items], [("2026-11-20", "22:30", "Air Purifier to Sleep"), ("2026-11-27", "07:00", "Air Purifier to Auto")])

    def test_alarm_tests_tasks_and_overnight_heating(self):
        items = build(
            tests=[{"name": "House alarm", "at": "2026-11-22T11:00:00+00:00"}],
            tasks=[{"title": "Put the bins out", "due": "2026-11-21T18:00:00", "for": ["Diane"]}, {"title": "Old", "due": "2026-11-01"}],
            overnight={"mode": "Eco", "start": "00:00", "end": "06:00"},
        )
        titles = [(i["date"], i["time"], i["title"]) for i in items]
        self.assertIn(("2026-11-22", "11:00", "House alarm test"), titles)
        self.assertIn(("2026-11-21", "18:00", "Put the bins out"), titles)
        self.assertIn(("2026-11-21", "00:00", "Heating to Eco overnight"), titles)
        self.assertNotIn("Old", [i["title"] for i in items])

    def test_smart_overnight_adds_nothing(self):
        self.assertEqual(build(overnight={"mode": "Smart", "start": "00:00", "end": "06:00"}), [])

    def test_sorted_and_limited_to_the_window(self):
        r = routine("light.a", "off", {"kind": "once", "at": "2026-12-10T10:00"})
        late = routine("light.b", "off", {"kind": "once", "at": "2026-11-21T08:00"})
        items = build(routines=[r, late])
        self.assertEqual([i["time"] for i in items], ["08:00"])
        self.assertEqual(len(up.build(NOW, 30, routines=[r, late], names={}, blinds={}, words=words, purifier=None, tests=[], overnight=None, tasks=[])), 2)


class CarAndAutomations(unittest.TestCase):
    CHARGING = {"entity": "select.zappi_charge_mode", "plugged": True, "smart": True, "role": "Additional",
                "window_start": "2026-11-20T23:30:00+00:00", "window_end": "2026-11-21T06:00:00+00:00",
                "dispatches": [{"start": "2026-11-21T04:30:00+00:00", "end": "2026-11-21T05:30:00+00:00"}]}

    def test_planned_slots_are_listed(self):
        items = [i for i in build(charging={**self.CHARGING, "role": "Primary"}) if i["kind"] == "car"]
        self.assertEqual([(i["date"], i["time"], i["title"]) for i in items], [("2026-11-21", "04:30", "Car charging until 05:30")])
        self.assertEqual(items[0]["tap"], {"to": "device", "entity": "select.zappi_charge_mode"})

    def test_a_plugged_in_car_on_smart_charge_gets_each_nights_cheap_rate(self):
        items = [i for i in build(charging=self.CHARGING) if i["kind"] == "car"]
        self.assertEqual([i["date"] for i in items if i["title"].startswith("Car charging at")], [f"2026-11-{d}" for d in range(20, 27)])
        self.assertEqual(items[0]["title"], "Car charging at the cheap rate until 06:00")

    def test_no_cheap_rate_when_unplugged_or_smart_charge_is_off(self):
        for off in ({"plugged": False}, {"smart": False}):
            items = [i for i in build(charging={**self.CHARGING, **off}) if i["title"].startswith("Car charging at")]
            self.assertEqual(items, [])

    BATTERY = {"alias": "Battery Notes: Weekly Status Report", "triggers": [{"at": "18:00:00", "trigger": "time"}], "conditions": [{"condition": "time", "weekday": ["fri"]}]}

    def test_a_time_automation_is_read_from_its_own_config(self):
        runs = up.time_automation_runs(self.BATTERY, NOW, datetime(2026, 12, 4, 23, 0, tzinfo=timezone.utc))
        self.assertEqual([r.strftime("%Y-%m-%d %H:%M") for r in runs], ["2026-11-20 18:00".replace("11-20", "11-27"), "2026-12-04 18:00"])

    def test_only_known_automations_are_listed_and_odd_triggers_are_ignored(self):
        known = {"Battery Notes: Weekly Status Report": ("Weekly battery report", "report", "House")}
        helper = {"alias": "Other", "triggers": [{"at": "input_datetime.x", "trigger": "time"}]}
        items = build(automations=[self.BATTERY, helper, {"alias": "Unknown", "triggers": [{"at": "09:00:00", "trigger": "time"}]}], known_automations=known)
        self.assertEqual([(i["date"], i["time"], i["title"], i["kind"]) for i in items], [("2026-11-27", "18:00", "Weekly battery report", "report")])
        self.assertEqual(items[0]["tap"], {"to": "none"})
        self.assertEqual(up.time_automation_runs(helper, NOW, datetime(2026, 12, 30, tzinfo=timezone.utc)), [])


class HomeAssistantValues(unittest.TestCase):
    """Inside Home Assistant a state's time attributes are datetime objects, not text: the live failure on 0.174.0."""

    def test_charging_times_may_be_datetimes(self):
        charging = {"entity": "select.zappi_charge_mode", "plugged": True, "smart": True, "role": "Additional",
                    "window_start": datetime(2026, 11, 20, 23, 30, tzinfo=timezone.utc), "window_end": datetime(2026, 11, 21, 6, 0, tzinfo=timezone.utc),
                    "dispatches": [{"start": datetime(2026, 11, 21, 4, 30, tzinfo=timezone.utc), "end": datetime(2026, 11, 21, 5, 30, tzinfo=timezone.utc)}]}
        titles = [i["title"] for i in build(charging=charging) if i["kind"] == "car"]
        self.assertIn("Car charging until 05:30", titles)
        self.assertIn("Car charging at the cheap rate until 06:00", titles)

    def test_one_bad_source_leaves_the_rest(self):
        r = routine("cover.roller_blind", "open", {"kind": "once", "at": "2026-11-21T08:15"})
        items = build(routines=[r], charging={"entity": "x", "dispatches": [{"start": 5, "end": object()}]})
        self.assertEqual([i["time"] for i in items], ["08:15"])


if __name__ == "__main__":
    unittest.main()
