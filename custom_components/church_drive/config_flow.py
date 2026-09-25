"""Config flow for Church Drive: one entry, nothing to fill in."""

from __future__ import annotations

from typing import Any

from homeassistant.config_entries import ConfigFlow, ConfigFlowResult

from .const import DOMAIN


class ChurchDriveConfigFlow(ConfigFlow, domain=DOMAIN):
    """Add Church Drive."""

    VERSION = 1

    async def async_step_user(
        self, user_input: dict[str, Any] | None = None
    ) -> ConfigFlowResult:
        """Create the single entry straight away."""
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()
        return self.async_create_entry(title="Church Drive", data={})
