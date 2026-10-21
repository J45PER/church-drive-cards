package com.churchdrive.app

import com.churchdrive.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CliveTodoTest {
    private fun item(s: String, done: Boolean = false) = TodoItem(s, s, done, null, null)

    @Test
    fun addsOneThingToMyList() {
        assertEquals(CliveTodoCommand.Add(listOf("Milk"), CliveList.Mine), parseCliveTodo("add milk"))
        assertEquals(CliveTodoCommand.Add(listOf("Milk"), CliveList.Mine), parseCliveTodo("Hey Clive, please add milk to my list."))
    }

    @Test
    fun addsToTheNamedList() {
        assertEquals(CliveTodoCommand.Add(listOf("Hoover the stairs"), CliveList.Cleaning), parseCliveTodo("add hoover the stairs to the cleaning list"))
        assertEquals(CliveTodoCommand.Add(listOf("Bin bags"), CliveList.Shared), parseCliveTodo("put bin bags on the shared list"))
    }

    @Test
    fun aSentenceThatEndsInToSomeoneIsNotAList() {
        assertEquals(CliveTodoCommand.Add(listOf("Talk to Gary"), CliveList.Mine), parseCliveTodo("add talk to Gary"))
    }

    @Test
    fun severalThingsWithCommas() {
        assertEquals(CliveTodoCommand.Add(listOf("Bread", "Milk", "Eggs"), CliveList.Mine), parseCliveTodo("add bread, milk and eggs"))
    }

    @Test
    fun ticksThingsOff() {
        assertEquals(CliveTodoCommand.Done("bins", null), parseCliveTodo("tick off bins"))
        assertEquals(CliveTodoCommand.Done("the bins", null), parseCliveTodo("Clive, check off the bins"))
        assertEquals(CliveTodoCommand.Done("bins", null), parseCliveTodo("mark bins as done"))
        assertEquals(CliveTodoCommand.Done("hoovering", CliveList.Cleaning), parseCliveTodo("complete hoovering on the cleaning list"))
    }

    @Test
    fun saysSoWhenSeveralFit() {
        val l = listOf(item("Take the bins out"), item("Bin bags"))
        assertEquals(2, (matchOpenTask(l, "bins") as CliveMatch.Several).items.size)
    }

    @Test
    fun putSomethingOnIsNotATodo() {
        assertEquals(CliveTodoCommand.Unknown, parseCliveTodo("put the lights on"))
    }

    @Test
    fun anythingElseIsNotUnderstood() {
        assertEquals(CliveTodoCommand.Unknown, parseCliveTodo("turn on the lights"))
        assertEquals(CliveTodoCommand.Unknown, parseCliveTodo("unlock the front door"))
        assertEquals(CliveTodoCommand.Unknown, parseCliveTodo(""))
    }

    @Test
    fun findsTheOpenTaskBeingMeant() {
        val l = listOf(item("Take the bins out"), item("Milk", done = true), item("Milk and eggs"))
        assertEquals("Take the bins out", (matchOpenTask(l, "the bins") as CliveMatch.One).item.summary)
        assertEquals("Milk and eggs", (matchOpenTask(l, "milk") as CliveMatch.One).item.summary)
        assertEquals(CliveMatch.None, matchOpenTask(l, "car tax"))
    }
}
