"""Config flow for Church Drive: one entry; options for the scene rooms and device health."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.config_entries import ConfigEntry, ConfigFlow, ConfigFlowResult, OptionsFlow
from homeassistant.core import callback
from homeassistant.helpers.selector import (
    EntitySelector,
    EntitySelectorConfig,
    SelectOptionDict,
    SelectSelector,
    SelectSelectorConfig,
    SelectSelectorMode,
)

from .const import CONF_HEALTH_ENTITIES, CONF_SCENE_GROUPS, DOMAIN
from .hue import async_get_bridge_api, async_list_groups


class ChurchDriveConfigFlow(ConfigFlow, domain=DOMAIN):
    """Add Church Drive."""

    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        """Create the single entry straight away."""
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()
        return self.async_create_entry(title="Church Drive", data={})

    @staticmethod
    @callback
    def async_get_options_flow(config_entry: ConfigEntry) -> OptionsFlow:
        """Options: universal scene rooms, and devices to watch."""
        return ChurchDriveOptionsFlow()


class ChurchDriveOptionsFlow(OptionsFlow):
    """Universal scenes (Hue rooms and zones) or device health (devices to watch)."""

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        return self.async_show_menu(step_id="init", menu_options=["scenes", "health"])

    def _save(self, user_input: dict[str, Any]) -> ConfigFlowResult:
        return self.async_create_entry(data={**self.config_entry.options, **user_input})

    async def async_step_health(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if user_input is not None:
            return self._save(user_input)
        schema = vol.Schema(
            {
                vol.Optional(
                    CONF_HEALTH_ENTITIES, default=self.config_entry.options.get(CONF_HEALTH_ENTITIES, [])
                ): EntitySelector(EntitySelectorConfig(multiple=True))
            }
        )
        return self.async_show_form(step_id="health", data_schema=schema)

    async def async_step_scenes(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        api = async_get_bridge_api(self.hass)
        if api is None:
            return self.async_abort(reason="hue_not_ready")
        if user_input is not None:
            return self._save(user_input)
        groups = async_list_groups(api)
        current = [g for g in self.config_entry.options.get(CONF_SCENE_GROUPS, []) if g in groups]
        schema = vol.Schema(
            {
                vol.Optional(CONF_SCENE_GROUPS, default=current): SelectSelector(
                    SelectSelectorConfig(
                        options=[SelectOptionDict(value=k, label=v) for k, v in groups.items()],
                        multiple=True,
                        mode=SelectSelectorMode.LIST,
                    )
                )
            }
        )
        return self.async_show_form(step_id="scenes", data_schema=schema)
