package com.churchdrive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedAppsTest {
    private fun normal(hex: String) = hex.replace(":", "").lowercase()

    @Test fun everyFingerprintIsASha256() {
        // A pasted fingerprint with a letter missing or doubled would quietly trust nothing; catch it here.
        TrustedApps.CERTS.forEach { assertTrue("$it", Regex("^[0-9a-f]{64}$").matches(normal(it))) }
    }

    @Test fun noFingerprintIsListedTwice() {
        assertEquals(TrustedApps.CERTS.size, TrustedApps.CERTS.map(::normal).toSet().size)
    }

    @Test fun everyChurchDriveAppIsTrusted() {
        assertEquals(
            setOf("com.churchdrive.manager", "com.churchdrive.todo", "com.churchdrive.satellites", "com.churchdrive.admin"),
            TrustedApps.PACKAGES,
        )
        // One Play signing certificate each (the phone and watch apps share a listing).
        assertEquals(4, TrustedApps.CERTS.size)
    }
}
