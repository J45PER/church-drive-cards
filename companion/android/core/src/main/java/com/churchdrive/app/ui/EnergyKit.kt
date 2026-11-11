package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.Templates
import com.churchdrive.app.ha.data
import com.churchdrive.app.ha.text
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** The cheap window is nearer than this, or it isn't worth waiting for. */
const val ASK_WITHIN_MS = 18 * 3_600_000L

const val CHARGE_LATER_SCRIPT = "script.zappi_charge_later"

/** The normal rate now and the cheap rate that's coming: [startMs] is when it starts (epoch ms). */
data class ChargeAsk(val nowPence: Double, val cheapPence: Double, val startMs: Long)

/** "Charge at 00:30 (cheap rate)" or "Charge now at 25.3p": the question as a dialog. Tapping outside it cancels. */
@Composable
fun ChargeAskDialog(ask: ChargeAsk, onLater: () -> Unit, onNow: () -> Unit, onCancel: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Wait for the cheap rate?") },
        text = { Text(chargeAskText(ask, now)) },
        confirmButton = { TextButton(onClick = onLater) { Text("Charge at ${Octopus.clock(ask.startMs)}") } },
        dismissButton = { TextButton(onClick = onNow) { Text("Charge now at ${Octopus.pence(ask.nowPence)}p") } },
    )
}

const val SMART_ENABLED = "input_boolean.zappi_smart_charge"

const val SMART_STATE = "input_select.zappi_smart_charge_state"

/** Smart charge as the house reports it: whether it's on, what it's doing, and whether the "charge later" script exists. */
data class SmartCharge(val on: Boolean, val phase: String?, val later: Boolean)

/**
 * What to ask when Start charge or Fast is pressed, or null to just charge: nothing to ask when smart charge isn't set up,
 * when the rate is already cheap, when the cheap rate is hardly cheaper (under half a penny), or when it's over 18 hours away.
 */
fun chargeAsk(entities: Map<String, EntityState>, now: Long): ChargeAsk? {
    if (smartCharge(entities)?.later != true) return null
    val rates = Octopus.allRates(entities)
    val cur = Octopus.current(rates, now) ?: return null
    val low = Octopus.cheapest(rates) ?: return null
    if (cur.pence <= low + 0.01 || low >= cur.pence - 0.5) return null
    val start = Octopus.nextCheapStart(rates, now) ?: return null
    if (start - now > ASK_WITHIN_MS) return null
    return ChargeAsk(cur.pence, low, start)
}

/** The wording of the question. */
fun chargeAskText(ask: ChargeAsk, now: Long): String =
    "It's the normal rate now (${Octopus.pence(ask.nowPence)}p a kWh). The cheap rate (${Octopus.pence(ask.cheapPence)}p) starts at " +
        "${Octopus.clock(ask.startMs)}, in ${inText(ask.startMs - now)}. Charging later hands it to smart charge: it starts by itself " +
        "at the cheap rate and stops when that ends."

/** Hands the charge to smart charge: it waits for the cheap rate (or starts at once if it's cheap already). */
fun chargeLater(call: CallService) = call("script", "turn_on", CHARGE_LATER_SCRIPT, data())

/** Start charge: unlock if locked, then Fast (the charger otherwise waits at the unit). */
fun chargeNow(entities: Map<String, EntityState>, call: CallService) {
    if (chargerOverrides(entities).locked == true) call("myenergi", "myenergi_unlock", ZAPPI_MODE, data())
    call("select", "select_option", ZAPPI_MODE, data("option" to "Fast"))
}

/** Null when the house hasn't set smart charge up (the card then shows no switch and never asks). */
fun smartCharge(entities: Map<String, EntityState>): SmartCharge? {
    val on = entities[SMART_ENABLED]?.takeIf { it.available } ?: return null
    return SmartCharge(on.state == "on", entities[SMART_STATE]?.takeIf { it.available }?.state, entities[CHARGE_LATER_SCRIPT] != null)
}

/** What the switch says under its name, as on the dashboard's charger card. */
fun smartText(sm: SmartCharge, rates: List<Rate>, now: Long): String {
    if (!sm.on) return "Off: it only charges when you start it"
    val until = Octopus.cheapUntil(rates, now)
    val next = if (until == null) Octopus.nextCheapStart(rates, now) else null
    return when (sm.phase) {
        "Charging" -> if (until != null) "Charging at the cheap rate until ${Octopus.clock(until)}" else "Charging at the cheap rate"
        "Waiting" -> if (next != null) "Waiting for the cheap rate, ${Octopus.clock(next)}" else "Starting at the cheap rate"
        "Done" -> "Finished for this plug-in"
        "Manual" -> "You're in control for this plug-in"
        else -> "Charges at the cheap rate when a car is plugged in"
    }
}

/** A brand's one-colour logo (Simple Icons, or our own set) when it is known by [brand], else the plain [icon]. */
@Composable
fun BrandOrIcon(brand: String?, icon: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { SimpleIcons.load(context); OwnLogos.load(context) }
    val key = brand?.takeIf { it.isNotBlank() }?.let { SimpleIcons.slugFor(it) }
    val name = when {
        key != null && SimpleIcons.paths?.containsKey(key) == true -> "si:$key"
        key != null && OwnLogos.paths?.containsKey(key) == true -> "ol:$key"
        else -> icon
    }
    HaIcon(name, CdVectors.Info, tint, size)
}

/**
 * The integrations that make cars (Vauxhall and Peugeot through Stellantis, the VW group), and Octopus: its Intelligent device is a car it
 * controls (the ID.5). That gives the battery % and its charging plan but not the plug, range or place; see [octopusCarState].
 */
val CAR_PLATFORMS = setOf("stellantis_vehicles", "vag_connect", "volkswagencarnet", "volkswagen_we_connect_id", "octopus_energy")

/** Figures this old, while plugged in, get "Car last reported HH:MM · 25m ago" and Refresh. */
const val CAR_STALE_MS = 20 * 60_000L

@Composable
fun CarCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val rows = carRows(config, entities, registry, personOf(LocalUserName.current, entities))
    // The minute, so "Car last reported 20:41" appears once the figures pass 20 minutes old.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    // Cars asked for figures in the last 3 minutes ("Asking the car…").
    var asked by remember { mutableStateOf(mapOf<String, Long>()) }
    if (rows.isEmpty()) return
    val charging = rows.count { it.charging }
    val tone = toneColors(Tone.Car)
    val title = config.optString("title").ifBlank { "Cars" }
    val fold = rememberFold("grid-fold|cars|$title")
    val ink = tone.onContainer
    fun fraction(b: Double?) = ((b ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    if (fold.value) {
        // Folded: each car's name and charge side by side (two to a row) with its bar under, from row 2.
        val lines = rows.chunked(2)
        GridCard(tone.container, ink, maxOf(2.5f, 1.5f + lines.size * 1f), title, fold = fold) {
            lines.forEachIndexed { r, pair ->
                pair.forEachIndexed { c, car ->
                    val colour = batteryFade(fraction(car.battery))
                    Column(Modifier.at(0.5f + c * 3.75f, 1.1f + r * 1f, 3.25f, 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(car.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(car.battery?.let { "${Math.round(it)}%" } ?: "–", style = MaterialTheme.typography.bodyMedium, color = ink)
                        }
                        LevelBar(fraction(car.battery), colour, ink, Modifier.fillMaxWidth(), 6.dp)
                    }
                }
            }
        }
        return
    }
    // Open: a block per car, 2.5 rows apart (a car with out of date figures has another row for its refresh), its icon circle one cell across with its centre on a line.
    val stale = rows.map { carStale(it, now) }
    // Each car: 1.5 for its name, figure and bar, half a cell for the range line, half a cell (a cell with a Refresh button) for old figures, and half a cell between cars.
    fun hasSub(c: CarRow) = c.range.isNotBlank() || c.fuel != null || c.end.isNotBlank()
    val pitch = rows.mapIndexed { i, c -> 1.5f + (if (hasSub(c)) 0.5f else 0f) + (if (stale[i]) (if (c.wake.isNotEmpty()) 1f else 0.5f) else 0f) + 0.5f }
    GridCard(tone.container, ink, 1f + pitch.sum(), title, fold = fold) {
        var top = 1f
        rows.forEachIndexed { i, car ->
            val y = top
            top += pitch[i]
            val barColour = batteryFade(fraction(car.battery))
            val tag = when {
                car.charging -> "Charging" to Tone.Cooling
                car.plugged -> "Plugged in" to Tone.Indigo
                car.where.equals("home", true) -> car.where to Tone.Green
                else -> car.where to Tone.Grey
            }
            val sub = listOfNotNull(
                car.range.takeIf { it.isNotBlank() }?.let { "$it electric" },
                car.fuel?.let { "Fuel ${Math.round(it)}%" + car.fuelRange.takeIf { r -> r.isNotBlank() }?.let { r -> " · $r" }.orEmpty() },
                car.end.takeIf { it.isNotBlank() },
            )
            Box(Modifier.atCentre(1f, y + 0.5f, 1f, 1f).background(tone.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                BrandOrIcon(car.name.trim().substringBefore(' '), if (car.charging) "mdi:car-electric" else "mdi:car", tone.accent, 22.dp)
            }
            Column(Modifier.at(2f, y, 4f, 1f), verticalArrangement = Arrangement.Center) {
                Text(car.name, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag.first.isNotBlank()) StatusPill(tag.first, toneColors(tag.second))
            }
            Text(car.battery?.let { "${Math.round(it)}%" } ?: "–", color = barColour, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.at(5.5f, y, 2f, 1f).wrapContentSize(Alignment.CenterEnd))
            LevelBar(fraction(car.battery), barColour, ink, Modifier.at(0.5f, y + 1.1f, 7f, 0.25f).wrapContentHeight(Alignment.CenterVertically))
            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), modifier = Modifier.at(0.5f, y + 1.45f, 7f, 0.5f))
            if (stale[i]) {
                val amber = toneColors(Tone.Amber)
                val asking = (asked[car.wake] ?: 0L) > now - 3 * 60_000L
                val staleY = y + 1.5f + (if (sub.isNotEmpty()) 0.5f else 0f)
                Row(Modifier.at(0.5f, staleY, if (car.wake.isNotEmpty()) 3.5f else 7f, if (car.wake.isNotEmpty()) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaIcon("mdi:clock-alert-outline", CdVectors.Info, amber.accent, 16.dp)
                    Text(carUpdatedText(car.asOf!!, now), color = amber.accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                if (car.wake.isNotEmpty()) GridButton(
                    TileItem("mdi:refresh", if (asking) "Asking the car…" else "Refresh", false) {
                        asked = asked + (car.wake to System.currentTimeMillis())
                        now = System.currentTimeMillis()
                        call("button", "press", car.wake, data())
                    },
                    tone, ink, !asking, false, Modifier.at(4.25f, staleY, 3.25f, 1f),
                )
            }
        }
    }
}

/** The entities of one car that the card reads, by their ids ("" when the car hasn't got one). */
data class CarEntities(val battery: String, val range: String, val fuel: String, val fuelRange: String, val plugged: String, val charging: String, val end: String, val tracker: String, val wake: String = "", val dispatch: String = "")

data class CarRow(
    val device: String, val name: String, val battery: Double?, val range: String, val fuel: Double?, val fuelRange: String,
    val plugged: Boolean, val charging: Boolean, val end: String, val where: String, val owners: List<String>,
    /** When the figures are from (epoch ms), and the car's wake-up button ("" when it hasn't one). */
    val asOf: Long? = null, val wake: String = "",
)

val DISPATCHING = Regex("binary_sensor\\.octopus_energy_.*_intelligent_dispatching")

/** What a car made by Octopus's Intelligent device can say: whether it is plugged in and charging, and Octopus's plan for it. */
data class OctopusCar(val plugged: Boolean, val charging: Boolean, val plan: String)

/** Octopus's plan: the planned dispatches that have not finished, oldest first, and whether one is on now. */
data class OctopusPlan(val slots: List<PlanSlot>, val active: Boolean)

/** One planned charge of Octopus's plan for the car it controls: when, how much (kWh, always positive), and SMART or BOOST. */
data class PlanSlot(val start: Long, val end: Long, val kwh: Double, val source: String)

/** When a car's figures are from: the integration's own "Last updated" (Stellantis: when the car sent them), else the state's. */
fun carAsOf(battery: EntityState?): Long? {
    if (battery == null) return null
    val raw = listOf("Last updated", "last_updated", "updated_at", "last_update").firstNotNullOfOrNull { battery.str(it)?.takeIf { v -> v.isNotBlank() } }
    return parseMillis(raw) ?: parseMillis(battery.lastChanged)
}

fun carEntities(ids: List<String>, entities: Map<String, EntityState>): CarEntities {
    fun pick(domain: String, match: Regex, skip: Regex? = null) =
        ids.firstOrNull { it.startsWith("$domain.") && match.containsMatchIn(it) && (skip == null || !skip.containsMatchIn(it)) && entities[it] != null }.orEmpty()
    return CarEntities(
        battery = ids.firstOrNull { id ->
            val e = entities[id]
            id.startsWith("sensor.") && e != null && e.str("device_class") == "battery" && e.str("unit_of_measurement") == "%" &&
                !Regex("_(service|12v|aux|soh|key)", RegexOption.IGNORE_CASE).containsMatchIn(id)
        }.orEmpty(),
        range = pick("sensor", Regex("(electric_|battery_)?range$"), Regex("fuel|combustion|total")),
        fuel = pick("sensor", Regex("_fuel(_level)?$")),
        fuelRange = pick("sensor", Regex("fuel_range$|combustion_range$")),
        plugged = pick("binary_sensor", Regex("plug"), Regex("lock")),
        charging = pick("binary_sensor", Regex("charging$")),
        end = pick("sensor", Regex("charging_end|charge_end|charging_time_left|remaining_charging")),
        tracker = pick("device_tracker", Regex(".")),
        wake = pick("button", Regex("wake_?up$")),
        dispatch = pick("binary_sensor", Regex("intelligent_dispatching$")),
    )
}

/** Every car of the card: those it names, else all of them; with "only mine" (the default), the signed-in person's and nobody's. */
fun carRows(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, me: String?, zone: ZoneId = ZoneId.systemDefault()): List<CarRow> {
    val people = entities["sensor.church_drive_people"]?.attributes?.optJSONArray("people") ?: JSONArray()
    fun owners(device: String) = (0 until people.length()).mapNotNull { people.optJSONObject(it) }
        .filter { p -> p.optJSONArray("cars")?.let { a -> (0 until a.length()).any { a.optString(it) == device } } == true }.map { it.optString("entity_id") }
    val found = registry.devicesOfPlatforms(CAR_PLATFORMS).filter { (_, ids) -> carEntities(ids, entities).battery.isNotEmpty() }
    val named = config.optJSONArray("cars")?.takeIf { it.length() > 0 }
    val cars = if (named != null) {
        (0 until named.length()).mapNotNull { named.optJSONObject(it) }.mapNotNull { c ->
            val device = c.optString("device")
            found[device]?.let { Triple(device, c.optString("name").ifBlank { registry.deviceNameById(device) ?: "Car" }, it) }
        }
    } else found.map { (device, ids) -> Triple(device, registry.deviceNameById(device) ?: "Car", ids) }
    val onlyMine = if (config.has("only_mine")) config.optBoolean("only_mine", true) else true
    return cars.map { (device, name, ids) -> Triple(device, name, ids) to owners(device) }
        .filter { (_, owned) -> !onlyMine || owned.isEmpty() || (me != null && me in owned) }
        .map { (car, owned) ->
            val (device, name, ids) = car
            val e = carEntities(ids, entities)
            val tracker = entities[e.tracker]
            val where = when {
                tracker == null || !tracker.available -> ""
                tracker.state == "home" -> "Home"
                tracker.state == "not_home" -> "Away"
                else -> tracker.state
            }
            val octopus = octopusCarState(e, entities, zone)
            val charging = octopus?.charging ?: (entities[e.charging]?.state == "on")
            val end = entities[e.end]
            val endText = (if (charging && end != null && end.available && end.state.isNotBlank()) chargeEndText(end.state, end.str("unit_of_measurement"), zone) else "")
                .ifEmpty { octopus?.plan.orEmpty() }
            CarRow(
                device, name, entities[e.battery]?.state?.toDoubleOrNull(), reading(entities[e.range]), entities[e.fuel]?.state?.toDoubleOrNull(),
                reading(entities[e.fuelRange]), octopus?.plugged ?: (entities[e.plugged]?.state == "on"), charging, endText, where, owned,
                carAsOf(entities[e.battery]), e.wake.takeIf { entities[it]?.state != "unavailable" }.orEmpty(),
            )
        }
}

/** Whether to say when the figures are from: plugged in or charging, and over 20 minutes old. */
fun carStale(car: CarRow, now: Long): Boolean = (car.plugged || car.charging) && car.asOf != null && now - car.asOf > CAR_STALE_MS

/** "Car last reported 20:41 · 25m ago", or "Car last reported Sat 20:41 · 2d ago" when it isn't today. */
fun carUpdatedText(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate() == t.toLocalDate()
    val mins = maxOf(0L, (now - at) / 60_000)
    val ago = when {
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
    return "Car last reported " + HomeTime.format(at, if (today) "HH:mm" else "EEE HH:mm", zone) + " · " + ago
}

/** "Full by 06:30" when the end is a time, else "45 min left". */
fun chargeEndText(state: String, unit: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = parseMillis(state)
    return if (at != null) "Full by " + HomeTime.format(at, "HH:mm", zone)
    else "$state${unit?.let { " $it" }.orEmpty()} left"
}

/**
 * Octopus gives that car no plug or charging sensor, so it is plugged in and charging when the Zappi is and this car is the Primary one
 * on it ([ZAPPI_CAR_ROLE]); its plan is the current or next dispatch. Null for a car that is not made by Octopus.
 */
fun octopusCarState(e: CarEntities, entities: Map<String, EntityState>, zone: ZoneId = ZoneId.systemDefault()): OctopusCar? {
    val disp = entities[e.dispatch]?.takeIf { it.available } ?: return null
    val onCharger = entities[ZAPPI_CAR_ROLE]?.takeIf { it.available }?.state == CAR_ROLE_PRIMARY
    val plug = entities[ZAPPI_PLUG]?.takeIf { it.available }?.state
    val plugged = onCharger && plug != null && !plug.contains("disconnected", ignoreCase = true)
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val charging = plugged && (power > 100 || plug?.startsWith("charging", ignoreCase = true) == true)
    fun at(key: String): String? = disp.str(key)?.let { parseMillis(it) }?.let { HomeTime.format(it, "HH:mm", zone) }
    val currentEnd = at("current_end")
    val nextStart = at("next_start")
    val nextEnd = at("next_end")
    val plan = when {
        disp.state == "on" && currentEnd != null -> "Octopus charging until $currentEnd"
        nextStart != null && nextEnd != null -> "Next Octopus charge $nextStart to $nextEnd"
        else -> ""
    }
    return OctopusCar(plugged, charging, plan)
}

/** Octopus's charging plan (the dispatching sensor's `planned_dispatches`), or null when the house has no Octopus Intelligent device. */
fun octopusPlan(entities: Map<String, EntityState>, now: Long): OctopusPlan? {
    val e = entities.values.firstOrNull { it.available && DISPATCHING.matches(it.entityId) } ?: return null
    val list = e.attributes?.optJSONArray("planned_dispatches") ?: JSONArray()
    val slots = (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val start = parseMillis(o.optString("start")) ?: return@mapNotNull null
        val end = parseMillis(o.optString("end")) ?: return@mapNotNull null
        if (end <= now) return@mapNotNull null
        PlanSlot(start, end, Math.abs(o.optDouble("charge_in_kwh", 0.0)), o.optString("source").uppercase())
    }.sortedBy { it.start }
    return OctopusPlan(slots, e.state == "on")
}

/** The signed-in person's `person.` entity, found by their name. */
fun personOf(name: String?, entities: Map<String, EntityState>): String? {
    val first = name?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
    if (first.isEmpty()) return null
    return entities.values.firstOrNull { it.entityId.startsWith("person.") && it.friendlyName.trim().substringBefore(' ').lowercase() == first }?.entityId
}

/** One line of the plan: "Now: 23:51 to 04:02 · 32.5 kWh · Boost", or "02:30 to 04:30 · 15.6 kWh · Smart". */
fun planLine(slot: PlanSlot, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val kwh = if (slot.kwh > 0) " · " + "%.1f".format(java.util.Locale.UK, slot.kwh) + " kWh" else ""
    val kind = if (slot.source == "BOOST") "Boost" else "Smart"
    return (if (slot.start <= now) "Now: " else "") + HomeTime.format(slot.start, "HH:mm", zone) + " to " + HomeTime.format(slot.end, "HH:mm", zone) + kwh + " · " + kind
}

const val CAR_NAME_ADDITIONAL = "Astra GSe"

// What people see for each role (the stored role names stay Primary and Additional).
const val CAR_NAME_PRIMARY = "VW ID5"

const val CAR_ROLE_ADDITIONAL = "Additional"

const val CAR_ROLE_PRIMARY = "Primary"

const val CAR_YES_SCRIPT = "script.zappi_car_yes"

/** Which car is on the charger: its role (Primary, Additional or Unknown) and whether somebody has confirmed it. */
data class CarRoleChoice(val role: String, val confirmed: Boolean)

const val ZAPPI_CAR_ROLE = "input_select.zappi_car_role"

const val ZAPPI_CONFIRMED = "input_boolean.zappi_car_confirmed"

/** The charger card's "Car plugged in" choice: only while a car is plugged in, and only when the house has the role helper. */
fun carRoleChoice(entities: Map<String, EntityState>): CarRoleChoice? {
    val role = entities[ZAPPI_CAR_ROLE]?.takeIf { it.available } ?: return null
    if (!chargerOverrides(entities).show) return null
    val confirmed = entities[ZAPPI_CONFIRMED]?.takeIf { it.available }?.state != "off"
    return CarRoleChoice(role.state, confirmed)
}

/**
 * Choose which car is plugged in. A car nobody has answered for is confirmed by the choice (the Yes script, with the role); one that is
 * already confirmed only changes its role, so a charge somebody took over by hand carries on.
 */
fun chooseCarRole(call: CallService, role: String, confirmed: Boolean) {
    if (confirmed) call("input_select", "select_option", ZAPPI_CAR_ROLE, data("option" to role))
    else call("script", "turn_on", CAR_YES_SCRIPT, data("variables" to JSONObject().put("role", role)))
}

const val CHARGING = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M99.1,109.1 L89.0,109.1 L88.4,108.9 L88.0,108.5 L87.7,108.0 L87.6,107.4 L87.8,106.8 L101.1,80.1 L101.8,79.4 L102.7,79.2 L103.6,79.6 L104.1,80.6 L104.1,94.1 L114.2,94.1 L114.8,94.3 L115.2,94.7 L115.5,95.2 L115.6,95.8 L115.4,96.4 L102.1,123.1 L101.6,123.7 L100.8,124.0 L100.2,123.9 L99.7,123.7 L99.3,123.2 L99.1,122.6 Z"

const val DISC_SCALE = 1.239130f

const val ERROR = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M106.8,78.8 L106.8,100.0 L106.4,102.2 L105.9,103.4 L105.1,104.5 L104.1,105.4 L103.0,106.1 L101.8,106.6 L100.4,106.8 L99.1,106.8 L97.8,106.5 L95.9,105.4 L94.6,104.2 L93.7,102.6 L93.3,100.9 L93.2,78.8 L93.6,76.6 L94.6,74.6 L95.5,73.7 L96.6,72.9 L98.7,72.1 L100.9,72.1 L103.0,72.7 L104.1,73.4 L105.1,74.3 L105.9,75.4 L106.4,76.6 Z M106.8,123.7 L106.6,125.1 L106.3,126.3 L105.6,127.5 L104.8,128.5 L103.8,129.4 L102.6,130.0 L101.3,130.4 L100.0,130.5 L98.7,130.4 L97.4,130.0 L96.2,129.4 L95.2,128.5 L94.4,127.5 L93.7,126.3 L93.4,125.1 L93.2,123.7 L93.4,122.4 L93.7,121.1 L94.4,120.0 L95.2,118.9 L96.2,118.1 L97.4,117.5 L98.7,117.1 L100.0,117.0 L101.3,117.1 L102.6,117.5 L103.8,118.1 L104.8,118.9 L105.6,120.0 L106.3,121.1 L106.6,122.4 Z"

/** The icon name a Cleaning page title carries to mean Gregg's resting (idle) icon, which never changes with what he is doing. */
const val GREGG_IDLE_ICON = "gregg:idle"

/**
 * Gregg's status icon: one disc with the glyph cut out and one ring round it, as on the icon board. The ring never changes size: it turns in
 * parts while he cleans, backwards while he returns, fills to the battery level while he charges, and blinks closed on an error.
 * [batteryPercent] only matters while charging.
 */
@Composable
fun GreggIcon(state: GreggState, tint: Color, size: Dp = 44.dp, batteryPercent: Double? = null, modifier: Modifier = Modifier) {
    val glyph = remember(state) { PathParser().parsePathString(glyphFor(state)).toPath().apply { fillType = PathFillType.EvenOdd } }
    val t = rememberInfiniteTransition(label = "gregg")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin")
    val back by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "back")
    val blink by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "blink")
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 200f
        scale(unit, unit, pivot = Offset.Zero) {
            scale(DISC_SCALE, pivot = Offset(100f, 100f)) { drawPath(glyph, tint) }
            when (state) {
                GreggState.Idle, GreggState.Charged -> ring(tint)
                GreggState.Error -> ring(tint, blink)
                GreggState.Charging -> {
                    ring(tint, 0.22f)
                    val sweep = ((batteryPercent ?: 100.0).coerceIn(0.0, 100.0) * 3.6).toFloat().coerceAtLeast(10f)
                    arcs(tint, -90f, sweep)
                }
                GreggState.Cleaning -> parts(tint, 3, 24f, spin)
                GreggState.Paused -> parts(tint, 3, 24f, 20f)
                GreggState.Returning -> parts(tint, 2, 90f, -back)
            }
        }
    }
}

/** What Gregg (the robot vacuum) is doing, as the status icon shows it. */
enum class GreggState {
    /** Not doing anything and not on the dock. */
    Idle,
    /** On the dock and still charging: the ring fills to the battery level and a bolt shows. */
    Charging,
    /** On the dock and full: the house, with the ring closed. */
    Charged,
    /** Cleaning: the ring in three parts turns round him. */
    Cleaning,
    /** Paused: the same ring, stopped, with pause bars. */
    Paused,
    /** Heading for the dock: the house, with two parts of the ring running backwards. */
    Returning,
    /** Stuck or faulted: the ring closes and blinks, with an exclamation mark. */
    Error,
}

const val HOUSE = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M95.9,77.9 L97.8,76.7 L99.6,76.2 L101.5,76.4 L103.4,77.3 L122.8,95.4 L124.2,96.8 L125.3,98.4 L126.0,99.6 L126.6,101.4 L127.2,104.2 L127.3,119.8 L127.1,121.7 L126.6,123.2 L126.0,124.2 L124.9,125.1 L123.4,125.7 L121.6,126.0 L107.9,126.0 L107.3,125.8 L107.0,125.2 L107.0,109.7 L106.9,108.2 L106.5,107.0 L105.6,105.9 L104.3,105.1 L102.6,104.8 L97.4,104.8 L96.0,105.1 L94.6,105.7 L94.0,106.3 L93.5,107.0 L93.1,108.2 L93.0,109.3 L93.0,125.1 L92.9,125.6 L92.6,125.8 L92.1,126.0 L77.4,125.9 L76.2,125.6 L75.1,125.1 L74.3,124.4 L73.6,123.6 L73.0,122.1 L72.7,120.3 L72.7,105.2 L73.1,102.3 L74.0,99.6 L75.2,97.6 L76.9,95.7 Z"

const val IDLE = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87.5,88 a12.5,12.5 0 1,0 25,0 a12.5,12.5 0 1,0 -25,0Z M95,118 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0Z M65.23,78.27 A41,41 0 0,1 134.77,78.27 L130.53,80.92 A36,36 0 0,0 69.47,80.92Z"

const val PAUSED = "M146.0,98.9 L145.7,94.4 L144.9,89.9 L143.7,85.6 L142.1,81.4 L140.0,77.3 L137.6,73.5 L134.8,70.0 L131.7,66.7 L128.3,63.7 L124.6,61.1 L120.7,58.9 L116.6,57.1 L112.3,55.7 L107.9,54.7 L103.4,54.1 L98.9,54.0 L94.4,54.3 L89.9,55.1 L85.6,56.3 L81.4,57.9 L77.3,60.0 L73.5,62.4 L70.0,65.2 L66.7,68.3 L63.7,71.7 L61.1,75.4 L58.9,79.3 L57.1,83.4 L55.7,87.7 L54.7,92.1 L54.1,96.6 L54.0,101.1 L54.3,105.6 L55.1,110.1 L56.3,114.4 L57.9,118.6 L60.0,122.7 L62.4,126.5 L65.2,130.0 L68.3,133.3 L71.7,136.3 L75.4,138.9 L79.3,141.1 L83.4,142.9 L87.7,144.3 L92.1,145.3 L96.6,145.9 L101.1,146.0 L105.6,145.7 L110.1,144.9 L114.4,143.7 L118.6,142.1 L122.7,140.0 L126.5,137.6 L130.0,134.8 L133.3,131.7 L136.3,128.3 L138.9,124.6 L141.1,120.7 L142.9,116.6 L144.3,112.3 L145.3,107.9 L145.9,103.4 Z M86.5,76.0 L90.5,76.1 L91.7,76.3 L92.8,76.8 L94.0,77.8 L95.0,79.1 L95.5,80.2 L95.8,81.8 L95.8,118.6 L95.5,119.8 L95.0,120.9 L93.7,122.5 L92.0,123.5 L90.1,124.0 L85.5,123.9 L84.3,123.7 L83.2,123.2 L82.3,122.5 L81.2,121.2 L80.5,119.8 L80.2,118.2 L80.2,81.4 L80.8,79.5 L81.4,78.4 L82.3,77.5 L83.2,76.8 L84.3,76.3 Z M110.5,76.0 L114.5,76.1 L115.7,76.3 L116.8,76.8 L118.0,77.8 L118.8,78.8 L119.5,80.2 L119.8,81.8 L119.8,118.6 L119.5,119.8 L119.0,120.9 L117.7,122.5 L116.0,123.5 L114.1,124.0 L109.5,123.9 L108.3,123.7 L107.2,123.2 L106.3,122.5 L105.2,121.2 L104.5,119.8 L104.2,118.2 L104.2,81.4 L104.8,79.5 L105.4,78.4 L106.3,77.5 L107.2,76.8 L108.3,76.3 Z"

const val TURRET = "M54,100 a46,46 0 1,0 92,0 a46,46 0 1,0 -92,0Z M87.5,88 a12.5,12.5 0 1,0 25,0 a12.5,12.5 0 1,0 -25,0Z M95,118 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0Z M65.23,78.27 A41,41 0 0,1 134.77,78.27 L130.53,80.92 A36,36 0 0,0 69.47,80.92Z"

/** The part of the disc glyph that is cut out of the disc, in the icon board's drawing (the disc is drawn at 46 and scaled up to 57). */
fun glyphFor(state: GreggState): String = when (state) {
    GreggState.Idle -> IDLE
    GreggState.Charging -> CHARGING
    GreggState.Charged, GreggState.Returning -> HOUSE
    GreggState.Cleaning -> TURRET
    GreggState.Paused -> PAUSED
    GreggState.Error -> ERROR
}

@Composable
fun ChargerCard(entities: Map<String, EntityState>, call: CallService) {
    val mode = entities[ZAPPI_MODE]
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val charging = power > 0
    val over = chargerOverrides(entities)
    // Stop was picked but the charger's readings haven't caught up yet (they refresh about once a minute).
    val stopping = mode?.state == "Stopped" && over.charging
    val tone = toneColors(Tone.Car)
    val session = entities[ZAPPI_SESSION]?.state?.toDoubleOrNull()
    val status = when {
        mode == null -> "Loading…"
        stopping -> "Stopping…"
        charging -> "Charging · ${"%.1f".format(power / 1000)} kW"
        else -> entities[ZAPPI_PLUG]?.state ?: entities[ZAPPI_STATUS]?.state ?: "Idle"
    }
    // "Starting…" for up to a minute after a tap.
    var pending by remember { mutableStateOf("") }
    LaunchedEffect(pending) { if (pending.isNotEmpty()) { delay(60_000); pending = "" } }
    // Start charge or Fast at the normal rate with the cheap rate coming up: ask first.
    var ask by remember { mutableStateOf<ChargeAsk?>(null) }
    var askFor by remember { mutableStateOf("charge") }
    ask?.let {
        ChargeAskDialog(
            it,
            onLater = { chargeLater(call); ask = null },
            onNow = {
                if (askFor == "fast") call("select", "select_option", ZAPPI_MODE, data("option" to "Fast"))
                else { pending = "charge"; chargeNow(entities, call) }
                ask = null
            },
            onCancel = { ask = null },
        )
    }
    val modeItems = chargerModes(mode?.options().orEmpty()).map { m ->
        TileItem(m.icon, m.name, mode?.state == m.key) {
            val q = if (m.key == "Fast") chargeAsk(entities, System.currentTimeMillis()) else null
            if (q != null) { askFor = "fast"; ask = q } else call("select", "select_option", ZAPPI_MODE, data("option" to m.key))
        }
    }
    val ink = tone.onContainer
    val fold = rememberFold("grid-fold|charger")
    val rate = Octopus.current(Octopus.allRates(entities), System.currentTimeMillis())?.let { "${Octopus.pence(it.pence)}p rate now" }
    if (fold.value) {
        GridCard(tone.container, ink, 2.5f, "Car Charger", fold = fold) {
            val first = 8f - modeItems.size
            Column(Modifier.at(0.5f, 0.9f, first - 0.5f - 0.5f, 1.2f), verticalArrangement = Arrangement.Center) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
                if (rate != null) Text(rate, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
            }
            modeItems.forEachIndexed { i, m ->
                SmallModeButton(m.icon, m.selected, tone, ink, false, Modifier.atCentre(first + i, 1.5f, 0.8f, 0.8f), mode?.available == true, m.label, m.onClick)
            }
        }
        return
    }
    val teal = toneColors(Tone.Car)
    val grey = toneColors(Tone.Grey)
    val sm = if (mode?.available == true) smartCharge(entities) else null
    val roleChoice = if (mode?.available == true) carRoleChoice(entities) else null
    // Down the card: the details (to line 3.5), the car choice, the charge buttons, the modes.
    var y = 3.5f
    val carsY = if (roleChoice != null) { val v = y; y += 1.5f; v } else 0f
    val barY = if (mode?.available == true) { val v = y; y += 1.5f; v } else 0f
    val modesY = y
    GridCard(tone.container, ink, modesY + 1.5f + 0.5f, "Car Charger", fold = fold) {
        Column(Modifier.at(0.5f, 1.25f, 5f, 2.25f)) {
            Text(status, style = MaterialTheme.typography.titleMedium, color = ink)
            if (session != null) Text("${temp(session)} kWh this session", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
            if (rate != null) Text(rate, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
            if (sm != null) Text(smartText(sm, Octopus.allRates(entities), System.currentTimeMillis()), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
        }
        if (power > 0) Text("${"%.1f".format(power / 1000)} kW", style = MaterialTheme.typography.headlineMedium, color = ink, modifier = Modifier.at(5.5f, 1.25f, 2f, 1f).wrapContentSize(Alignment.CenterEnd))
        if (roleChoice != null) {
            val c = roleChoice
            GridButton(TileItem("mdi:car-electric", CAR_NAME_PRIMARY, c.role == CAR_ROLE_PRIMARY) { chooseCarRole(call, CAR_ROLE_PRIMARY, c.confirmed) }, teal, ink, true, false, Modifier.at(0.5f, carsY, 3.25f, 1f))
            GridButton(TileItem("mdi:car", CAR_NAME_ADDITIONAL, c.role == CAR_ROLE_ADDITIONAL) { chooseCarRole(call, CAR_ROLE_ADDITIONAL, c.confirmed) }, teal, ink, true, false, Modifier.at(4.25f, carsY, 3.25f, 1f))
        }
        if (mode?.available == true) {
            // Always shown: a button and a status. Grey with no car, teal while charging, tappable when it can start.
            val main: Triple<TileItem, ToneColors, Pair<Color, Boolean>> = when {
                stopping -> Triple(TileItem("mdi:stop-circle-outline", "Stopping…", true) {}, grey, ink to false)
                over.charging -> Triple(TileItem("mdi:lightning-bolt", "Charging", true) {}, teal, teal.accent to false)
                over.show -> Triple(
                    TileItem("mdi:lightning-bolt", if (pending == "charge") "Starting…" else "Start charge", true) {
                        val q = chargeAsk(entities, System.currentTimeMillis())
                        if (q != null) { askFor = "charge"; ask = q } else { pending = "charge"; chargeNow(entities, call) }
                    },
                    teal, ink to (pending != "charge"),
                )
                else -> Triple(TileItem("mdi:ev-plug-type2", "No car connected", false) {}, grey, ink to false)
            }
            val barW = if (sm != null) 3.25f else 7f
            GridButton(main.first, main.second, main.third.first, main.third.second, false, Modifier.at(0.5f, barY, barW, 1f))
            if (sm != null) {
                GridButton(TileItem("mdi:clock-fast", "Smart charge", sm.on) { call("input_boolean", if (sm.on) "turn_off" else "turn_on", SMART_ENABLED, data()) }, teal, ink, true, false, Modifier.at(4.25f, barY, 3.25f, 1f))
            }
        }
        modeItems.forEachIndexed { i, m ->
            val w = 1.5f; val gap = 1f / 3f
            if (modeItems.size == 4) GridButton(m, teal, ink, mode?.available == true, true, Modifier.at(0.5f + i * (w + gap), modesY, w, 1.5f))
        }
        if (modeItems.size != 4) GridTiles(modeItems, modesY, 1.5f, teal, ink, mode?.available == true, stacked = true)
    }
}

/**
 * The charger's one button, as on the dashboard's charger card: [show] while a car is plugged in (or charging),
 * Start charge until it's [charging]. [locked] is null when the charger doesn't say; it only decides whether Start
 * charge unlocks first (the sensor stays on even mid-charge, so it isn't shown).
 */
data class ChargerOverrides(val show: Boolean, val locked: Boolean?, val charging: Boolean)

/** A small icon button on a folded grid card: 0.8 of a cell, the current mode filled in the card's strong colour. */
@Composable
fun SmallModeButton(icon: String, selected: Boolean, tone: ToneColors, ink: Color, triggered: Boolean, modifier: Modifier, enabled: Boolean, description: String, onClick: () -> Unit) {
    val bg = when { selected && triggered -> tone.onAccent; selected -> tone.accent; triggered -> Color.White.copy(alpha = 0.30f); else -> ink.copy(alpha = 0.10f) }
    val fg = when { selected && triggered -> Color.White; selected -> tone.onAccent; else -> ink }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(enabled = enabled, onClickLabel = description, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { HaIcon(icon, CdVectors.ShieldOutline, fg, 20.dp) }
}

/** A status as a pill: the card's strong colour on a faint wash of itself. Every card's status goes in one (the rest of the text is white). */
@Composable
fun StatusPill(text: String, tone: ToneColors, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text, color = tone.accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 1,
        modifier = modifier.background(tone.accent.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
    )
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

const val ZAPPI_LOCKED = "binary_sensor.zappi_locked"

const val ZAPPI_MODE = "select.zappi_charge_mode"

const val ZAPPI_PLUG = "sensor.zappi_plug_status"

const val ZAPPI_POWER = "sensor.zappi_charging_power"

const val ZAPPI_SESSION = "sensor.zappi_charge_added_session"

const val ZAPPI_STATUS = "sensor.zappi_status"

fun chargerOverrides(entities: Map<String, EntityState>): ChargerOverrides {
    val power = entities[ZAPPI_POWER]?.state?.toDoubleOrNull() ?: 0.0
    val plug = entities[ZAPPI_PLUG]?.takeIf { it.available }?.state
    // The Zappi sits at 0 W / "Waiting for EV" between bursts; its plug status still says Charging.
    val charging = power > 100 || plug?.startsWith("charging", ignoreCase = true) == true
    val plugged = plug != null && !plug.contains("disconnected", ignoreCase = true)
    val locked = entities[ZAPPI_LOCKED]?.takeIf { it.available }?.let { it.state == "on" }
    return ChargerOverrides(plugged || charging, locked, charging)
}

/** One of the Zappi's modes: what it's called in Home Assistant, what the button says, and its icon. */
data class ChargerMode(val key: String, val name: String, val icon: String)

/**
 * The icons the cards use for modes, in one place. The defaults below are the dashboard's own; [overrides] (keyed
 * `group.key`, such as `fan.sleep`) take over when Home Assistant supplies icons, so the app follows the dashboard.
 */
object IconMap {
    val overrides = mutableStateMapOf<String, String>()

    fun of(group: String, key: String, default: String): String = overrides["$group.${key.lowercase()}"] ?: default

    /** Takes Home Assistant's list (`church_drive/icons`: group, then mode, then icon). Without the integration, nothing changes. */
    fun load(result: Any?) {
        val icons = (result as? org.json.JSONObject)?.optJSONObject("icons") ?: return
        val next = mutableMapOf<String, String>()
        for (group in icons.keys()) {
            val modes = icons.optJSONObject(group) ?: continue
            for (key in modes.keys()) {
                val icon = if (modes.isNull(key)) null else modes.optString(key).takeIf { it.isNotBlank() }
                if (icon != null) next["$group.${key.lowercase()}"] = icon
            }
        }
        overrides.clear()
        overrides.putAll(next)
    }
}

/** The Zappi's modes in the dashboard's order (Stop, Eco, Eco+, Fast), those the charger offers. */
fun chargerModes(options: List<String>): List<ChargerMode> =
    listOf(
        ChargerMode("Stopped", "Stop", IconMap.of("charger", "Stopped", "mdi:stop-circle-outline")),
        ChargerMode("Eco", "Eco", IconMap.of("charger", "Eco", "mdi:leaf")),
        ChargerMode("Eco+", "Eco+", IconMap.of("charger", "Eco+", "mdi:solar-power")),
        ChargerMode("Fast", "Fast", IconMap.of("charger", "Fast", "mdi:lightning-bolt")),
    ).filter { options.isEmpty() || it.key in options }

/** How long an "all is well" line stays before it slides away. */
const val FADE_AFTER_MS = 8_000L

/**
 * A line of the page header. A good (green) line is only news once after the app updates: it shows for a few seconds, then
 * slides away to the side (each a moment after the one above), or swipes off sooner, and stays away on later visits. A line
 * that warns (amber, red...) stays, can't be swiped away, and a good line comes back when it starts to warn.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HeaderLine(line: JSONObject, index: Int, seen: Set<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = line.optString("text")
    val text = rememberTemplate(key)
    val alert = rememberTemplate(line.optString("alert_when"))?.trim()?.lowercase() == "true"
    val colour = if (alert) line.optString("alert_color") else line.optString("color")
    val tone = toneColors(toneFromColour(colour) ?: Tone.Green)
    val icon = if (alert) line.optString("alert_icon").ifBlank { line.optString("icon") } else line.optString("icon")
    if (text.isNullOrBlank()) return
    // Who's home is part of the alarm card now (for administrators); its line only speaks up when everyone is out and the alarm is off.
    if (isPeopleLine(key) && (!LocalIsAdmin.current || !alert)) return
    var gone by androidx.compose.runtime.remember { mutableStateOf(!alert && key in seen) }
    // The line is good (not a warning): note that it has been shown, and put it away. A warning always brings it back.
    LaunchedEffect(alert) {
        if (alert) gone = false else if (!gone) {
            HeaderSeen.add(context, key)
            kotlinx.coroutines.delay(FADE_AFTER_MS)
            gone = true
        }
    }
    val body = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth().background(tone.container, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToneIconName(icon, CdVectors.Info, tone, 32)
            Text(styledLine(text), color = tone.onContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    val leave = androidx.compose.animation.core.tween<Float>(500, delayMillis = index * LEAVE_STAGGER_MS)
    androidx.compose.animation.AnimatedVisibility(
        visible = !gone,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(500, delayMillis = index * LEAVE_STAGGER_MS)) { it } +
            androidx.compose.animation.fadeOut(leave) +
            androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(300, delayMillis = index * LEAVE_STAGGER_MS + 400)),
    ) {
        if (alert) {
            Box(Modifier.padding(bottom = 8.dp)) { body() }
        } else {
            val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { if (it != androidx.compose.material3.SwipeToDismissBoxValue.Settled) { HeaderSeen.add(context, key); gone = true }; true },
            )
            androidx.compose.material3.SwipeToDismissBox(state = state, backgroundContent = { }, modifier = Modifier.padding(bottom = 8.dp)) { body() }
        }
    }
}

/** The Security page's lines: doors open, the last doorbell or movement, who's home. Each is a template Home Assistant renders. */
@Composable
fun HeaderLines(config: JSONObject) {
    val lines = config.optJSONArray("header_lines")
    val context = androidx.compose.ui.platform.LocalContext.current
    val seen = androidx.compose.runtime.remember { HeaderSeen.load(context) }
    Column {
        for (i in 0 until (lines?.length() ?: 0)) {
            val line = lines?.optJSONObject(i) ?: continue
            HeaderLine(line, i, seen)
        }
    }
}

/** The pause between one line leaving and the next. */
const val LEAVE_STAGGER_MS = 180

/** The header line that says who is home: its template reads the people. */
fun isPeopleLine(template: String) = "states.person" in template

/** Every card's folded state, kept by key for as long as the app is open, so a card stays as it was left when you go to another page and back. */
object FoldStore {
    val states = mutableMapOf<String, androidx.compose.runtime.MutableState<Boolean>>()
}

/** One button on the grid: icon and label (side by side, or stacked when [stacked]), filled in the strong colour when selected. Corners 18 dp, as every agreed button. */
@Composable
fun GridButton(item: TileItem, tone: ToneColors, content: Color, enabled: Boolean, stacked: Boolean, modifier: Modifier) {
    val ink = (if (item.selected) tone.onAccent else content).copy(alpha = if (enabled) 1f else 0.4f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.clip(shape).background(if (item.selected) tone.accent else content.copy(alpha = 0.10f), shape)
            .clickable(enabled = enabled, onClickLabel = item.label, onClick = item.onClick).semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        if (stacked) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                HaIcon(item.icon, CdVectors.Info, ink, 24.dp)
                if (item.showLabel) CentredText(item.label, ink, 12.sp, Modifier.padding(top = 4.dp), lineHeight = 14.sp)
            }
        } else {
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HaIcon(item.icon, CdVectors.Info, ink, Ui.TileIcon)
                if (item.showLabel) CentredText(item.label, ink, Ui.TileText, Modifier.padding(end = 2.dp))
            }
        }
    }
}

/**
 * A card on the grid. [rows] is its height in rows. The title sits on row 1 with the top of its lowercase letters on the middle of the
 * row and its left edge on the half line of column A. [fold] (when the card can fold) draws the arrow in column 8 of row 1.
 */
@Composable
fun GridCard(
    container: Color,
    content: Color,
    rows: Float,
    title: String,
    modifier: Modifier = Modifier,
    deviceEntity: String? = null,
    titleColor: Color = content,
    fold: androidx.compose.runtime.MutableState<Boolean>? = null,
    /** Where the fold arrow's cell starts, in cells from the top (0 for the title row). */
    arrowTop: Float = 0f,
    body: @Composable GridScope.() -> Unit,
) {
    val openDevice = LocalOpenDevice.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().then(
            if (deviceEntity == null) Modifier
            else Modifier.androidxLongPress {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                openDevice(deviceEntity)
            },
        ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(modifier = Modifier.glowBehind(container)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = maxWidth / 8f
                Box(Modifier.fillMaxWidth().height(cell * rows)) {
                    val scope = GridScope(cell)
                    if (title.isNotBlank()) {
                        Text(
                            gridTitle(title), style = MaterialTheme.typography.headlineSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = cell * 0.5f, end = cell).fillMaxWidth().paddingFromBaseline(top = cell * 0.5f + 12.dp),
                        )
                    }
                    if (fold != null) {
                        val folded = fold.value
                        Box(
                            Modifier.offset(cell * 7f, cell * arrowTop).size(cell).clip(RoundedCornerShape(50))
                                .clickable(onClickLabel = if (folded) "Expand" else "Collapse") { fold.value = !folded }
                                .semantics { contentDescription = if (folded) "Expand" else "Collapse" },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                if (folded) CdVectors.ChevronDown else CdVectors.ChevronUp, contentDescription = null,
                                tint = content.copy(alpha = 0.6f), modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    scope.body()
                }
            }
        }
    }
}

/**
 * The card grid agreed on the card review page: a card is 8 equal columns wide with square cells, so its height is a number of rows
 * (whole or half). Row 1 is the title line: the title across columns 1 to 7 and the fold arrow in column 8. Everything else is placed
 * in cell units on the grid's lines (see the `at` helpers), so every card lines up the same way.
 */
class GridScope(val cell: Dp) {
    /** A box [w] by [h] cells whose top left corner is at ([x], [y]) cells from the card's top left. */
    fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = offset(cell * x, cell * y).size(cell * w, cell * h)

    /** A box [w] by [h] cells centred on the point ([cx], [cy]) cells from the card's top left. */
    fun Modifier.atCentre(cx: Float, cy: Float, w: Float, h: Float): Modifier = at(cx - w / 2f, cy - h / 2f, w, h)

    /** A box from [x] cells to the right edge of column 8 minus [rightGap] cells, [h] tall, at [y]. */
    fun Modifier.across(x: Float, y: Float, h: Float, rightGap: Float = 0.5f): Modifier =
        offset(cell * x, cell * y).width(cell * (8f - x - rightGap)).height(cell * h)
}

/**
 * A row of buttons on the grid at row line [y], [h] cells tall: three are 2 cells wide with half a cell between, four are 1.5 wide with a third
 * between, two are 3.25 wide, one fills the width; any other number shares the width with a quarter cell between.
 */
@Composable
fun GridScope.GridTiles(items: List<TileItem>, y: Float, h: Float, tone: ToneColors, content: Color, enabled: Boolean, stacked: Boolean = h > 1.2f) {
    val n = items.size
    if (n == 0) return
    val (w, gap) = when (n) {
        1 -> 7f to 0f
        2 -> 3.25f to 0.5f
        3 -> 2f to 0.5f
        4 -> 1.5f to (1f / 3f)
        else -> ((7f - 0.25f * (n - 1)) / n) to 0.25f
    }
    items.forEachIndexed { i, it -> GridButton(it, tone, content, enabled, stacked, Modifier.at(0.5f + i * (w + gap), y, w, h)) }
}

/** A thin level bar (8 dp), [fraction] of its track filled in [colour]. */
@Composable
fun LevelBar(fraction: Float, colour: Color, ink: Color, modifier: Modifier = Modifier, thickness: Dp = 8.dp) {
    Box(modifier.height(thickness).background(ink.copy(alpha = 0.25f), RoundedCornerShape(thickness / 2))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thickness).background(colour, RoundedCornerShape(thickness / 2)))
    }
}

fun Modifier.androidxLongPress(onLongPress: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) })

/** A battery's bar colour: green when full, then yellow, amber and red as it empties. */
fun batteryFade(f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    return if (x >= 0.75f) Color(0xFF4CC38A) else levelFade(x)
}

/** Each word of a title starts with a capital letter. */
fun gridTitle(title: String): String = title.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** The level bar's colour as it empties: blue when full, then green, yellow, amber, red. One colour for the whole fill. */
fun levelFade(f: Float): Color {
    val stops = listOf(0f to Color(0xFFE5534B), 0.25f to Color(0xFFE8A33B), 0.5f to Color(0xFFE6C93A), 0.75f to Color(0xFF4CC38A), 1f to Color(0xFF5B8CFF))
    val x = f.coerceIn(0f, 1f)
    for (i in 0 until stops.size - 1) {
        val (f0, c0) = stops[i]; val (f1, c1) = stops[i + 1]
        if (x <= f1) return androidx.compose.ui.graphics.lerp(c0, c1, (x - f0) / (f1 - f0))
    }
    return stops.last().second
}

/** Whether a grid card is folded, kept by [key] while the app is open (by default open, or folded when [folded] is true the first time). */
@Composable
fun rememberFold(key: String, folded: Boolean = false): androidx.compose.runtime.MutableState<Boolean> {
    // Also kept on the phone, so it is still as you left it after the app has been closed.
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("church_drive_folds", android.content.Context.MODE_PRIVATE)
    val state = androidx.compose.runtime.remember(key) { FoldStore.states.getOrPut(key) { mutableStateOf(prefs.getBoolean(key, folded)) } }
    androidx.compose.runtime.LaunchedEffect(key, state.value) { prefs.edit().putBoolean(key, state.value).apply() }
    return state
}

/**
 * Which "all is well" lines of a page header have already been shown since the app last updated. A good line is news once
 * per update; after that it stays away until it warns.
 */
object HeaderSeen {
    private const val PREFS = "header_lines"
    private const val KEY_BUILD = "build"
    private const val KEY_SEEN = "seen"

    /** The installed app's version and build, so what was seen is forgotten after an update (read from the package, as this file is shared by the apps). */
    private fun build(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName}.${info.longVersionCode}"
    }.getOrDefault("")

    /** The lines seen so far: none once the app has been updated since they were stored. */
    fun seenFor(storedBuild: String?, build: String, stored: Set<String>): Set<String> = if (storedBuild == build) stored else emptySet()

    fun load(context: Context): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return seenFor(p.getString(KEY_BUILD, null), build(context), p.getStringSet(KEY_SEEN, emptySet()).orEmpty())
    }

    fun add(context: Context, key: String) {
        val seen = load(context) + key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BUILD, build(context)).putStringSet(KEY_SEEN, seen).apply()
    }
}

/** Whether the signed-in person is an administrator: who's home is for them only. */
val LocalIsAdmin = compositionLocalOf { false }

/** The app's template watcher, set once in MainActivity. */
val LocalTemplates = compositionLocalOf<Templates?> { null }

/** HA's rendering of a template (kept up to date), or null until it arrives or if there's no template. */
@Composable
fun rememberTemplate(template: String?): String? {
    val templates = LocalTemplates.current ?: return null
    if (template.isNullOrBlank()) return null
    // Templates may use the signed-in person's `my_list` and `my_name`, as the dashboard's do.
    val me = LocalUserName.current
    val full = if (Regex("\\bmy_(list|name)\\b").containsMatchIn(template)) {
        "{% set my_name = '${me?.trim()?.substringBefore(' ').orEmpty().replace("'", "")}' %}" +
            "{% set my_list = '${myTodoList(me).orEmpty()}' %}" + template
    } else template
    LaunchedEffect(full) { templates.watch(full) }
    return templates.value(full)
}

@Composable
fun OctopusPlanCard(entities: Map<String, EntityState>) {
    val now = System.currentTimeMillis()
    val plan = octopusPlan(entities, now) ?: return
    val first = plan.slots.firstOrNull()
    val tone = toneColors(Tone.Energy)
    // Grid card: the title on row 1, a line per slot half a cell apart (the slot being used now in a pill), two and a half rows for one slot, half a row more for each.
    val shown = plan.slots.take(6)
    val rows = if (shown.isEmpty()) 3f else 2f + 0.5f * shown.size
    GridCard(tone.container, tone.onContainer, rows, "Octopus Charging Plan") {
        if (shown.isEmpty()) {
            Text("Nothing planned yet. Octopus plans once the car is plugged in.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.at(0.5f, 1.25f, 7f, 1.5f))
        } else shown.forEachIndexed { k, slot ->
            val y = 1.5f + 0.5f * k
            Box(Modifier.at(0.5f, y - 0.25f, 7f, 0.5f), contentAlignment = Alignment.CenterStart) {
                if (slot.start <= now) StatusPill(planLine(slot, now), tone)
                else Text(planLine(slot, now), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A titled group of cards, like the dashboard's section panel: a coloured icon, the title,
 * a live summary on the right, and a chevron when tapping the header opens the section's page.
 */
@Composable
fun SectionPanel(
    title: String,
    icon: ImageVector? = null,
    iconName: String? = null,
    tone: Tone = Tone.Grey,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    /** Draws the header's icon itself (Gregg's status), in place of [iconName] and [icon]. */
    iconSlot: (@Composable (ToneColors) -> Unit)? = null,
    /** Tapping the heading folds the panel away and back (remembered for the title while the app is open). */
    collapsible: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = toneColors(tone)
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else if (collapsible) it.clickable(onClickLabel = if (open) "Collapse $title" else "Expand $title") { open = !open } else it }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconSlot != null) iconSlot(colors)
            else if (iconName == GREGG_IDLE_ICON) GreggIcon(GreggState.Idle, colors.accent, 36.dp)
            else if (iconName != null) ToneIconName(iconName, icon ?: CdVectors.ShieldOutline, colors, size = 36)
            else if (icon != null) ToneIcon(icon, colors, size = 36)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                // A section's title sits on the page background, not on a card, so it takes the section's colour.
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            if (!summary.isNullOrBlank()) {
                StatusPill(summary, colors)
            }
            if (onClick != null) {
                Icon(CdVectors.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (collapsible) {
                Icon(if (open) CdVectors.ChevronUp else CdVectors.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Folding minimises the cards (each shrinks to a short strip) rather than hiding them.
        androidx.compose.runtime.CompositionLocalProvider(LocalCompactCards provides (collapsible && !open)) {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/** A line rendered by Home Assistant with `<b>` in it, as text with the bold parts bold (other tags dropped). */
fun styledLine(html: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    Regex("(<[^>]*>)|([^<]+)").findAll(html).forEach { m ->
        val tag = m.groupValues[1]
        val text = m.groupValues[2]
        if (tag.equals("<b>", ignoreCase = true)) {
            bold = true
        } else if (tag.equals("</b>", ignoreCase = true)) {
            bold = false
        } else if (text.isNotEmpty()) {
            if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
            } else {
                append(text)
            }
        }
    }
}
