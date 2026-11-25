import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "notice_icons_logic",
    pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "notice_icons_logic.py",
)
ni = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ni)


class NoticeIconsLogic(unittest.TestCase):
    def test_the_agreed_picks(self):
        self.assertEqual(ni.default_icon("smoke"), "smoke-detector-alert")
        self.assertEqual(ni.default_icon("power_down"), "octopus+power")
        self.assertEqual(ni.default_icon("cheap_rate"), "octopus+pound")
        self.assertEqual(ni.default_icon("internet"), "globe/off")
        self.assertEqual(ni.default_icon("car_not_plugged"), "car+ev-plug")
        self.assertEqual(ni.default_icon("todo_overdue"), "todo-clock-logo")

    def test_arrivals_use_the_house(self):
        self.assertEqual(ni.default_icon("arrivals:person.hayley"), "home")

    def test_the_alarm_mode_follows_the_mode(self):
        self.assertEqual(ni.default_icon("alarm_mode", title="Alarm Set: Home", message="By Jamie"), "shield-home")
        self.assertEqual(ni.default_icon("alarm_mode", title="Alarm Set: Away", message="By Jamie"), "shield-lock")
        self.assertEqual(ni.default_icon("alarm_mode", title="Alarm Set: Night", message="By Jamie"), "shield-moon")
        self.assertEqual(ni.default_icon("alarm_mode", title="Alarm Disarmed", message="By Jamie"), "shield-off")

    def test_end_of_life_follows_the_device(self):
        self.assertEqual(ni.default_icon("safety_alarms", title="Smoke Alarm", message="Entrance: replace"), "smoke-detector-alert")
        self.assertEqual(ni.default_icon("safety_alarms", title="Carbon Monoxide Alarm", message="Kitchen: replace"), "co-alert")

    def test_clive_heating_wears_his_badge(self):
        self.assertEqual(ni.default_icon("clive_actions", title="Heating: Auto"), "thermostat+clive")
        self.assertEqual(ni.default_icon("clive_actions", title="Heating: Eco"), "leaf+clive")

    def test_reminders_and_lists_by_tag(self):
        self.assertEqual(ni.default_icon(None, tag="church-drive-task-bins"), "todo-logo+pin")
        self.assertEqual(ni.default_icon(None, tag="inbox-3"), "todo-logo+alert-circle-solid")
        self.assertEqual(ni.default_icon(None, tag="shopping-today"), "cart")
        self.assertEqual(ni.default_icon(None, tag="something"), "")

    def test_managers_choice_wins_and_cleans(self):
        self.assertEqual(ni.icon_for("smoke", {"smoke": "fire-alert"}), "fire-alert")
        self.assertEqual(ni.icon_for("smoke", {}), "smoke-detector-alert")
        self.assertEqual(ni.clean_icon(" Car+EV-plug "), "car+ev-plug")
        self.assertEqual(ni.clean_icon("<script>"), "")
        self.assertEqual(ni.clean_icon(None), "")


if __name__ == "__main__":
    unittest.main()
