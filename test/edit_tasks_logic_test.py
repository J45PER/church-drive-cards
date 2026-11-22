import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "edit_tasks_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "edit_tasks_logic.py"
)
et = importlib.util.module_from_spec(spec)
spec.loader.exec_module(et)


class EditTasks(unittest.TestCase):
    def test_the_words_of_a_task(self):
        self.assertEqual(et.task_summary({"kind": "palette"}), "Colours edit waiting")
        self.assertEqual(et.task_summary({"kind": "cards"}), "Card colours edit waiting")
        self.assertEqual(et.task_summary({"kind": "layouts"}), "Layout edit waiting")
        self.assertEqual(et.task_summary({"kind": "something_new"}), "Something new edit waiting")

    def test_a_task_is_matched_by_its_marker(self):
        self.assertEqual(et.marker_of(et.task_description("abc123")), "abc123")
        self.assertEqual(et.marker_of("Church Drive edit 9f2-x · for Jamie"), "9f2-x")
        self.assertIsNone(et.marker_of("Buy milk"))
        self.assertIsNone(et.marker_of(None))
