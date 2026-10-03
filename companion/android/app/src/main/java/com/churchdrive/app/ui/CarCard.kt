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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** The integrations that make cars (Vauxhall and Peugeot through Stellantis, the VW group). */
val CAR_PLATFORMS = setOf("stellantis_vehicles", "vag_connect", "volkswagencarnet", "volkswagen_we_connect_id")

/** The entities of one car that the card reads, by their ids ("" when the car hasn't got one). */
data class CarEntities(val battery: String, val range: String, val fuel: String, val fuelRange: String, val plugged: String, val charging: String, val end: String, val tracker: String)

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
    )
}

data class CarRow(
    val device: String, val name: String, val battery: Double?, val range: String, val fuel: Double?, val fuelRange: String,
    val plugged: Boolean, val charging: Boolean, val end: String, val where: String, val owners: List<String>,
)

private fun reading(e: EntityState?): String =
    e?.state?.toDoubleOrNull()?.let { "${Math.round(it)} ${e.str("unit_of_measurement").orEmpty()}".trim() }.orEmpty()

/** "Full by 06:30" when the end is a time, else "45 min left". */
fun chargeEndText(state: String, unit: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = parseMillis(state)
    return if (at != null) "Full by " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(at).atZone(zone))
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
            val charging = entities[e.charging]?.state == "on"
            val end = entities[e.end]
            val endText = if (charging && end != null && end.available && end.state.isNotBlank()) chargeEndText(end.state, end.str("unit_of_measurement"), zone) else ""
            CarRow(
                device, name, entities[e.battery]?.state?.toDoubleOrNull(), reading(entities[e.range]), entities[e.fuel]?.state?.toDoubleOrNull(),
                reading(entities[e.fuelRange]), entities[e.plugged]?.state == "on", charging, endText, where, owned,
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
fun CarCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry) {
    val rows = carRows(config, entities, registry, personOf(LocalUserName.current, entities))
    if (rows.isEmpty()) return
    val charging = rows.count { it.charging }
    val tone = toneColors(Tone.Teal)
    EntityCard(tone.container, tone.onContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            config.optString("title").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            rows.forEach { car ->
                val barColour = when {
                    car.battery == null -> Color.Gray
                    car.battery < 20 -> Color(0xFFEF5350)
                    car.battery < 40 -> Color(0xFFFFA726)
                    else -> tone.accent
                }
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
                        ToneIconName(if (car.charging) "mdi:car-electric" else "mdi:car", Icons.Filled.Info, tone, 36)
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
                }
            }
        }
    }
}
