"""Saved colour rules (custom_components/church_drive/saved_colours_logic.py)."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "saved_colours_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "saved_colours_logic.py"
)
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)


class CleanTest(unittest.TestCase):
    def test_good_colours_are_kept_in_order(self):
        self.assertEqual(s.clean([[28, 100], [195.5, 80]]), [[28.0, 100.0], [195.5, 80.0]])

    def test_junk_is_dropped(self):
        self.assertEqual(s.clean([[10, 20], "oops", [1], [1, 2, 3], ["a", 5], None, [float("nan"), 5]]), [[10.0, 20.0]])
        self.assertEqual(s.clean(None), [])
        self.assertEqual(s.clean("x"), [])

    def test_hue_goes_round_the_wheel_and_saturation_is_held(self):
        self.assertEqual(s.clean([[370, 140], [-10, -5]]), [[10.0, 100.0], [350.0, 0.0]])

    def test_colours_that_look_alike_are_kept_once(self):
        self.assertEqual(len(s.clean([[359, 50], [1, 51], [120, 50]])), 2)

    def test_at_most_eight(self):
        many = [[i * 30, 80] for i in range(12)]
        self.assertEqual(len(s.clean(many)), s.MAX_COLOURS)

    def test_same(self):
        self.assertTrue(s.same((359, 50), (1, 50)))
        self.assertFalse(s.same((10, 50), (40, 50)))


if __name__ == "__main__":
    unittest.main()
