import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).parent.parent
spec = importlib.util.spec_from_file_location("assistant_logic", ROOT / "custom_components" / "church_drive" / "assistant_logic.py")
al = importlib.util.module_from_spec(spec)
spec.loader.exec_module(al)

SERVED = json.loads((ROOT / "custom_components" / "church_drive" / "frontend" / "clive" / "cards.json").read_text())


class Shipped(unittest.TestCase):
    def test_every_shipped_rule_and_correction_survives_cleaning(self):
        rules = SERVED["done_motions"]["rules"]
        self.assertEqual(al.clean_rules(rules), rules)
        heard = SERVED["heard"]["corrections"]
        self.assertEqual(al.clean_heard(heard), heard)

    def test_the_apps_built_in_copy_is_the_served_one(self):
        asset = json.loads((ROOT / "companion" / "android" / "core" / "src" / "main" / "assets" / "clive-assistant.json").read_text())
        self.assertEqual(asset, {"motions": SERVED["done_motions"]["rules"], "heard": SERVED["heard"]["corrections"]})


class Clean(unittest.TestCase):
    def test_a_rule_needs_an_icon_and_a_motion_the_apps_know(self):
        rules = al.clean_rules([
            {"says": ["open"], "motion": {"type": "swap", "from": "cd:a", "to": "cd:b"}},
            {"icon": ["Kettle"], "motion": {"type": "explode"}},
            {"icon": ["door"], "motion": {"type": "swap", "from": "cd:door", "to": "javascript:alert(1)"}},
            {"is": ["mdi:garage"], "motion": {"type": "wipe", "from": "cd:garage", "to": "cd:garage-open", "dir": "sideways", "ms": 99999}},
            "nonsense",
        ])
        self.assertEqual(rules, [
            {"icon": ["kettle"], "motion": None},
            {"icon": ["door"], "motion": None},
            {"is": ["mdi:garage"], "motion": {"type": "wipe", "ms": 600, "from": "cd:garage", "to": "cd:garage-open", "dir": "down"}},
        ])

    def test_corrections_are_lower_case_words_to_a_name(self):
        self.assertEqual(al.clean_heard({" Hailey ": " Hayley ", "bad word!": "x", "ian": "Ian", "x": 3}), {"hailey": "Hayley"})
        self.assertEqual(al.clean_heard("no"), {})

    def test_first_names(self):
        self.assertEqual(al.first_names(["Jamie Smith", "Hayley", "", "Jamie Jones"]), ["Jamie", "Hayley"])


if __name__ == "__main__":
    unittest.main()
