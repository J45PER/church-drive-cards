import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "colours_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "colours_logic.py"
)
cl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cl)

DEFAULTS = {
    "lights": {"name": "Lights", "group": "Home", "colours": ["#FFC857", "#4E4128", "#D5A190", "#FFE4AB", "#6E572D"]},
    "gregg": {"name": "Gregg", "group": "Home", "colours": ["#4C8DFF", "#203254", "#637BFC", "#A6C6FF", "#233F73"]},
}
FIVE = ["#112233", "#223344", "#334455", "#445566", "#556677"]


class Clean(unittest.TestCase):
    def test_keeps_well_formed_changes_only(self):
        raw = {
            "lights": {"name": "  Lamps  ", "colours": [c.lower() for c in FIVE]},
            "Bad Key": {"name": "x"},
            "gregg": {"colours": ["#12"] * 5},
            "x": "not a dict",
            "ok_one": {"colours": FIVE},
        }
        out = cl.clean_overrides(raw)
        self.assertEqual(set(out), {"lights", "ok_one"})
        self.assertEqual(out["lights"]["name"], "Lamps")
        self.assertEqual(out["lights"]["colours"], FIVE)

    def test_junk_is_nothing(self):
        self.assertEqual(cl.clean_overrides(None), {})
        self.assertEqual(cl.clean_overrides([1, 2]), {})

    def test_a_long_name_is_cut(self):
        out = cl.clean_overrides({"a": {"name": "n" * 200}})
        self.assertEqual(len(out["a"]["name"]), cl.MAX_NAME)


class Merge(unittest.TestCase):
    def test_defaults_alone(self):
        p = cl.merge(DEFAULTS, {})
        self.assertEqual(p["lights"]["colours"][0], "#FFC857")
        self.assertFalse(p["lights"]["custom"])

    def test_changes_go_on_top_and_additions_are_custom(self):
        p = cl.merge(DEFAULTS, cl.clean_overrides({"lights": {"colours": FIVE}, "mine": {"name": "Mine", "colours": FIVE}}))
        self.assertEqual(p["lights"]["colours"], FIVE)
        self.assertEqual(p["lights"]["name"], "Lights")
        self.assertTrue(p["mine"]["custom"])
        self.assertEqual(p["mine"]["group"], "Your palettes")

    def test_a_new_palette_needs_colours(self):
        self.assertNotIn("nameonly", cl.merge(DEFAULTS, {"nameonly": {"name": "x"}}))


class Kinds(unittest.TestCase):
    def test_glow_palette_is_colour_accent_light(self):
        k = cl.kind_palettes(cl.merge(DEFAULTS, cl.clean_overrides({"gregg": {"colours": FIVE}})))
        self.assertEqual(k["gregg"], ["#112233", "#334455", "#445566"])
        self.assertEqual(k["lights"], ["#FFC857", "#D5A190", "#FFE4AB"])
        self.assertNotIn("energy", k)


if __name__ == "__main__":
    unittest.main()
