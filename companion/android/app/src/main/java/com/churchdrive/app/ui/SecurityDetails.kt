package com.churchdrive.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * What the Security page hands to its Details pages (decided 2026-11-17): its status lines ("lines") for the alarm's Details page,
 * and the Safety panel's base station tiles ("safety") for the smoke and CO alarms' Details page. From [DashboardPanels.SECURITY_DETAILS].
 */
val LocalSecurityDetails = compositionLocalOf<List<PanelSpec>> { emptyList() }

/** The three monthly tests, kept by the house (`church_drive/alarm_tests`) and refreshed when they change; null until it answers. */
@Composable
fun rememberAlarmTests(): MutableState<Map<String, AlarmTest>?> {
    val api = LocalHaApi.current
    val state = remember { mutableStateOf<Map<String, AlarmTest>?>(null) }
    var changed by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, changed) { parseAlarmTests(api?.ask("church_drive/alarm_tests", JSONObject()))?.let { state.value = it } }
    DisposableEffect(api) {
        val sub = api?.subscribe("subscribe_events", JSONObject().put("event_type", "church_drive_alarm_tests_changed")) { changed++ } ?: -1
        onDispose { if (sub >= 0) api?.close(sub) }
    }
    return state
}

/**
 * The alarm's Details page (press and hold the alarm card): the Security page's status lines (doors, movement, who's home, smoke
 * and CO, batteries) and, for an administrator, the house alarm's monthly test. Nothing for any other device.
 */
@Composable
fun AlarmDetailsExtras(item: DeviceHealthItem) {
    if (ALARM_ENTITY !in item.entityIds) return
    val lines = LocalSecurityDetails.current.firstOrNull { it.title == "lines" }?.cards?.firstOrNull()?.config
    if (lines != null) {
        Text("Status", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusLines(lines)
    }
    if (LocalIsAdmin.current) {
        val tests = rememberAlarmTests()
        tests.value?.get("alarm")?.let { AlarmTestRow(it, tests) }
    }
}

/**
 * One monthly test as a row that opens in place: its name and when it next runs, and, open, the switch, the day of the month
 * (earlier and later), the time, what it tests, and Cancel and Save.
 */
@Composable
fun AlarmTestRow(test: AlarmTest, tests: MutableState<Map<String, AlarmTest>?>) {
    val api = LocalHaApi.current
    val scope = rememberCoroutineScope()
    val neutral = panelNeutral()
    val ink = neutral.onContainer
    var open by remember { mutableStateOf(false) }
    var enabled by remember(test) { mutableStateOf(test.enabled) }
    var day by remember(test) { mutableIntStateOf(test.day) }
    var time by remember(test) { mutableStateOf(test.time) }
    var message by remember(test) { mutableStateOf<String?>(null) }
    EntityCard(neutral.container, ink) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Close the test schedule" else "Change the test schedule") { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaIcon("cd:calendar-clock", CdVectors.Info, ink, 24.dp)
            Column(Modifier.weight(1f)) {
                Text("${test.name} test", style = MaterialTheme.typography.titleMedium, color = ink)
                if (!open) Text(if (test.enabled) test.next?.let { "Next: $it" } ?: test.summary else "Not scheduled", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            }
            HaIcon(if (open) "cd:chevron-up" else "cd:chevron-right", CdVectors.Info, ink.copy(alpha = 0.7f), 22.dp)
        }
        if (open) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Test each month", style = MaterialTheme.typography.titleSmall, color = ink, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; message = null })
            }
            Text("On the ${ordinalDay(day)} of each month${if (day > 28) " (or the month's last day)" else ""}", style = MaterialTheme.typography.bodyMedium, color = ink)
            TileRow(
                listOf(
                    TileItem("cd:minus", "Earlier", false) { day = if (day <= 1) 31 else day - 1; message = null },
                    TileItem("cd:plus", "Later", false) { day = if (day >= 31) 1 else day + 1; message = null },
                ),
                neutral, ink,
            )
            ScheduleTime("At", time, false, ink) { picked -> picked?.let { time = it; message = null } }
            val what = if (test.kind == "alarm") "Sounds ${test.targets.joinToString(", ").ifBlank { "the indoor siren" }} for about 10 seconds."
            else "Tests ${test.targets.joinToString(", ").ifBlank { "nothing found yet" }}."
            Text("$what Everyone home is told 15 and 5 minutes before.", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f))
            test.lastText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f)) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            TileRow(
                listOf(
                    TileItem("cd:close", "Cancel", false) { enabled = test.enabled; day = test.day; time = test.time; open = false },
                    TileItem("cd:check", "Save", false) {
                        scope.launch {
                            val saved = parseAlarmTests(api?.ask("church_drive/alarm_tests/set", alarmTestSetBody(test.kind, enabled, day, time)))
                            if (saved != null) { tests.value = saved; open = false }
                            else message = "The house didn't accept that${api?.lastErrorCode?.let { " ($it)" }.orEmpty()}."
                        }
                    },
                ),
                neutral, ink,
            )
        }
    }
}

/**
 * The smoke and CO alarms' Details page (press and hold the Smoke & CO Alarms card): each alarm's state, battery, last check-in,
 * end of life and whether it's silenced; the base station's tiles; and, for an administrator, the smoke and CO monthly tests.
 */
@Composable
fun SafetyDetailsPage(entities: Map<String, EntityState>, registry: Registry, call: CallService, onClose: () -> Unit) {
    val items = safetyItems(entities, registry)
    val tiles = LocalSecurityDetails.current.firstOrNull { it.title == "safety" }?.cards.orEmpty()
    val admin = LocalIsAdmin.current
    val tests = if (admin) rememberAlarmTests() else null
    FullPage("Smoke & CO alarms", onBack = onClose) {
        if (items.isEmpty()) Text("Data not available", style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            val tone = toneColors(if (item.on) Tone.Red else if (item.unavailable) Tone.Grey else Tone.Green)
            val ink = tone.onContainer
            fun sibling(suffix: String) = registry.siblings(item.id).firstOrNull { it.endsWith(suffix) }?.let { entities[it] }
            val lifeEnd = sibling("_is_life_end")?.state == "on"
            val silenced = sibling("_mute_status")?.state == "on"
            EntityCard(tone.container, ink) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIconName(SAFETY_ICONS[item.kind] ?: "cd:smoke-detector", CdVectors.Info, tone, 40)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, color = ink)
                        Text(
                            listOfNotNull(
                                when (item.kind) { "carbon_monoxide" -> "CO alarm"; "heat" -> "Heat alarm"; else -> "Smoke alarm" },
                                item.ppm?.let { "${if (it % 1.0 == 0.0) it.toInt() else it} ppm" },
                                item.battery?.let { "Battery $it%" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f),
                        )
                    }
                    StatusPill(if (item.on) "Alarm" else if (item.unavailable) "Not responding" else "All clear", tone)
                }
                listOfNotNull(
                    item.report?.let { shortDayOf(it)?.let { d -> "Last checked in $d" } },
                    if (lifeEnd) "End of life: replace this alarm" else null,
                    if (silenced) "Silenced" else null,
                ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ink) }
            }
        }
        if (tiles.isNotEmpty()) {
            Text("Base station", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tiles.forEach { TileCard(it.config, entities, call) }
        }
        tests?.value?.let { t ->
            Text("Monthly tests", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t["smoke"]?.let { AlarmTestRow(it, tests) }
            t["co"]?.let { AlarmTestRow(it, tests) }
        }
    }
}
