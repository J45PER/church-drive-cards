import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "reminder_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "reminder_logic.py"
)
rl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rl)


class ReminderLogic(unittest.TestCase):
    def test_action_ids_round_trip(self):
        a = rl.action_id("snooze", "todo.priorities_jamie", "abc123", "Jamie", "zone.tesco")
        self.assertEqual(
            rl.parse_action_id(a),
            {"kind": "snooze", "todo": "todo.priorities_jamie", "uid": "abc123", "first": "Jamie", "zone": "zone.tesco"},
        )
        self.assertEqual(rl.parse_action_id(rl.action_id("done", "todo.cleaning", "u", "Hayley"))["zone"], "")

    def test_foreign_actions_are_ignored(self):
        for bad in ("", "OPEN", "CD|nuke|todo.x|u|J|", "CD|done|light.x|u|J|", "CD|done|todo.x|u|J", None):
            self.assertIsNone(rl.parse_action_id(bad))

    def test_buttons(self):
        acts = rl.actions_for("todo.cleaning", "u1", "Jamie")
        self.assertEqual([a["title"] for a in acts], ["Done", "Snooze…"])
        self.assertEqual(rl.parse_action_id(acts[1]["action"])["kind"], "snooze")

    def test_snooze_lengths(self):
        self.assertEqual([rl.snooze_minutes(m) for m in (5, "10", 20, 30.0, 60)], [5, 10, 20, 30, 60])
        for bad in (0, 7, 15, 90, "x", None, -5):
            self.assertIsNone(rl.snooze_minutes(bad))

    def test_arrival_matches_the_zone_by_name(self):
        self.assertTrue(rl.arrived_in("home", "zone.home", "Home"))
        self.assertTrue(rl.arrived_in("Tesco", "zone.tesco", "tesco"))
        self.assertFalse(rl.arrived_in("not_home", "zone.tesco", "Tesco"))
        self.assertFalse(rl.arrived_in("Tesco", "zone.gone", None))

    def test_recipients(self):
        everyone = ["Jamie", "Hayley", "Diane", "Ian"]
        self.assertEqual(rl.recipients(["Hayley"], "Jamie", everyone), ["Hayley"])
        self.assertEqual(rl.recipients([], "Jamie", everyone), ["Jamie"])
        self.assertEqual(rl.recipients([], None, everyone), everyone)
        self.assertEqual(rl.recipients(["Everyone"], "Jamie", everyone), everyone)
        self.assertEqual(rl.recipients(["Jamie", "Jamie", " "], None, everyone), ["Jamie"])


if __name__ == "__main__":
    unittest.main()
