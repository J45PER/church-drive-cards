import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location(
    "clive_router_logic", pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "clive_router_logic.py"
)
cr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cr)


class Plain(unittest.TestCase):
    def test_plain_commands_are_fast(self):
        for text in ["Turn off the landing light", "turn the bedroom fan off", "Please close the living room blind", "Can you switch on the patio light strip",
                     "Dim the kitchen lights to 30%", "set the garden light to blue", "pause the music in the kitchen"]:
            self.assertTrue(cr.is_plain_command(text), text)

    def test_anything_needing_more_is_strong(self):
        for text in ["What's the weather like", "Is the garden light on?", "Who is home", "Turn the heating on", "turn the lights off at 11pm", "schedule the blind to open",
                     "Turn off the lights tonight", "Run the bedroom fan for 30 minutes", "Add milk to the shopping list", "play some music", "hello", "yes please",
                     "Turn on the lights and tell me the score"]:
            self.assertFalse(cr.is_plain_command(text), text)

    def test_empty_and_long_are_strong(self):
        self.assertFalse(cr.is_plain_command(""))
        self.assertFalse(cr.is_plain_command("turn off " + "the light " * 20))


class Routing(unittest.TestCase):
    def test_a_plain_command_goes_to_the_fast_model_when_there_is_one(self):
        r = cr.Router()
        self.assertEqual(r.choose("turn off the landing light", None), cr.FAST)
        self.assertEqual(r.choose("turn off the landing light", None, fast_available=False), cr.STRONG)

    def test_a_conversation_the_strong_model_started_stays_with_it(self):
        r = cr.Router()
        r.remember("c1", cr.STRONG)
        self.assertEqual(r.choose("turn off the landing light", "c1"), cr.STRONG)
        r.remember("c2", cr.FAST)
        self.assertEqual(r.choose("what's the weather", "c2"), cr.STRONG)
        self.assertEqual(r.choose("turn off the fan", "c2"), cr.FAST)

    def test_the_memory_is_bounded(self):
        r = cr.Router()
        for i in range(cr.MAX_CONVERSATIONS + 50):
            r.remember(f"c{i}", cr.STRONG)
        self.assertEqual(r.choose("turn off the fan", "c0"), cr.FAST)
        self.assertEqual(r.choose("turn off the fan", f"c{cr.MAX_CONVERSATIONS + 49}"), cr.STRONG)
