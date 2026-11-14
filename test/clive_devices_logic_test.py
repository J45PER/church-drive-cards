"""Tests for the His devices shaping (custom_components/church_drive/clive_devices_logic.py)."""

import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("clive_devices_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "clive_devices_logic.py")
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)


class PhonesTest(unittest.TestCase):
    def test_own_app_phones_only(self):
        people = [{"first": "Jamie", "phones": [{"own_app": True, "name": "Pixel", "model": "Pixel 10", "version": "0.150.3", "seen": "2026-11-14T10:00:00+00:00", "on": True},
                                                {"own_app": False, "name": "Old", "seen": None}]},
                  {"first": "Ian", "name": "Ian", "phones": [{"own_app": True, "name": "S22", "version": "0.124.0", "seen": "2026-11-10T10:00:00+00:00", "on": False}]}]
        rows = d.shape_phones(people, 24, 1_794_000_000.0)
        self.assertEqual(sorted(r["name"] for r in rows), ["Pixel", "S22"])
        self.assertEqual({r["name"]: r["notifications"] for r in rows}, {"Pixel": True, "S22": False})

    def test_answering_window(self):
        now = 1_794_000_000.0
        from datetime import datetime, timezone
        recent = datetime.fromtimestamp(now - 3600, timezone.utc).isoformat()
        old = datetime.fromtimestamp(now - 3 * 86400, timezone.utc).isoformat()
        rows = d.shape_phones([{"first": "A", "phones": [{"own_app": True, "name": "p1", "seen": recent}, {"own_app": True, "name": "p2", "seen": old}, {"own_app": True, "name": "p3", "seen": None}]}], 24, now)
        self.assertEqual({r["name"]: r["answering"] for r in rows}, {"p1": True, "p2": False, "p3": False})
        self.assertEqual(rows[0]["name"], "p1")   # answering first


class SatelliteTest(unittest.TestCase):
    def test_states(self):
        s = d.shape_satellite("assist_satellite.kitchen", "listening", "Kitchen", "2026-11-14T10:00:00+00:00", "Kitchen", "2026.11.1", "Round")
        self.assertEqual((s["state"], s["answering"], s["area"], s["version"]), ("Listening", True, "Kitchen", "2026.11.1"))
        dead = d.shape_satellite("assist_satellite.hall", "unavailable", "", None, None, None, None)
        self.assertEqual((dead["state"], dead["answering"], dead["name"]), ("Not answering", False, "assist_satellite.hall"))


if __name__ == "__main__":
    unittest.main()
