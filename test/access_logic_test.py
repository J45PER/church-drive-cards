"""Tests for who can see which device (custom_components/church_drive/access_logic.py)."""

import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("access_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "access_logic.py")
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)

ADMINS = ["person.jamie", "person.diane"]
ENTITIES = ["media_player.living_room_tv", "media_player.kitchen_speaker", "media_player.office_display", "light.hall", "fan.bedroom"]


class VisibleMapTest(unittest.TestCase):
    def test_unassigned_media_is_admin_only_and_assigned_is_shared_with_admins(self):
        m = a.visible_map({"media_player.living_room_tv": ["person.ian", "person.diane"]}, ENTITIES, ADMINS)
        self.assertEqual(m["media_player.living_room_tv"], ["person.diane", "person.ian", "person.jamie"])
        self.assertEqual(m["media_player.kitchen_speaker"], ["person.diane", "person.jamie"])
        self.assertEqual(m["media_player.office_display"], ["person.diane", "person.jamie"])

    def test_other_devices_are_everyones_until_limited(self):
        m = a.visible_map({"light.hall": ["person.hayley"]}, ENTITIES, ADMINS)
        self.assertEqual(m["light.hall"], ["person.diane", "person.hayley", "person.jamie"])
        self.assertNotIn("fan.bedroom", m)

    def test_nobody_assigned_means_admins_only_for_media_and_no_entry_for_the_rest(self):
        m = a.visible_map({"media_player.living_room_tv": [], "fan.bedroom": []}, ENTITIES, ADMINS)
        self.assertEqual(m["media_player.living_room_tv"], ["person.diane", "person.jamie"])
        self.assertNotIn("fan.bedroom", m)

    def test_can_use(self):
        m = a.visible_map({"media_player.living_room_tv": ["person.ian"]}, ENTITIES, ADMINS)
        self.assertTrue(a.can_use(m, "media_player.living_room_tv", "person.ian"))
        self.assertTrue(a.can_use(m, "media_player.living_room_tv", "person.jamie"))
        self.assertFalse(a.can_use(m, "media_player.living_room_tv", "person.hayley"))
        self.assertFalse(a.can_use(m, "media_player.kitchen_speaker", "person.ian"))
        self.assertFalse(a.can_use(m, "media_player.kitchen_speaker", None))
        self.assertTrue(a.can_use(m, "light.hall", None))   # an entity that is everyone's


class VisibleMediaTest(unittest.TestCase):
    def test_what_each_person_is_told_about(self):
        players = [{"entity_id": "media_player.living_room_tv", "name": "Living Room TV"}, {"entity_id": "media_player.office_display", "name": "Office display"}]
        m = a.visible_map({"media_player.living_room_tv": ["person.ian"]}, ["media_player.living_room_tv", "media_player.office_display"], ADMINS)
        self.assertEqual([p["name"] for p in a.visible_media(m, players, "person.ian", False)], ["Living Room TV"])
        self.assertEqual(len(a.visible_media(m, players, "person.hayley", False)), 0)
        self.assertEqual(len(a.visible_media(m, players, None, True)), 2)   # an administrator (or an automation)


class MediaCallTest(unittest.TestCase):
    def test_actions(self):
        self.assertEqual(a.media_call("pause"), ("media_pause", {}))
        self.assertEqual(a.media_call("mute"), ("volume_mute", {"is_volume_muted": True}))
        self.assertEqual(a.media_call("volume_set", 35), ("volume_set", {"volume_level": 0.35}))
        self.assertEqual(a.media_call("volume_set", "150"), ("volume_set", {"volume_level": 1.0}))
        self.assertEqual(a.media_call("select_source", " HDMI 1 "), ("select_source", {"source": "HDMI 1"}))

    def test_bad(self):
        for action, value in (("explode", None), ("volume_set", "loud"), ("volume_set", None), ("select_source", " ")):
            with self.assertRaises(ValueError):
                a.media_call(action, value)


if __name__ == "__main__":
    unittest.main()
