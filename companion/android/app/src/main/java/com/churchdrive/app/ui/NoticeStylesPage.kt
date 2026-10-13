package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.data
import com.churchdrive.app.house.NoticeStyle
import com.churchdrive.app.house.NoticeStyles
import com.churchdrive.app.house.defaultNoticeStyle
import com.churchdrive.app.house.noticeStylesFor
import com.churchdrive.app.house.openAndroidNoticeSettings
import com.churchdrive.app.house.sendNoticeTest
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.items(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

private fun groupIcon(group: String): String = when (group) {
    "Safety" -> "mdi:fire"
    "Security" -> "mdi:shield-home"
    "House" -> "mdi:home"
    "Energy" -> "mdi:lightning-bolt"
    "Car" -> "mdi:car-electric"
    "System" -> "mdi:cog"
    "People" -> "mdi:account-group"
    "To-dos" -> "mdi:checkbox-marked-circle-outline"
    else -> "mdi:wrench"
}

/** What a kind does on this phone until the person chooses: loud for a safety alarm, otherwise a pop-up. */
private fun styleOf(context: android.content.Context, kind: JSONObject): NoticeStyle =
    NoticeStyles.chosen(context, kind.optString("key")) ?: defaultNoticeStyle(kind.optBoolean("critical"), false, false)

/**
 * "Notification styles" (Account > Settings): every kind of notification the house sends, whether you get it (the same
 * setting as Manager's table, for you only) and how it appears on this phone: an alert that rings through silent, a pop-up
 * (Android's banner), a quiet pop-up, only in the shade, or off. Tap one to choose and to try it.
 */
@Composable
fun NoticeStylesPage(isAdmin: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = rememberWs("church_drive/people")
    var openKind by remember { mutableStateOf<String?>(null) }
    var changes by remember { mutableIntStateOf(0) }
    var confirmOff by remember { mutableStateOf<JSONObject?>(null) }
    FullPage("Notifications", onBack) {
        val d = view.data
        if (d == null) {
            Text(if (view.failed) "The house's notifications couldn't be read. Check the connection and try again." else "Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val me = d.optString("me")
            val kinds = d.optJSONArray("kinds").items().filter { it.optBoolean("available", true) && (isAdmin || !it.optBoolean("admin_only")) }
            Text(
                "Choose which notifications you get, and how each one appears on this phone. Anything marked as a safety alarm can ring through silent mode.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            kindGroups(JSONArray(kinds)).forEach { (group, list) ->
                Text(group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                list.forEach { kind ->
                    val key = kind.optString("key")
                    val gets = me.isBlank() || kindGoesTo(kind, me)
                    val style = remember(changes, key) { styleOf(context, kind) }
                    ListItem(
                        headlineContent = { Text(kind.optString("name")) },
                        supportingContent = {
                            Text(
                                when {
                                    !gets -> "Not sent to you"
                                    else -> style.label + (if (kind.optBoolean("critical")) " · safety alarm" else "")
                                },
                            )
                        },
                        leadingContent = { HaIcon(groupIcon(group), Icons.Filled.Notifications, if (gets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
                        trailingContent = {
                            if (me.isNotBlank()) {
                                Switch(checked = gets, onCheckedChange = { on ->
                                    if (!on && kind.optBoolean("critical")) confirmOff = kind
                                    else view.send("church_drive/people/mine", data("kind" to key, "on" to on))
                                })
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { openKind = key },
                    )
                }
            }
        }
    }
    confirmOff?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmOff = null },
            title = { Text("Stop ${kind.optString("name").lowercase()}?") },
            text = { Text("This is a safety alarm. You will not be told about it on any of your phones.") },
            confirmButton = { TextButton({ view.send("church_drive/people/mine", data("kind" to kind.optString("key"), "on" to false)); confirmOff = null }) { Text("Stop telling me") } },
            dismissButton = { TextButton({ confirmOff = null }) { Text("Keep it on") } },
        )
    }
    val key = openKind
    val kind = key?.let { k -> view.data?.optJSONArray("kinds").items().firstOrNull { it.optString("key") == k } }
    if (kind != null) NoticeKindPage(kind) { openKind = null; changes++ }
}

/** One kind: a preview, the ways it can appear, and buttons to try it and to open Android's own settings for it. */
@Composable
private fun NoticeKindPage(kind: JSONObject, onBack: () -> Unit) {
    val context = LocalContext.current
    val key = kind.optString("key")
    val critical = kind.optBoolean("critical")
    var chosen by remember(key) { mutableStateOf(styleOf(context, kind)) }
    var message by remember { mutableStateOf("") }
    val tone = toneColors(Tone.Grey)
    FullPage(kind.optString("name"), onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Church Drive · now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(kind.optString("name"), style = MaterialTheme.typography.titleMedium)
                Text(kind.optString("note").ifBlank { "This is how it will look on this phone." }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("How it appears on this phone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        noticeStylesFor(critical).forEach { style ->
            Row(
                Modifier.fillMaxWidth().clickable { chosen = style; NoticeStyles.set(context, key, style); message = "" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = chosen == style, onClick = { chosen = style; NoticeStyles.set(context, key, style); message = "" })
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(style.label, style = MaterialTheme.typography.bodyLarge)
                    Text(style.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (critical && chosen != NoticeStyle.Alert) {
            Text("This is a safety alarm: it will not ring through silent mode unless it is set to Alert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TileRow(
            listOf(
                TileItem("mdi:bell-ring", "Send me a test", false) {
                    message = if (sendNoticeTest(context, kind.optString("name"), chosen)) "Sent. Look at the top of your screen." else "Nothing sent: it is off, or notifications are not allowed for this app."
                },
                TileItem("mdi:cog", "Android settings", false) { openAndroidNoticeSettings(context, chosen) },
            ),
            tone, MaterialTheme.colorScheme.onSurface,
        )
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
