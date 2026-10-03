package com.churchdrive.app.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What the person chose when they added a widget (which rooms, which thermostat, which stats), kept for each widget. */
object WidgetConfig {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    fun get(context: Context, appWidgetId: Int): JSONObject =
        runCatching { JSONObject(prefs(context).getString(appWidgetId.toString(), null) ?: "{}") }.getOrDefault(JSONObject())

    fun save(context: Context, appWidgetId: Int, config: JSONObject) {
        prefs(context).edit().putString(appWidgetId.toString(), config.toString()).apply()
    }

    fun remove(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply { appWidgetIds.forEach { remove(it.toString()) } }.apply()
    }

    fun strings(config: JSONObject, key: String): List<String> =
        config.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }.orEmpty()

    fun put(config: JSONObject, key: String, values: List<String>): JSONObject = config.put(key, JSONArray(values))
}
