package com.churchdrive.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** A line rendered by Home Assistant with `<b>` in it, as text with the bold parts bold (other tags dropped). */
fun styledLine(html: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    Regex("(<[^>]*>)|([^<]+)").findAll(html).forEach { m ->
        val tag = m.groupValues[1]
        val text = m.groupValues[2]
        when {
            tag.equals("<b>", ignoreCase = true) -> bold = true
            tag.equals("</b>", ignoreCase = true) -> bold = false
            text.isNotEmpty() -> if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } else append(text)
        }
    }
}
