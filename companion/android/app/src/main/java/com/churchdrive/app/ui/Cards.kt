package com.churchdrive.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import com.churchdrive.app.ha.text
import org.json.JSONObject
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)

/** Whether cards can be folded at all. Off for now: the folded look is being revised. */
private val FOLDING_ON = false

/**
 * Lets one card fold down to a strip and back (its own chevron, remembered by [key] while the app is open). A card that draws
 * its own strip when [LocalCompactCards] is on passes [ownStrip]; for any other, [plainTitle] and [plainStatus] make a plain one.
 */
@Composable
fun CollapsibleCard(key: String, ownStrip: Boolean, plainTitle: String = "", plainStatus: String = "", content: @Composable () -> Unit) {
    // Folding is switched off while the folded look is revised: every card is drawn full size, as before.
    if (!FOLDING_ON) {
        content()
        return
    }
    var collapsed by rememberSaveable(key) { mutableStateOf(false) }
    val fold = CardFold(collapsed) { collapsed = !collapsed }
    CompositionLocalProvider(LocalCompactCards provides collapsed, LocalCardFold provides fold) {
        Box(Modifier.fillMaxWidth().animateContentSize()) {
            if (collapsed && !ownStrip) CompactStrip(panelNeutral(), plainTitle, null, plainStatus, dense = true) else content()
            // The same corner, the same size, open or folded, so it does not move. matchParentSize: a card that draws nothing keeps no stray button.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier.padding(top = 22.dp, end = 16.dp).size(32.dp).clip(CircleShape)
                        .clickable(onClickLabel = if (collapsed) "Expand" else "Collapse", onClick = fold.toggle)
                        .semantics { contentDescription = if (collapsed) "Expand" else "Collapse" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (collapsed) CdVectors.ChevronDown else CdVectors.ChevronUp, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Room for the fold chevron at the end of a card's first row, so a value there is not under it. Nothing where the card cannot fold. */
@Composable
fun FoldGutter() {
    if (LocalCardFold.current != null) Spacer(Modifier.width(36.dp))
}

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

private fun alarmTime(iso: String?): String? =
    runCatching { HomeTime.format(java.time.Instant.parse(iso).toEpochMilli(), "dd MMM, HH:mm") }.getOrNull()

/** The sensor the integration keeps with who really changed the alarm (Ring only knows its own account). */
const val ALARM_ACTOR_ENTITY = "sensor.church_drive_alarm_actor"
private const val ALARM_ACTOR_WINDOW_MS = 120_000L

/**
 * Who to name for the alarm's last change, and when: the person Home Assistant recorded ([actor], `sensor.church_drive_alarm_actor`)
 * when it is for the alarm's current state and Ring's own time is not much newer, else Ring's own name. Null when there is neither.
 */
fun alarmWho(alarm: EntityState, actor: EntityState?, ringBy: String?, ringAt: String?): Pair<String, String>? {
    val a = actor?.attributes
    if (actor != null && a != null && actor.available && a.optString("alarm") == alarm.entityId && a.optString("alarm_state") == alarm.state) {
        val mine = runCatching { java.time.Instant.parse(a.optString("at")).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(a.optString("at")).toInstant().toEpochMilli() }.getOrNull()
        val ring = ringAt?.let { r -> runCatching { java.time.Instant.parse(r).toEpochMilli() }.getOrNull() }
        if (mine != null && (ring == null || ring <= mine + ALARM_ACTOR_WINDOW_MS)) return actor.state to a.optString("at")
    }
    return if (!ringBy.isNullOrBlank() && !ringAt.isNullOrBlank()) ringBy to ringAt else null
}

/** The two lines beside the shield: what to do during a delay, otherwise who armed or disarmed it, and when. */
private fun alarmLines(alarm: EntityState, actor: EntityState? = null): Pair<String, String> {
    fun by(who: String?, at: String?) = alarmWho(alarm, actor, who, at)?.let { w -> alarmTime(w.second)?.let { t -> w.first to t } }
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
fun AlarmCard(alarm: EntityState?, call: CallService, entityId: String = ALARM_ENTITY, actor: EntityState? = null, people: List<PersonRow> = emptyList()) {
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
    val (line1, line2) = alarm?.let { alarmLines(it, actor) } ?: ("" to "")
    val features = alarm?.num("supported_features")?.toInt() ?: 0
    // During a delay the mode being armed is lit; otherwise the current state.
    val activeKey = if (inDelay || triggered) alarm?.str("targetState") else state

    // The mode buttons the alarm supports, in order.
    class Mode(val key: String, val label: String, val icon: String, val service: String)
    val modes = buildList {
        add(Mode("disarmed", "Disarm", "mdi:shield-off-outline", "alarm_disarm"))
        if (features and 1 != 0) add(Mode("armed_home", "Home", "mdi:shield-home", "alarm_arm_home"))
        if (features and 2 != 0) add(Mode("armed_away", "Away", "mdi:shield-lock", "alarm_arm_away"))
        if (features and 4 != 0) add(Mode("armed_night", "Night", "mdi:shield-moon", "alarm_arm_night"))
    }
    val fold = rememberFold("grid-fold|$entityId")
    val ink = if (triggered) tone.onAccent else content
    val shield = ALARM_STATES[state]?.icon ?: "mdi:shield-outline"
    val titleText = "Home Security"
    @Composable
    fun androidx.compose.foundation.layout.BoxScope.Ring(sizeCells: Float, cell: androidx.compose.ui.unit.Dp, iconDp: androidx.compose.ui.unit.Dp) {
        Canvas(Modifier.size(cell * sizeCells)) {
            val stroke = 5.dp.toPx() * sizeCells / 1.5f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(ring.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sweep = if (inDelay) 360f * (remaining.toFloat() / total).coerceIn(0f, 1f) else 360f
            drawArc(ring, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        HaIcon(shield, CdVectors.ShieldOutline, ring, iconDp)
    }
    if (fold.value) {
        // Folded: two and a half rows. One band on the middle of row 2: a one cell ring, who and when over two lines from the line between
        // columns B and C, and the modes as small buttons centred on the column lines at the right.
        GridCard(container, content, 2.5f, titleText, deviceEntity = entityId, fold = fold) {
            Box(Modifier.atCentre(1f, 1.5f, 1f, 1f), contentAlignment = Alignment.Center) { Ring(1f, cell, 26.dp) }
            val firstBtn = 8f - modes.size
            Column(Modifier.at(2f, 0.9f, firstBtn - 2f - 0.5f, 1.2f), verticalArrangement = Arrangement.Center) {
                val one = line1.ifEmpty { alarmLabel(state) }
                Text(one, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val two = if (inDelay) "${remaining}s" else line2
                if (two.isNotEmpty()) Text(two, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            modes.forEachIndexed { i, m ->
                SmallModeButton(m.icon, activeKey == m.key, tone, ink, triggered, Modifier.atCentre(firstBtn + i, 1.5f, 0.8f, 0.8f), enabled, m.label) { send(m.service) }
            }
        }
        return
    }
    val rows = 5f + if (people.isEmpty()) 0f else Math.ceil((0.5f + 0.75f * people.size) * 2.0).toFloat() / 2f
    GridCard(container, content, rows, titleText, deviceEntity = entityId, fold = fold) {
        // The ring: 1.5 cells across, from the half line of column A, centred on the line between rows 2 and 3.
        Box(Modifier.atCentre(1.25f, 2f, 1.5f, 1.5f), contentAlignment = Alignment.Center) { Ring(1.5f, cell, 40.dp) }
        // Who and when from the centre of column C, centred on the same line.
        Column(Modifier.at(2.5f, 1f, if (inDelay) 3f else 5f, 2f), verticalArrangement = Arrangement.Center) {
            if (line1.isNotEmpty()) Text(line1, style = MaterialTheme.typography.titleMedium, color = ink)
            if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
        if (inDelay) Text("${remaining}s", style = MaterialTheme.typography.headlineMedium, color = ring, modifier = Modifier.at(5.5f, 1.5f, 2f, 1f).wrapContentSize(Alignment.CenterEnd))
        // The modes: 2 cells wide each (three modes), or 1.5 wide where there is a Night mode, 1.5 tall, base on the middle of row 5.
        val w = if (modes.size > 3) 1.5f else 2f
        val gap = if (modes.size > 3) 1f / 3f else 0.5f
        modes.forEachIndexed { i, m ->
            ModeTile(m.label, m.icon, activeKey == m.key, tone, content, Modifier.at(0.5f + i * (w + gap), 3f, w, 1.5f), enabled, triggered, Modifier.fillMaxSize()) { send(m.service) }
        }
        // Who's home, one line each (administrators only: the page passes nobody for anyone else).
        people.forEachIndexed { i, p ->
            val (word, _) = presencePill(p.state, p.place, p.zone)
            Row(Modifier.at(0.5f, 5f + 0.75f * i, 7f, 0.75f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HaIcon(if (p.state == "home") "cd:home" else "cd:home-away", CdVectors.ShieldOutline, ring, 20.dp)
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
                Text(word, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f), maxLines = 1)
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
    triggered: Boolean = false,
    inner: Modifier = Modifier.height(Ui.TallTileHeight),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    // On a triggered card (fully tinted in the strong colour) the buttons use a light wash, and the current mode a dark fill, so they read.
    val bg = when { selected && triggered -> tone.onAccent; selected -> tone.accent; triggered -> Color.White.copy(alpha = 0.30f); else -> content.copy(alpha = 0.10f) }
    val ink = when { selected && triggered -> Color.White; selected -> tone.onAccent; else -> content }
    Box(modifier.clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick)) {
        Column(
            modifier = inner,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HaIcon(icon, CdVectors.ShieldOutline, ink, 24.dp)
            CentredText(label, ink, 12.sp, Modifier.padding(top = 4.dp), lineHeight = 14.sp)
        }
    }
}

// ---- Climate ----

const val CLIMATE_ENTITY = "climate.downstairs"

/** Off grey, Eco green, cooling blue, otherwise heating orange: the same as the dashboard's thermostat. */
fun climateTone(c: EntityState?): Tone = when {
    c == null || c.state == "off" || !c.available -> Tone.Grey
    c.str("preset_mode") == "eco" -> Tone.Green
    c.str("hvac_action") == "cooling" -> Tone.Cooling
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

/** [v] moved onto the thermostat's step grid (anchored at its minimum) and kept within its limits, so targets stay tidy: 18, 18.5, 19. */
fun snapToStep(v: Double, step: Double, min: Double, max: Double): Double {
    val s = if (step > 0) step else 0.5
    val snapped = min + Math.round((v - min) / s) * s
    return (Math.round(snapped.coerceIn(min, max) * 10) / 10.0)
}

/** A shortcut button on a thermostat card: a heating mode or a preset. */
data class QuickSetting(val name: String, val hvacMode: String?, val presetMode: String?)

val DEFAULT_QUICK = listOf(QuickSetting("Off", "off", null), QuickSetting("Heat", "heat", null), QuickSetting("Eco", null, "eco"))

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
    humidityPct: Double? = null,
    quick: List<QuickSetting>? = null,
    quality: Int? = null,
    zone: ZoneControl? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val tone = toneColors(climateTone(climate))
    var sheetOpen by remember { mutableStateOf(false) }
    val enabled = climate?.available == true
    val target = climate?.num("temperature")
    val step = climate?.num("target_temp_step") ?: 0.5
    val eco = climate?.str("preset_mode") == "eco"
    // Taps build on what was last shown, so quick presses add up; one call goes out once tapping stops.
    var pending by remember(target) { mutableStateOf(target) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(climate)
    fun nudge(by: Double) {
        val base = pending ?: return
        val lo = climate?.num("min_temp") ?: 5.0
        val next = snapToStep(base + by, step, lo, climate?.num("max_temp") ?: 30.0)
        pending = next
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(600)
            // The thermostat ignores a new target while Eco is on (its number is the Eco setpoint), so leave Eco first.
            if (latest?.str("preset_mode") == "eco" && "none" in latest?.list("preset_modes").orEmpty()) {
                call("climate", "set_preset_mode", id, data("preset_mode" to "none"))
                var waited = 0
                while (latest?.str("preset_mode") == "eco" && waited < 5_000) {
                    delay(250)
                    waited += 250
                }
            }
            call("climate", "set_temperature", id, data("temperature" to next))
        }
    }
    fun isSelected(q: QuickSetting) = when {
        q.hvacMode != null -> climate?.state == q.hvacMode && (q.hvacMode == "off" || !eco)
        q.presetMode != null -> climate?.str("preset_mode") == q.presetMode && climate?.state != "off"
        else -> false
    }
    fun applyQuick(q: QuickSetting) {
        val steps = quickSteps(climate, q)
        scope.launch {
            steps.forEachIndexed { i, step ->
                // A preset waits for the thermostat to come on (up to five seconds).
                if (i > 0 && step.service == "set_preset_mode") {
                    var waited = 0
                    while (latest?.state == "off" && waited < 5_000) {
                        delay(250)
                        waited += 250
                    }
                }
                call("climate", step.service, id, data(step.key to step.value))
            }
        }
    }
    val cur = climate?.num("current_temperature")
    val off = climate == null || climate.state == "off"
    val title = zone?.zone?.replaceFirstChar { it.uppercase() } ?: climate?.friendlyName ?: "Thermostat"
    val fold = rememberFold("grid-fold|$id")
    val items = (quick ?: DEFAULT_QUICK).map { q -> TileItem(quickSettingIcon(q), q.name, isSelected(q)) { applyQuick(q) } }
    val lo = climate?.num("min_temp") ?: 5.0
    val hi = climate?.num("max_temp") ?: 30.0
    val ink = tone.onContainer
    @Composable
    fun GridScope.Dial(cx: Float, cy: Float, ringCells: Float) {
        Box(Modifier.atCentre(cx, cy, ringCells + 0.5f, ringCells + 0.5f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ringPx = cell.toPx() * ringCells
                val stroke = 5.dp.toPx() * ringCells / 1.5f
                val r = (ringPx - stroke) / 2f
                val c = center
                val sc = ringPx / 64f
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(2 * r, 2 * r)
                val start = 135f
                val sweep = 270f
                drawArc(ink.copy(alpha = 0.18f), start, sweep, false, tl, sz, style = Stroke(stroke))
                if (!off && pending != null && cur != null) {
                    val ft = ((pending!! - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
                    val fc = ((cur - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
                    val a = minOf(ft, fc); val b = maxOf(ft, fc)
                    drawArc(tone.accent.copy(alpha = 0.4f), start, sweep * a, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(tone.accent, start + sweep * a, sweep * (b - a), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    fun pt(f: Float, rad: Float): Offset {
                        val ang = Math.toRadians((start + sweep * f).toDouble())
                        return Offset(c.x + rad * Math.cos(ang).toFloat(), c.y + rad * Math.sin(ang).toFloat())
                    }
                    // The line is the target, the white dot is the temperature now; both may reach past the ring.
                    drawLine(tone.accent, pt(ft, 25f * sc), pt(ft, 37f * sc), strokeWidth = 3.5f * sc)
                    drawCircle(Color.White, 4.2f * sc, pt(fc, r))
                }
            }
            Text(if (off) "Off" else temp(pending) + "°", style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, title, deviceEntity = climate?.entityId, fold = fold) {
            Dial(1f, 1.5f, 1f)
            val firstBtn = 8f - items.size
            Column(Modifier.at(2f, 0.9f, firstBtn - 2f - 0.5f, 1.2f), verticalArrangement = Arrangement.Center) {
                Text("${temp(cur)}° room", style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
                Text(climateWord(climate), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
            }
            (quick ?: DEFAULT_QUICK).forEachIndexed { i, q ->
                SmallModeButton(quickSettingIcon(q), isSelected(q), tone, ink, false, Modifier.atCentre(firstBtn + i, 1.5f, 0.8f, 0.8f), enabled, q.name) { applyQuick(q) }
            }
        }
        return
    }
    // The zone line and the graph below the buttons are drawn when the page gives them.
    var end = 6f
    val zoneY = if (zone != null) { val y = end + 0.5f; end = y + 1f; y } else 0f
    val belowY = if (below != null) { val y = end + 0.5f; end = y + 2f; y } else 0f
    GridCard(tone.container, ink, end + 0.5f, title, deviceEntity = climate?.entityId, fold = fold) {
        // The dial: the ring is the size and place of the alarm's ring.
        Dial(1.25f, 2f, 1.5f)
        // Humidity and the outside temperature from the centre of column C, the room's temperature on the right.
        Column(Modifier.at(2.5f, 1f, 3f, 2f), verticalArrangement = Arrangement.Center) {
            (humidity?.state?.toDoubleOrNull() ?: humidityPct)?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaIcon("mdi:water-percent", CdVectors.Info, ink, 20.dp); Text("${temp(it)}%", style = MaterialTheme.typography.bodyMedium, color = ink)
                }
            }
            outdoor?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaIcon("mdi:weather-partly-cloudy", CdVectors.Info, ink, 20.dp); Text("Outside ${temp(it)}°", style = MaterialTheme.typography.bodyMedium, color = ink)
                }
            }
        }
        Column(Modifier.at(5.5f, 1f, 2f, 2f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End) {
            Text("${temp(cur)}°", style = MaterialTheme.typography.headlineMedium, color = ink)
            Text("room", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
        }
        // Lower and Raise: 3.25 cells wide, a cell tall, starting on the line between rows 3 and 4.
        GridButton(TileItem("mdi:minus", "Lower", false, false) { nudge(-step) }, tone, ink, enabled && !off, false, Modifier.at(0.5f, 3f, 3.25f, 1f))
        GridButton(TileItem("mdi:plus", "Raise", false, false) { nudge(step) }, tone, ink, enabled && !off, false, Modifier.at(4.25f, 3f, 3.25f, 1f))
        GridTiles(items, 4.5f, 1.5f, tone, ink, enabled, stacked = true)
        if (zone != null) {
            Box(Modifier.at(0.5f, zoneY, 7f, 1f)) { ZoneStatusLine(zone, tone) { sheetOpen = true } }
            if (sheetOpen) ThermostatSheet(zone.zone.replaceFirstChar { it.uppercase() }, zone, tone, call) { sheetOpen = false }
        }
        if (below != null) Box(Modifier.at(0.5f, belowY, 7f, 2f)) { androidx.compose.runtime.CompositionLocalProvider(LocalGraphFill provides true) { below() } }
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

/** A wide Lower or Raise button, the height and wash of a mode tile. */
@Composable
fun StepBar(icon: ImageVector, description: String, tone: ToneColors, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(Ui.TileHeight)
            .clip(Ui.TileShape)
            .background(tone.onContainer.copy(alpha = 0.10f), Ui.TileShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.onContainer.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

// ---- Vacuum ----

const val VACUUM_ENTITY = "vacuum.gregg"
const val VACUUM_BATTERY = "sensor.gregg_battery"

fun vacuumTone(v: EntityState?): Tone = when (v?.state) {
    "error" -> Tone.Red
    "cleaning" -> Tone.Gregg
    "returning" -> Tone.Gregg
    null, "unavailable", "unknown" -> Tone.Grey
    else -> Tone.Gregg
}

fun vacuumSummary(v: EntityState?, battery: EntityState?): String {
    val state = v?.state?.replaceFirstChar { it.uppercase() } ?: "Loading…"
    val b = battery?.state?.takeIf { it != "unknown" && it != "unavailable" }
    return if (b != null) "$state · $b%" else state
}

@Composable
fun VacuumCard(vacuum: EntityState?, battery: EntityState?, call: CallService, entities: Map<String, EntityState> = emptyMap()) {
    val area = entities["sensor.gregg_cleaning_area"]?.state?.toDoubleOrNull()
    val minutes = entities["sensor.gregg_cleaning_time"]?.state?.toDoubleOrNull()
    val lastClean = listOfNotNull(area?.let { "${Math.round(it)} m²" }, minutes?.let { "${Math.round(it)} min" }).joinToString(" · ")
    val detail = if (lastClean.isNotBlank()) "Last clean $lastClean" else null
    val error = entities["sensor.gregg_error_message"]?.state?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" && it != "None" }
    val tone = toneColors(vacuumTone(vacuum))
    val enabled = vacuum?.available == true
    val cleaning = vacuum?.state == "cleaning"
    val ink = tone.onContainer
    val pct = battery?.state?.toDoubleOrNull()
    val word = vacuum?.state?.replaceFirstChar { it.uppercase() } ?: "Loading…"
    val fold = rememberFold("grid-fold|${vacuum?.entityId ?: VACUUM_ENTITY}")
    // Start, Stop (the same as Pause) and Home.
    val buttons = listOf(
        TileItem("cd:play", if (cleaning) "Pause" else if (vacuum?.state == "paused") "Resume" else "Start", cleaning, false) {
            call("vacuum", if (cleaning) "pause" else "start", VACUUM_ENTITY, data())
        },
        TileItem("cd:stop", "Stop", vacuum?.state == "idle", false) { call("vacuum", "stop", VACUUM_ENTITY, data()) },
        TileItem("cd:home", "Dock", vacuum?.state == "returning" || vacuum?.state == "docked", false) { call("vacuum", "return_to_base", VACUUM_ENTITY, data()) },
    )
    val name = vacuum?.friendlyName ?: "Vacuum"
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, name, deviceEntity = vacuum?.entityId, fold = fold) {
            Box(Modifier.atCentre(1f, 1.5f, 1.2f, 1.2f), contentAlignment = Alignment.Center) { GreggIcon(greggState(vacuum, battery), tone.accent, cell * 1.112f, pct) }
            Column(Modifier.at(2f, 0.9f, 2.5f, 1.2f), verticalArrangement = Arrangement.Center) {
                Text(word, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
                if (pct != null) Text("${pct.toInt()}%", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
            }
            buttons.forEachIndexed { i, b ->
                SmallModeButton(b.icon, b.selected, tone, ink, false, Modifier.atCentre(5f + i, 1.5f, 0.8f, 0.8f), enabled, b.label, b.onClick)
            }
        }
        return
    }
    GridCard(tone.container, ink, 5f, name, deviceEntity = vacuum?.entityId, fold = fold) {
        // The icon takes the place and size of the alarm's ring.
        Box(Modifier.atCentre(1.25f, 2f, 1.7f, 1.7f), contentAlignment = Alignment.Center) { GreggIcon(greggState(vacuum, battery), tone.accent, cell * 1.668f, pct) }
        Column(Modifier.at(2.5f, 1f, 5f, 2f), verticalArrangement = Arrangement.Center) {
            Text(word, style = MaterialTheme.typography.titleMedium, color = ink)
            if (pct != null) Text("${pct.toInt()}% battery", style = MaterialTheme.typography.bodyMedium, color = ink)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            if (!error.isNullOrBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = toneColors(Tone.Red).accent)
        }
        if (pct != null) LevelBar((pct / 100.0).toFloat(), levelFade((pct / 100.0).toFloat()), ink, Modifier.at(0.5f, 3f, 7f, 0.25f).wrapContentHeight(Alignment.CenterVertically))
        buttons.forEachIndexed { i, b -> GridButton(b, tone, ink, enabled, false, Modifier.at(0.5f + i * 2.5f, 3.5f, 2f, 1f)) }
    }
}

// ---- Car charger (Zappi) ----

