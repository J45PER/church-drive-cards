"""Light role rules (custom_components/church_drive/light_roles_logic.py)."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "light_roles_logic", pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive" / "light_roles_logic.py"
)
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


class RolesTest(unittest.TestCase):
    def test_primary_is_the_default_and_isnt_kept(self):
        self.assertEqual(r.clean_roles({"light.a": "primary", "light.b": "ambient", "light.c": "tertiary"}), {"light.b": "ambient", "light.c": "tertiary"})

    def test_rubbish_is_dropped(self):
        self.assertEqual(r.clean_roles({"switch.a": "ambient", "light.b": "boss", 3: "ambient"}), {})
        self.assertEqual(r.clean_roles(None), {})

    def test_a_light_with_no_role_is_primary(self):
        self.assertEqual(r.role_of({"light.b": "ambient"}, "light.a"), "primary")
        self.assertEqual(r.role_of({"light.b": "ambient"}, "light.b"), "ambient")
