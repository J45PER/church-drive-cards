package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.churchdrive.app.house.askHouseForNoticeTest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import com.churchdrive.app.Session
import com.churchdrive.app.ha.MobileApp
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

/** This phone's entry among a person's phones (matched by the name it is registered under), or null if the house doesn't list it. */
internal fun thisPhone(people: List<JSONObject>, me: String, service: String): JSONObject? =
    people.firstOrNull { it.optString("entity_id") == me }?.optJSONArray("phones").items().firstOrNull { it.optString("service") == service }

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
            val phone = thisPhone(d.optJSONArray("people").items(), me, MobileApp.notifyService(Session(context).deviceName ?: ""))
            if (phone != null && !phone.optBoolean("on", true)) {
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Notifications are off for this phone") },
                        supportingContent = {
                            Text(if (isAdmin) "The house isn't sending anything to this phone. Turn it on to get them here." else "The house isn't sending anything to this phone. Ask an administrator to turn it on.")
                        },
                        trailingContent = {
                            if (isAdmin) Switch(checked = false, onCheckedChange = {
                                view.send("church_drive/people/phone", data("person" to me, "service" to phone.optString("service"), "on" to true))
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
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
                        leadingContent = { HaIcon(groupIcon(group), CdVectors.Chat, if (gets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
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

/** A notification as it will look: the words, the camera picture if it has one, and its buttons (from the house's sample for the kind). */
@Composable
private fun NoticePreview(sample: JSONObject?, name: String, note: String, style: NoticeStyle) {
    val title = sample?.optString("title")?.ifBlank { null } ?: name
    val message = sample?.optString("message")?.ifBlank { null } ?: note.ifBlank { "This is how it will look on this phone." }
    val buttons = sample?.optJSONArray("buttons")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Church Drive · now" + if (style == NoticeStyle.Alert) " · rings through silent" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (style == NoticeStyle.Alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (sample?.optBoolean("picture") == true) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("Camera picture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (buttons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { label ->
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            CentredText(label, MaterialTheme.colorScheme.onSecondaryContainer, 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** One kind: a preview, the ways it can appear, and buttons to try it and to open Android's own settings for it. */
@Composable
private fun NoticeKindPage(kind: JSONObject, onBack: () -> Unit) {
    val context = LocalContext.current
    val key = kind.optString("key")
    val critical = kind.optBoolean("critical")
    var chosen by remember(key) { mutableStateOf(styleOf(context, kind)) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val tone = toneColors(Tone.Grey)
    FullPage(kind.optString("name"), onBack) {
        NoticePreview(kind.optJSONObject("sample"), kind.optString("name"), kind.optString("note"), chosen)
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
                    message = "Sending…"
                    scope.launch {
                        // From the house, so it has the real buttons and picture; if the house can't be reached, a plain one made here.
                        val fromHouse = chosen != NoticeStyle.Off && withContext(Dispatchers.IO) { askHouseForNoticeTest(context, key) }
                        message = when {
                            fromHouse -> "Sent from the house: look at the top of your screen. It is how the real one will look, with its buttons and picture."
                            sendNoticeTest(context, kind.optString("name"), chosen) -> "Couldn't reach the house, so this is a plain test (no buttons or picture)."
                            else -> "Nothing sent: it is off, or notifications are not allowed for this app."
                        }
                    }
                },
                TileItem("mdi:cog", "Android settings", false) { openAndroidNoticeSettings(context, chosen) },
            ),
            tone, MaterialTheme.colorScheme.onSurface,
        )
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
