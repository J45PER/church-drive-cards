package com.churchdrive.app

import com.churchdrive.app.ui.alarmTestSetBody
import com.churchdrive.app.ui.ordinalDay
import com.churchdrive.app.ui.parseAlarmTests
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTestDataTest {
    @Test fun readsTheThreeTests() {
        val tests = parseAlarmTests(JSONObject("""{"tests":{
            "alarm":{"enabled":false,"day":1,"time":"11:00","name":"House alarm","summary":"Off","next":null,"targets":["Ring Chime"],"last":null},
            "co":{"enabled":true,"day":15,"time":"10:30","name":"CO alarm","summary":"The 15th of each month at 10:30","next":{"text":"Mon 15 Dec 10:30"},"targets":["Carbon Monoxide Alarm"],"last":{"at":"2026-11-15T10:30:00+00:00","tested":1,"of":1}},
            "smoke":{"enabled":true,"day":1,"time":"11:00","name":"Smoke and heat alarms","summary":"x","targets":["A","B"],"last":{"tested":1,"of":2}}
        }}"""))!!
        assertEquals(3, tests.size)
        assertEquals("Mon 15 Dec 10:30", tests["co"]!!.next)
        assertEquals(15, tests["co"]!!.day)
        assertNull(tests["alarm"]!!.next)
        assertEquals(listOf("A", "B"), tests["smoke"]!!.targets)
        assertEquals("Last test: 1 of 2 answered", tests["smoke"]!!.lastText)
        assertNull(tests["alarm"]!!.lastText)
    }

    @Test fun anOlderHouseHasNone() {
        assertNull(parseAlarmTests(JSONObject("{}")))
        assertNull(parseAlarmTests(null))
    }

    @Test fun ordinalsAndTheBody() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "31st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 31).map { ordinalDay(it) })
        val body = alarmTestSetBody("co", true, 40, "10:30")
        assertEquals(31, body.getInt("day"))
        assertEquals("co", body.getString("kind"))
    }
}
