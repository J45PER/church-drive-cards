import pathlib
import re
import unittest

# alarm_actor.py needs Home Assistant, so it can't be imported here; these check what broke before: Home Assistant only
# accepts an event filter that is marked @callback (a plain lambda made the whole notification setup fail).
SRC = (pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "alarm_actor.py").read_text()
INIT = (pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "__init__.py").read_text()


class AlarmActorTest(unittest.TestCase):
    def test_the_event_filter_is_a_marked_callback_not_a_lambda(self):
        self.assertNotIn("event_filter=lambda", SRC)
        self.assertRegex(SRC, r"@staticmethod\n    @callback\n    def _is_alarm")
        self.assertIn("event_filter=self._is_alarm", SRC)

    def test_a_failure_to_start_cannot_stop_notifications_starting(self):
        start = INIT.index("alarm_actor = AlarmActor(")
        before = INIT[max(0, start - 200):start]
        self.assertIn("try:", before)
        self.assertRegex(INIT[start:start + 500], r"except Exception")


if __name__ == "__main__":
    unittest.main()
