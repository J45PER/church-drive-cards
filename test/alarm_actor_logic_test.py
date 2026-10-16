import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "alarm_actor_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "alarm_actor_logic.py"
)
al = importlib.util.module_from_spec(spec)
spec.loader.exec_module(al)

ALARM = "alarm_control_panel.church_drive_alarm"


class AlarmActorLogicTest(unittest.TestCase):
    def test_a_login_s_request_is_credited_to_the_next_change(self):
        calls = {}
        al.remember(calls, "alarm_disarm", {"entity_id": ALARM}, "user-jamie", 1000.0)
        self.assertEqual(al.credit(calls, ALARM, 1006.0), "user-jamie")

    def test_each_request_is_credited_once(self):
        calls = {}
        al.remember(calls, "alarm_arm_away", {"entity_id": ALARM}, "user-jamie", 1000.0)
        al.credit(calls, ALARM, 1005.0)
        self.assertIsNone(al.credit(calls, ALARM, 1010.0), "a later keypad change is not Jamie's")

    def test_an_old_request_is_not_credited(self):
        calls = {}
        al.remember(calls, "alarm_disarm", {"entity_id": ALARM}, "user-jamie", 1000.0)
        self.assertIsNone(al.credit(calls, ALARM, 1000.0 + al.WINDOW_SECONDS + 1))

    def test_requests_with_no_login_or_other_services_are_ignored(self):
        calls = {}
        al.remember(calls, "alarm_disarm", {"entity_id": ALARM}, None, 1000.0)
        al.remember(calls, "alarm_trigger", {"entity_id": ALARM}, "user-jamie", 1000.0)
        self.assertEqual(calls, {})

    def test_a_list_of_alarms_is_each_remembered(self):
        calls = {}
        al.remember(calls, "alarm_disarm", {"entity_id": [ALARM, "alarm_control_panel.other"]}, "u", 5.0)
        self.assertEqual(sorted(calls), [ALARM, "alarm_control_panel.other"])
        self.assertEqual(al.entities_of(None), [])


if __name__ == "__main__":
    unittest.main()
