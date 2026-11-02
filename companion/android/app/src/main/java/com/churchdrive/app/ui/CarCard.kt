package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The dashboard's Car card: each car's battery, range and charging, from the car's own integration.
// Some cars (Stellantis) only send new figures when something happens, so while a car's plugged in and its figures
// are over 20 minutes old the card says when they're from, and Refresh presses the car's wake-up button.

/** The car battery's colour, the dashboard's rule: red under 20%, orange under 40%, else [ok] (the Car card's teal); grey when unknown. */
fun carBatteryColour(level: Double?, ok: Color): Color = when {
    level == null -> Color.Gray
    level < 20 -> Color(0xFFEF5350)
    level < 40 -> Color(0xFFFFA726)
    else -> ok
}

/**
 * The integrations that make cars (Vauxhall and Peugeot through Stellantis, the VW group), and Octopus: its Intelligent device is a car it
 * controls (the ID.5). That gives the battery % and its charging plan but not the plug, range or place; see [octopusCarState].
 */
val CAR_PLATFORMS = setOf("stellantis_vehicles", "vag_connect", "volkswagencarnet", "volkswagen_we_connect_id", "octopus_energy")

/** The entities of one car that the card reads, by their ids ("" when the car hasn't got one). */
data class CarEntities(val battery: String, val range: String, val fuel: String, val fuelRange: String, val plugged: String, val charging: String, val end: String, val tracker: String, val wake: String = "", val dispatch: String = "")

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

/** One planned charge of Octopus's plan for the car it controls: when, how much (kWh, always positive), and SMART or BOOST. */
data class PlanSlot(val start: Long, val end: Long, val kwh: Double, val source: String)

/** Octopus's plan: the planned dispatches that have not finished, oldest first, and whether one is on now. */
data class OctopusPlan(val slots: List<PlanSlot>, val active: Boolean)

private val DISPATCHING = Regex("binary_sensor\\.octopus_energy_.*_intelligent_dispatching")

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

/** One line of the plan: "Now: 23:51 to 04:02 · 32.5 kWh · Boost", or "02:30 to 04:30 · 15.6 kWh · Smart". */
fun planLine(slot: PlanSlot, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val kwh = if (slot.kwh > 0) " · " + "%.1f".format(java.util.Locale.UK, slot.kwh) + " kWh" else ""
    val kind = if (slot.source == "BOOST") "Boost" else "Smart"
    return (if (slot.start <= now) "Now: " else "") + HomeTime.format(slot.start, "HH:mm", zone) + " to " + HomeTime.format(slot.end, "HH:mm", zone) + kwh + " · " + kind
}

/** What a car made by Octopus's Intelligent device can say: whether it is plugged in and charging, and Octopus's plan for it. */
data class OctopusCar(val plugged: Boolean, val charging: Boolean, val plan: String)

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

data class CarRow(
    val device: String, val name: String, val battery: Double?, val range: String, val fuel: Double?, val fuelRange: String,
    val plugged: Boolean, val charging: Boolean, val end: String, val where: String, val owners: List<String>,
    /** When the figures are from (epoch ms), and the car's wake-up button ("" when it hasn't one). */
    val asOf: Long? = null, val wake: String = "",
)

/** Figures this old, while plugged in, get "Car last reported HH:MM · 25m ago" and Refresh. */
const val CAR_STALE_MS = 20 * 60_000L

/** When a car's figures are from: the integration's own "Last updated" (Stellantis: when the car sent them), else the state's. */
fun carAsOf(battery: EntityState?): Long? {
    if (battery == null) return null
    val raw = listOf("Last updated", "last_updated", "updated_at", "last_update").firstNotNullOfOrNull { battery.str(it)?.takeIf { v -> v.isNotBlank() } }
    return parseMillis(raw) ?: parseMillis(battery.lastChanged)
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

private fun reading(e: EntityState?): String =
    e?.state?.toDoubleOrNull()?.let { "${Math.round(it)} ${e.str("unit_of_measurement").orEmpty()}".trim() }.orEmpty()

/** "Full by 06:30" when the end is a time, else "45 min left". */
fun chargeEndText(state: String, unit: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = parseMillis(state)
    return if (at != null) "Full by " + HomeTime.format(at, "HH:mm", zone)
    else "$state${unit?.let { " $it" }.orEmpty()} left"
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

/** The signed-in person's `person.` entity, found by their name. */
fun personOf(name: String?, entities: Map<String, EntityState>): String? {
    val first = name?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
    if (first.isEmpty()) return null
    return entities.values.firstOrNull { it.entityId.startsWith("person.") && it.friendlyName.trim().substringBefore(' ').lowercase() == first }?.entityId
}

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
    val tone = toneColors(Tone.Teal)
    EntityCard(tone.container, tone.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            config.optString("title").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            rows.forEach { car ->
                val barColour = carBatteryColour(car.battery, tone.accent)
                val tag = when {
                    car.charging -> "Charging"
                    car.plugged -> "Plugged in"
                    else -> car.where
                }
                val sub = listOfNotNull(
                    car.range.takeIf { it.isNotBlank() }?.let { "$it electric" },
                    car.fuel?.let { "Fuel ${Math.round(it)}%" + car.fuelRange.takeIf { r -> r.isNotBlank() }?.let { r -> " · $r" }.orEmpty() },
                    car.end.takeIf { it.isNotBlank() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The make's logo (Vauxhall, Tesla...) when it is known, else a car.
                        Box(Modifier.size(36.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                            BrandOrIcon(car.name.trim().substringBefore(' '), if (car.charging) "mdi:car-electric" else "mdi:car", tone.accent, 22.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            if (tag.isNotBlank()) Text(
                                tag, color = tone.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp).background(tone.accent.copy(alpha = 0.18f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Text(car.battery?.let { "${Math.round(it)}%" } ?: "–", color = barColour, style = MaterialTheme.typography.headlineMedium)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(((car.battery ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(barColour, RoundedCornerShape(4.dp)))
                    }
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (carStale(car, now)) {
                        val amber = toneColors(Tone.Amber)
                        val asking = (asked[car.wake] ?: 0L) > now - 3 * 60_000L
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HaIcon("mdi:clock-alert-outline", CdVectors.Info, amber.accent, 16.dp)
                            Text(carUpdatedText(car.asOf!!, now), color = amber.accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (car.wake.isNotEmpty()) Box(Modifier.width(150.dp)) {
                                TileRow(
                                    listOf(TileItem("mdi:refresh", if (asking) "Asking the car…" else "Refresh", false) {
                                        asked = asked + (car.wake to System.currentTimeMillis())
                                        now = System.currentTimeMillis()
                                        call("button", "press", car.wake, data())
                                    }),
                                    tone, tone.onContainer, !asking,
                                )
                            }
                        }
                    }
                }
            }
        }
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
