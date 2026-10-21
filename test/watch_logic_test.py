import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "watch_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "watch_logic.py"
)
wl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wl)

ALL = ["alarm", "climate", "lights", "cleaning", "car"]


def ids(cfg):
    return [p["id"] for p in cfg["pages"]]


class Pages(unittest.TestCase):
    def test_every_page_is_shown_by_default(self):
        cfg = wl.clean_config(None)
        self.assertEqual(ids(cfg), ALL)
        self.assertTrue(all(p["on"] for p in cfg["pages"]))
        self.assertEqual(cfg, wl.clean_config("rubbish"))

    def test_the_order_given_is_kept(self):
        cfg = wl.clean_config({"pages": [{"id": "car", "on": True}, {"id": "alarm", "on": False}]})
        self.assertEqual(ids(cfg), ["car", "alarm", "climate", "lights", "cleaning"])
        self.assertFalse(cfg["pages"][1]["on"])
        self.assertTrue(cfg["pages"][2]["on"])

    def test_names_alone_are_accepted_and_unknown_or_repeated_ones_dropped(self):
        cfg = wl.clean_config({"pages": ["lights", "lights", "tea", 5, {"id": "nope"}, None]})
        self.assertEqual(ids(cfg), ["lights", "alarm", "climate", "cleaning", "car"])

    def test_it_can_be_run_again_over_its_own_output(self):
        cfg = wl.clean_config({"pages": ["car", "lights"], "hidden": {"climate": ["fan.a"]}, "lights_top": "zones"})
        self.assertEqual(cfg, wl.clean_config(cfg))


class Hidden(unittest.TestCase):
    def test_only_known_pages_and_safe_ids_are_kept(self):
        cfg = wl.clean_config({"hidden": {"climate": ["fan.a", "fan.a", "bad id", 5, "climate.b"], "tea": ["x"], "car": []}})
        self.assertEqual(cfg["hidden"], {"climate": ["fan.a", "climate.b"]})

    def test_a_page_with_nothing_hidden_is_left_out(self):
        self.assertEqual(wl.clean_config({"hidden": {"lights": []}})["hidden"], {})
        self.assertEqual(wl.clean_config({"hidden": "no"})["hidden"], {})

    def test_the_list_is_capped(self):
        cfg = wl.clean_config({"hidden": {"lights": [f"light.l{i}" for i in range(1000)]}})
        self.assertEqual(len(cfg["hidden"]["lights"]), wl.MAX_HIDDEN)


class Lights(unittest.TestCase):
    def test_the_top_level_is_one_of_three(self):
        self.assertEqual(wl.clean_config({"lights_top": "floors"})["lights_top"], "floors")
        self.assertEqual(wl.clean_config({"lights_top": "zones"})["lights_top"], "zones")
        self.assertEqual(wl.clean_config({"lights_top": "attic"})["lights_top"], "rooms")


class Cars(unittest.TestCase):
    CAR = {"name": "Astra", "battery": "sensor.astra_battery", "plugged": "binary_sensor.astra_battery_plugged"}

    def test_a_good_car_is_kept(self):
        self.assertEqual(wl.clean_config({"cars": [self.CAR]})["cars"], [self.CAR])

    def test_a_car_needs_a_name_and_the_right_kinds_of_entity(self):
        bad = [
            {**self.CAR, "name": ""},
            {**self.CAR, "battery": "light.astra"},
            {**self.CAR, "plugged": "sensor.astra_plugged"},
            {**self.CAR, "battery": "Sensor.Astra"},
            {"name": "x"},
            "car",
        ]
        self.assertEqual(wl.clean_config({"cars": bad})["cars"], [])

    def test_there_is_a_limit(self):
        many = [{**self.CAR, "name": f"Car {i}"} for i in range(20)]
        self.assertEqual(len(wl.clean_config({"cars": many})["cars"]), wl.MAX_CARS)


class Clive(unittest.TestCase):
    def test_defaults_and_overrides(self):
        self.assertEqual(wl.clean_config(None)["clive"], {"voice": True, "visual": True, "follow": False, "buzz": True})
        cfg = wl.clean_config({"clive": {"voice": False, "follow": True, "visual": "yes", "extra": 1}})
        self.assertEqual(cfg["clive"], {"voice": False, "visual": True, "follow": True, "buzz": True})


if __name__ == "__main__":
    unittest.main()
