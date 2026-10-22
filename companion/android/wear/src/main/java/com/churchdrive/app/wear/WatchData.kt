package com.churchdrive.app.wear

import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ui.SceneLooks
import com.churchdrive.app.ui.ask
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

// The house entities the watch reads and controls. The same ones as the phone app's cards (ALARM_ENTITY, VACUUM_ENTITY and ZAPPI_* in ui/Cards.kt).
const val ALARM = "alarm_control_panel.church_drive_alarm"
const val ALARM_ACTOR = "sensor.church_drive_alarm_actor"
const val VACUUM = "vacuum.gregg"
const val VACUUM_BATTERY = "sensor.gregg_battery"
const val VACUUM_SUCTION = "select.gregg_suction_level"
const val VACUUM_INTENSITY = "select.gregg_cleaning_intensity"
const val VACUUM_MOP = "select.gregg_mop_intensity"
const val ZAPPI_MODE = "select.zappi_charge_mode"
const val ZAPPI_POWER = "sensor.zappi_charging_power"
const val ZAPPI_STATUS = "sensor.zappi_status"
const val ZAPPI_PLUG = "sensor.zappi_plug_status"
const val ZAPPI_SESSION = "sensor.zappi_charge_added_session"
const val ZAPPI_LOCKED = "binary_sensor.zappi_locked"
const val SMART_ENABLED = "input_boolean.zappi_smart_charge"
const val SMART_STATE = "input_select.zappi_smart_charge_state"
const val CHARGE_LATER_SCRIPT = "script.zappi_charge_later"

/**
 * What the watch knows about the house, kept up to date by one Home Assistant template that lists every entity the watch needs (all the
 * thermostats, fans, lights, the vacuum and its settings, the charger and the cars). Home Assistant re-sends it whenever one of them changes,
 * so the watch never has to be told about the other thousand entities in the house.
 */
object WatchData {
    val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val config = MutableStateFlow(WatchConfig.Default)
    val floors = MutableStateFlow<List<WFloor>>(emptyList())
    val looks = MutableStateFlow(SceneLooks.Empty)

    /** Area names by area id, for rooms the dashboard names by their area. */
    val areaNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** True once there is something to show: remembered from last time, or sent by the house. */
    val loaded = MutableStateFlow(false)

    /** True once the house itself has sent the states (until then the remembered ones are on screen, with the loader over them). */
    val live = MutableStateFlow(false)

    private var lastSaved = 0L

    private var subscription = -1

    private var prefs: android.content.SharedPreferences? = null

    /** Shows what was known last time until the house answers (so the pages are right on the first frame). */
    fun restore(context: android.content.Context) {
        val p = context.applicationContext.getSharedPreferences("watch_data", android.content.Context.MODE_PRIVATE)
        prefs = p
        p.getString("config", null)?.let { raw -> runCatching { config.value = WatchConfig.parse(JSONObject(raw)) } }
        // What the house last said, so the pages are there at once while the live answer catches up.
        p.getString("dashboard", null)?.let { raw -> runCatching { floors.value = WatchLights.parse(JSONObject(raw)) } }
        p.getString("library", null)?.let { raw -> runCatching { looks.value = SceneLooks.parse(JSONObject(raw), null) } }
        p.getString("entities", null)?.let { raw -> runCatching { if (!live.value) show(JSONObject(raw)) } }
    }

    /** Only the watch settings (Clive's app needs no more). */
    suspend fun loadConfig() {
        val raw = WatchHa.api.ask("church_drive/watch", JSONObject()) ?: return
        config.value = WatchConfig.parse(raw)
        (raw as? JSONObject)?.let { prefs?.edit()?.putString("config", it.toString())?.apply() }
    }

    /** Reads what the watch is set up to show and starts the live states. Call when the line comes up, and again after it drops. */
    suspend fun load() {
        val api = WatchHa.api
        loadConfig()
        api.ask("church_drive/library", JSONObject())?.let {
            looks.value = SceneLooks.parse(it, null)
            (it as? JSONObject)?.let { o -> prefs?.edit()?.putString("library", o.toString())?.apply() }
        }
        (api.ask("lovelace/config", JSONObject().put("url_path", WatchLights.DASHBOARD)) as? JSONObject)?.let {
            floors.value = WatchLights.parse(it)
            prefs?.edit()?.putString("dashboard", it.toString())?.apply()
        }
        follow()
    }

    /** Starts (or restarts) the template that keeps [entities] live. */
    fun follow() {
        if (subscription >= 0) WatchHa.api.close(subscription)
        val template = template(config.value, floors.value)
        subscription = WatchHa.api.subscribe("render_template", JSONObject().put("template", template).put("report_errors", false)) { event -> apply(event) }
    }

    /** The line dropped: the subscription is gone with it. */
    fun lost() {
        subscription = -1
        live.value = false
    }

    private fun apply(event: JSONObject) {
        val r = event.opt("result")
        val obj = when (r) {
            is JSONObject -> r
            is String -> runCatching { JSONObject(r) }.getOrNull()
            else -> null
        } ?: return
        show(obj)
        live.value = true
        // Kept for next time, at most every twenty seconds (the house changes things all day).
        val now = System.currentTimeMillis()
        if (now - lastSaved > 20_000L) {
            lastSaved = now
            prefs?.edit()?.putString("entities", obj.toString())?.apply()
        }
    }

    /** Puts a template answer on screen. */
    private fun show(obj: JSONObject) {
        entities.value = parseEntities(obj)
        obj.optJSONArray("areas")?.let { a ->
            val names = (0 until a.length()).mapNotNull { a.optJSONArray(it) }.filter { it.length() == 2 && !it.isNull(1) }.associate { it.optString(0) to it.optString(1) }
            if (names.isNotEmpty()) areaNames.value = names
        }
        if (entities.value.isNotEmpty()) loaded.value = true
    }

    /** The template's answer as entities. */
    internal fun parseEntities(obj: JSONObject): Map<String, EntityState> {
        val list = obj.optJSONArray("e") ?: return emptyMap()
        val out = LinkedHashMap<String, EntityState>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            out[id] = EntityState(id, o.optString("s", "unknown"), o.optJSONObject("a") ?: JSONObject())
        }
        return out
    }

    /** The entities whose full attributes the watch needs, and those it only reads a few attributes of. */
    private val FULL = listOf(ALARM_ACTOR)

    private fun slimIds(cfg: WatchConfig, floors: List<WFloor>): List<String> {
        val fixed = listOf(
            VACUUM_BATTERY, VACUUM_SUCTION, VACUUM_INTENSITY, VACUUM_MOP,
            ZAPPI_MODE, ZAPPI_POWER, ZAPPI_STATUS, ZAPPI_PLUG, ZAPPI_SESSION, ZAPPI_LOCKED, SMART_ENABLED, SMART_STATE, CHARGE_LATER_SCRIPT,
        )
        val cars = cfg.cars.flatMap { listOf(it.battery, it.plugged) }
        val scenes = floors.flatMap { f -> f.rooms.flatMap { r -> r.scenes.filter { it.haScene == null }.map { it.selectEntity } } }
        return (fixed + cars + scenes).distinct()
    }

    /**
     * The Jinja template: a JSON object with `e`, the entities as {id, s: state, a: attributes}, and `areas`, [area id, name] pairs.
     * Whole domains (climate, fan, vacuum, the alarm) come with every attribute; lights only with what the watch needs, so a house with
     * sixty lights doesn't send sixty light groups' member lists every time one changes.
     */
    internal fun template(cfg: WatchConfig, floors: List<WFloor>): String {
        fun list(ids: List<String>) = ids.joinToString(", ") { "'$it'" }
        val areas = WatchLights.areaIds(floors).toList()
        return buildString {
            append("{%- set ns = namespace(l=[]) -%}")
            append("{%- for dom in [states.climate, states.fan, states.vacuum, states.alarm_control_panel] -%}")
            append("{%- for s in dom -%}{%- set ns.l = ns.l + [{'id': s.entity_id, 's': s.state, 'a': s.attributes}] -%}{%- endfor -%}")
            append("{%- endfor -%}")
            // Air purifiers: the PM2.5 sensor that goes with each fan named so (sensor.<fan>_pm2_5).
            append("{%- for s in states.fan -%}{%- set pm = 'sensor.' ~ s.object_id ~ '_pm2_5' -%}")
            append("{%- if states(pm) not in ['unknown', 'unavailable'] -%}{%- set ns.l = ns.l + [{'id': pm, 's': states(pm), 'a': {}}] -%}{%- endif -%}{%- endfor -%}")
            append("{%- for s in states.light -%}{%- set ns.l = ns.l + [{'id': s.entity_id, 's': s.state, 'a': {'brightness': s.attributes.get('brightness'), ")
            append("'supported_color_modes': s.attributes.get('supported_color_modes'), 'friendly_name': s.name}}] -%}{%- endfor -%}")
            append("{%- for i in [${list(FULL)}] -%}{%- set s = states[i] -%}{%- if s is not none -%}")
            append("{%- set ns.l = ns.l + [{'id': i, 's': s.state, 'a': s.attributes}] -%}{%- endif -%}{%- endfor -%}")
            append("{%- for i in [${list(slimIds(cfg, floors))}] -%}{%- set s = states[i] -%}{%- if s is not none -%}")
            append("{%- set ns.l = ns.l + [{'id': i, 's': s.state, 'a': {'friendly_name': s.name, 'options': s.attributes.get('options'), 'unit_of_measurement': s.attributes.get('unit_of_measurement')}}] -%}")
            append("{%- endif -%}{%- endfor -%}")
            append("{%- set ar = namespace(l=[]) -%}")
            if (areas.isNotEmpty()) append("{%- for a in [${list(areas)}] -%}{%- set ar.l = ar.l + [[a, area_name(a)]] -%}{%- endfor -%}")
            append("{{ {'e': ns.l, 'areas': ar.l} | to_json }}")
        }
    }

    /** One service call. */
    fun call(domain: String, service: String, entity: String, data: JSONObject = JSONObject()) {
        WatchHa.client.callService(domain, service, entity, data)
    }
}
