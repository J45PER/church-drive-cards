package com.churchdrive.app

import com.churchdrive.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTodoTest {
    private fun item(s: String, done: Boolean = false) = TodoItem(s, s, done, null, null)

    @Test
    fun addsOneThingToMyList() {
        assertEquals(AssistantTodoCommand.Add(listOf("Milk"), AssistantList.Mine), parseAssistantTodo("add milk"))
        assertEquals(AssistantTodoCommand.Add(listOf("Milk"), AssistantList.Mine), parseAssistantTodo("Hey Clive, please add milk to my list."))
    }

    @Test
    fun addsToTheNamedList() {
        assertEquals(AssistantTodoCommand.Add(listOf("Hoover the stairs"), AssistantList.Cleaning), parseAssistantTodo("add hoover the stairs to the cleaning list"))
        assertEquals(AssistantTodoCommand.Add(listOf("Bin bags"), AssistantList.Shared), parseAssistantTodo("put bin bags on the shared list"))
    }

    @Test
    fun aSentenceThatEndsInToSomeoneIsNotAList() {
        assertEquals(AssistantTodoCommand.Add(listOf("Talk to Gary"), AssistantList.Mine), parseAssistantTodo("add talk to Gary"))
    }

    @Test
    fun severalThingsWithCommas() {
        assertEquals(AssistantTodoCommand.Add(listOf("Bread", "Milk", "Eggs"), AssistantList.Mine), parseAssistantTodo("add bread, milk and eggs"))
    }

    @Test
    fun ticksThingsOff() {
        assertEquals(AssistantTodoCommand.Done("bins", null), parseAssistantTodo("tick off bins"))
        assertEquals(AssistantTodoCommand.Done("the bins", null), parseAssistantTodo("Clive, check off the bins"))
        assertEquals(AssistantTodoCommand.Done("bins", null), parseAssistantTodo("mark bins as done"))
        assertEquals(AssistantTodoCommand.Done("hoovering", AssistantList.Cleaning), parseAssistantTodo("complete hoovering on the cleaning list"))
    }

    @Test
    fun saysSoWhenSeveralFit() {
        val l = listOf(item("Take the bins out"), item("Bin bags"))
        assertEquals(2, (matchOpenTask(l, "bins") as AssistantMatch.Several).items.size)
    }

    @Test
    fun putSomethingOnIsNotATodo() {
        assertEquals(AssistantTodoCommand.Unknown, parseAssistantTodo("put the lights on"))
    }

    @Test
    fun anythingElseIsNotUnderstood() {
        assertEquals(AssistantTodoCommand.Unknown, parseAssistantTodo("turn on the lights"))
        assertEquals(AssistantTodoCommand.Unknown, parseAssistantTodo("unlock the front door"))
        assertEquals(AssistantTodoCommand.Unknown, parseAssistantTodo(""))
    }

    @Test
    fun findsTheOpenTaskBeingMeant() {
        val l = listOf(item("Take the bins out"), item("Milk", done = true), item("Milk and eggs"))
        assertEquals("Take the bins out", (matchOpenTask(l, "the bins") as AssistantMatch.One).item.summary)
        assertEquals("Milk and eggs", (matchOpenTask(l, "milk") as AssistantMatch.One).item.summary)
        assertEquals(AssistantMatch.None, matchOpenTask(l, "car tax"))
    }
}
