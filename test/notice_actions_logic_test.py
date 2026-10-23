import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "notice_actions_logic",
    pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "notice_actions_logic.py",
)
na = importlib.util.module_from_spec(spec)
spec.loader.exec_module(na)


def titles(buttons):
    return [b["title"] for b in buttons]


class NoticeActionsLogic(unittest.TestCase):
    def test_the_doorbell_can_be_answered_viewed_and_muted(self):
        b = na.actions_for("doorbell")
        self.assertEqual(titles(b), ["Answer", "View", "Mute 1 hour"])
        self.assertEqual(b[0]["uri"], "church-drive-camera:camera.front_door_live_view?answer")
        self.assertEqual(b[1]["uri"], "church-drive-camera:camera.front_door_live_view")
        self.assertEqual(b[2]["action"], "CD_mute:60")

    def test_a_smoke_alarm_shows_its_rooms_camera_and_can_turn_lights_on(self):
        b = na.actions_for("smoke", tag="church-drive-safety-binary_sensor_entrance_smoke_alarm_alarm_status")
        self.assertEqual(titles(b), ["View camera", "Lights on"])
        self.assertEqual(b[0]["uri"], "church-drive-camera:camera.entrance_live_view")
        # A room with no camera: just the lights.
        self.assertEqual(titles(na.actions_for("smoke", tag="church-drive-safety-binary_sensor_top_floor_smoke_alarm_alarm_status")), ["Lights on"])

    def test_a_missing_camera_gives_no_view_button(self):
        self.assertEqual(titles(na.actions_for("doorbell", exists=lambda e: False)), ["Mute 1 hour"])

    def test_the_back_door_looks_at_the_garden(self):
        b = na.actions_for("door_left_open", tag="church-drive-door-open-binary_sensor_back_door")
        self.assertEqual(titles(b), ["Snooze 10 min", "View camera"])
        self.assertEqual(b[1]["uri"], "church-drive-camera:camera.garden_live_view")

    def test_motion_uses_the_camera_in_its_photo(self):
        b = na.actions_for("night_motion", image="/api/camera_proxy/camera.driveway_snapshot")
        self.assertEqual(b[0]["uri"], "church-drive-camera:camera.driveway_live_view")

    def test_only_a_stuck_vacuum_offers_the_dock(self):
        self.assertEqual(titles(na.actions_for("vacuum", title="🧹 Gregg needs help")), ["Send to dock", "Open Cleaning"])
        self.assertEqual(titles(na.actions_for("vacuum", title="🧹 Gregg's finished")), ["Open Cleaning"])

    def test_never_a_call_never_the_alarm(self):
        every = []
        for kind in ["smoke", "heat", "co", "alarm_triggered", "doorbell", "door_away", "door_left_open", "alarm_mode",
                     "night_motion", "tamper", "vacuum", "damp", "left_on", "cheap_rate", "power_down", "car_plugged",
                     "car_charged", "car_not_plugged", "backup"]:
            every += na.actions_for(kind, tag="church-drive-door-binary_sensor_front_door", title="needs help")
        for b in every:
            text = (b["action"] + b.get("uri", "") + b["title"]).lower()
            self.assertNotIn("tel:", text)
            self.assertNotIn("call", text)
            self.assertNotIn("disarm", text)
        self.assertTrue(all(len(na.actions_for(k)) <= 3 for k in ["smoke", "doorbell", "vacuum"]))

    def test_clive_offers_to_be_resumed_only_when_he_has_stepped_back(self):
        paused = na.actions_for("clive_actions", tag="church-drive-clive-paused-upstairs")
        self.assertEqual(titles(paused), ["Resume Clive", "Open Climate"])
        self.assertEqual(paused[0]["action"], "CD_clive_resume")
        self.assertEqual(titles(na.actions_for("clive_actions", tag="church-drive-clive-climate-upstairs")), ["Open Climate"])
        self.assertEqual(na.split_action("CD_clive_resume"), ("clive_resume", None))
        self.assertFalse(na.is_phone_action("CD_clive_resume"))
        self.assertEqual(na.tap_link_for("clive_actions"), "/dashboard-mobile/climate")

    def test_action_ids_split_and_unknown_ones_are_ignored(self):
        self.assertEqual(na.split_action("CD_snooze:30"), ("snooze", 30))
        self.assertEqual(na.split_action("CD_lights_on"), ("lights_on", None))
        self.assertIsNone(na.split_action("CD_format_everything"))
        self.assertIsNone(na.split_action("DONE_abc"))

    def test_the_home_assistant_app_is_not_given_the_phone_only_buttons(self):
        self.assertTrue(na.is_phone_action("CD_mute:60"))
        self.assertTrue(na.is_phone_action("CD_snooze:10"))
        self.assertFalse(na.is_phone_action("CD_lights_on"))
        self.assertFalse(na.is_phone_action("URI"))

    def test_a_battery_alert_opens_the_device_that_ran_low(self):
        ids = ["binary_sensor.back_door", "binary_sensor.back_door_battery_plus_low"]
        got = na.tap_link_for("battery_report", "church-drive-battery-binary_sensor_back_door_battery_plus_low", "/battery-status/battery-status", ids)
        self.assertEqual(got, "church-drive-device:binary_sensor.back_door_battery_plus_low")

    def test_a_health_alert_with_no_known_device_opens_device_health(self):
        self.assertEqual(na.tap_link_for("battery_report", "church-drive-battery-nothing", "/battery-status/battery-status", []), "church-drive-health")
        self.assertEqual(na.tap_link_for("device_stale", "church-drive-health", "/dashboard-mobile/climate"), "church-drive-health")
        self.assertEqual(na.tap_link_for("low_batteries", "", ""), "church-drive-health")

    def test_an_old_battery_status_link_on_any_kind_opens_device_health(self):
        self.assertEqual(na.tap_link_for("other", "", "/battery-status/battery-status"), "church-drive-health")

    def test_a_kind_with_no_link_opens_its_page_and_a_given_link_is_kept(self):
        self.assertEqual(na.tap_link_for("smoke"), "/dashboard-mobile/security")
        self.assertEqual(na.tap_link_for("vacuum"), "/dashboard-mobile/cleaning")
        self.assertEqual(na.tap_link_for("cheap_rate"), "")
        self.assertEqual(na.tap_link_for("door_away", "", "/dashboard-mobile/security?x=1"), "/dashboard-mobile/security?x=1")
        self.assertEqual(na.tap_link_for("doorbell", "", "church-drive-camera:camera.front_door_live_view?answer"), "church-drive-camera:camera.front_door_live_view?answer")

    def test_device_links_are_for_the_church_drive_app_only(self):
        self.assertTrue(na.is_app_only_link("church-drive-health"))
        self.assertTrue(na.is_app_only_link("church-drive-device:sensor.x"))
        self.assertFalse(na.is_app_only_link("/dashboard-mobile/security"))
        self.assertFalse(na.is_app_only_link("church-drive-camera:camera.x"))


if __name__ == "__main__":
    unittest.main()
