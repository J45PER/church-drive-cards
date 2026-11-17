package com.churchdrive.app

import com.churchdrive.app.ui.BlindSchedule
import com.churchdrive.app.ui.PurifierSchedule
import com.churchdrive.app.ui.SCHEDULE_DAY_LETTERS
import com.churchdrive.app.ui.SCHEDULE_DAY_PRESETS
import com.churchdrive.app.ui.SlotSchedule
import com.churchdrive.app.ui.slotTimesText
import com.churchdrive.app.ui.slotToggleDay
import com.churchdrive.app.ui.toBlind
import com.churchdrive.app.ui.toPurifier
import com.churchdrive.app.ui.toSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleEditDataTest {
    @Test fun aBlindScheduleSurvivesTheEditor() {
        val b = BlindSchedule("s1", listOf(0, 1, 2, 3, 4), null, "20:00")
        assertEquals(b, b.toSlot().toBlind())
    }

    @Test fun aPurifierScheduleSurvivesTheEditor() {
        val p = PurifierSchedule("s2", listOf(5, 6), "08:00", "23:00")
        assertEquals(p, p.toSlot().toPurifier())
    }

    @Test fun aPurifierTimeIsNeverLeftOut() {
        val p = SlotSchedule("s1", listOf(0), null, null).toPurifier()
        assertEquals("07:00", p.auto)
        assertEquals("22:30", p.sleep)
    }

    @Test fun daysToggleAndStayInOrder() {
        val s = SlotSchedule("s1", listOf(0, 4), "07:30", null)
        assertEquals(listOf(0, 2, 4), slotToggleDay(s, 2).days)
        assertEquals(listOf(4), slotToggleDay(s, 0).days)
    }

    @Test fun theTimesLeaveOutOneThatIsNotSet() {
        assertEquals("Open 07:30 · Close 20:00", slotTimesText(SlotSchedule("s", listOf(0), "07:30", "20:00"), "Open", "Close"))
        assertEquals("Close 20:00", slotTimesText(SlotSchedule("s", listOf(0), null, "20:00"), "Open", "Close"))
        assertEquals("No times set", slotTimesText(SlotSchedule("s", listOf(0), null, null), "Open", "Close"))
    }

    @Test fun theQuickPicksAndLettersCoverTheWeek() {
        assertEquals(7, SCHEDULE_DAY_LETTERS.size)
        assertEquals(listOf(0, 1, 2, 3, 4), SCHEDULE_DAY_PRESETS.first { it.first == "Weekdays" }.second)
        assertEquals((0..6).toList(), SCHEDULE_DAY_PRESETS.first { it.first == "Every day" }.second)
    }
}
