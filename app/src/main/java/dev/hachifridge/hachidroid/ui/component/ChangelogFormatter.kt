package dev.hachifridge.hachidroid.ui.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Parses release body markdown into a styled [AnnotatedString] suitable for
 * display in the built-in app updater dialog.
 */
object ChangelogFormatter {
    private val TOP_HEADER_REGEX = Regex(
        """^#{1,3}\s*(what'?s changed|changelog|release notes)\s*.*$""",
        RegexOption.IGNORE_CASE
    )
    private val SECTION_HEADER_REGEX = Regex("""^#{1,4}\s+(.+)$""")
    private val BULLET_REGEX = Regex("""^([ \t]*)[*\-+]([ \t]+)(.*)$""")
    private val INLINE_FORMAT_REGEX = Regex("""(\*\*([^*]+)\*\*|`([^`]+)`)""")

    fun format(raw: String): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("")

        val lines = raw.lines()
        val processedLines = mutableListOf<String>()
        var skipTopHeader = true

        for (line in lines) {
            val trimmed = line.trim()
            if (skipTopHeader) {
                if (TOP_HEADER_REGEX.matches(trimmed)) {
                    continue
                }
                if (trimmed.isEmpty()) {
                    continue
                }
                skipTopHeader = false
            }

            // Collapse consecutive empty lines
            if (trimmed.isEmpty()) {
                if (processedLines.isNotEmpty() && processedLines.last().isNotEmpty()) {
                    processedLines.add("")
                }
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(line)
            val normalizedLine = when {
                bulletMatch != null -> {
                    val indent = bulletMatch.groupValues[1]
                    val content = bulletMatch.groupValues[3]
                    if (indent.length >= 2) "    ◦ $content" else "• $content"
                }
                else -> line
            }
            processedLines.add(normalizedLine)
        }

        // Trim trailing empty lines
        while (processedLines.isNotEmpty() && processedLines.last().isBlank()) {
            processedLines.removeAt(processedLines.size - 1)
        }

        return buildAnnotatedString {
            for ((index, line) in processedLines.withIndex()) {
                appendFormattedLine(line)
                if (index < processedLines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendFormattedLine(line: String) {
        val headingMatch = SECTION_HEADER_REGEX.matchEntire(line.trim())
        if (headingMatch != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(headingMatch.groupValues[1])
            }
            return
        }

        var cursor = 0
        val matches = INLINE_FORMAT_REGEX.findAll(line)
        for (match in matches) {
            if (match.range.first > cursor) {
                append(line.substring(cursor, match.range.first))
            }
            val boldText = match.groups[2]?.value
            val codeText = match.groups[3]?.value
            when {
                boldText != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }
                codeText != null -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(codeText)
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < line.length) {
            append(line.substring(cursor))
        }
    }
}
