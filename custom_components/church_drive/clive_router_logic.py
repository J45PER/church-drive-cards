"""Which model answers Clive: the fast one for plain commands, the strong one for everything else.

A simple command ("turn off the landing light", "close the blind") needs little thinking, and every round trip to the model costs seconds, so a
smaller, quicker model does it. Anything that needs the web, a card, a schedule or judgement, and every follow-up in a conversation the strong
model started, stays with the strong model, so what Clive says does not change. A fast answer that fails is asked again of the strong model.
"""

from __future__ import annotations

import re
from collections import OrderedDict

FAST = "fast"
STRONG = "strong"
MAX_CONVERSATIONS = 200

# What a plain command starts with, after the polite beginning.
_POLITE = r"(?:(?:please|hey|clive|okay|ok|just|can you|could you|would you|will you|can i have|i want you to|i'd like you to)\s+)*"
_COMMAND = re.compile(
    r"^" + _POLITE + r"(?:turn|switch|set|dim|brighten|open|close|shut|raise|lower|start|stop|pause|resume|play|mute|unmute|put|make|increase|decrease|toggle|"
    r"light up|kill|cut)\b",
    re.IGNORECASE,
)
# Words that mean it needs the web, a card, a schedule, a list or some judgement: the strong model's work.
_NOT_PLAIN = re.compile(
    r"\b(?:at|for|in|by|after|before) \d|\b(?:schedule|scheduled|remind|reminder|timer|every|daily|weekly|tonight|tomorrow|today|yesterday|later|until|"
    r"heating|thermostat|temperature|alarm|charge|charger|zappi|car|list|shopping|task|todo|to-do|tasks|"
    r"who|what|when|where|why|how|which|is it|are there|tell me|show me|search|score|result|fixture|news|weather|forecast|price|cost|recipe|"
    r"play(?:ing)? (?:some|a |the song|music|my)|spotify|youtube|netflix|tv|television)\b",
    re.IGNORECASE,
)


def is_plain_command(text: str) -> bool:
    """A short, simple instruction to switch or set something, with nothing that needs the web, a card, a schedule or a list."""
    words = " ".join(str(text or "").split())
    if not words or len(words) > 90 or "?" in words:
        return False
    return bool(_COMMAND.match(words)) and not _NOT_PLAIN.search(words)


# What ends a conversation: the app closes after the answer to these, so the reply is a short sign-off and never a question ("Anything else?").
_DISMISSALS = {
    "no", "nope", "nah", "no thanks", "no thank you", "thanks", "thank you", "cheers", "ok thanks", "okay thanks", "ok thank you", "okay thank you",
    "that's it", "thats it", "that's all", "thats all", "that's it thanks", "that's it thank you", "that's all thanks", "that's all thank you",
    "nothing else", "nothing more", "never mind", "nevermind", "i'm done", "im done", "goodbye", "bye",
}
_NAMES = ("clive", "jarvis")
SIGN_OFFS = ("No problem.", "Right you are.", "Of course.", "Very good.")


def is_dismissal(text: str) -> bool:
    """Someone saying they want nothing more ("no thank you", "that's it", "bye"), nothing else."""
    said = re.sub(r"[^a-z' ]", " ", str(text or "").lower().replace("\u2019", "'"))
    said = " ".join(said.split())
    for name in _NAMES:
        said = said.removeprefix(name + " ").removesuffix(" " + name)
    return said.removesuffix(" please").strip() in _DISMISSALS


class Router:
    """Chooses a model for each request and remembers which one a conversation started with."""

    def __init__(self) -> None:
        self._seen: OrderedDict[str, str] = OrderedDict()

    def choose(self, text: str, conversation_id: str | None, fast_available: bool = True) -> str:
        if not fast_available:
            return STRONG
        # A conversation the strong model started stays with it: its offers ("Shall I go through the forecast?") and the yes that follows need the same one.
        if conversation_id and self._seen.get(conversation_id) == STRONG:
            return STRONG
        return FAST if is_plain_command(text) else STRONG

    def remember(self, conversation_id: str | None, model: str) -> None:
        if not conversation_id:
            return
        self._seen[conversation_id] = model
        self._seen.move_to_end(conversation_id)
        while len(self._seen) > MAX_CONVERSATIONS:
            self._seen.popitem(last=False)
