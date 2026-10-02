package com.churchdrive.app.ha

import org.json.JSONObject

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
) {
    val friendlyName: String
        get() = attributes.optString("friendly_name", entityId)

    val available: Boolean get() = state != "unavailable" && state != "unknown"

    /** A numeric attribute, or null when it's missing. */
    fun num(attr: String): Double? =
        if (attributes.has(attr) && !attributes.isNull(attr)) attributes.optDouble(attr).takeIf { !it.isNaN() } else null

    fun str(attr: String): String? =
        if (attributes.has(attr) && !attributes.isNull(attr)) attributes.optString(attr) else null

    fun options(): List<String> =
        attributes.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    companion object {
        fun fromJson(o: JSONObject) = EntityState(
            entityId = o.getString("entity_id"),
            state = o.optString("state", "unknown"),
            attributes = o.optJSONObject("attributes") ?: JSONObject(),
        )
    }
}

enum class ConnectionState { Disconnected, Connecting, Connected, AuthFailed }

/** A service call: domain, service, target entity, extra data. */
typealias CallService = (domain: String, service: String, entityId: String, data: JSONObject) -> Unit

fun data(vararg pairs: Pair<String, Any>): JSONObject = JSONObject().apply { pairs.forEach { put(it.first, it.second) } }
