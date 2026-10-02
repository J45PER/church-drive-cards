package com.churchdrive.app.ha

import org.json.JSONArray
import org.json.JSONObject

/**
 * Which entities belong to which device, and the devices' names, from Home Assistant's registries. The cards
 * use it to find a sensor's neighbours on the same device, such as a smoke alarm's battery.
 */
class Registry(private val entityDevice: Map<String, String>, private val deviceNames: Map<String, String>) {
    private val byDevice: Map<String, List<String>> =
        entityDevice.entries.groupBy({ it.value }, { it.key })

    /** The device's name (the one set by the user, else its own), or null. */
    fun deviceName(entityId: String): String? = entityDevice[entityId]?.let { deviceNames[it] }

    /** The other entities on the same device as [entityId], in registry order. */
    fun siblings(entityId: String): List<String> {
        val device = entityDevice[entityId] ?: return emptyList()
        return byDevice[device].orEmpty().filter { it != entityId }
    }

    companion object {
        val Empty = Registry(emptyMap(), emptyMap())

        /** [entities] is `config/entity_registry/list_for_display`; [devices] is `config/device_registry/list`. */
        fun parse(entities: Any?, devices: Any?): Registry {
            val list = (entities as? JSONObject)?.optJSONArray("entities")
            val entityDevice = mutableMapOf<String, String>()
            for (i in 0 until (list?.length() ?: 0)) {
                val e = list?.optJSONObject(i) ?: continue
                val id = e.optString("ei")
                val device = e.optString("di")
                if (id.isNotEmpty() && device.isNotEmpty()) entityDevice[id] = device
            }
            val names = mutableMapOf<String, String>()
            val d = devices as? JSONArray
            for (i in 0 until (d?.length() ?: 0)) {
                val o = d?.optJSONObject(i) ?: continue
                val name = o.optString("name_by_user").takeIf { it.isNotBlank() } ?: o.optString("name").takeIf { it.isNotBlank() }
                if (name != null && o.has("id")) names[o.getString("id")] = name
            }
            return Registry(entityDevice, names)
        }
    }
}
