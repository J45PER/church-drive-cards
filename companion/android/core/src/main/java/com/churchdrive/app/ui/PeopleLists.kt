package com.churchdrive.app.ui

import androidx.compose.runtime.Composable
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import org.json.JSONObject

// The Automatic to-dos panel's lists, shared so the Church Drive app and Clive Admin draw the same ones.

/** A person's to-do list for the Automatic to-dos panel: each person Church Drive knows who has a list. */
fun peopleLists(entities: Map<String, EntityState>): List<Pair<String, String>> {
    val people = entities["sensor.church_drive_people"]?.attributes?.optJSONArray("people") ?: return emptyList()
    return (0 until people.length()).mapNotNull { people.optJSONObject(it) }.mapNotNull { p ->
        val list = p.optString("list")
        val first = p.optString("first").ifBlank { p.optString("name") }
        if (list.isBlank() || entities[list]?.state.let { it == null || it == "unknown" || it == "unavailable" }) null else first to list
    }
}

/** The Automatic to-dos panel's generated cards: a to-do list for each person Church Drive knows. */
@Composable
fun PeopleListsCard(entities: Map<String, EntityState>, call: CallService) {
    peopleLists(entities).forEach { (first, list) ->
        TaskListCard(JSONObject().put("entity", list).put("title", first), entities, call)
    }
}

