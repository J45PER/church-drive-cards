"""Clive's front door: a conversation agent that hands each request to the fast or the strong Claude agent (clive_router_logic.py).

The Assist pipeline talks to this one. It answers nothing itself: it asks the fast agent (Claude with a small model) for a plain command and the
strong agent (Claude with the bigger model) for everything else, with the same instructions and tools, and gives back the answer as it comes. If the
fast agent is missing or answers with an error, the strong agent answers instead, so choosing it can only make Clive quicker, never silent.
"""

from __future__ import annotations

import logging

from homeassistant.components import conversation
from homeassistant.components.conversation import ConversationEntity, ConversationEntityFeature, ConversationInput, ConversationResult
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import MATCH_ALL
from homeassistant.core import HomeAssistant
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers import intent

from .clive_router_logic import FAST, SIGN_OFFS, STRONG, Router, is_dismissal

_LOGGER = logging.getLogger(__name__)

STRONG_AGENT = "conversation.claude_conversation"
FAST_AGENT = "conversation.claude_conversation_fast"


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([CliveRouter(entry)])


class CliveRouter(ConversationEntity):
    _attr_has_entity_name = True
    _attr_name = "Clive"
    _attr_supported_features = ConversationEntityFeature.CONTROL

    def __init__(self, entry: ConfigEntry) -> None:
        self._attr_unique_id = f"{entry.entry_id}_clive_router"
        self._router = Router()
        self._sign_off = 0

    @property
    def supported_languages(self) -> list[str] | str:
        return MATCH_ALL

    def _fast_available(self) -> bool:
        return er.async_get(self.hass).async_get(FAST_AGENT) is not None and self.hass.states.get(FAST_AGENT) is not None

    async def async_process(self, user_input: ConversationInput) -> ConversationResult:
        """Hand the request on. (Not _async_handle_message: the chosen agent keeps the chat log, so this one does not open a second.)"""
        if is_dismissal(user_input.text):
            # Nothing to think about: a short sign-off, with no question (the app closes after it, and "Anything else?" would be asked of nobody).
            speech = SIGN_OFFS[self._sign_off % len(SIGN_OFFS)]
            self._sign_off += 1
            response = intent.IntentResponse(language=user_input.language)
            response.async_set_speech(speech)
            return ConversationResult(response=response, conversation_id=user_input.conversation_id, continue_conversation=False)
        model = self._router.choose(user_input.text, user_input.conversation_id, self._fast_available())
        result = await self._ask(user_input, FAST_AGENT if model == FAST else STRONG_AGENT)
        if model == FAST and result.response.response_type == intent.IntentResponseType.ERROR:
            _LOGGER.warning("Clive's fast agent could not answer %r (%s): asking the strong one", user_input.text, result.response.error_code if hasattr(result.response, "error_code") else "error")
            model = STRONG
            result = await self._ask(user_input, STRONG_AGENT)
        self._router.remember(result.conversation_id or user_input.conversation_id, model)
        return result

    async def _ask(self, user_input: ConversationInput, agent_id: str) -> ConversationResult:
        return await conversation.async_converse(
            self.hass,
            text=user_input.text,
            conversation_id=user_input.conversation_id,
            context=user_input.context,
            language=user_input.language,
            agent_id=agent_id,
            device_id=user_input.device_id,
            satellite_id=user_input.satellite_id,
            extra_system_prompt=user_input.extra_system_prompt,
        )
