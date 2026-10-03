package com.churchdrive.app

import com.churchdrive.app.ui.TaskWords
import com.churchdrive.app.ui.buildDescription
import com.churchdrive.app.ui.cleanTime
import com.churchdrive.app.ui.dueChoiceOf
import com.churchdrive.app.ui.parseTaskWords
import com.churchdrive.app.ui.repeatChoiceOf
import com.churchdrive.app.ui.repeatWords
import com.churchdrive.app.ui.timeOf
import com.churchdrive.app.ui.updateFields
import com.churchdrive.app.ui.whoChoiceOf
import com.churchdrive.app.ui.whoWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TodoEditTest {
    private val today = LocalDate.of(2026, 10, 3) // a Saturday

    @Test
    fun anOrdinaryNoteIsAllNotes() {
        assertEquals(TaskWords(null, null, "Buy milk"), parseTaskWords("Buy milk"))
        assertEquals(TaskWords(null, null, ""), parseTaskWords(null))
    }

    @Test
    fun repeatWhoAndNotesComeApart() {
        val w = parseTaskWords("Every 2 weeks: Mon 09:00 · for Hayley · bring bins in")
        assertEquals("Every 2 weeks: Mon 09:00", w.repeat)
        assertEquals("for Hayley", w.who)
        assertEquals("bring bins in", w.notes)
        assertEquals("Every 2 weeks", repeatChoiceOf(w.repeat))
        assertEquals("09:00", timeOf(w.repeat))
        assertEquals("Just me", whoChoiceOf(w.who, "Hayley"))
    }

    @Test
    fun repeatChoicesRoundTrip() {
        for (choice in listOf("Once", "Every day", "Every week", "Every 2 weeks", "Monthly")) {
            val words = repeatWords(choice, "08:30", today)!!
            assertEquals(choice, repeatChoiceOf(words))
        }
        assertEquals("Sat 08:30", repeatWords("Every week", "08:30", today))
        assertEquals("Monthly on the 3rd 08:30", repeatWords("Monthly", "08:30", today))
        assertNull(repeatWords("None", "08:30", today))
    }

    @Test
    fun repeatsTheAppHasNoChoiceForAreKeptAsTheyAre() {
        val raw = "Every 30 days after done 09:00"
        assertEquals(raw, repeatChoiceOf(raw))
        assertEquals(raw, repeatWords(raw, "09:00", today))
    }

    @Test
    fun descriptionKeepsOnlyNotesWithoutARepeat() {
        assertEquals("note", buildDescription(null, "for everyone", " note "))
        assertEquals("Once · no reminders", buildDescription("Once", whoWords("No reminders", "Ian"), ""))
        assertEquals("Mon 09:00 · for Ian · x", buildDescription("Mon 09:00", whoWords("Just me", "Ian"), "x"))
    }

    @Test
    fun dueChoicesAndTimes() {
        assertEquals("No date", dueChoiceOf(null, today))
        assertEquals("Tomorrow", dueChoiceOf("2026-10-04", today))
        assertEquals("Today", dueChoiceOf("2026-10-03T09:00:00+01:00", today))
        assertEquals("Keep", dueChoiceOf("2026-12-25", today))
        assertEquals("09:05", cleanTime("9:05"))
        assertNull(cleanTime("25:00"))
        assertNull(cleanTime("soon"))
    }

    @Test
    fun updateFieldsClearKeepOrSetTheDay() {
        val set = updateFields("u", " Bins ", "Tomorrow", "07:30", today, "d")
        assertEquals("2026-10-04 07:30:00", set["due_datetime"])
        assertEquals("Bins", set["rename"])
        val dateOnly = updateFields("u", "Bins", "Today", null, today, "")
        assertEquals("2026-10-03", dateOnly["due_date"])
        assertNull(dateOnly["description"])
        val clear = updateFields("u", "Bins", "No date", null, today, "d")
        assertEquals(true, clear.containsKey("due_date"))
        assertNull(clear["due_date"])
        val keep = updateFields("u", "Bins", "Keep", null, today, "d")
        assertEquals(false, keep.containsKey("due_date") || keep.containsKey("due_datetime"))
    }
}
