package com.churchdrive.app.ui

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The Octopus "Last 7 days" card's rules. The Octopus Energy integration keeps every finished day as long-term statistics in
 * Home Assistant (the ones the Energy dashboard uses) and fills in days Octopus sends late, so the card reads those.
 */

/** The statistic ids for the meters, or null where the house has no such meter. */
data class WeekIds(
    val elecCost: String?, val elecKwh: String?, val offPeak: String?, val peak: String?, val standard: String?,
    val gasCost: String?, val gasKwh: String?,
) {
    fun all(): List<String> = listOfNotNull(elecCost, elecKwh, offPeak, peak, standard, gasCost, gasKwh)
}

/** One finished day. A value is null where Octopus hasn't sent it. */
data class WeekDay(
    val date: LocalDate, val elecCost: Double?, val elecKwh: Double?, val gasCost: Double?, val gasKwh: Double?,
    val offPeak: Double?, val peak: Double?, val standard: Double?,
) {
    val total: Double get() = (elecCost ?: 0.0) + (gasCost ?: 0.0)
    val hasData: Boolean get() = elecCost != null || gasCost != null

    /** The share of this day's electricity used at the cheap rate, when the tariff has cheap and other rates. */
    val cheapShare: Int?
        get() {
            val cheap = offPeak ?: 0.0
            val all = cheap + (peak ?: 0.0) + (standard ?: 0.0)
            return if (cheap > 0 && all > 0) Math.round(cheap / all * 100).toInt() else null
        }
}

data class WeekSummary(
    val days: List<WeekDay>, val hasElec: Boolean, val hasGas: Boolean,
    val elecCost: Double, val elecKwh: Double, val gasCost: Double, val gasKwh: Double,
    val cost: Double, val average: Double?, val cheapShare: Int?,
    val elecLatest: LocalDate?, val gasLatest: LocalDate?, val elecBehind: Int?, val gasBehind: Int?,
    val latestWithData: LocalDate?,
)

/** How the week's spending splits, as whole percentages adding to 100. */
data class Balance(val elec: Int, val gas: Int)

object EnergyWeek {
    /** The statistic ids from the entity ids of the two meters' `current_rate` sensors (their account numbers differ). */
    fun ids(elecRateEntity: String?, gasRateEntity: String?): WeekIds {
        fun stat(entity: String?, tail: String): String? =
            entity?.removePrefix("sensor.octopus_energy_")?.removeSuffix("current_rate")?.let { "octopus_energy:$it$tail" }
        return WeekIds(
            elecCost = stat(elecRateEntity, "previous_accumulative_cost"),
            elecKwh = stat(elecRateEntity, "previous_accumulative_consumption"),
            offPeak = stat(elecRateEntity, "previous_accumulative_consumption_off_peak"),
            peak = stat(elecRateEntity, "previous_accumulative_consumption_peak"),
            standard = stat(elecRateEntity, "previous_accumulative_consumption_standard"),
            gasCost = stat(gasRateEntity, "previous_accumulative_cost"),
            gasKwh = stat(gasRateEntity, "previous_accumulative_consumption_kwh"),
        )
    }

    /** The seven days up to and including yesterday (today isn't finished), oldest first. */
    fun lastDays(now: Long, zone: ZoneId, n: Int = 7): List<LocalDate> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (n downTo 1).map { today.minusDays(it.toLong()) }
    }

    /** [result] is `recorder/statistics_during_period` for a day period: { id: [ { start (ms), change } ] }. */
    fun build(result: Any?, ids: WeekIds, now: Long, zone: ZoneId, n: Int = 7): WeekSummary {
        val o = result as? JSONObject
        fun series(id: String?): Map<LocalDate, Double> {
            val arr = id?.let { o?.optJSONArray(it) } ?: return emptyMap()
            val out = HashMap<LocalDate, Double>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                if (r.isNull("change") || !r.has("start")) continue
                val change = r.optDouble("change")
                if (change.isNaN()) continue
                out[Instant.ofEpochMilli(r.optLong("start")).atZone(zone).toLocalDate()] = change
            }
            return out
        }
        val elecCost = series(ids.elecCost); val elecKwh = series(ids.elecKwh)
        val gasCost = series(ids.gasCost); val gasKwh = series(ids.gasKwh)
        val off = series(ids.offPeak); val peak = series(ids.peak); val std = series(ids.standard)
        val dates = lastDays(now, zone, n)
        val days = dates.map { WeekDay(it, elecCost[it], elecKwh[it], gasCost[it], gasKwh[it], off[it], peak[it], std[it]) }
        val withData = days.filter { it.hasData }
        val yesterday = dates.last()
        fun latest(pick: (WeekDay) -> Double?): LocalDate? = days.lastOrNull { pick(it) != null }?.date
        fun behind(d: LocalDate?): Int? = d?.let { ChronoUnit.DAYS.between(it, yesterday).toInt() }
        val cheap = days.sumOf { it.offPeak ?: 0.0 }
        val other = days.sumOf { (it.peak ?: 0.0) + (it.standard ?: 0.0) }
        val eLatest = latest { it.elecCost }
        val gLatest = latest { it.gasCost }
        val cost = withData.sumOf { it.total }
        return WeekSummary(
            days = days, hasElec = ids.elecCost != null, hasGas = ids.gasCost != null,
            elecCost = days.sumOf { it.elecCost ?: 0.0 }, elecKwh = days.sumOf { it.elecKwh ?: 0.0 },
            gasCost = days.sumOf { it.gasCost ?: 0.0 }, gasKwh = days.sumOf { it.gasKwh ?: 0.0 },
            cost = cost, average = if (withData.isEmpty()) null else cost / withData.size,
            cheapShare = if (cheap > 0 && cheap + other > 0) Math.round(cheap / (cheap + other) * 100).toInt() else null,
            elecLatest = eLatest, gasLatest = gLatest, elecBehind = behind(eLatest), gasBehind = behind(gLatest),
            latestWithData = withData.lastOrNull()?.date,
        )
    }

    fun balance(w: WeekSummary): Balance? {
        val total = w.elecCost + w.gasCost
        if (total <= 0) return null
        val elec = Math.round(w.elecCost / total * 100).toInt()
        return Balance(elec, 100 - elec)
    }

    /** "Electricity is 3 days behind (latest Wed 7 Oct)", or empty when all is current. [label] words a date. */
    fun lagNote(w: WeekSummary, label: (LocalDate) -> String): String {
        fun one(name: String, latest: LocalDate?, behind: Int?): String = when {
            latest == null -> "$name: nothing from Octopus in these days"
            (behind ?: 0) > 0 -> "$name is $behind day${if (behind == 1) "" else "s"} behind (latest ${label(latest)})"
            else -> ""
        }
        return listOf(
            if (w.hasElec) one("Electricity", w.elecLatest, w.elecBehind) else "",
            if (w.hasGas) one("Gas", w.gasLatest, w.gasBehind) else "",
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun pounds(v: Double?): String = v?.let { "£" + "%.2f".format(it) } ?: "–"

    fun kwh(v: Double?): String = v?.let { "%.1f kWh".format(it) } ?: ""
}
