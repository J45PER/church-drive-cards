package com.churchdrive.app

import com.churchdrive.app.ui.DeviceHealthItem
import com.churchdrive.app.ui.deviceForHouseTask
import com.churchdrive.app.ui.houseTaskHaPath
import com.churchdrive.app.ui.houseTaskPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceHealthTest {
    private fun device(id: String, name: String) = DeviceHealthItem(id, name, null, emptyList(), 17, false, null, false, null, null)

    @Test
    fun aHouseJobFindsTheDeviceNamedInItsWords() {
        val items = listOf(device("a", "Ring Front Doorbell"), device("b", "Front Door"), device("c", "Hall sensor"))
        // The longest name that fits wins, so "Front Door" doesn't steal the doorbell's job.
        assertEquals("a", deviceForHouseTask("Replace battery: Ring Front Doorbell", items)?.deviceId)
        assertEquals("c", deviceForHouseTask("Replace battery: hall SENSOR", items)?.deviceId)
        assertNull(deviceForHouseTask("Clean the air purifier pre-filter", items))
        assertNull(deviceForHouseTask("anything", emptyList()))
    }

    @Test
    fun aHouseJobAboutAPlaceOpensThatPage() {
        assertEquals("Cleaning", houseTaskPage("Vacuum message"))
        assertEquals("Security", houseTaskPage("Safety alarm"))
        assertNull(houseTaskPage("Low batteries"))
        assertNull(houseTaskPage("Filters due"))
        assertNull(houseTaskPage(""))
    }

    @Test
    fun aHouseUpdateOpensHomeAssistantsUpdatesPage() {
        assertEquals("/config/updates", houseTaskHaPath("Updates"))
        assertEquals("/config/updates", houseTaskHaPath("Update available"))
        assertNull(houseTaskHaPath("Low batteries"))
        assertNull(houseTaskHaPath("Vacuum message"))
    }
}
