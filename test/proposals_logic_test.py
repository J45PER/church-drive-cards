import importlib.util
import pathlib
import sys
import types
import unittest

base = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"
# proposals_logic imports its sibling colours_logic relatively, so load both as a package without importing Home Assistant.
pkg = types.ModuleType("cd_pkg")
pkg.__path__ = [str(base)]
sys.modules["cd_pkg"] = pkg
for name in ("colours_logic", "proposals_logic"):
    spec = importlib.util.spec_from_file_location(f"cd_pkg.{name}", base / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[f"cd_pkg.{name}"] = mod
    spec.loader.exec_module(mod)
pl = sys.modules["cd_pkg.proposals_logic"]

OK = ["#FFC857", "#4E4128", "#D5A190", "#FFE4AB", "#6E572D"]


class Normalise(unittest.TestCase):
    def test_a_set_keeps_good_palettes_and_refuses_a_bad_one(self):
        out = pl.normalise_palette_change({"mode": "set", "palettes": {"lights": {"name": " Lamps ", "colours": [c.lower() for c in OK]}}})
        self.assertEqual(out["palettes"]["lights"], {"name": "Lamps", "colours": OK})
        with self.assertRaises(ValueError):
            pl.normalise_palette_change({"mode": "set", "palettes": {"lights": {"colours": ["#12"] * 5}}})
        with self.assertRaises(ValueError):
            pl.normalise_palette_change({"mode": "set", "palettes": {}})
        with self.assertRaises(ValueError):
            pl.normalise_palette_change("junk")

    def test_reset_needs_real_keys_and_replace_can_be_empty(self):
        self.assertEqual(pl.normalise_palette_change({"mode": "reset", "keys": ["a", "a", "b"]})["keys"], ["a", "b"])
        with self.assertRaises(ValueError):
            pl.normalise_palette_change({"mode": "reset", "keys": ["Bad Key"]})
        self.assertEqual(pl.normalise_palette_change({"mode": "replace", "palettes": {}})["palettes"], {})
        with self.assertRaises(ValueError):
            pl.normalise_palette_change({"mode": "nonsense"})


class Apply(unittest.TestCase):
    def test_set_merges_replace_swaps_reset_puts_back(self):
        have = {"lights": {"name": "Lamps", "colours": OK}}
        added = pl.apply_palette_change(have, {"mode": "set", "palettes": {"gregg": {"colours": OK}}})
        self.assertEqual(set(added), {"lights", "gregg"})
        self.assertEqual(set(pl.apply_palette_change(added, {"mode": "replace", "palettes": {"only": {"colours": OK}}})), {"only"})
        self.assertEqual(set(pl.apply_palette_change(added, {"mode": "reset", "keys": ["lights"]})), {"gregg"})
        self.assertEqual(have, {"lights": {"name": "Lamps", "colours": OK}})  # the old overrides are untouched


class Checks(unittest.TestCase):
    def test_contrast_of_black_and_white_is_21(self):
        self.assertAlmostEqual(pl.contrast("#000000", "#FFFFFF"), 21.0, places=1)

    def test_a_pale_dark_colour_is_flagged_and_a_good_one_is_not(self):
        bad = pl.check_palette_change({"palettes": {"lights": {"colours": ["#FFC857", "#EEEEEE", "#D5A190", "#FFE4AB", "#6E572D"]}}})
        self.assertEqual(len(bad), 1)
        self.assertIn("hard to read", bad[0])
        self.assertEqual(pl.check_palette_change({"palettes": {"lights": {"colours": OK}}}), [])
        dup = pl.check_palette_change({"palettes": {"x": {"colours": ["#112233"] * 5}}})
        self.assertTrue(any("same" in n for n in dup))


class Items(unittest.TestCase):
    def test_trimming_keeps_everything_pending(self):
        items = [{"id": str(i), "status": "applied"} for i in range(pl.MAX_ITEMS)] + [{"id": "p", "status": "pending"}, {"id": "q", "status": "pending"}]
        out = pl.trim_items(items)
        self.assertEqual(len(out), pl.MAX_ITEMS)
        self.assertEqual([i["id"] for i in out if i["status"] == "pending"], ["p", "q"])

    def test_a_new_proposal_waits_and_cuts_a_long_note(self):
        item = pl.new_proposal("abc", "palette", {"mode": "set"}, "n" * 900, "Jamie", 1.0, [], False)
        self.assertEqual((item["status"], len(item["note"]), item["resolved_at"]), ("pending", pl.MAX_NOTE, None))


if __name__ == "__main__":
    unittest.main()
