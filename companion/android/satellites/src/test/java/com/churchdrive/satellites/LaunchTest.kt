package com.churchdrive.satellites

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Starts the real MainActivity the way the phone does (its manifest, its content providers, its first screen). A crash on launch fails here with its stack trace. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test fun theActivityStarts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        assertNotNull(controller.get())
    }
}
