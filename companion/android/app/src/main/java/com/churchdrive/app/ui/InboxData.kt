package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** One task the house found in some text, to be checked before it goes on a list. [list] is a to-do entity id. */
data class InboxTask(val summary: String, val list: String, val due: String, val note: String, val keep: Boolean = true)

/** A to-do list the tasks can go on. */
data class InboxList(val id: String, val name: String)

/** Tasks found in one piece of text, waiting for the person to check them. */
data class InboxBatch(val id: String, val source: String, val tasks: List<InboxTask>)

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun parseTasks(arr: JSONArray?): List<InboxTask> = arr.objects().mapNotNull { o ->
    val summary = o.optString("summary").trim()
    if (summary.isEmpty()) null else InboxTask(summary, o.optString("list"), o.optString("due"), o.optString("note"))
}

/** The batches from `church_drive/inbox`. */
fun parseInbox(result: Any?): List<InboxBatch> =
    (result as? JSONObject)?.optJSONArray("batches").objects().mapNotNull { o ->
        val tasks = parseTasks(o.optJSONArray("tasks"))
        val id = o.optString("id")
        if (id.isEmpty() || tasks.isEmpty()) null else InboxBatch(id, o.optString("source"), tasks)
    }

/** The lists from `church_drive/inbox`, with their friendly names. */
fun parseInboxLists(result: Any?): List<InboxList> =
    (result as? JSONObject)?.optJSONArray("lists").objects().map { InboxList(it.optString("id"), it.optString("name")) }

/** The batch `church_drive/inbox/submit` made, or null when nothing was found or it failed. */
fun parseSubmitted(result: Any?, source: String): InboxBatch? {
    val o = result as? JSONObject ?: return null
    val id = o.optString("batch").takeIf { it.isNotEmpty() && it != "null" } ?: return null
    return parseTasks(o.optJSONArray("tasks")).takeIf { it.isNotEmpty() }?.let { InboxBatch(id, source, it) }
}

/** The body of `church_drive/inbox/confirm`: only the ticked tasks, as edited. */
fun confirmTasks(tasks: List<InboxTask>): JSONArray = JSONArray().also { arr ->
    tasks.filter { it.keep && it.summary.isNotBlank() }.forEach {
        arr.put(JSONObject().put("summary", it.summary.trim()).put("list", it.list).put("due", it.due).put("note", it.note))
    }
}

/** A list's name for a chip: "Priorities Jamie" is just "Jamie". */
fun listChipName(name: String): String = name.removePrefix("Priorities ").trim().ifEmpty { name }

/** The text of a share: the shared text, else the highlighted text. Blank is nothing to send. */
fun sharedText(sendText: CharSequence?, processText: CharSequence?): String? =
    (sendText?.toString()?.takeIf { it.isNotBlank() } ?: processText?.toString()?.takeIf { it.isNotBlank() })?.trim()?.take(6000)

/** What `church_drive/inbox/submit` with auto_add put on the lists: each task's wording and its list's name, or null if it failed. */
fun parseAdded(result: Any?, lists: List<InboxList>): List<String>? {
    val o = result as? JSONObject ?: return null
    return parseTasks(o.optJSONArray("tasks")).map { t ->
        val where = lists.firstOrNull { it.id == t.list }?.let { listChipName(it.name) } ?: t.list.removePrefix("todo.")
        "${t.summary} → $where"
    }
}
