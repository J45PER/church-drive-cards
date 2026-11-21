"""Hybrid scene rules (custom_components/church_drive/hybrid_logic.py)."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "hybrid_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "hybrid_logic.py"
)
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)

GOOD = {
    "name": "Movie",
    "icon": "cd:television",
    "target": "light.living_room",
    "looks": {
        "light.ceiling": {"t": "off"},
        "light.strip": {"t": "dynamic", "v": "soho"},
        "light.lamp": {"t": "colour", "v": "#FF9A3C"},
        "light.shelf": {"t": "gradient", "v": ["#3ec6ff", "#5b8cfd", "#b36bff"]},
        "light.wall": {"t": "white", "v": "relax"},
        "light.hall": {"t": "leave"},
    },
}


class LooksTest(unittest.TestCase):
    def test_every_kind_of_look_is_kept(self):
        for look in GOOD["looks"].values():
            self.assertIsNotNone(h.clean_look(look))

    def test_colours_are_lowercased(self):
        self.assertEqual(h.clean_look({"t": "colour", "v": "#FF9A3C"}), {"t": "colour", "v": "#ff9a3c"})

    def test_bad_looks_are_refused(self):
        for bad in (None, "off", {"t": "sparkle"}, {"t": "colour", "v": "red"}, {"t": "colour"}, {"t": "gradient", "v": ["#ffffff"]},
                    {"t": "gradient", "v": ["#ffffff"] * 6}, {"t": "white", "v": "Not A Key"}, {"t": "white", "v": 5}):
            self.assertIsNone(h.clean_look(bad), bad)


class HybridTest(unittest.TestCase):
    def test_a_good_hybrid_is_kept_with_an_id(self):
        out = h.clean_hybrid(GOOD)
        self.assertEqual(out["name"], "Movie")
        self.assertEqual(out["target"], "light.living_room")
        self.assertTrue(out["id"])
        self.assertEqual(len(out["looks"]), 6)

    def test_the_name_is_trimmed_and_cut_short(self):
        out = h.clean_hybrid({**GOOD, "name": "  " + "x" * 40 + "  "})
        self.assertEqual(len(out["name"]), h.MAX_NAME)

    def test_an_unknown_icon_becomes_the_default(self):
        self.assertEqual(h.clean_hybrid({**GOOD, "icon": "mdi:skull"})["icon"], h.DEFAULT_ICON)
        self.assertEqual(h.clean_hybrid({**GOOD, "icon": "cd:moon"})["icon"], "cd:moon")

    def test_it_needs_a_name_a_target_and_something_to_do(self):
        with self.assertRaises(ValueError):
            h.clean_hybrid({**GOOD, "name": " "})
        with self.assertRaises(ValueError):
            h.clean_hybrid({**GOOD, "target": "switch.x"})
        with self.assertRaises(ValueError):
            h.clean_hybrid({**GOOD, "looks": {}})
        with self.assertRaises(ValueError):
            h.clean_hybrid({**GOOD, "looks": {"light.a": {"t": "leave"}}})
        with self.assertRaises(ValueError):
            h.clean_hybrid({**GOOD, "looks": {"switch.a": {"t": "off"}}})

    def test_changing_a_hybrid_keeps_its_id_and_target(self):
        first = h.clean_hybrid(GOOD)
        again = h.clean_hybrid({"name": "Film", "looks": {"light.a": {"t": "off"}}}, first)
        self.assertEqual(again["id"], first["id"])
        self.assertEqual(again["target"], first["target"])
        self.assertEqual(again["name"], "Film")

    def test_stored_junk_is_dropped(self):
        kept = h.clean_all([h.clean_hybrid(GOOD), {"name": ""}, "oops", None])
        self.assertEqual(len(kept), 1)
        self.assertEqual(h.clean_all(None), [])

    def test_a_target_lists_only_its_own(self):
        a = h.clean_hybrid(GOOD)
        b = h.clean_hybrid({**GOOD, "target": "light.hall", "name": "Other"})
        self.assertEqual(h.for_target([a, b], "light.hall"), [b])
        self.assertEqual(len(h.for_target([a, b], None)), 2)


class HelpersTest(unittest.TestCase):
    def test_rgb(self):
        self.assertEqual(h.rgb_of("#ff9a3c"), [255, 154, 60])

    def test_gradient_spec_is_a_still_colour_scene(self):
        spec = h.gradient_spec([(0.3, 0.3), (0.5, 0.4)])
        self.assertEqual(spec["kind"], "colour")
        self.assertFalse(spec["dynamic"])
        self.assertEqual(len(spec["colors"]), 2)


if __name__ == "__main__":
    unittest.main()


class PlayOrderTest(unittest.TestCase):
    def test_a_ring_is_played_last(self):
        self.assertEqual(h.play_order(["light.up", "light.down"], {"light.up"}), ["light.down", "light.up"])
        self.assertEqual(h.play_order(["light.down", "light.up"], {"light.up"}), ["light.down", "light.up"])

    def test_only_a_coloured_ring_counts(self):
        rings = {"light.up"}
        self.assertTrue(h.ring_is_colourful({"light.up": {"t": "gradient", "v": ["#000000", "#ffffff"]}}, rings))
        self.assertFalse(h.ring_is_colourful({"light.up": {"t": "off"}}, rings))
        self.assertFalse(h.ring_is_colourful({"light.down": {"t": "white", "v": "bright"}}, rings))

    def test_a_warm_white_is_orange_and_a_cool_one_is_blue(self):
        warm = h.kelvin_rgb(2700)
        cool = h.kelvin_rgb(6500)
        self.assertEqual(warm[0], 255)
        self.assertGreater(warm[0], warm[2])
        self.assertGreater(cool[2], warm[2])
        self.assertTrue(all(0 <= c <= 255 for c in warm + cool))
