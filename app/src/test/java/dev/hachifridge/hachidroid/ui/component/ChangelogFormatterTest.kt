package dev.hachifridge.hachidroid.ui.component

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogFormatterTest {

    @Test
    fun format_emptyOrBlankReturnsEmpty() {
        assertEquals("", ChangelogFormatter.format("").text)
        assertEquals("", ChangelogFormatter.format("   \n\n  ").text)
    }

    @Test
    fun format_stripsTopLevelWhatsChangedHeader() {
        val markdown = """
            ## What's Changed
            * **feat: new feature**
            (by @tenshou in abc1234)
        """.trimIndent()

        val result = ChangelogFormatter.format(markdown)
        assertTrue(!result.text.contains("What's Changed"))
        assertTrue(result.text.startsWith("• feat: new feature"))
    }

    @Test
    fun format_normalizesBulletsAndSubBullets() {
        val markdown = """
            ## What's Changed
            * **Main commit**
                * sub detail
                (by @user in 1234567)
            - Another commit
        """.trimIndent()

        val result = ChangelogFormatter.format(markdown)
        val lines = result.text.lines()
        assertEquals(4, lines.size)
        assertEquals("• Main commit", lines[0])
        assertEquals("    ◦ sub detail", lines[1])
        assertEquals("    (by @user in 1234567)", lines[2])
        assertEquals("• Another commit", lines[3])
    }

    @Test
    fun format_stylesBoldAndCode() {
        val markdown = "* **bold title**: uses `MyClass` now"
        val result = ChangelogFormatter.format(markdown)

        assertEquals("• bold title: uses MyClass now", result.text)

        val boldStyles = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldStyles.size)
        assertEquals(2, boldStyles[0].start)
        assertEquals(12, boldStyles[0].end) // "bold title"

        val codeStyles = result.spanStyles.filter { it.item.fontFamily == FontFamily.Monospace }
        assertEquals(1, codeStyles.size)
        assertEquals(19, codeStyles[0].start)
        assertEquals(26, codeStyles[0].end) // "MyClass"
    }

    @Test
    fun format_convertsSubheadingsToBold() {
        val markdown = """
            ### Bug Fixes
            * Fix memory leak
        """.trimIndent()

        val result = ChangelogFormatter.format(markdown)
        val lines = result.text.lines()
        assertEquals("Bug Fixes", lines[0])
        assertEquals("• Fix memory leak", lines[1])

        val headingStyle = result.spanStyles.first { it.start == 0 && it.end == "Bug Fixes".length }
        assertEquals(FontWeight.Bold, headingStyle.item.fontWeight)
    }
}
