"""Own layout rules (custom_components/church_drive/layout_logic.py)."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "layout_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "layout_logic.py"
)
l = importlib.util.module_from_spec(spec)
spec.loader.exec_module(l)

GOOD = {
    "pages": {
        "climate": {"order": ["Cooling", "Heating"], "hidden": ["Air Quality"], "cards": {"Heating": {"order": ["a", "b"], "hidden": ["c"]}}},
        "home": {"own": [{"id": "ev1", "title": "Evenings", "icon": "cd:moon", "tone": "lights", "dynamic": True,
                          "cards": [{"page": "lighting", "section": "Ground Floor", "card": "custom:light-control-card|area:kitchen"}]}]},
    }
}


class CleanTest(unittest.TestCase):
    def test_a_good_layout_is_kept(self):
        out = l.clean_layout(GOOD)
        self.assertEqual(out["pages"]["climate"]["order"], ["Cooling", "Heating"])
        self.assertEqual(out["pages"]["climate"]["cards"]["Heating"], {"order": ["a", "b"], "hidden": ["c"], "added": []})
        own = out["pages"]["home"]["own"][0]
        self.assertEqual((own["title"], own["icon"], own["tone"], own["dynamic"]), ("Evenings", "cd:moon", "lights", True))
        self.assertEqual(len(own["cards"]), 1)

    def test_junk_is_dropped(self):
        for bad in (None, "x", [], {"pages": []}, {"pages": {"Bad Page!": {"order": ["a"]}}}):
            self.assertEqual(l.clean_layout(bad), {"pages": {}})

    def test_a_page_with_no_changes_is_not_kept(self):
        self.assertEqual(l.clean_layout({"pages": {"climate": {"order": [], "hidden": []}}}), {"pages": {}})

    def test_ids_are_distinct_short_strings(self):
        out = l.clean_layout({"pages": {"home": {"order": ["a", "a", 5, "", "b", "x" * 500]}}})
        self.assertEqual(out["pages"]["home"]["order"], ["a", "b"])

    def test_own_sections_only_on_the_pages_that_allow_them(self):
        own = [{"id": "s1", "title": "Mine", "cards": []}]
        self.assertEqual(len(l.clean_layout({"pages": {"home": {"own": own}}})["pages"]["home"]["own"]), 1)
        self.assertEqual(l.clean_layout({"pages": {"climate": {"own": own}}}), {"pages": {}})

    def test_an_own_section_needs_an_id_and_a_name(self):
        self.assertIsNone(l.clean_own_section({"id": "a b", "title": "x"}))
        self.assertIsNone(l.clean_own_section({"id": "ok", "title": "  "}))
        self.assertIsNone(l.clean_own_section("x"))

    def test_an_own_section_falls_back_to_safe_looks(self):
        s = l.clean_own_section({"id": "ok", "title": "T" * 60, "icon": "mdi:skull", "tone": "neon"})
        self.assertEqual((s["icon"], s["tone"], s["dynamic"], len(s["title"])), ("cd:palette", "auto", False, l.MAX_TITLE))

    def test_own_cards_are_references_without_repeats(self):
        ref = {"page": "climate", "section": "Heating", "card": "x"}
        s = l.clean_own_section({"id": "ok", "title": "T", "cards": [ref, ref, {"page": "Bad!", "section": "s", "card": "c"}, "x"]})
        self.assertEqual(s["cards"], [ref])

    def test_the_same_card_can_be_in_more_than_one_section(self):
        ref = {"page": "climate", "section": "Heating", "card": "x"}
        out = l.clean_layout({"pages": {"home": {"own": [{"id": "a", "title": "A", "cards": [ref]}, {"id": "b", "title": "B", "cards": [ref]}]}}})
        self.assertEqual(len(out["pages"]["home"]["own"]), 2)

    def test_at_most_twelve_own_sections(self):
        own = [{"id": f"s{i}", "title": "T", "cards": []} for i in range(20)]
        self.assertEqual(len(l.clean_layout({"pages": {"home": {"own": own}}})["pages"]["home"]["own"]), l.MAX_OWN_SECTIONS)


class AddedTest(unittest.TestCase):
    def test_a_house_section_keeps_what_was_added_to_it(self):
        added = [{"entity": "light.hall"}, {"card": {"page": "lighting", "section": "Ground Floor", "card": "x"}}]
        out = l.clean_layout({"pages": {"home": {"cards": {"Lights": {"added": added}}}}})
        self.assertEqual(out["pages"]["home"]["cards"]["Lights"]["added"], added)

    def test_bad_additions_are_dropped_and_repeats_kept_once(self):
        bad = [{"entity": "not an entity"}, {"entity": 5}, {"card": {"page": "Bad!", "section": "s", "card": "c"}}, "x", {"entity": "light.a"}, {"entity": "light.a"}]
        out = l.clean_layout({"pages": {"home": {"cards": {"Lights": {"added": bad}}}}})
        self.assertEqual(out["pages"]["home"]["cards"]["Lights"]["added"], [{"entity": "light.a"}])

    def test_a_section_with_only_additions_is_kept(self):
        out = l.clean_layout({"pages": {"home": {"cards": {"Security": {"added": [{"entity": "lock.front"}]}}}}})
        self.assertIn("Security", out["pages"]["home"]["cards"])

    def test_at_most_the_most_are_kept(self):
        many = [{"entity": f"light.l{i}"} for i in range(40)]
        out = l.clean_layout({"pages": {"home": {"cards": {"Lights": {"added": many}}}}})
        self.assertEqual(len(out["pages"]["home"]["cards"]["Lights"]["added"]), l.MAX_ADDED)


if __name__ == "__main__":
    unittest.main()
