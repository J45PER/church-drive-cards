import importlib.util
import pathlib
import re
import unittest

base = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive"


def load(name):
    spec = importlib.util.spec_from_file_location(name, base / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


ns = load("notice_samples")
kinds_src = (base / "kinds.py").read_text()
KEYS = re.findall(r'\{"key": "([a-z_]+)"', kinds_src)


class SampleTest(unittest.TestCase):
    def test_every_kind_has_words_of_its_own(self):
        missing = [k for k in KEYS if k not in ns.SAMPLES and k not in ("todo_summary", "todo_new", "devices_not_responding", "low_batteries", "filters_due", "safety_alarms", "updates", "vacuum_messages")]
        self.assertEqual(missing, [], "add a sample for these kinds in notice_samples.py")

    def test_a_kind_without_a_sample_says_its_name(self):
        s = ns.sample_for("mystery", "Mystery kind", "A note")
        self.assertEqual((s["title"], s["message"], s["camera"]), ("Mystery kind", "A note", ""))

    def test_an_arrival_names_the_person(self):
        self.assertEqual(ns.sample_for("arrivals:person.jamie", "Jamie gets home or leaves")["title"], "🏠 Jamie is home")

    def test_camera_kinds_point_at_a_camera(self):
        self.assertEqual(ns.sample_for("doorbell")["camera"], "front_door")
        self.assertEqual(ns.sample_for("night_motion")["camera"], "garden")


if __name__ == "__main__":
    unittest.main()
