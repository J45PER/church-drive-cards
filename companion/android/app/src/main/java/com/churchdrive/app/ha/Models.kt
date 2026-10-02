package com.churchdrive.app.ha

import org.json.JSONObject

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
) {
    val friendlyName: String
        get() = attributes.optString("friendly_name", entityId)

    companion object {
        fun fromJson(o: JSONObject) = EntityState(
            entityId = o.getString("entity_id"),
            state = o.optString("state", "unknown"),
            attributes = o.optJSONObject("attributes") ?: JSONObject(),
        )
    }
}

enum class ConnectionState { Disconnected, Connecting, Connected, AuthFailed }
