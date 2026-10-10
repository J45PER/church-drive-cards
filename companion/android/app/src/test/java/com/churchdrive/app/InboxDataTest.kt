package com.churchdrive.app

import com.churchdrive.app.ui.InboxTask
import com.churchdrive.app.ui.confirmTasks
import com.churchdrive.app.ui.InboxList
import com.churchdrive.app.ui.listChipName
import com.churchdrive.app.ui.parseAdded
import com.churchdrive.app.ui.parseInbox
import com.churchdrive.app.ui.parseInboxLists
import com.churchdrive.app.ui.parseSubmitted
import com.churchdrive.app.ui.sharedText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxDataTest {
    private val batch = """{"batches":[{"id":"abc","source":"Share","tasks":[
        {"id":"1","summary":"Buy milk","list":"todo.shopping_list","due":"","note":""},
        {"id":"2","summary":"  ","list":"todo.priorities_jamie","due":"","note":""},
        {"id":"3","summary":"Book dentist","list":"todo.priorities_jamie","due":"2026-10-16","note":"before Friday"}]}],
        "lists":[{"id":"todo.shopping_list","name":"Shopping list"},{"id":"todo.priorities_jamie","name":"Priorities Jamie"}]}"""

    @Test
    fun batchesDropBlankTasksAndKeepTheRest() {
        val b = parseInbox(JSONObject(batch)).single()
        assertEquals("abc", b.id)
        assertEquals(listOf("Buy milk", "Book dentist"), b.tasks.map { it.summary })
        assertEquals("2026-10-16", b.tasks[1].due)
    }

    @Test
    fun listsKeepTheirNamesAndChipsDropThePriorities() {
        val lists = parseInboxLists(JSONObject(batch))
        assertEquals(listOf("Shopping list", "Priorities Jamie"), lists.map { it.name })
        assertEquals("Jamie", listChipName("Priorities Jamie"))
        assertEquals("Shopping list", listChipName("Shopping list"))
    }

    @Test
    fun aSubmitWithNothingFoundIsNull() {
        assertNull(parseSubmitted(JSONObject("""{"batch":null,"tasks":[],"added":0}"""), "Share"))
        assertNull(parseSubmitted(null, "Share"))
        val ok = parseSubmitted(JSONObject("""{"batch":"b1","tasks":[{"summary":"Call Sam","list":"todo.priorities_jamie","due":"","note":""}]}"""), "Highlight")
        assertEquals("b1", ok?.id)
    }

    @Test
    fun onlyTickedAndNamedTasksAreConfirmed() {
        val arr = confirmTasks(
            listOf(
                InboxTask(" Buy milk ", "todo.shopping_list", "", ""),
                InboxTask("Skip me", "todo.shopping_list", "", "", keep = false),
                InboxTask("   ", "todo.shopping_list", "", ""),
            ),
        )
        assertEquals(1, arr.length())
        assertEquals("Buy milk", arr.getJSONObject(0).getString("summary"))
    }

    @Test
    fun sharedTextPrefersTheSharedTextThenTheHighlight() {
        assertEquals("a", sharedText("a", "b"))
        assertEquals("b", sharedText(" ", "b"))
        assertNull(sharedText(null, "  "))
    }

    @Test
    fun addedTasksSayWhichListEachWentOn() {
        val lists = listOf(InboxList("todo.shopping_list", "Shopping list"), InboxList("todo.priorities_jamie", "Priorities Jamie"))
        val reply = JSONObject("""{"batch":null,"added":2,"tasks":[
            {"summary":"Get milk","list":"todo.shopping_list","due":"","note":""},
            {"summary":"Book dentist","list":"todo.priorities_jamie","due":"","note":""}]}""")
        assertEquals(listOf("Get milk → Shopping list", "Book dentist → Jamie"), parseAdded(reply, lists))
        assertNull(parseAdded(null, lists))
    }
}
