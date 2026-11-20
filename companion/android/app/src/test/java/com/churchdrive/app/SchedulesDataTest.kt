package com.churchdrive.app

import com.churchdrive.app.ui.ScheduleGroup
import com.churchdrive.app.ui.groupSchedules
import com.churchdrive.app.ui.parseSchedules
import com.churchdrive.app.ui.scheduleToggle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SchedulesDataTest {
    private val sample = JSONObject("""{"schedules":[
        {"source":"device","id":"cover.b","title":"Hayley's blind","lines":["Mon-Fri Open 07:30 · Close 20:00"],"enabled":true,"next":"2026-11-21T07:30:00+00:00","next_text":"Open Sat 07:30","entity":"cover.b","by":"","done":false},
        {"source":"clive","id":"r1","title":"Open the bedroom blind","lines":["Tomorrow at 08:15"],"enabled":true,"next":"2026-11-21T08:15:00+00:00","entity":"cover.b","by":"Jamie","done":false},
        {"source":"clive","id":"r2","title":"Landing light off","lines":["Every day at 23:00"],"enabled":false,"next":null,"entity":"light.l","by":"","done":false},
        {"source":"clive","id":"r3","title":"Old one","lines":["Today at 06:00"],"enabled":true,"next":null,"entity":"light.l","by":"","done":true}
    ]}""")

    @Test fun readsTheList() {
        val items = parseSchedules(sample)!!
        assertEquals(4, items.size)
        assertEquals("Open Sat 07:30", items[0].nextText)
        assertNull(items[1].nextText)
        assertNull(items[2].next)
    }

    @Test fun unreadableIsNull() {
        assertNull(parseSchedules(null))
        assertNull(parseSchedules(JSONObject("{}")))
    }

    @Test fun groupsComingUpOffAndDone() {
        val groups = groupSchedules(parseSchedules(sample)!!)
        assertEquals(listOf(ScheduleGroup.Coming, ScheduleGroup.Off, ScheduleGroup.Done), groups.map { it.first })
        // Soonest first: the blind at 07:30 before Clive's at 08:15.
        assertEquals(listOf("cover.b", "r1"), groups[0].second.map { it.id })
    }

    @Test fun togglesGoToTheRightPlace() {
        val items = parseSchedules(sample)!!
        assertEquals("church_drive/blind_schedule/set", scheduleToggle(items[0], false)!!.first)
        assertEquals("cover.b", scheduleToggle(items[0], false)!!.second.getString("entity"))
        assertEquals("church_drive/routines/set", scheduleToggle(items[1], true)!!.first)
        assertEquals("r1", scheduleToggle(items[1], true)!!.second.getString("id"))
        assertNotNull(scheduleToggle(items[2], true))
    }
}
