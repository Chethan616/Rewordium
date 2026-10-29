package com.noxquill.rewordium.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Just enough Markdown for chat replies: headings, bullet and numbered lists,
 * **bold**, *italic* and `code`. Anything else shows as written.
 */
@Composable
fun rememberMarkdown(text: String, codeBackground: Color): AnnotatedString = remember(text, codeBackground) {
    buildAnnotatedString {
        val lines = text.trim().lines()
        lines.forEachIndexed { index, rawLine ->
            var line = rawLine.trimEnd()
            val heading = Regex("^#{1,6}\\s+").find(line)
            val bullet = Regex("^\\s*[-*•]\\s+").find(line)
            val numbered = Regex("^\\s*(\\d+)[.)]\\s+").find(line)
            when {
                heading != null -> {
                    line = line.removeRange(heading.range)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { inline(line, codeBackground) }
                }
                bullet != null -> {
                    val indent = if (bullet.value.startsWith("  ")) "    " else ""
                    append("$indent•  ")
                    inline(line.removeRange(bullet.range), codeBackground)
                }
                numbered != null -> {
                    append("${numbered.groupValues[1]}.  ")
                    inline(line.removeRange(numbered.range), codeBackground)
                }
                line.trim() == "---" -> append("")
                else -> inline(line, codeBackground)
            }
            if (index < lines.lastIndex) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.inline(text: String, codeBackground: Color) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(text.substring(i + 2, end), codeBackground) }
                    i = end + 2
                } else { append(marker); i += 2 }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append('`'); i++ }
            }
            (text[i] == '*' || text[i] == '_') && i + 1 < text.length && text[i + 1] != ' ' -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                val wordBoundaryOk = marker == '*' || i == 0 || !text[i - 1].isLetterOrDigit()
                if (end > i + 1 && wordBoundaryOk) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(marker); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
