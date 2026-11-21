"""A timer that touches Home Assistant's state must run on the event loop (custom_components/church_drive).

`async_call_later` runs a plain function or lambda on a worker thread, and from there `hass.async_create_task` is refused: the Cloakroom light
turned on with the FP300 but never off, because its off timer was a bare lambda. A callback given to it must be a coroutine function or a
@callback, so a lambda there has to be wrapped in `callback(...)`.
"""
import pathlib
import re
import unittest

FILES = sorted((pathlib.Path(__file__).resolve().parent.parent / "custom_components" / "church_drive").glob("*.py"))


class EventLoopSafetyTest(unittest.TestCase):
    def test_no_timer_is_a_bare_lambda(self):
        bare = re.compile(r"async_call_later\([^\n]*,\s*lambda\b")
        offenders = [f"{f.name}:{n}" for f in FILES for n, line in enumerate(f.read_text().splitlines(), 1) if bare.search(line)]
        self.assertEqual([], offenders, "wrap a lambda given to async_call_later in callback(...)")


if __name__ == "__main__":
    unittest.main()
