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


class PlaceTests(unittest.TestCase):
    LIDL = {"name": "Lidl Protaras", "address": "Protaras, Cyprus", "lat": 35.0123456789, "lon": 34.0567891234}

    def test_clean_place_rounds_and_names(self):
        p = sl.clean_place(self.LIDL)
        self.assertEqual((p["lat"], p["lon"]), (35.012346, 34.056789))
        self.assertEqual(sl.clean_place({"lat": 1, "lon": 2})["name"], "Shop")

    def test_clean_place_rejects_bad(self):
        for bad in (None, "zone.home", {"lat": "x", "lon": 1}, {"lat": 91, "lon": 0}, {"lat": 0, "lon": 0}, {"lat": 5}):
            self.assertIsNone(sl.clean_place(bad))

    def test_clean_places_dedupes_and_limits(self):
        many = [{"lat": 50 + i * 0.01, "lon": 1} for i in range(9)]
        self.assertEqual(len(sl.clean_places(many)), sl.MAX_PLACES)
        self.assertEqual(len(sl.clean_places([self.LIDL, dict(self.LIDL)])), 1)

    def test_valid_time(self):
        self.assertEqual(sl.valid_time("9:05"), "09:05")
        self.assertEqual(sl.valid_time("23:59"), "23:59")
        for bad in ("24:00", "9", "", None, "ab:cd"):
            self.assertEqual(sl.valid_time(bad), "")

    def test_near_place(self):
        p = sl.clean_place(self.LIDL)
        self.assertEqual(sl.near_place([p], 35.0124, 34.0568)["name"], "Lidl Protaras")
        self.assertIsNone(sl.near_place([p], 35.02, 34.06))
        self.assertIsNone(sl.near_place([p], None, None))
        self.assertAlmostEqual(sl.distance_m(51.5, 0, 51.5, 0), 0)

    def test_maps_url(self):
        self.assertEqual(sl.maps_url({"lat": 35.1, "lon": 34.2}), "https://www.google.com/maps/dir/?api=1&destination=35.1,34.2")


class Steps(unittest.TestCase):
    def mk_steps(self, *texts):
        items, _ = sl.add_items([], list(texts), nid, sl.STEPS)
        return items

    def test_a_step_keeps_its_words(self):
        self.assertEqual(sl.clean_item("Get a ladder", sl.STEPS), "Get a ladder")
        self.assertEqual(sl.clean_item("- check the Hub.", sl.STEPS), "Check the Hub")
        self.assertEqual(sl.clean_item("Get bread"), "Bread")

    def test_a_steps_task_says_steps_and_carries_the_marker_and_word(self):
        items = self.mk_steps("Check the Hub", "Forward the port")
        self.assertEqual(sl.task_summary("Ring Talk", items, sl.STEPS), "Steps: Ring Talk (0 of 2)")
        self.assertEqual(sl.task_summary("", [], sl.STEPS), "Steps")
        d = sl.task_description("abc123def456", items, sl.STEPS)
        self.assertEqual(d.split("\n")[-1], "church-drive-shopping:abc123def456 steps")
        self.assertIn("• Check the Hub", d)
        self.assertEqual(sl.parse_marker(d), "abc123def456")
        # A shopping task is unchanged.
        self.assertEqual(sl.task_description("abc123def456", items).split("\n")[-1], "church-drive-shopping:abc123def456")

    def test_a_step_is_never_unavailable(self):
        items = self.mk_steps("One")
        with self.assertRaises(ValueError):
            sl.set_status(items, items[0]["id"], sl.UNAVAILABLE, sl.STEPS)
        done = sl.set_status(items, items[0]["id"], sl.BOUGHT, sl.STEPS)
        self.assertTrue(sl.is_done(done))

    def test_shared_shopping_never_goes_on_a_steps_list(self):
        steps = {"id": "s", "people": ["Jamie"], "date": "", "created": "1", "done": False, "kind": "steps", "items": self.mk_steps("One")}
        shop = {"id": "p", "people": ["Jamie"], "date": "", "created": "2", "done": False, "items": mk("Milk")}
        self.assertEqual(sl.pick_list([steps, shop], "Jamie")["id"], "p")
        self.assertIsNone(sl.pick_list([steps], "Jamie"))

    def test_arriving_somewhere_with_steps_says_steps(self):
        steps = {"id": "s", "name": "Ring Talk", "kind": "steps", "items": self.mk_steps("One", "Two")}
        title, message = sl.arrival_message("Home", [steps], [])
        self.assertEqual(title, "At Home: steps to do")
        self.assertIn("Ring Talk (2 to do)", message)
