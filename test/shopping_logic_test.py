import importlib.util
import itertools
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "shopping_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "shopping_logic.py"
)
sl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sl)
ids = (f"{n:06x}" for n in itertools.count(1))
nid = lambda: next(ids)


def mk(*texts):
    items, _ = sl.add_items([], list(texts), nid)
    return items


class Shopping(unittest.TestCase):
    def test_clean_item(self):
        self.assertEqual(sl.clean_item("- buy some milk."), "Milk")
        self.assertEqual(sl.clean_item("Pick up the dry cleaning"), "The dry cleaning")
        self.assertEqual(sl.clean_item("  hand   soap "), "Hand soap")
        self.assertEqual(sl.clean_item("•  Get bread"), "Bread")

    def test_add_dedupes_and_reopens(self):
        items = mk("Milk", "Eggs")
        items, n = sl.add_items(items, ["buy milk", "Bread"], nid)
        self.assertEqual((n, [i["text"] for i in items]), (1, ["Milk", "Eggs", "Bread"]))
        items = sl.set_status(items, items[0]["id"], sl.BOUGHT)
        items, n = sl.add_items(items, ["milk"], nid)
        self.assertEqual((n, items[0]["status"]), (1, sl.OPEN))

    def test_done_only_when_nothing_open(self):
        items = mk("Milk", "Eggs")
        self.assertFalse(sl.is_done([]))
        self.assertFalse(sl.is_done(items))
        items = sl.set_status(items, items[0]["id"], sl.BOUGHT)
        self.assertFalse(sl.is_done(items))
        items = sl.set_status(items, items[1]["id"], sl.UNAVAILABLE)
        self.assertTrue(sl.is_done(items))
        self.assertEqual(sl.missed_from(items), ["Eggs"])
        with self.assertRaises(ValueError):
            sl.set_status(items, items[0]["id"], "lost")
        with self.assertRaises(KeyError):
            sl.set_status(items, "nope", sl.BOUGHT)

    def test_task_text_and_marker(self):
        items = mk("Milk", "Eggs")
        self.assertEqual(sl.task_summary("Weekly", items), "Shopping: Weekly (0 of 2)")
        self.assertEqual(sl.task_summary("", []), "Shopping")
        d = sl.task_description("abc123def456", items)
        self.assertIn("• Milk", d)
        self.assertEqual(sl.parse_marker(d), "abc123def456")
        self.assertIsNone(sl.parse_marker("Buy milk"))

    def test_missed_memory(self):
        m = sl.merge_missed([], ["Eggs"], "Weekly", "2026-10-11")
        m = sl.merge_missed(m, ["eggs", "Flour"], "Weekly", "2026-10-12")
        self.assertEqual([x["text"] for x in m], ["Eggs", "Flour"])
        self.assertEqual([x["text"] for x in sl.drop_missed(m, ["EGGS"])], ["Flour"])

    def test_picking_and_dates(self):
        a = {"id": "a", "people": ["Jamie"], "date": "", "created": "1", "items": [], "done": False}
        b = {"id": "b", "people": ["Jamie", "Hayley"], "date": "2026-10-14", "created": "2", "items": [], "done": False}
        c = {"id": "c", "people": ["Hayley"], "date": "2026-10-12", "created": "3", "items": [], "done": False}
        d = {"id": "d", "people": ["Jamie"], "date": "2026-10-11", "created": "4", "items": [], "done": True}
        ls = [a, b, c, d]
        self.assertEqual([l["id"] for l in sl.for_person(ls, "Jamie")], ["b", "a"])
        self.assertEqual(sl.pick_list(ls, "Hayley")["id"], "c")
        self.assertIsNone(sl.pick_list(ls, "Diane"))
        self.assertEqual([l["id"] for l in sl.due_today(ls, "2026-10-14")], ["b"])
        self.assertEqual(sl.valid_date("2026-10-14T09:00"), "2026-10-14")
        self.assertEqual(sl.valid_date("tomorrow"), "")

    def test_shop_zones_and_arrival(self):
        self.assertTrue(sl.is_shop_zone("Tesco Extra", set(), "zone.tesco"))
        self.assertTrue(sl.is_shop_zone("Town", {"zone.town"}, "zone.town"))
        self.assertFalse(sl.is_shop_zone("School", set(), "zone.school"))
        l = {"name": "Weekly", "items": mk("Milk", "Eggs")}
        self.assertIn("Weekly (2 to get)", sl.arrival_message("Tesco", [l], [])[1])
        self.assertIn("Eggs", sl.arrival_message("Tesco", [], [{"text": "Eggs"}])[1])
        self.assertIsNone(sl.arrival_message("Tesco", [], []))


if __name__ == "__main__":
    unittest.main()
