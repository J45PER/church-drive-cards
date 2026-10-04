package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.data
import java.time.LocalDateTime
import java.time.ZoneId

// The to-do highlights at the top of the Home page, as the dashboard's Home header has them: up to three of what is waiting
// for the signed-in person (their own list, the shared one and the house's jobs for them), overdue first.

const val SHARED_LIST = "todo.priorities_everyone"
const val HOUSE_LIST = "todo.priorities_automatic"

/** One row of the highlights. */
data class Highlight(val uid: String, val list: String, val text: String, val overdue: Boolean, val auto: Boolean, val icon: String)

/**
 * What is waiting, soonest due first (overdue before the rest), then in list order: the person's own, the shared list, the house's.
 * A repeating task is always on its list, so it shows only when it is due in the next two days; the house's jobs only when they are
 * for this person or everyone; the house's own jobs have no tick (the device clears them itself).
 */
fun todoHighlights(lists: Map<String, List<TodoItem>>, first: String?, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): List<Highlight> {
    fun order(id: String) = when (id) { HOUSE_LIST -> 2; SHARED_LIST -> 1; else -> 0 }
    val soon = now.plusDays(2)
    val open = mutableListOf<Triple<TodoItem, String, HouseTask?>>()
    lists.keys.sortedBy { order(it) }.forEach { list ->
        lists[list].orEmpty().forEach { item ->
            if (item.done) return@forEach
            val repeat = parseTaskWords(item.description).repeat
            if (repeat != null && !repeat.equals("once", ignoreCase = true)) {
                val due = item.due?.let { dueTime(it, zone) }
                if (due != null && due.isAfter(soon)) return@forEach
            }
            val auto = list == HOUSE_LIST || Regex("^Automatic").containsMatchIn(item.description.orEmpty())
            val task = if (auto) houseTask(item) else null
            if (list == HOUSE_LIST && task != null && !houseTaskFor(task, first)) return@forEach
            open += Triple(item, list, task)
        }
    }
    val today = now.toLocalDate().toString()
    return open.withIndex()
        .sortedWith(compareBy({ it.value.first.due?.let { d -> dueTime(d, zone) } ?: LocalDateTime.MAX }, { it.index }))
        .map { (_, v) ->
            val (item, list, task) = v
            Highlight(
                item.uid, list, item.summary,
                overdue = item.due?.take(10)?.let { it < today } == true,
                auto = task != null,
                icon = task?.icon ?: if (list == SHARED_LIST) "mdi:account-group" else "mdi:flag",
            )
        }
}

@Composable
fun TodoHighlights(entities: Map<String, EntityState>, call: CallService, onOpen: () -> Unit) {
    val me = LocalUserName.current
    val first = me?.trim()?.substringBefore(' ')
    val mine = myTodoList(me)
    val own = rememberTodoItems(mine.orEmpty(), entities)
    val shared = rememberTodoItems(SHARED_LIST, entities)
    val house = rememberTodoItems(HOUSE_LIST, entities)
    var ticked by remember { mutableStateOf(setOf<String>()) }
    val lists = buildMap {
        if (mine != null) own?.let { put(mine, it) }
        shared?.let { put(SHARED_LIST, it) }
        house?.let { put(HOUSE_LIST, it) }
    }
    val items = todoHighlights(lists, first).filter { it.uid !in ticked }
    val shown = items.take(3)
    val extra = items.size - shown.size
    val purple = toneColors(Tone.Purple)
    val red = toneColors(Tone.Red)
    val green = toneColors(Tone.Green)
    EntityCard(purple.container, purple.onContainer) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shown.isEmpty()) {
                    val loaded = own != null || shared != null || house != null
                    HighlightPill(if (loaded) "Nothing waiting on you" else "Loading your to-dos…", if (loaded) "mdi:check-circle-outline" else "mdi:format-list-checks", if (loaded) green else purple, null, onOpen)
                }
                shown.forEach { h ->
                    HighlightPill(h.text, h.icon, if (h.overdue) red else purple, if (h.auto) null else ({
                        ticked = ticked + h.uid
                        call("todo", "update_item", h.list, data("item" to h.uid, "status" to "completed"))
                    }), onOpen)
                }
            }
            Column(
                Modifier.width(58.dp).background(purple.accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).clickable { onOpen() }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HaIcon("mdi:format-list-checks", Icons.Filled.Info, purple.accent, 22.dp)
                Text(if (extra > 0) "+$extra more" else "To-do", color = purple.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HighlightPill(text: String, icon: String, tone: ToneColors, onTick: (() -> Unit)?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(tone.accent.copy(alpha = 0.16f), RoundedCornerShape(13.dp)).clickable { onOpen() }
            .padding(start = 8.dp, end = if (onTick != null) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HaIcon(icon, Icons.Filled.Info, tone.accent, 16.dp)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onTick != null) Box(
            Modifier.size(22.dp).border(2.dp, tone.accent.copy(alpha = 0.7f), CircleShape).clickable { onTick() },
            contentAlignment = Alignment.Center,
        ) { HaIcon("mdi:check", Icons.Filled.Info, MaterialTheme.colorScheme.onSurface, 14.dp) }
    }
}
