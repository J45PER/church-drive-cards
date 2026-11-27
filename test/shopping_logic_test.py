import importlib.util
import itertools
import pathlib
import unittest
from datetime import datetime

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


class Projects(unittest.TestCase):
    def test_a_project_is_a_checklist_that_is_not_a_task(self):
        self.assertEqual(sl.kind_of({"kind": "project"}), "project")
        self.assertEqual(sl.kind_of({"kind": "other"}), "shopping")
        items, _ = sl.add_items([], ["Get a ladder."], nid, "project")
        self.assertEqual(items[0]["text"], "Get a ladder")
        with self.assertRaises(ValueError):
            sl.set_status(items, items[0]["id"], "unavailable", "project")
        self.assertEqual(sl.set_status(items, items[0]["id"], "bought", "project")[0]["status"], "bought")

    def test_a_project_has_no_reminder_on_its_date(self):
        project = {"id": "p", "people": ["Jamie"], "date": "2026-12-01", "done": False, "kind": "project", "items": []}
        steps = {**project, "id": "s", "kind": "steps"}
        self.assertEqual([l["id"] for l in sl.due_today([project, steps], "2026-12-01")], ["s"])

    def test_a_project_never_takes_shared_shopping(self):
        project = {"id": "p", "people": ["Jamie"], "date": "", "created": "1", "done": False, "kind": "project", "items": []}
        self.assertIsNone(sl.pick_list([project], "Jamie"))


class Rename(unittest.TestCase):
    def test_a_step_can_be_renamed_and_keeps_its_status(self):
        items, _ = sl.add_items([], ["Buy a ladder", "Paint"], nid, "project")
        items = sl.set_status(items, items[0]["id"], "bought", "project")
        out = sl.rename_item(items, items[0]["id"], "  get the ladder. ", "project")
        self.assertEqual((out[0]["text"], out[0]["status"], out[1]["text"]), ("Get the ladder", "bought", "Paint"))

    def test_a_rename_cannot_be_empty_or_clash_or_miss(self):
        items, _ = sl.add_items([], ["One", "Two"], nid, "project")
        with self.assertRaises(ValueError):
            sl.rename_item(items, items[0]["id"], " . ", "project")
        with self.assertRaises(ValueError):
            sl.rename_item(items, items[0]["id"], "two", "project")
        with self.assertRaises(KeyError):
            sl.rename_item(items, "nope", "Three", "project")


class ShoppingMainList(unittest.TestCase):
    def test_what_was_typed_gives_a_name_a_quantity_and_a_note(self):
        self.assertEqual(sl.parse_item("milk 2"), ("Milk", 2, ""))
        self.assertEqual(sl.parse_item("2x milk"), ("Milk", 2, ""))
        self.assertEqual(sl.parse_item("milk x2"), ("Milk", 2, ""))
        self.assertEqual(sl.parse_item("Milk (semi-skimmed)"), ("Milk", 1, "semi-skimmed"))
        self.assertEqual(sl.parse_item("bread - seeded"), ("Bread", 1, "seeded"))
        self.assertEqual(sl.parse_item("buy eggs"), ("Eggs", 1, ""))
        # A step is left as written.
        self.assertEqual(sl.parse_item("Call 2 plumbers", sl.STEPS), ("Call 2 plumbers", 1, ""))

    def test_items_are_sorted_into_aisles(self):
        for text, cat in [("Bananas", "Fruit and veg"), ("Cheddar", "Dairy"), ("Sourdough loaf", "Bakery"), ("Kitchen roll", "Household"),
                          ("Tea bags", "Drinks"), ("Smoked salmon", "Meat and fish"), ("Lightbulbs", "Other")]:
            self.assertEqual(sl.guess_category(text), cat, text)

    def test_an_added_item_carries_its_quantity_note_and_aisle(self):
        items, _ = sl.add_items([], ["milk 2", {"text": "Wine", "cat": "Drinks", "note": "red"}], nid)
        self.assertEqual((items[0]["text"], items[0]["qty"], items[0]["cat"]), ("Milk", 2, "Dairy"))
        self.assertEqual((items[1]["qty"], items[1]["note"], items[1]["cat"]), (1, "red", "Drinks"))
        # Steps stay plain.
        steps, _ = sl.add_items([], ["Call 2 plumbers"], nid, sl.STEPS)
        self.assertNotIn("qty", steps[0])

    def test_asking_again_changes_the_quantity_and_reopens(self):
        items, _ = sl.add_items([], ["milk"], nid)
        items = sl.set_status(items, items[0]["id"], sl.BOUGHT)
        items, n = sl.add_items(items, ["milk 3"], nid)
        self.assertEqual((n, items[0]["status"], items[0]["qty"]), (1, sl.OPEN, 3))

    def test_quantity_note_and_aisle_can_be_changed(self):
        items, _ = sl.add_items([], ["milk"], nid)
        items = sl.set_fields(items, items[0]["id"], qty=4, note="oat", cat="Drinks")
        self.assertEqual((items[0]["qty"], items[0]["note"], items[0]["cat"]), (4, "oat", "Drinks"))
        with self.assertRaises(ValueError):
            sl.set_fields(items, items[0]["id"], cat="Nonsense")
        with self.assertRaises(KeyError):
            sl.set_fields(items, "nope", qty=1)

    def test_older_items_get_the_new_fields(self):
        self.assertEqual(sl.with_defaults({"id": "a", "text": "Eggs", "status": "open"}), {"id": "a", "text": "Eggs", "status": "open", "qty": 1, "note": "", "cat": "Dairy"})

    def test_what_is_bought_is_remembered_and_suggested_when_due(self):
        h = {}
        for day in ("2026-10-24", "2026-10-31", "2026-11-07", "2026-11-14"):
            h = sl.record_bought(h, "Bread", "Bakery", day)
        h = sl.record_bought(h, "Caviar", "Other", "2026-11-14")
        # Bought weekly: not due 3 days after, due 7 days after; once is not a habit.
        self.assertEqual(sl.suggestions(h, "2026-11-17", [], {}), [])
        got = sl.suggestions(h, "2026-11-21", [], {})
        self.assertEqual([(g["text"], g["cat"], g["reason"]) for g in got], [("Bread", "Bakery", "most weeks")])
        # Not suggested when it is already on the list, or after it was dismissed.
        self.assertEqual(sl.suggestions(h, "2026-11-21", ["bread"], {}), [])
        self.assertEqual(sl.suggestions(h, "2026-11-21", [], {"bread": sl.dismissed_until("2026-11-21")}), [])
        self.assertEqual(len(sl.suggestions(h, "2026-12-30", [], {"bread": "2026-12-01"})), 1)

    def test_reasons_read_like_a_person_would_say_them(self):
        self.assertEqual(sl.reason_for(7), "most weeks")
        self.assertEqual(sl.reason_for(21), "every 3 weeks")
        self.assertEqual(sl.reason_for(3), "every 3 days")

    def test_bought_items_leave_the_main_list_after_a_day(self):
        items = [
            {"id": "a", "text": "Old", "status": "bought", "bought_at": "2026-11-20T09:00:00+00:00"},
            {"id": "b", "text": "New", "status": "bought", "bought_at": "2026-11-21T09:00:00+00:00"},
            {"id": "c", "text": "Open", "status": "open"},
        ]
        kept = sl.clear_old_bought(items, "2026-11-21T12:00:00+00:00")
        self.assertEqual([i["id"] for i in kept], ["b", "c"])


class ShoppingUsualShop(unittest.TestCase):
    def shops(self, *days, place="Tesco Extra", time="10:07"):
        h = []
        for d in days:
            h = sl.record_shop(h, d, time, place, 53.0, -1.2)
        return h

    def test_times_round_to_the_half_hour(self):
        self.assertEqual([sl.round_time(t) for t in ("10:07", "10:20", "23:50", "09:45")], ["10:00", "10:30", "00:00", "10:00"])

    def test_one_visit_a_day_and_a_dozen_kept(self):
        h = self.shops("2026-11-07", "2026-11-07")
        self.assertEqual(len(h), 1)
        many = self.shops(*[f"2026-{m:02d}-{d:02d}" for m, d in [(1, 3), (1, 10), (1, 17), (1, 24), (1, 31), (2, 7), (2, 14), (2, 21), (2, 28), (3, 7), (3, 14), (3, 21), (3, 28), (4, 4)]])
        self.assertEqual(len(many), sl.MAX_HABITS)

    def test_the_usual_shop_needs_three_visits_on_the_same_day_at_the_same_place(self):
        # 7, 14 and 21 Nov 2026 are Saturdays.
        self.assertIsNone(sl.usual_shop(self.shops("2026-11-07", "2026-11-14")))
        usual = sl.usual_shop(self.shops("2026-11-07", "2026-11-14", "2026-11-21"))
        self.assertEqual((usual["dow"], usual["time"], usual["place"]), (5, "10:00", "Tesco Extra"))
        mixed = self.shops("2026-11-07", "2026-11-14") + sl.record_shop([], "2026-11-21", "10:00", "Aldi")
        self.assertIsNone(sl.usual_shop(mixed))

    def test_the_offer_comes_the_evening_before_and_goes_with_a_trip_or_a_no(self):
        usual = sl.usual_shop(self.shops("2026-11-07", "2026-11-14", "2026-11-21"))
        # Next Saturday is 28 Nov 2026.
        self.assertIsNone(sl.offer_for(usual, datetime(2026, 11, 27, 17, 59), set(), 5))
        o = sl.offer_for(usual, datetime(2026, 11, 27, 18, 0), set(), 5)
        self.assertEqual((o["name"], o["date"], o["time"], o["place"], o["day"]), ("Weekly grocery shop", "2026-11-28", "10:00", "Tesco Extra", "Saturday"))
        self.assertIsNotNone(sl.offer_for(usual, datetime(2026, 11, 28, 9, 0), set(), 5))
        # Once the time has passed the next one is a week away, so nothing yet.
        self.assertIsNone(sl.offer_for(usual, datetime(2026, 11, 28, 10, 30), set(), 5))
        self.assertIsNone(sl.offer_for(usual, datetime(2026, 11, 27, 19, 0), {"2026-11-28"}, 5))
        self.assertIsNone(sl.offer_for(usual, datetime(2026, 11, 27, 19, 0), set(), 5, "2026-11-28"))
        self.assertIsNone(sl.offer_for(usual, datetime(2026, 11, 27, 19, 0), set(), 0))
        self.assertIsNone(sl.offer_for(None, datetime(2026, 11, 27, 19, 0), set(), 5))


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
