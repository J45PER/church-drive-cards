"""The rules for a fitting with a main light and a ring (custom_components/church_drive/fitting_logic.py)."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "fitting_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "fitting_logic.py"
)
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)


def fitting(main_on=True, mode="white", ring_on=False, ring_rgb=None):
    fit = f.new_fitting()
    fit["main"] = f.clean_main({"on": main_on, "mode": mode, "kelvin": 2700, "rgb": [10, 20, 30], "brightness": 80})
    if ring_rgb:
        fit["ring"] = f.clean_ring({"on": ring_on, "rgb": ring_rgb})
    else:
        fit["ring"]["on"] = ring_on
    return fit


def kinds(ops):
    return [o["op"] for o in ops]


class FittingTest(unittest.TestCase):
    def test_a_flat_ring_is_one_group(self):
        g = f.groups(f.clean_ring({"on": True, "rgb": [255, 0, 0]})["segments"])
        self.assertEqual(1, len(g))
        self.assertEqual(list(range(13)), g[0]["indices"])

    def test_a_gradient_is_a_group_per_look(self):
        segs = [{"rgb": [i * 10, 0, 0], "brightness": 100} for i in range(13)]
        self.assertEqual(13, len(f.groups(segs)))
        segs[1] = dict(segs[0])
        self.assertEqual(12, len(f.groups(segs)))

    def test_white_main_under_a_coloured_ring_sets_the_main_light_first_as_rgb(self):
        ops = f.plan(fitting(), {"main": {"on": True, "mode": "white", "kelvin": 2700, "brightness": 60}, "ring": {"on": True, "rgb": [0, 60, 255]}})
        self.assertEqual(["main_colour", "ring"], kinds(ops))
        self.assertEqual(255, ops[0]["rgb"][0])  # warm white is orange-ish: red is full
        self.assertEqual([0, 60, 255], ops[1]["groups"][0]["rgb"])

    def test_white_main_with_the_ring_off_is_one_white_command(self):
        req = {"main": {"on": True, "mode": "white", "kelvin": 3000}}
        self.assertEqual(["main_white"], kinds(f.plan(fitting(ring_on=False), req)))

    def test_white_main_with_a_coloured_ring_keeps_the_ring(self):
        cur = fitting(ring_on=True, ring_rgb=[0, 0, 255])
        req = {"main": {"on": True, "mode": "white", "kelvin": 3000}}
        ops = f.plan(cur, req)
        self.assertEqual(["main_colour", "ring"], kinds(ops))
        self.assertEqual([0, 0, 255], ops[1]["groups"][0]["rgb"])
        self.assertTrue(f.result(cur, req)["ring"]["on"])

    def test_colour_on_the_main_light_paints_the_ring_unless_asked_to_keep_it(self):
        cur = fitting(ring_on=True, ring_rgb=[255, 0, 0])
        req = {"main": {"on": True, "mode": "colour", "rgb": [0, 0, 255]}}
        self.assertEqual(["main_colour"], kinds(f.plan(cur, req)))
        self.assertEqual([0, 0, 255], f.result(cur, req)["ring"]["segments"][5]["rgb"])
        keep = f.plan(cur, {**req, "keep_ring": True})
        self.assertEqual(["main_colour", "ring"], kinds(keep))
        self.assertEqual([255, 0, 0], keep[1]["groups"][0]["rgb"])
        self.assertEqual([255, 0, 0], f.result(cur, {**req, "keep_ring": True})["ring"]["segments"][5]["rgb"])

    def test_a_ring_on_its_own_first_puts_the_main_light_down_as_a_colour(self):
        ops = f.plan(fitting(main_on=True, mode="white"), {"ring": {"on": True, "rgb": [0, 255, 0]}})
        self.assertEqual(["main_colour", "ring"], kinds(ops))

    def test_a_ring_on_its_own_while_the_device_is_off_powers_it_on_with_the_main_light(self):
        cur = fitting(main_on=False)
        req = {"ring": {"on": True, "rgb": [0, 255, 0]}}
        self.assertEqual(["main_colour", "ring"], kinds(f.plan(cur, req)))
        self.assertTrue(f.result(cur, req)["main"]["on"])

    def test_everything_off_is_one_device_off(self):
        cur = fitting(ring_on=True, ring_rgb=[0, 0, 255])
        self.assertEqual(["device_off"], kinds(f.plan(cur, {"main": {"on": False}, "ring": {"on": False}})))

    def test_nothing_asked_nothing_sent(self):
        self.assertEqual([], f.plan(fitting(), {}))

    def test_a_ramp_runs_from_the_first_colour_to_the_last(self):
        r = f.ramp([[255, 0, 0], [0, 0, 255]], 13)
        self.assertEqual([255, 0, 0], r[0])
        self.assertEqual([0, 0, 255], r[-1])
        self.assertEqual(13, len(r))
        self.assertEqual([[9, 9, 9]] * 3, f.ramp([[9, 9, 9]], 3))

    def test_hybrid_looks_become_requests(self):
        self.assertEqual({"on": False}, f.main_from_look({"t": "off"}))
        self.assertEqual({"on": True, "mode": "colour", "rgb": [255, 0, 0]}, f.main_from_look({"t": "colour", "v": "#ff0000"}))
        main = f.main_from_look({"t": "white", "v": "relax"}, {"mirek": 447, "brightness": 56.25})
        self.assertEqual("white", main["mode"])
        self.assertEqual(2237, main["kelvin"])
        self.assertIsNone(f.main_from_look({"t": "leave"}))
        self.assertIsNone(f.main_from_look({"t": "dynamic", "v": "soho"}))
        ring = f.ring_from_look({"t": "gradient", "v": ["#ff0000", "#0000ff"]})
        self.assertEqual(13, len(ring["segments"]))
        self.assertEqual([255, 0, 0], ring["segments"][0]["rgb"])
        self.assertEqual([0, 0, 255], ring["segments"][-1]["rgb"])
        self.assertEqual({"on": True, "rgb": [0, 255, 0]}, f.ring_from_look({"t": "colour", "v": "#00ff00"}))
        self.assertIsNone(f.ring_from_look({"t": "dynamic", "v": "soho"}))

    def test_kelvin_rgb_is_warm_then_cool(self):
        warm, cool = f.kelvin_rgb(2700), f.kelvin_rgb(6500)
        self.assertEqual(255, warm[0])
        self.assertGreater(cool[2], warm[2])


if __name__ == "__main__":
    unittest.main()
