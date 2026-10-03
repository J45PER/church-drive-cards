package com.churchdrive.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class VersionTest {
    private fun version(): String = Properties().apply { File("../version.properties").inputStream().use { load(it) } }.getProperty("version")

    @Test
    fun theVersionIsThreeNumbers() {
        assertTrue(version(), Regex("""\d+\.\d+\.\d+""").matches(version()))
    }

    @Test
    fun theChangelogHasAHeadingForTheVersion() {
        val changelog = File("../CHANGELOG.md").readText()
        assertTrue("CHANGELOG.md needs a '## ${version()}' heading", Regex("(?m)^## ${Regex.escape(version())}( |$)").containsMatchIn(changelog))
    }
}
