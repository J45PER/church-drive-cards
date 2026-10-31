import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"
spec = importlib.util.spec_from_file_location("snapshot_logic", ROOT / "snapshot_logic.py")
sl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sl)

EVENTS = (ROOT / "events.py").read_text()
HOUR = 3600


class Interval(unittest.TestCase):
    def test_defaults_and_battery(self):
        self.assertEqual(sl.interval_s(False, None, None), 60 * 60)
        self.assertEqual(sl.interval_s(True, None, None), 180 * 60)
        self.assertEqual(sl.interval_s(False, 30, 240), 30 * 60)
        self.assertEqual(sl.interval_s(True, 30, 240), 240 * 60)

    def test_zero_means_never_and_rubbish_means_the_default(self):
        self.assertEqual(sl.interval_s(False, 0, 0), 0)
        self.assertEqual(sl.interval_s(True, 5, 0), 0)
        self.assertEqual(sl.interval_s(False, "soon", None), 60 * 60)
        self.assertEqual(sl.interval_s(False, -5, None), 0)


class Due(unittest.TestCase):
    def test_a_quiet_camera_is_asked_once_its_picture_is_old(self):
        now = 100 * HOUR
        self.assertFalse(sl.snapshot_due(now, now - 59 * 60, 0, HOUR))
        self.assertTrue(sl.snapshot_due(now, now - 61 * 60, 0, HOUR))

    def test_counting_from_the_last_event_means_a_busy_camera_is_not_asked(self):
        now = 100 * HOUR
        last = sl.newest([now - 5 * HOUR, now - 10 * 60, None])  # an event ten minutes ago
        self.assertFalse(sl.snapshot_due(now, last, 0, HOUR))

    def test_a_press_from_anywhere_counts(self):
        now = 100 * HOUR
        self.assertFalse(sl.snapshot_due(now, sl.newest([now - 9 * HOUR, now - 5 * 60]), 0, HOUR))

    def test_not_asked_again_while_the_last_ask_is_fresh(self):
        now = 100 * HOUR
        self.assertFalse(sl.snapshot_due(now, now - 5 * HOUR, now - 10 * 60, HOUR))
        self.assertTrue(sl.snapshot_due(now, now - 5 * HOUR, now - 2 * HOUR, HOUR))

    def test_never_and_unavailable(self):
        now = 100 * HOUR
        self.assertFalse(sl.snapshot_due(now, 0, 0, 0))
        self.assertFalse(sl.snapshot_due(now, 0, 0, HOUR, available=False))

    def test_a_camera_with_nothing_known_is_asked(self):
        self.assertTrue(sl.snapshot_due(100 * HOUR, sl.newest([None, 0]), 0, HOUR))

    def test_newest(self):
        self.assertEqual(sl.newest([]), 0.0)
        self.assertEqual(sl.newest([3, None, 7, 5]), 7)


class Wiring(unittest.TestCase):
    # events.py needs Home Assistant, so it can't be imported here.
    def test_the_timer_asks_with_the_cameras_own_button_and_survives_a_failure(self):
        self.assertIn("async_track_time_interval(self.hass, self._async_snapshots", EVENTS)
        body = EVENTS[EVENTS.index("async def _async_snapshots"):EVENTS.index("# ---- which cameras")]
        self.assertIn('f"button.{base}_take_snapshot"', body)
        self.assertIn("snapshot_due(", body)
        self.assertIn("except Exception", body)
        self.assertIn("blocking=False", body)

    def test_the_apps_and_cards_no_longer_ask_by_themselves(self):
        root = ROOT.parent.parent
        kt = (root / "companion/android/app/src/main/java/com/churchdrive/app/ui/CameraCard.kt").read_text()
        self.assertNotIn('call("button", "press"', kt.split("fun CameraCard(")[1].split("Box(")[0])
        js = (root / "src/camera-card.js").read_text()
        self.assertIn("if (!force || !h", js)


if __name__ == "__main__":
    unittest.main()
