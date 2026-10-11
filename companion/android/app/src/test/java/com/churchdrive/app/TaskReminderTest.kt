package com.churchdrive.app

import com.churchdrive.app.ha.MobileApp
import com.churchdrive.app.ha.SNOOZE_MINUTES
import com.churchdrive.app.ha.parseTaskAction
import com.churchdrive.app.ha.snoozeLabel
import com.churchdrive.app.ui.NO_PLACE
import com.churchdrive.app.ui.Place
import com.churchdrive.app.ui.parsePlaceReminders
import com.churchdrive.app.ui.placeChoiceOf
import com.churchdrive.app.ui.placeChoices
import com.churchdrive.app.ui.reminderKey
import com.churchdrive.app.ui.zoneOfChoice
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReminderTest {
    @Test
    fun theHousesButtonIdsAreRead() {
        val a = parseTaskAction("CD|snooze|todo.priorities_jamie|abc|Jamie|zone.tesco")!!
        assertEquals("snooze", a.kind)
        assertEquals("todo.priorities_jamie", a.todo)
        assertEquals("zone.tesco", a.zone)
        assertEquals("", parseTaskAction("CD|done|todo.cleaning|u1|Hayley|")!!.zone)
    }

    @Test
    fun otherButtonsAreIgnored() {
        listOf(null, "", "OPEN", "CD|nuke|todo.x|u|J|", "CD|done|light.x|u|J|", "CD|done|todo.x|u|J").forEach { assertNull(it, parseTaskAction(it)) }
    }

    @Test
    fun theSnoozeLengthsAreTheFiveOnOffer() {
        assertEquals(listOf(5, 10, 20, 30, 60), SNOOZE_MINUTES)
        assertEquals(listOf("5 minutes", "10 minutes", "20 minutes", "30 minutes", "1 hour"), SNOOZE_MINUTES.map(::snoozeLabel))
    }

    @Test
    fun aPushKeepsOnlyTheHousesTaskButtons() {
        val event = JSONObject(
            """{"title":"At Tesco: Buy milk","message":"You're at Tesco.","data":{"tag":"t","actions":[
                {"action":"CD|done|todo.shopping_list|u|Jamie|zone.tesco","title":"Done"},
                {"action":"CD|snooze|todo.shopping_list|u|Jamie|zone.tesco","title":"Snooze…"},
                {"action":"OTHER","title":"Nope"}]}}""",
        )
        assertEquals(listOf("Done", "Snooze…"), MobileApp.parsePush(event)!!.actions.map { it.title })
    }

    @Test
    fun theSnoozeMessageGoesToTheHouseAsAnEvent() {
        val a = parseTaskAction("CD|snooze|todo.priorities_jamie|abc|Jamie|")!!
        val body = MobileApp.taskActionBody(a, "snooze", 20)
        assertEquals("fire_event", body.getString("type"))
        val event = body.getJSONObject("data").getJSONObject("event_data")
        assertEquals("church_drive_task_action", body.getJSONObject("data").getString("event_type"))
        assertEquals(20, event.getInt("minutes"))
        assertEquals("abc", event.getString("uid"))
        assertEquals("Jamie", event.getString("who"))
    }

    @Test
    fun placesAndWhichTaskIsTiedToOne() {
        val reply = JSONObject(
            """{"zones":[{"zone":"zone.home","name":"Home"},{"zone":"zone.tesco","name":"Tesco"}],
                "reminders":{"todo.priorities_jamie|abc":{"zone":"zone.tesco","who":["Jamie"]}},"snooze_minutes":[5,10]}""",
        )
        val found = parsePlaceReminders(reply)!!
        assertEquals(listOf(Place("zone.home", "Home"), Place("zone.tesco", "Tesco")), found.places)
        assertEquals("zone.tesco", found.zoneByTask[reminderKey("todo.priorities_jamie", "abc")])
        assertEquals(listOf(NO_PLACE, "Home", "Tesco"), placeChoices(found.places))
        assertEquals("Tesco", placeChoiceOf(found.places, "zone.tesco"))
        assertEquals(NO_PLACE, placeChoiceOf(found.places, null))
        assertEquals("zone.home", zoneOfChoice(found.places, "Home"))
        assertNull(zoneOfChoice(found.places, NO_PLACE))
    }

    @Test
    fun aHouseWithoutPlaceRemindersGivesNothing() {
        assertNull(parsePlaceReminders(null))
        assertNull(parsePlaceReminders(JSONObject("{}")))
    }
}
