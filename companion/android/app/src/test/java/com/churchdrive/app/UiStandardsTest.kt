package com.churchdrive.app

import com.churchdrive.app.ui.MdiIcons
import com.churchdrive.app.ui.tileRowSizes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's look-and-feel rules (`companion/android/UI-STANDARDS.md`) as checks. They fail the build when a change
 * breaks one, so the rules hold without anyone having to remember them. When one fails, fix the change; only change a
 * check deliberately, and say why in the commit.
 */
class UiStandardsTest {
    private val ui = File("src/main/java/com/churchdrive/app/ui")
    private val sources = File("src/main/java/com/churchdrive/app")

    private fun uiFiles() = ui.listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }
    private fun allFiles() = sources.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.name }.toList()
    private fun count(source: String, pattern: String) = Regex("(?<![A-Za-z])" + Regex.escape(pattern)).findAll(source).count()

    // ---- Tiles fill their rows

    @Test
    fun tileRowsShareTheWidthEvenlyAndNeverLeaveAGap() {
        for (perRow in 3..5) {
            for (count in 1..14) {
                val sizes = tileRowSizes(count, perRow)
                assertEquals("$count tiles, $perRow per row", count, sizes.sum())
                assertTrue("$sizes", sizes.all { it in 1..perRow })
                assertTrue("rows differ by more than one: $sizes", sizes.max() - sizes.min() <= 1)
                assertTrue("the last row is the longest: $sizes", sizes.last() <= sizes.first())
            }
        }
        assertEquals(listOf(3, 2), tileRowSizes(5, 4))
        assertEquals(listOf(3, 3), tileRowSizes(6, 4))
        assertEquals(listOf(4, 3), tileRowSizes(7, 4))
        assertEquals(listOf(4), tileRowSizes(4, 4))
        assertEquals(listOf(3, 3, 3), tileRowSizes(9, 4))
        assertEquals(emptyList<Int>(), tileRowSizes(0, 4))
    }

    // ---- Sizes and text come from the kit

    @Test
    fun tileAndButtonHeightsComeFromTheKit() {
        // 48, 56 and 64 dp are the kit's tile heights (Ui.TileHeight, Ui.TallTileHeight): never typed in a card.
        val literal = Regex("""\.height\(\s*(48|56|64)(\.0)?\.dp\s*\)""")
        for (file in uiFiles()) {
            if (file.name == "UiKit.kt") continue
            assertEquals("${file.name} writes a tile height; use Ui.TileHeight or Ui.TallTileHeight", 0, literal.findAll(file.readText()).count())
        }
    }

    @Test
    fun labelsOnTilesAreCentredText() {
        // A bare Text on a tile sits high (the line leaves space under the letters). CentredText trims it.
        assertEquals("ModeIcons.kt tiles must use CentredText, not Text", 0, count(File(ui, "ModeIcons.kt").readText(), "Text("))
        val cards = File(ui, "Cards.kt").readText()
        val tile = cards.substringAfter("private fun ModeTile(", "")
        assertTrue("the alarm's mode tiles must use CentredText", tile.contains("CentredText("))
        assertTrue("scene tiles must use CentredText", File(ui, "Lights.kt").readText().contains("CentredText(scene.name"))
    }

    @Test
    fun choiceButtonsComeFromTheKitNotRawButtons() {
        // Groups of choices are TileRow, IconRow or OptionRow. A raw Material button in a card is how sizes drift.
        val allowed = mapOf(
            "Cards.kt" to mapOf("Button(" to 1, "OutlinedButton(" to 1, "FilledIconButton(" to 1, "ChoiceButton(" to 1),
            "ClimateCards.kt" to mapOf("ChoiceButton(" to 1),
            "LoginScreen.kt" to mapOf("Button(" to 1),
            "TodoCards.kt" to mapOf("FilledIconButton(" to 1),
        )
        val names = listOf("Button(", "OutlinedButton(", "FilledTonalButton(", "TextButton(", "FilledIconButton(", "ChoiceButton(")
        for (file in uiFiles()) {
            val text = file.readText()
            for (name in names) {
                val limit = allowed[file.name]?.get(name) ?: 0
                assertTrue(
                    "${file.name} uses $name (${count(text, name)}, allowed $limit): use TileRow, IconRow or OptionRow from the kit",
                    count(text, name) <= limit,
                )
            }
        }
    }

    // ---- Icons are real

    private fun realIcons() = JSONObject(File("src/main/assets/mdi-icons.json").readText())

    @Test
    fun everyIconNamedInTheCodeIsARealMaterialDesignIcon() {
        // An unknown icon name draws the (i) fallback. Home Assistant's icons are checked by the same set.
        val real = realIcons()
        val bad = mutableListOf<String>()
        for (file in allFiles()) {
            for (m in Regex(""""mdi:([a-z0-9-]+)"""").findAll(file.readText())) {
                if (!real.has(m.groupValues[1])) bad += "${file.name}: mdi:${m.groupValues[1]}"
            }
        }
        assertEquals("not Material Design icons: $bad", emptyList<String>(), bad)
    }

    @Test
    fun theBuiltInIconsAreAllInTheFullSetToo() {
        val real = realIcons()
        val bad = MdiIcons.paths.keys.filter { !real.has(it) }
        assertEquals("built-in icons not in the full set: $bad", emptyList<String>(), bad)
        assertTrue("the full set looks incomplete", real.length() > 7000)
    }
}
