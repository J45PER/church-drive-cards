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



class CardsIconsLayouts(unittest.TestCase):
    def test_cards_need_valid_ids_and_a_palette_or_note(self):
        out = pl.normalise_change("cards", {"cards": {"octopus-gas": {"palette": "gas", "comment": "  purple  "}}})
        self.assertEqual(out, {"cards": {"octopus-gas": {"palette": "gas", "comment": "purple"}}})
        for bad in ({"cards": {}}, {"cards": {"Bad Id": {"palette": "gas"}}}, {"cards": {"x": {}}}, {"cards": {"x": {"palette": "Bad Key"}}}, "junk"):
            with self.assertRaises(ValueError):
                pl.normalise_change("cards", bad)

    def test_a_card_pick_is_checked_against_the_palettes_that_exist(self):
        change = pl.normalise_change("cards", {"cards": {"fan": {"palette": "airr"}}})
        self.assertEqual(len(pl.check_change("cards", change, {"air", "gas"})), 1)
        self.assertEqual(pl.check_change("cards", change, {"airr"}), [])

    def test_an_icon_change_names_a_panel_and_a_valid_icon(self):
        out = pl.normalise_change("icons", {"dashboard": "dashboard-mobile", "view": "energy", "panel": " Octopus Gas ", "icon": "cd:bolt", "before": "phu:octopusenergy"})
        self.assertEqual((out["panel"], out["icon"], out["before"]), ("Octopus Gas", "cd:bolt", "phu:octopusenergy"))
        for bad in ({"dashboard": "d", "view": "v", "panel": "p", "icon": "bolt"}, {"dashboard": "D x", "view": "v", "panel": "p", "icon": "cd:bolt"}, {"dashboard": "d", "view": "v", "panel": "", "icon": "cd:bolt"}):
            with self.assertRaises(ValueError):
                pl.normalise_change("icons", bad)

    def test_icons_prefer_the_houses_own_set(self):
        mdi = pl.normalise_change("icons", {"dashboard": "d", "view": "v", "panel": "p", "icon": "mdi:fan"})
        self.assertIn("cd:", pl.check_change("icons", mdi)[0])
        unknown = pl.normalise_change("icons", {"dashboard": "d", "view": "v", "panel": "p", "icon": "cd:nope"})
        self.assertIn("not in the house", pl.check_change("icons", unknown, icon_names={"cd:bolt"})[0])
        self.assertEqual(pl.check_change("icons", pl.normalise_change("icons", {"dashboard": "d", "view": "v", "panel": "p", "icon": "cd:bolt"}), icon_names={"cd:bolt"}), [])

    def test_a_layout_lists_each_panel_once_and_hides_only_listed_ones(self):
        out = pl.normalise_change("layouts", {"dashboard": "d", "view": "energy", "order": ["A", "B", "C"], "hidden": ["C"], "before": {"order": ["B", "A", "C"], "hidden": []}})
        self.assertEqual((out["order"], out["hidden"], out["before"]["order"]), (["A", "B", "C"], ["C"], ["B", "A", "C"]))
        for bad in ({"order": ["A", "A"]}, {"order": ["A"], "hidden": ["Z"]}, {"order": []}):
            with self.assertRaises(ValueError):
                pl.normalise_change("layouts", {"dashboard": "d", "view": "v", **bad})
        hide_all = pl.normalise_change("layouts", {"dashboard": "d", "view": "v", "order": ["A"], "hidden": ["A"]})
        self.assertIn("empty", pl.check_change("layouts", hide_all)[0])

    def test_a_version_remembers_what_was_replaced_and_restoring_puts_it_back(self):
        item = {"change": pl.normalise_change("layouts", {"dashboard": "d", "view": "v", "order": ["A", "B"], "hidden": [], "before": {"order": ["B", "A"], "hidden": ["B"]}})}
        data = pl.version_data_for("layouts", item, {})
        self.assertEqual(pl.restore_change("layouts", data), {"dashboard": "d", "view": "v", "order": ["B", "A"], "hidden": ["B"]})
        icon = {"change": pl.normalise_change("icons", {"dashboard": "d", "view": "v", "panel": "P", "icon": "cd:bolt", "before": "cd:gift"})}
        self.assertEqual(pl.restore_change("icons", pl.version_data_for("icons", icon, {}))["icon"], "cd:gift")
        cards = {"change": pl.normalise_change("cards", {"cards": {"fan": {"palette": "air"}, "gas": {"comment": "x"}}})}
        data = pl.version_data_for("cards", cards, {"fan": {"palette": "heating"}})
        self.assertEqual(pl.restore_change("cards", data), {"cards": {"fan": {"palette": "heating"}}})
        with self.assertRaises(ValueError):
            pl.restore_change("icons", {"dashboard": "d", "view": "v", "panel": "P", "icon": None})


if __name__ == "__main__":
    unittest.main()
