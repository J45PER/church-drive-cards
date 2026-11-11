package com.churchdrive.admin

import com.churchdrive.app.ui.CliveColours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteNameTest {
    @Test fun keysBecomeWords() {
        assertEquals("Lights", paletteName("lights"))
        assertEquals("Alarm home", paletteName("alarm_home"))
        assertEquals("Person jamie", paletteName("person_jamie"))
    }

    @Test fun everyPaletteHasAName() {
        assertTrue(CliveColours.byKey.isNotEmpty())
        CliveColours.byKey.keys.forEach { assertTrue(paletteName(it).isNotBlank()) }
    }
}
