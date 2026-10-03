package com.churchdrive.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.churchdrive.app.MainActivity
import com.churchdrive.app.R
import com.churchdrive.app.Session
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.HaAuth
import com.churchdrive.app.ha.Refresh
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import kotlin.concurrent.thread

/** A home-screen card that shows one live fact from the house. Tapping it opens the app. */
abstract class CardWidget : AppWidgetProvider() {
    abstract fun look(states: Map<String, EntityState>?): WidgetLook

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread {
            try {
                val look = look(fetchStates(context))
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_card)
                    views.setTextViewText(R.id.widget_title, look.title)
                    views.setTextViewText(R.id.widget_value, look.value)
                    views.setTextViewText(R.id.widget_sub, look.sub)
                    views.setInt(R.id.widget_bg, "setColorFilter", look.colour)
                    views.setOnClickPendingIntent(R.id.widget_root, open)
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val http = OkHttpClient()

        /** Every entity's state from Home Assistant's REST API, or null when signed out or unreachable. Blocking. */
        fun fetchStates(context: Context): Map<String, EntityState>? = runCatching {
            val session = Session(context)
            val url = session.url?.trim()?.trimEnd('/') ?: return null
            var token = session.token ?: return null
            fun get(t: String) = http.newCall(Request.Builder().url("$url/api/states").header("Authorization", "Bearer $t").build()).execute()
            var response = get(token)
            // The access token from a username-and-password sign-in lasts half an hour: refresh it once and try again.
            if (response.code == 401) {
                response.close()
                val refresh = session.refreshToken ?: return null
                val fresh = (HaAuth.refresh(url, refresh) as? Refresh.Fresh) ?: return null
                session.saveAccess(fresh.tokens.access)
                token = fresh.tokens.access
                response = get(token)
            }
            response.use { r ->
                if (!r.isSuccessful) return null
                val list = JSONArray(r.body?.string().orEmpty())
                (0 until list.length()).map { EntityState.fromJson(list.getJSONObject(it)) }.associateBy { it.entityId }
            }
        }.getOrNull()
    }
}

class AlarmWidget : CardWidget() {
    override fun look(states: Map<String, EntityState>?) = WidgetLooks.alarm(states)
}

class LightsWidget : CardWidget() {
    override fun look(states: Map<String, EntityState>?) = WidgetLooks.lights(states)
}

class ClimateWidget : CardWidget() {
    override fun look(states: Map<String, EntityState>?) = WidgetLooks.climate(states)
}
