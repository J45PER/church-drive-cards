package com.churchdrive.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import com.churchdrive.app.ha.data
import org.json.JSONArray
import org.json.JSONObject

// The cards of the Manager dashboard that an administrator sees in the app: each of its panels is a page in the account panel.

// ---------------------------------------------------------------------------------------------- Pure bits

/** A person's to-do list for the Automatic to-dos panel: each person Church Drive knows who has a list. */
fun peopleLists(entities: Map<String, EntityState>): List<Pair<String, String>> {
    val people = entities["sensor.church_drive_people"]?.attributes?.optJSONArray("people") ?: return emptyList()
    return (0 until people.length()).mapNotNull { people.optJSONObject(it) }.mapNotNull { p ->
        val list = p.optString("list")
        val first = p.optString("first").ifBlank { p.optString("name") }
        if (list.isBlank() || entities[list]?.state.let { it == null || it == "unknown" || it == "unavailable" }) null else first to list
    }
}

/** A vertical stack: its cards one under another. */
@Composable
fun StackCard(config: JSONObject, entities: Map<String, EntityState>, registry: Registry, call: CallService) {
    val cards = config.optJSONArray("cards") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until cards.length()) {
            val c = cards.optJSONObject(i) ?: continue
            CardView(CardSpec(c.optString("type"), c), entities, registry, call)
        }
    }
}

/** The Automatic to-dos panel's generated cards: a to-do list for each person Church Drive knows. */
@Composable
fun PeopleListsCard(entities: Map<String, EntityState>, call: CallService) {
    peopleLists(entities).forEach { (first, list) ->
        TaskListCard(JSONObject().put("entity", list).put("title", first), entities, call)
    }
}

