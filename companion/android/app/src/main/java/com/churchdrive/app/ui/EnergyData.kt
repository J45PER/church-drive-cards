package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One half-hour of electricity price: when it starts and ends (epoch ms) and what it costs, in pence per kWh. */
data class Rate(val start: Long, val end: Long, val pence: Double)

/** The Octopus entities and rates the Energy page shows, found by the shape of their ids (the account numbers differ). */
object Octopus {
    fun find(entities: Map<String, EntityState>, pattern: String): EntityState? {
        val re = Regex(pattern)
        return entities.values.firstOrNull { re.matches(it.entityId) }
    }

    val electricityRate = "sensor\\.octopus_energy_electricity_.*_current_rate"
    val offPeak = "binary_sensor\\.octopus_energy_electricity_.*_off_peak"
    val demand = "sensor\\.octopus_energy_electricity_.*_current_demand"
    val electricityToday = "sensor\\.octopus_energy_electricity_.*_current_accumulative_cost"
    val electricityTodayKwh = "sensor\\.octopus_energy_electricity_.*_current_accumulative_consumption"
    val electricityYesterday = "sensor\\.octopus_energy_electricity_.*_previous_accumulative_cost"
    val electricityYesterdayKwh = "sensor\\.octopus_energy_electricity_.*_previous_accumulative_consumption"
    val gasRate = "sensor\\.octopus_energy_gas_.*_current_rate"
    val gasToday = "sensor\\.octopus_energy_gas_.*_current_accumulative_cost"
    val gasYesterday = "sensor\\.octopus_energy_gas_.*_previous_accumulative_cost"
    val points = "sensor\\.octopus_energy_a_.*_octoplus_points"
    val currentDay = "event\\.octopus_energy_electricity_.*_current_day_rates"
    val nextDay = "event\\.octopus_energy_electricity_.*_next_day_rates"

    /** The rates held in an event's `rates` attribute (today's or tomorrow's), oldest first. */
    fun rates(event: EntityState?): List<Rate> {
        val list = event?.attributes?.optJSONArray("rates") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val s = parseMillis(o.optString("start")) ?: return@mapNotNull null
            val e = parseMillis(o.optString("end")) ?: return@mapNotNull null
            if (!o.has("value_inc_vat")) return@mapNotNull null
            Rate(s, e, o.optDouble("value_inc_vat") * 100)
        }.sortedBy { it.start }
    }

    fun allRates(entities: Map<String, EntityState>): List<Rate> =
        (rates(find(entities, currentDay)) + rates(find(entities, nextDay))).distinctBy { it.start }.sortedBy { it.start }

    fun current(rates: List<Rate>, now: Long): Rate? = rates.firstOrNull { it.start <= now && now < it.end }

    /** The cheapest price in the list. */
    fun cheapest(rates: List<Rate>): Double? = rates.minOfOrNull { it.pence }

    private fun isCheap(r: Rate, low: Double) = r.pence <= low + 0.01

    /** Where "cheap" ends if it is cheap now: the end of the run of cheapest-priced half hours. */
    fun cheapUntil(rates: List<Rate>, now: Long): Long? {
        val low = cheapest(rates) ?: return null
        val cur = current(rates, now)?.takeIf { isCheap(it, low) } ?: return null
        var end = cur.end
        for (r in rates) if (r.start == end && isCheap(r, low)) end = r.end
        return end
    }

    /** When the next cheap half hour starts, if it is not cheap now. */
    fun nextCheapStart(rates: List<Rate>, now: Long): Long? {
        val low = cheapest(rates) ?: return null
        return rates.firstOrNull { it.start >= now && isCheap(it, low) }?.start
    }

    fun clock(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** "Cheap now 7.5p · until 05:30", "Peak 24.6p · cheap from 02:00", or "No rates yet". */
    fun rateLine(rates: List<Rate>, now: Long): String {
        val cur = current(rates, now) ?: return "No rates yet"
        val until = cheapUntil(rates, now)
        if (until != null) return "Cheap now ${pence(cur.pence)}p · until ${clock(until)}"
        val next = nextCheapStart(rates, now)
        return "Peak ${pence(cur.pence)}p" + (next?.let { " · cheap from ${clock(it)}" } ?: "")
    }

    fun pence(p: Double): String = "%.1f".format(p)

    /** Pounds from a sensor's state, "£1.23", or "–". */
    fun pounds(e: EntityState?): String = e?.state?.toDoubleOrNull()?.let { "£" + "%.2f".format(it) } ?: "–"

    fun kwh(e: EntityState?): String = e?.state?.toDoubleOrNull()?.let { "%.1f kWh".format(it) } ?: "–"

    /** A rate sensor (pounds per kWh) as pence, or "–". */
    fun rateText(e: EntityState?, digits: Int = 1): String =
        e?.state?.toDoubleOrNull()?.let { "%.${digits}f".format(it * 100) + "p" } ?: "–"

    fun power(e: EntityState?): String {
        val w = e?.state?.toDoubleOrNull() ?: return "Live use not available"
        return if (w < 1000) "${w.toInt()} W" else "%.1f kW".format(w / 1000)
    }
}

/** One row of the System card. */
data class SystemRow(val icon: String, val name: String, val sub: String, val pill: String? = null, val bad: Boolean = false)

object SystemRows {
    /** The administrator's view of how the house's systems are: internet, remote access, backups, updates. */
    fun rows(entities: Map<String, EntityState>, now: Long = System.currentTimeMillis()): List<SystemRow> {
        val rows = mutableListOf<SystemRow>()
        val internet = entities.values.firstOrNull {
            it.entityId.startsWith("binary_sensor.") && it.str("device_class") == "connectivity" &&
                Regex("wan|internet").containsMatchIn(it.entityId)
        }
        if (internet != null) {
            val up = internet.state == "on"
            rows += SystemRow("mdi:web", "Internet", if (up) "Connected" else "Down", if (up) "Online" else "Down", !up)
        }
        entities["binary_sensor.remote_ui"]?.let {
            val up = it.state == "on"
            rows += SystemRow("mdi:cloud-outline", "Remote access", "Home Assistant Cloud", if (up) "On" else "Down", !up)
        }
        val last = entities.values.firstOrNull { it.entityId.startsWith("sensor.backup_last_successful") }
        val next = entities.values.firstOrNull { it.entityId.startsWith("sensor.backup_next_scheduled") }
        if (last != null || next != null) {
            val lastMs = parseMillis(last?.state)
            val nextMs = parseMillis(next?.state)
            val old = lastMs != null && now - lastMs > 2 * 86_400_000L
            val sub = listOfNotNull(
                lastMs?.let { "Last ${Octopus.clock(it)}" } ?: "No backup yet",
                nextMs?.let { "next ${Octopus.clock(it)}" },
            ).joinToString(" · ")
            rows += SystemRow("mdi:content-save-outline", "Backups", sub, if (old) "Overdue" else null, old)
        }
        val updates = entities.values.filter { it.entityId.startsWith("update.") && it.state == "on" }
        rows += SystemRow(
            "mdi:update", "Updates",
            if (updates.isEmpty()) "Up to date"
            else updates.take(3).joinToString(", ") { it.str("title") ?: it.friendlyName } +
                (if (updates.size > 3) " and ${updates.size - 3} more" else ""),
            if (updates.isEmpty()) null else "${updates.size}",
        )
        return rows
    }
}
