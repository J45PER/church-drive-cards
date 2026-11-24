import importlib.util
import pathlib
import unittest
from datetime import datetime as D

spec = importlib.util.spec_from_file_location(
    "task_alerts_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "task_alerts_logic.py"
)
tl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tl)


def task(due="2026-11-24T15:00:00", description=""):
    return {"todo": "todo.a", "uid": "1", "summary": "Call the plumber", "due": due, "description": description}


class Alerts(unittest.TestCase):
    def test_lead_reminds_once_at_its_moment(self):
        leads, sent = {"todo.a|1": 30}, {}
        self.assertEqual(tl.reminders_due([task()], leads, sent, D(2026, 11, 24, 14, 29)), [])
        due = tl.reminders_due([task()], leads, sent, D(2026, 11, 24, 14, 31))
        self.assertEqual([d["lead"] for d in due], [30])
        tl.mark_lead(sent, "todo.a", "1", due[0]["due_at"])
        self.assertEqual(tl.reminders_due([task()], leads, sent, D(2026, 11, 24, 14, 40)), [])

    def test_a_moved_due_time_reminds_again(self):
        leads, sent = {"todo.a|1": 0}, {}
        tl.mark_lead(sent, "todo.a", "1", D(2026, 11, 24, 15, 0))
        self.assertEqual(len(tl.reminders_due([task("2026-11-24T18:00:00")], leads, sent, D(2026, 11, 24, 18, 1))), 1)

    def test_a_reminder_the_house_slept_through_is_dropped(self):
        self.assertEqual(tl.reminders_due([task()], {"todo.a|1": 0}, {}, D(2026, 11, 24, 19, 0)), [])

    def test_only_a_task_with_a_time_has_a_lead_reminder(self):
        self.assertEqual(tl.reminders_due([task("2026-11-24")], {"todo.a|1": 0}, {}, D(2026, 11, 24, 23, 59)), [])

    def test_the_repeat_automation_already_reminds_at_the_time(self):
        t = task(description="Once · for Jamie")
        self.assertEqual(tl.reminders_due([t], {"todo.a|1": 0}, {}, D(2026, 11, 24, 15, 1)), [])
        self.assertEqual(len(tl.reminders_due([t], {"todo.a|1": 15}, {}, D(2026, 11, 24, 14, 46))), 1)

    def test_overdue_is_said_once_then_each_morning(self):
        sent = {}
        out = tl.overdue_now([task()], sent, D(2026, 11, 24, 15, 5))
        self.assertTrue(out[0]["first"])
        tl.mark_overdue(sent, "todo.a", "1", out[0]["due_at"], "2026-11-24")
        self.assertEqual(tl.overdue_now([task()], sent, D(2026, 11, 24, 18, 0)), [])
        self.assertEqual(tl.overdue_now([task()], sent, D(2026, 11, 25, 8, 59)), [])
        morning = tl.overdue_now([task()], sent, D(2026, 11, 25, 9, 0))
        self.assertFalse(morning[0]["first"])
        tl.mark_overdue(sent, "todo.a", "1", morning[0]["due_at"], "2026-11-25")
        self.assertEqual(tl.overdue_now([task()], sent, D(2026, 11, 25, 12, 0)), [])

    def test_a_day_only_task_is_announced_in_the_morning_after(self):
        t = task("2026-11-24")
        self.assertEqual(tl.overdue_now([t], {}, D(2026, 11, 24, 23, 59, 30)), [])
        self.assertEqual(tl.overdue_now([t], {}, D(2026, 11, 25, 8, 0)), [])
        self.assertEqual(len(tl.overdue_now([t], {}, D(2026, 11, 25, 9, 0))), 1)

    def test_not_yet_due_is_not_overdue(self):
        self.assertEqual(tl.overdue_now([task()], {}, D(2026, 11, 24, 14, 0)), [])

    def test_leads_and_words(self):
        self.assertEqual((tl.valid_lead("15"), tl.valid_lead(7), tl.valid_lead(None)), (15, None, None))
        self.assertEqual([tl.lead_text(x) for x in (0, 15, 60, 120, 1440)], ["now", "in 15 minutes", "in 1 hour", "in 2 hours", "tomorrow"])
        self.assertEqual(tl.overdue_words(D(2026, 11, 22, 9, 0), D(2026, 11, 24, 9, 0)), "2 days overdue")

    def test_prune_forgets_closed_tasks(self):
        self.assertEqual(tl.prune({"a|1": {}, "b|2": {}}, {"a|1"}), {"a|1": {}})


if __name__ == "__main__":
    unittest.main()
