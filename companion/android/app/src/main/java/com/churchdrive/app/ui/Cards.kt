package com.churchdrive.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import com.churchdrive.app.ha.text
import org.json.JSONObject
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)

/** The shared look of every entity card: a rounded card tinted in the entity's tone. */
@Composable
fun EntityCard(
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { body() }
    }
}

/** An icon in a tinted circle, as used at the start of a card or section header. */
@Composable
fun ToneIcon(icon: ImageVector, tone: ToneColors, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** An icon drawn from its name (`mdi:` or `phu:`) in a tinted circle; [fallback] shows when the name isn't known. */
@Composable
fun ToneIconName(name: String?, fallback: ImageVector, tone: ToneColors, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        HaIcon(name, fallback, tone.accent, (size * 0.55f).dp)
    }
}

/** One of a row of mutually exclusive choices; the current one is filled in the tone's strong colour. */
@Composable
fun ChoiceButton(
    label: String,
    selected: Boolean,
    tone: ToneColors,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val padding = PaddingValues(horizontal = 4.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = tone.accent, contentColor = tone.onAccent),
        ) { Text(label, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
            border = BorderStroke(1.dp, content.copy(alpha = 0.4f)),
        ) { Text(label, maxLines = 1) }
    }
}

// ---- Alarm ----

const val ALARM_ENTITY = "alarm_control_panel.church_drive_alarm"

private data class AlarmInfo(val label: String, val icon: String)

/** The dashboard's icon for an alarm state, e.g. `mdi:shield-home`. */
fun alarmIconName(state: String?): String = ALARM_STATES[state]?.icon ?: "mdi:shield-lock"

private val ALARM_STATES = mapOf(
    "disarmed" to AlarmInfo("Disarmed", "mdi:shield-off-outline"),
    "armed_home" to AlarmInfo("Armed Home", "mdi:shield-home"),
    "armed_away" to AlarmInfo("Armed Away", "mdi:shield-lock"),
    "armed_night" to AlarmInfo("Armed Night", "mdi:shield-moon"),
    "arming" to AlarmInfo("Arming", "mdi:shield-sync"),
    "pending" to AlarmInfo("Entry Delay", "mdi:shield-sync"),
    "triggered" to AlarmInfo("Triggered!", "mdi:shield-alert"),
)

private val ALARM_MODE_NAMES = mapOf("armed_home" to "Home", "armed_away" to "Away", "armed_night" to "Night")

fun alarmLabel(state: String?): String = when (state) {
    null -> "Loading…"
    else -> ALARM_STATES[state]?.label ?: state.replaceFirstChar { it.uppercase() }
}

private val ALARM_TIME = java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm", java.util.Locale.UK)
    .withZone(java.time.ZoneId.systemDefault())

private fun alarmTime(iso: String?): String? =
    runCatching { ALARM_TIME.format(java.time.Instant.parse(iso)) }.getOrNull()

/** The two lines beside the shield: what to do during a delay, otherwise who armed or disarmed it, and when. */
private fun alarmLines(alarm: EntityState): Pair<String, String> {
    fun by(who: String?, at: String?) = alarmTime(at)?.let { t -> who?.takeIf { it.isNotBlank() }?.let { it to t } }
    return when (alarm.state) {
        "arming" -> "Leave now" to "until armed"
        "pending" -> "Disarm now" to "until the alarm sounds"
        "triggered" -> "Alarm sounding" to "Disarm to stop it"
        "disarmed" -> by(alarm.str("lastDisarmedBy"), alarm.str("lastDisarmedTime"))?.let { "Disarmed by ${it.first}" to it.second }
        else -> by(alarm.str("lastArmedBy"), alarm.str("lastArmedTime"))?.let { "Armed by ${it.first}" to it.second }
    } ?: ("" to "")
}

/**
 * The dashboard's alarm card: the state as the title in its colour, a shield inside a ring that empties
 * during an entry or exit delay, what to do (or who and when), and a button for each mode the alarm supports.
 */
@Composable
fun AlarmCard(alarm: EntityState?, call: CallService, entityId: String = ALARM_ENTITY) {
    val state = alarm?.state
    val tone = toneColors(alarmTone(state))
    val triggered = state == "triggered"
    // Triggered is fully tinted, like the dashboard card.
    val container = if (triggered) tone.accent else tone.container
    val content = if (triggered) tone.onAccent else tone.onContainer
    val ring = if (triggered) tone.onAccent else tone.accent
    val enabled = alarm != null
    fun send(service: String) = call("alarm_control_panel", service, entityId, data())

    val inDelay = state == "arming" || state == "pending"
    val secsLeft = when (state) {
        "pending" -> alarm?.num("entrySecondsLeft")?.toInt() ?: 0
        "arming" -> alarm?.num("exitSecondsLeft")?.toInt() ?: 0
        else -> 0
    }
    // Count down locally between updates; the ring is full at the start of a delay.
    var remaining by remember(state, secsLeft) { mutableIntStateOf(secsLeft) }
    val total = remember(state) { secsLeft.coerceAtLeast(1) }
    LaunchedEffect(state, secsLeft) {
        remaining = secsLeft
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }

    val target = ALARM_MODE_NAMES[alarm?.str("targetState")]
    val title = when {
        state == "arming" && target != null -> "Arming $target"
        else -> alarmLabel(state)
    }
    val (line1, line2) = alarm?.let { alarmLines(it) } ?: ("" to "")
    val features = alarm?.num("supported_features")?.toInt() ?: 0
    // During a delay the mode being armed is lit; otherwise the current state.
    val activeKey = if (inDelay || triggered) alarm?.str("targetState") else state

    EntityCard(container, content) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = if (triggered) content else tone.accent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                Canvas(Modifier.size(64.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(ring.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    val sweep = if (inDelay) 360f * (remaining.toFloat() / total).coerceIn(0f, 1f) else 360f
                    drawArc(ring, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                HaIcon(ALARM_STATES[state]?.icon ?: "mdi:shield-outline", Icons.Filled.Security, ring, 30.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (line1.isNotEmpty()) Text(line1, style = MaterialTheme.typography.titleMedium)
                if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodyMedium)
            }
            if (inDelay) Text("${remaining}s", style = MaterialTheme.typography.headlineMedium, color = ring)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            ModeTile("Disarm", "mdi:shield-off-outline", activeKey == "disarmed", tone, content, m, enabled) { send("alarm_disarm") }
            if (features and 1 != 0) {
                ModeTile("Home", "mdi:shield-home", activeKey == "armed_home", tone, content, m, enabled) { send("alarm_arm_home") }
            }
            if (features and 2 != 0) {
                ModeTile("Away", "mdi:shield-lock", activeKey == "armed_away", tone, content, m, enabled) { send("alarm_arm_away") }
            }
            if (features and 4 != 0) {
                ModeTile("Night", "mdi:shield-moon", activeKey == "armed_night", tone, content, m, enabled) { send("alarm_arm_night") }
            }
        }
    }
}

/** A mode button: the dashboard's icon over the label; the current mode is filled in the state colour. */
@Composable
private fun ModeTile(
    label: String,
    icon: String,
    selected: Boolean,
    tone: ToneColors,
    content: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val ink = if (selected) tone.onAccent else content
    Column(
        modifier = modifier
            .height(Ui.TallTileHeight)
            .clip(shape)
            .then(
                if (selected) Modifier.background(tone.accent)
                else Modifier.border(1.dp, content.copy(alpha = 0.4f), shape),
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HaIcon(icon, Icons.Filled.Security, ink, 24.dp)
        CentredText(label, ink, 12.sp, Modifier.padding(top = 4.dp), lineHeight = 14.sp)
    }
}

// ---- Climate ----

const val CLIMATE_ENTITY = "climate.downstairs"

/** Off grey, Eco green, cooling blue, otherwise heating orange: the same as the dashboard's thermostat. */
fun climateTone(c: EntityState?): Tone = when {
    c == null || c.state == "off" || !c.available -> Tone.Grey
    c.str("preset_mode") == "eco" -> Tone.Green
    c.str("hvac_action") == "cooling" -> Tone.Blue
    else -> Tone.Orange
}

fun climateWord(c: EntityState?): String = when {
    c == null -> "Loading…"
    c.state == "off" -> "Off"
    c.str("preset_mode") == "eco" -> "Eco"
    c.str("hvac_action") == "heating" -> "Heating"
    c.str("hvac_action") == "cooling" -> "Cooling"
    else -> "Idle"
}

fun temp(v: Double?): String = if (v == null) "–" else "%.1f".format(v).removeSuffix(".0")

/** A shortcut button on a thermostat card: a heating mode or a preset. */
data class QuickSetting(val name: String, val hvacMode: String?, val presetMode: String?)

private val DEFAULT_QUICK = listOf(QuickSetting("Off", "off", null), QuickSetting("Heat", "heat", null), QuickSetting("Eco", null, "eco"))

/** The `quick_settings` of a dashboard climate card, or null for the usual Off, Heat and Eco. */
fun quickSettings(config: JSONObject): List<QuickSetting>? {
    val a = config.optJSONArray("quick_settings") ?: return null
    val list = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        QuickSetting(o.optString("name"), o.text("hvac_mode"), o.text("preset_mode"))
    }
    return list.ifEmpty { null }
}

@Composable
fun ClimateCard(
    climate: EntityState?,
    call: CallService,
    id: String = CLIMATE_ENTITY,
    humidity: EntityState? = null,
    outdoor: Double? = null,
    quick: List<QuickSetting>? = null,
    quality: Int? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val tone = toneColors(climateTone(climate))
    val enabled = climate?.available == true
    val target = climate?.num("temperature")
    val step = climate?.num("target_temp_step") ?: 0.5
    // Taps build on what was last shown, so quick presses add up.
    var pending by remember(target) { mutableStateOf(target) }
    fun nudge(by: Double) {
        val next = ((pending ?: return) + by).coerceIn(climate?.num("min_temp") ?: 5.0, climate?.num("max_temp") ?: 30.0)
        pending = next
        call("climate", "set_temperature", id, data("temperature" to next))
    }

    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${temp(climate?.num("current_temperature"))}°",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    buildString {
                        append(climateWord(climate))
                        if (climate != null && climate.state != "off") append(" · target ${temp(pending)}°")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton(Icons.Filled.Remove, "Lower", tone, enabled && climate?.state != "off") { nudge(-step) }
                StepButton(Icons.Filled.Add, "Raise", tone, enabled && climate?.state != "off") { nudge(step) }
            }
        }
        val extra = listOfNotNull(
            quality?.let { "Home climate $it/100 · ${qualityWord(it)}" },
            humidity?.state?.toDoubleOrNull()?.let { "Humidity ${temp(it)}%" },
            outdoor?.let { "Outside ${temp(it)}°" },
        ).joinToString(" · ")
        if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodyMedium)
        val eco = climate?.str("preset_mode") == "eco"
        TileRow(
            (quick ?: DEFAULT_QUICK).map { q ->
                val selected = when {
                    q.hvacMode != null -> climate?.state == q.hvacMode && (q.hvacMode == "off" || !eco)
                    q.presetMode != null -> climate?.str("preset_mode") == q.presetMode && climate?.state != "off"
                    else -> false
                }
                TileItem(quickSettingIcon(q), q.name, selected) {
                    if (q.hvacMode != null) call("climate", "set_hvac_mode", id, data("hvac_mode" to q.hvacMode))
                    else if (q.presetMode != null) call("climate", "set_preset_mode", id, data("preset_mode" to q.presetMode))
                }
            },
            tone, tone.onContainer, enabled, perRow = 5,
        )
        extra?.invoke()
    }
}

@Composable
fun StepButton(icon: ImageVector, description: String, tone: ToneColors, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = tone.accent, contentColor = tone.onAccent),
    ) { Icon(icon, contentDescription = description) }
}

// ---- Vacuum ----

const val VACUUM_ENTITY = "vacuum.gregg"
const val VACUUM_BATTERY = "sensor.gregg_battery"

fun vacuumTone(v: EntityState?): Tone = when (v?.state) {
    "error" -> Tone.Red
    "cleaning" -> Tone.Blue
    "returning" -> Tone.Blue
    null, "unavailable", "unknown" -> Tone.Grey
    else -> Tone.Blue
}

fun vacuumSummary(v: EntityState?, battery: EntityState?): String {
    val state = v?.state?.replaceFirstChar { it.uppercase() } ?: "Loading…"
    val b = battery?.state?.takeIf { it != "unknown" && it != "unavailable" }
    return if (b != null) "$state · $b%" else state
}

@Composable
fun VacuumCard(vacuum: EntityState?, battery: EntityState?, call: CallService) {
    val tone = toneColors(vacuumTone(vacuum))
    val enabled = vacuum?.available == true
    val cleaning = vacuum?.state == "cleaning"
    EntityCard(tone.container, tone.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToneIcon(Icons.Filled.CleaningServices, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(vacuum?.friendlyName ?: "Vacuum", style = MaterialTheme.typography.titleMedium)
                Text(vacuumSummary(vacuum, battery), style = MaterialTheme.typography.bodyMedium)
            }
        }
        IconRow(
            listOf(
                IconItem(if (cleaning) "mdi:pause" else "mdi:play", if (cleaning) "Pause" else if (vacuum?.state == "paused") "Resume" else "Start", true) {
                    call("vacuum", if (cleaning) "pause" else "start", VACUUM_ENTITY, data())
                },
                IconItem("mdi:home-import-outline", "Dock", false) { call("vacuum", "return_to_base", VACUUM_ENTITY, data()) },
            ),
            tone, tone.onContainer, enabled,
        )
    }
}

// ---- Car charger (Zappi) ----

const val ZAPPI_MODE = "select.zappi_charge_mode"
const val ZAPPI_POWER = "sensor.zappi_charging_power"
const val ZAPPI_STATUS = "sensor.zappi_status"
const val ZAPPI_PLUG = "sensor.zappi_plug_status"
const val ZAPPI_SESSION = "sensor.zappi_charge_added_session"

@Composable
fun ChargerCard(entities: Map<String, EntityState>, call: CallService) {
    val mode = entities[ZAPPI_MODE]
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val charging = power > 0
    val tone = toneColors(if (charging) Tone.Teal else Tone.Grey)
    val session = entities[ZAPPI_SESSION]?.state?.toDoubleOrNull()
    val status = when {
        mode == null -> "Loading…"
        charging -> "Charging · ${"%.1f".format(power / 1000)} kW"
        else -> entities[ZAPPI_PLUG]?.state ?: entities[ZAPPI_STATUS]?.state ?: "Idle"
    }
    EntityCard(tone.container, tone.onContainer) {
        Column {
            Text(status, style = MaterialTheme.typography.titleLarge)
            if (session != null) {
                Text("${temp(session)} kWh this session", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val teal = toneColors(Tone.Teal)
        TileRow(
            chargerModes(mode?.options().orEmpty()).map { m ->
                TileItem(m.icon, m.name, mode?.state == m.key) {
                    call("select", "select_option", ZAPPI_MODE, data("option" to m.key))
                }
            },
            teal, tone.onContainer, mode?.available == true,
        )
    }
}
