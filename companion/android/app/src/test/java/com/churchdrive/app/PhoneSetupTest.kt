package com.churchdrive.app

import com.churchdrive.app.house.PhoneSetup
import com.churchdrive.app.house.SetupState
import com.churchdrive.app.house.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSetupTest {
    private val allGood = SetupState(
        notifyOn = true, canNotify = true, locationOn = true, canLocate = true, precise = true, always = true, batteryFree = true,
    )

    @Test fun aPhoneSetUpProperlyHasNothingMissing() {
        assertTrue(PhoneSetup.missing(allGood).isEmpty())
    }

    @Test fun eachSettingIsCheckedOnItsOwn() {
        assertEquals(listOf(SetupStep.Notifications), PhoneSetup.missing(allGood.copy(canNotify = false)))
        assertEquals(listOf(SetupStep.Notifications), PhoneSetup.missing(allGood.copy(notifyOn = false)))
        assertEquals(listOf(SetupStep.Location), PhoneSetup.missing(allGood.copy(locationOn = false)))
        assertEquals(listOf(SetupStep.PreciseLocation), PhoneSetup.missing(allGood.copy(precise = false)))
        assertEquals(listOf(SetupStep.AlwaysLocation), PhoneSetup.missing(allGood.copy(always = false)))
        assertEquals(listOf(SetupStep.Battery), PhoneSetup.missing(allGood.copy(batteryFree = false)))
    }

    @Test fun approximateLocationIsNotEnough() {
        // Android 12 lets the person give "approximate": it counts as location, but not as precise.
        val approximate = allGood.copy(precise = false, always = false)
        assertEquals(listOf(SetupStep.PreciseLocation, SetupStep.AlwaysLocation), PhoneSetup.missing(approximate))
    }

    @Test fun aPhoneWithNothingAllowedIsAskedInOrderLocationBeforeItsRefinements() {
        val none = SetupState(false, false, false, false, false, false, false)
        assertEquals(
            listOf(SetupStep.Notifications, SetupStep.Location, SetupStep.PreciseLocation, SetupStep.AlwaysLocation, SetupStep.Battery),
            PhoneSetup.missing(none),
        )
    }

    @Test fun theQuestionOpensOnceForEachVersionThatFindsSomethingMissing() {
        val missing = listOf(SetupStep.Battery)
        assertTrue(PhoneSetup.dialogDue(null, "0.99.0", missing))
        assertTrue(PhoneSetup.dialogDue("0.98.0", "0.99.0", missing))
        assertFalse(PhoneSetup.dialogDue("0.99.0", "0.99.0", missing))
        assertFalse(PhoneSetup.dialogDue(null, "0.99.0", emptyList()))
    }

    @Test fun theUpdateQuestionOpensAtMostOnceADay() {
        val day = PhoneSetup.DAY_MS
        assertTrue(PhoneSetup.updateDialogDue(0L, 1_000L))
        assertFalse(PhoneSetup.updateDialogDue(1_000L, 1_000L + day - 1))
        assertTrue(PhoneSetup.updateDialogDue(1_000L, 1_000L + day))
    }

    @Test fun aRefusedStepGoesToTheAppsSettingsTheSecondTime() {
        assertFalse(PhoneSetup.needsSettings(0))
        assertTrue(PhoneSetup.needsSettings(1))
        assertEquals("Open settings", PhoneSetup.button(SetupStep.PreciseLocation, true))
        assertEquals("Allow precise", PhoneSetup.button(SetupStep.PreciseLocation, false))
    }

    @Test fun everyStepHasWordsAndAUniqueKey() {
        for (step in SetupStep.entries) {
            assertTrue(PhoneSetup.title(step).isNotBlank())
            assertTrue(PhoneSetup.why(step).isNotBlank())
        }
        assertEquals(SetupStep.entries.size, SetupStep.entries.map { it.key }.toSet().size)
    }

    @Test fun theBannerSaysHowManyNeedFixing() {
        assertEquals("This phone is set up", PhoneSetup.summary(emptyList()))
        assertTrue(PhoneSetup.summary(listOf(SetupStep.Battery)).contains("battery"))
        assertTrue(PhoneSetup.summary(listOf(SetupStep.Battery, SetupStep.Location)).startsWith("2 settings"))
    }
}
