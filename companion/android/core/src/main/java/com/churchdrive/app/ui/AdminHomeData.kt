package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import org.json.JSONObject

// What Clive Admin's Home page says: each card's headline, second line and rows, worked out from what the house knows. No drawing here
// (AdminHome.kt draws them), so the wording and the rules can be tested.

/** One thing on a card. */
sealed interface HomeItem {
    /** A line: icon, name and a value at the right, coloured by [level] when it is not fine. */
    data class Row(val icon: String, val name: String, val value: String, val level: HealthLevel = HealthLevel.Ok) : HomeItem

    /** A small heading inside the card, with a figure at the right. */
    data class Head(val name: String, val value: String) : HomeItem

    /** How a total splits between electricity and gas: a bar, with a label under each end. */
    data class Balance(val electricityPercent: Int, val left: String, val right: String) : HomeItem

    /** The height of this item on the card grid, in rows. */
    val height: Float
        get() = when (this) {
            is Balance -> 1f
            else -> 0.75f
        }
}

/** A card's headline and second line, its colour and the rows it shows open. */
data class HomeSummary(val tone: Tone, val icon: String, val line1: String, val line2: String, val items: List<HomeItem>)

private val DOOR_CLASSES = setOf("door", "window", "opening", "garage_door")
private val ALARM_CLASSES = setOf("smoke", "carbon_monoxide", "heat", "gas")

private fun List<String>.asNames(): String = joinToString(", ")

private fun day(ms: Long, now: Long): String {
    val today = HomeTime.format(now, "d MMM")
    val d = HomeTime.format(ms, "d MMM")
    return if (d == today) HomeTime.format(ms, "HH:mm") else d
}

/** The alarm's state in words. */
fun alarmWords(state: String): String = when (state) {
    "disarmed" -> "Disarmed"
    "armed_home" -> "Armed home"
    "armed_away" -> "Armed away"
    "armed_night" -> "Armed night"
    "arming" -> "Arming"
    "pending" -> "Waiting to arm"
    "triggered" -> "ALARM"
    else -> "Alarm not available"
}

/** The Security card: the alarm, doors, smoke and CO alarms, tampering, the base station and the last movement and doorbell. */
fun securitySummary(entities: Map<String, EntityState>, registry: Registry, now: Long): HomeSummary {
    val alarm = entities["alarm_control_panel.church_drive_alarm"]
    val state = alarm?.state ?: "unavailable"
    val binary = entities.values.filter { it.entityId.startsWith("binary_sensor.") && it.available }
    val doors = binary.filter { it.str("device_class") in DOOR_CLASSES && registry.platformOf(it.entityId) !in CAR_PLATFORMS }
    val open = doors.filter { it.state == "on" }
    val alarms = binary.filter { it.str("device_class") in ALARM_CLASSES }
    val sounding = alarms.filter { it.state == "on" }
    val tampered = binary.filter { it.str("device_class") == "tamper" && it.state == "on" }
    val base = entities["sensor.church_drive_base_station_info"]
    val acOk = base?.str("acStatus").let { it == null || it == "ok" }
    val commOk = base?.str("commStatus").let { it == null || it == "ok" }

    val tone = when {
        state == "triggered" || sounding.isNotEmpty() -> Tone.Red
        state == "armed_home" || state == "armed_night" -> Tone.AlarmHome
        state == "armed_away" -> Tone.AlarmAway
        state == "arming" || state == "pending" -> Tone.ArmingHome
        state == "disarmed" -> Tone.AlarmOff
        else -> Tone.Grey
    }
    val since = alarm?.lastChanged?.let { parseMillis(it) }
    val line2 = when {
        sounding.isNotEmpty() -> sounding.map { it.friendlyName.removeSuffix(" Alarm Status") }.asNames() + " sounding"
        open.isNotEmpty() -> open.map { it.friendlyName }.asNames() + " open"
        tampered.isNotEmpty() -> "Tamper: " + tampered.map { it.friendlyName.removeSuffix(" Tamper") }.asNames()
        else -> (since?.let { "Since ${HomeTime.format(it, "HH:mm")} · " } ?: "") + if (doors.isEmpty()) "no door sensors" else "doors closed"
    }

    val items = mutableListOf<HomeItem>()
    if (doors.isNotEmpty()) {
        if (open.isEmpty()) items += HomeItem.Row("cd:door", doors.map { it.friendlyName }.asNames(), "Closed")
        else open.forEach { d -> items += HomeItem.Row("cd:door-open", d.friendlyName, "Open since " + (d.lastChanged?.let { parseMillis(it) }?.let { day(it, now) } ?: "–"), HealthLevel.Warn) }
    }
    if (alarms.isNotEmpty()) {
        items += if (sounding.isEmpty()) HomeItem.Row("cd:smoke-detector", "Smoke and CO alarms", "${alarms.size} clear")
        else HomeItem.Row("cd:smoke-detector-alert", "Smoke and CO alarms", "${sounding.size} sounding", HealthLevel.Bad)
    }
    if (tampered.isNotEmpty()) items += HomeItem.Row("cd:alert-circle", "Tamper", tampered.size.toString() + " open", HealthLevel.Bad)
    if (base != null) {
        val power = if (acOk) "Mains" else "Battery power"
        val battery = base.str("batteryStatus")?.let { " · battery $it" }.orEmpty()
        items += HomeItem.Row("cd:plug", "Base station", if (commOk) power + battery else "Not answering", if (commOk && acOk) HealthLevel.Ok else HealthLevel.Warn)
    }
    fun latest(vararg classes: String): Pair<String, Long>? = entities.values
        .filter { it.entityId.startsWith("event.") && it.str("device_class") in classes }
        .mapNotNull { e -> parseMillis(e.state)?.let { e to it } }
        .maxByOrNull { it.second }
        ?.let { (e, ms) -> e.friendlyName.removeSuffix(" Motion").removeSuffix(" Doorbell") to ms }
    latest("motion")?.let { (name, ms) -> items += HomeItem.Row("cd:cctv", "Last movement", "$name · ${day(ms, now)}") }
    latest("doorbell")?.let { (name, ms) -> items += HomeItem.Row("cd:doorbell", "Last doorbell", "$name · ${day(ms, now)}") }

    val icon = when {
        tone == Tone.Red -> "cd:shield-alert"
        state == "disarmed" -> "cd:shield-off"
        state == "armed_home" || state == "armed_night" -> "cd:shield-home"
        state == "armed_away" -> "cd:shield-lock"
        else -> "cd:shield-outline"
    }
    return HomeSummary(tone, icon, alarmWords(state), line2, items)
}

/** The Device health card: its colour is the worst device's; open, the devices that need attention, then what will need you next. */
fun healthSummary(health: List<DeviceHealthItem>, rows: Int = 5): HomeSummary {
    val worst = health.maxOfOrNull { it.level } ?: HealthLevel.Ok
    val tone = when (worst) { HealthLevel.Bad -> Tone.Red; HealthLevel.Warn -> Tone.Amber; HealthLevel.Ok -> Tone.Green }
    val attention = health.filter { it.needsAttention }
    fun icon(i: DeviceHealthItem) = when {
        i.offline || i.staleReason != null -> "cd:network-off"
        i.batteryLevel != null && i.batteryLevel != HealthLevel.Ok -> "cd:battery-low"
        i.filterLevel != null && i.filterLevel != HealthLevel.Ok -> "cd:air-filter"
        else -> "cd:device-health"
    }
    val items = attention.take(rows).map { HomeItem.Row(icon(it), it.name, it.summary, it.level) }.toMutableList()
    // What will need you next: the lowest battery or filter among those that are fine.
    val next = health.filter { !it.needsAttention }
        .mapNotNull { i -> listOfNotNull(i.battery?.let { it to "cd:battery-100" }, i.filter?.let { it to "cd:air-filter" }).minByOrNull { it.first }?.let { (pct, ic) -> Triple(i, pct, ic) } }
        .sortedBy { it.second }
    next.take((rows - items.size).coerceAtLeast(0)).forEach { (i, pct, ic) -> items += HomeItem.Row(ic, i.name, "$pct%") }
    val line1 = if (health.isEmpty()) "No devices watched yet" else deviceHealthSummary(health)
    val line2 = if (attention.isEmpty()) "Nothing needs you" else attention.take(2).joinToString(" · ") { it.name }
    return HomeSummary(tone, "cd:device-health", line1, line2, items)
}

/** Who's home: each person and since when. */
fun peopleSummary(entities: Map<String, EntityState>, registry: Registry): HomeSummary {
    val rows = peopleRows(entities, registry)
    val home = rows.count { it.state == "home" }
    val items = rows.map { p ->
        val first = p.name.trim().substringBefore(' ')
        val word = presencePill(p.state, p.place, p.zone).first
        val since = entities[p.id]?.lastChanged?.let { parseMillis(it) }?.let { " since " + HomeTime.format(it, "HH:mm") }.orEmpty()
        if (p.state == "unknown") HomeItem.Row("cd:home-away", first, "Location not shared")
        else HomeItem.Row(if (p.state == "home") "cd:home" else "cd:home-away", first, word + since)
    }
    val line1 = if (rows.isEmpty()) "Nobody set up" else "$home home · ${rows.size - home} away"
    val names = rows.filter { it.state == "home" }.map { it.name.trim().substringBefore(' ') }
    val line2 = when { names.isEmpty() -> "Nobody home"; else -> names.joinToString(", ") }
    return HomeSummary(Tone.People, "cd:group", line1, line2, items)
}

/** When the cheapest-priced half hours run, as a start and an end: the run that is on now, else the next one. */
fun cheapWindow(rates: List<Rate>, now: Long): Pair<Long, Long>? {
    val low = Octopus.cheapest(rates) ?: return null
    fun cheap(r: Rate) = r.pence <= low + 0.01
    val sorted = rates.sortedBy { it.start }
    val first = sorted.firstOrNull { cheap(it) && it.end > now } ?: return null
    var start = first.start
    var end = first.end
    // Back to where this run began (the part before now is still in the day's rates), then forward to where it ends.
    for (r in sorted.reversed()) if (r.end == start && cheap(r)) start = r.start
    for (r in sorted) if (r.start == end && cheap(r)) end = r.end
    return start to end
}

/** The Energy card's top half, from the sensors: today's cost, the rate, the cheap window, today, gas, yesterday. */
fun energySummary(entities: Map<String, EntityState>, now: Long): HomeSummary {
    val elecToday = Octopus.find(entities, Octopus.electricityToday)?.state?.toDoubleOrNull()
    val gasToday = Octopus.find(entities, Octopus.gasToday)?.state?.toDoubleOrNull()
    val total = listOfNotNull(elecToday, gasToday).takeIf { it.isNotEmpty() }?.sum()
    val rates = Octopus.allRates(entities)
    val cur = Octopus.current(rates, now)
    val window = cheapWindow(rates, now)?.let { (s, e) -> "${Octopus.clock(s)} to ${Octopus.clock(e)}" }
    val rateWord = when {
        cur == null -> null
        Octopus.cheapUntil(rates, now) != null -> "Cheap now ${Octopus.pence(cur.pence)}p"
        else -> "Peak ${Octopus.pence(cur.pence)}p"
    }
    val line1 = total?.let { "£" + "%.2f".format(it) + " so far today" } ?: "Data not available"
    val line2 = listOfNotNull(rateWord, window?.let { "cheap $it" }).joinToString(" · ").ifBlank { "Rates not available" }
    val yesterday = listOfNotNull(
        Octopus.find(entities, Octopus.electricityYesterday)?.state?.toDoubleOrNull(),
        Octopus.find(entities, Octopus.gasYesterday)?.state?.toDoubleOrNull(),
    ).takeIf { it.isNotEmpty() }?.sum()
    val items = mutableListOf<HomeItem>()
    items += HomeItem.Row("cd:bolt", "Electricity today", Octopus.pounds(Octopus.find(entities, Octopus.electricityToday)) + (Octopus.find(entities, Octopus.electricityTodayKwh)?.let { " · " + Octopus.kwh(it) } ?: ""))
    items += HomeItem.Row("cd:fire", "Gas today", Octopus.pounds(Octopus.find(entities, Octopus.gasToday)))
    items += HomeItem.Row("cd:clock", "Yesterday", yesterday?.let { "£" + "%.2f".format(it) } ?: "Data not available")
    items += HomeItem.Row("cd:pound", "Cheap rate", window ?: "Data not available")
    return HomeSummary(Tone.Energy, "cd:bolt", line1, line2, items)
}

/** The Energy card's second half, from the last seven days; "Data not available" while they have not been read. */
fun weekItems(week: WeekSummary?): List<HomeItem> {
    if (week == null || week.days.none { it.hasData }) return listOf(HomeItem.Head("Last 7 days", "Data not available"))
    val out = mutableListOf<HomeItem>()
    out += HomeItem.Head("Last 7 days", EnergyWeek.pounds(week.cost) + (week.average?.let { " · " + EnergyWeek.pounds(it) + " a day" } ?: ""))
    EnergyWeek.balance(week)?.let { b -> out += HomeItem.Balance(b.elec, "Electricity ${b.elec}%", "Gas ${b.gas}%") }
    if (week.hasElec) out += HomeItem.Row("cd:bolt", "Electricity week", EnergyWeek.pounds(week.elecCost) + " · " + EnergyWeek.kwh(week.elecKwh))
    if (week.hasGas) out += HomeItem.Row("cd:fire", "Gas week", EnergyWeek.pounds(week.gasCost))
    week.cheapShare?.let { out += HomeItem.Row("cd:pound", "Used at cheap rate", "$it%") }
    return out
}

/** Updates and backups, from the System card's rows. */
fun systemSummary(entities: Map<String, EntityState>, now: Long): HomeSummary {
    val rows = SystemRows.rows(entities, now)
    val bad = rows.firstOrNull { it.bad }
    val updates = rows.firstOrNull { it.name == "Updates" }?.pill?.toIntOrNull() ?: 0
    val backup = rows.firstOrNull { it.name == "Backups" }
    val tone = when { bad != null -> Tone.Red; updates > 0 -> Tone.Amber; else -> Tone.Green }
    val line1 = when {
        bad != null -> bad.name + (bad.pill?.let { " " + it.lowercase() } ?: " not fine")
        updates > 0 -> "$updates update${if (updates == 1) "" else "s"} waiting"
        else -> "All fine"
    }
    val items = rows.map { r ->
        HomeItem.Row(r.icon, r.name, r.pill ?: r.sub, if (r.bad) HealthLevel.Bad else if (r.pill != null && r.name == "Updates") HealthLevel.Warn else HealthLevel.Ok)
    }
    return HomeSummary(tone, "cd:save", line1, backup?.sub ?: rows.firstOrNull()?.sub ?: "", items)
}

/** Clive's devices, from `church_drive/clive/devices`: phones with the app and voice satellites. */
fun cliveDevicesSummary(data: JSONObject?, now: Long): HomeSummary {
    if (data == null) return HomeSummary(Tone.Purple, "cd:smart-speaker", "Data not available", "", emptyList())
    val items = mutableListOf<HomeItem>()
    var total = 0
    var silent = 0
    val phones = data.optJSONArray("phones")
    for (i in 0 until (phones?.length() ?: 0)) {
        val p = phones?.optJSONObject(i) ?: continue
        total++
        val answering = p.optBoolean("answering")
        if (!answering) silent++
        val who = p.optString("who").ifBlank { "Phone" }
        val seen = p.optString("seen").takeIf { it.isNotBlank() }?.let { parseMillis(it) }
        val version = p.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" }
        items += HomeItem.Row(
            "cd:device-phone", "$who's ${p.optString("name").ifBlank { "phone" }}",
            if (answering) version ?: "Answering" else "Not heard" + (seen?.let { " since " + day(it, now) } ?: ""),
            if (answering) HealthLevel.Ok else HealthLevel.Warn,
        )
    }
    val sats = data.optJSONArray("satellites")
    for (i in 0 until (sats?.length() ?: 0)) {
        val s = sats?.optJSONObject(i) ?: continue
        total++
        val answering = s.optBoolean("answering")
        if (!answering) silent++
        items += HomeItem.Row("cd:smart-speaker", s.optString("name"), s.optString("state").ifBlank { if (answering) "Answering" else "Not answering" }, if (answering) HealthLevel.Ok else HealthLevel.Warn)
    }
    val line1 = when { total == 0 -> "No devices yet"; silent == 0 -> "All $total answering"; else -> "$silent of $total not answering" }
    val wake = data.optString("wake_word").takeIf { it.isNotBlank() && it != "null" }?.let { "Wake word: " + it.replace('_', ' ') }.orEmpty()
    // Clive's own purple; amber only when one of his devices is not answering.
    return HomeSummary(if (silent > 0) Tone.Amber else Tone.Purple, "cd:smart-speaker", line1, wake, items)
}

/** The kinds of edit sent from the website, in words. */
private fun editWords(kind: String): String = when (kind) {
    "palette" -> "Colours"
    "cards" -> "Card colours"
    "icons" -> "Icons"
    "layout" -> "Layout"
    "chats" -> "Chat notes"
    else -> kind.replaceFirstChar { it.uppercase() }
}

/** The edits waiting on the website for Clive to check, one line each ("Colours edit waiting · Jamie"). */
fun editsWaiting(entities: Map<String, EntityState>): List<String> {
    val pending = entities["sensor.church_drive_proposals"]?.attributes?.optJSONArray("pending") ?: return emptyList()
    return (0 until pending.length()).mapNotNull { pending.optJSONObject(it) }.filter { it.optString("status", "pending") == "pending" }.map { p ->
        editWords(p.optString("kind")) + " edit waiting" + p.optString("by").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    }
}
