package com.churchdrive.app.ha

import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/**
 * Home Assistant's own template engine for the app: the summary line and colour of a dashboard panel are
 * written as templates, and the app asks HA to render them (and keep them up to date), so it says exactly
 * what the dashboard says. Results are Compose state, so anything showing one redraws when it changes.
 */
class Templates(private val client: HaClient) {
    private val results = mutableStateMapOf<String, String>()
    private val wanted = linkedSetOf<String>()
    private val ids = mutableMapOf<String, Int>()

    /** The latest result for [template], or null until HA has sent one. */
    fun value(template: String): String? = results[template]

    /** Asks to be kept up to date with [template]. Safe to call again and again. */
    @Synchronized
    fun watch(template: String) {
        if (wanted.add(template)) start(template)
    }

    /** Starts every wanted template again: after connecting, when the old subscriptions are gone. */
    @Synchronized
    fun restart() {
        ids.clear()
        wanted.forEach { start(it) }
    }

    private fun start(template: String) {
        val id = client.subscribe(
            "render_template",
            JSONObject().put("template", template).put("report_errors", false),
        ) { event -> event.opt("result")?.let { results[template] = it.toString().trim() } }
        if (id >= 0) ids[template] = id
    }
}
