package com.churchdrive.app.ui

import com.churchdrive.app.ha.EntityState

/**
 * Who can see which device, as the house publishes it (`access` on `sensor.church_drive_people`): the entities that are not everyone's, each with the people
 * (person entities) who can see it. Administrators are in every list. A TV or speaker nobody was given to is for administrators only. Hiding, not a lock.
 */
object DeviceAccess {
    /** The signed-in user's Home Assistant id (from `auth/current_user`), set when the connection learns who is signed in. */
    @Volatile var userId: String? = null

    /** The person entity of the signed-in user (a person entity's `user_id` is the login it belongs to), or null when there is none. */
    fun me(entities: Map<String, EntityState>, userId: String? = this.userId): String? =
        userId?.takeIf { it.isNotBlank() }?.let { id -> entities.values.firstOrNull { it.entityId.startsWith("person.") && it.attributes.optString("user_id") == id }?.entityId }

    /** Whether [person] may see [entityId]: everyone for an entity the house does not list; otherwise only those listed. Nobody known is not let in. */
    fun canSee(entities: Map<String, EntityState>, entityId: String, person: String? = me(entities)): Boolean {
        val access = entities["sensor.church_drive_people"]?.attributes?.optJSONObject("access") ?: return true
        val who = access.optJSONArray(entityId) ?: return true
        return person != null && (0 until who.length()).any { who.optString(it) == person }
    }
}
