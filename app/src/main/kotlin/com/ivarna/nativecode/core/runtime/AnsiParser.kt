package com.ivarna.nativecode.core.runtime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val ansiStandard = listOf(
    Color(0xFF000000), // 0 Black
    Color(0xFFCC0000), // 1 Red
    Color(0xFF00CC00), // 2 Green
    Color(0xFFCCCC00), // 3 Yellow
    Color(0xFF5577FF), // 4 Blue
    Color(0xFFCC00CC), // 5 Magenta
    Color(0xFF00CCCC), // 6 Cyan
    Color(0xFFCCCCCC), // 7 White
)

private val ansiBright = listOf(
    Color(0xFF666666), // 8  Bright Black
    Color(0xFFFF4444), // 9  Bright Red
    Color(0xFF44FF44), // 10 Bright Green
    Color(0xFFFFFF44), // 11 Bright Yellow
    Color(0xFF6CB6FF), // 12 Bright Blue
    Color(0xFFFF44FF), // 13 Bright Magenta
    Color(0xFF44FFFF), // 14 Bright Cyan
    Color(0xFFFFFFFF), // 15 Bright White
)

private fun ansi256(index: Int): Color? = when {
    index in 0..7 -> ansiStandard[index]
    index in 8..15 -> ansiBright[index - 8]
    index in 16..231 -> {
        val a = index - 16
        Color((a / 36) * 51, ((a / 6) % 6) * 51, (a % 6) * 51)
    }
    index in 232..255 -> {
        val g = (index - 232) * 10 + 8
        Color(g, g, g)
    }
    else -> null
}

private data class SgrState(
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

private fun SgrState.toSpanStyle(default: Color) = SpanStyle(
    color = fg ?: default,
    background = bg ?: Color.Unspecified,
    fontWeight = when {
        bold -> FontWeight.Bold
        dim -> FontWeight.Light
        else -> null
    },
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
)

private fun applySgr(state: SgrState, params: String): SgrState {
    if (params.isEmpty() || params == "0") return SgrState()
    val codes = params.split(';').mapNotNull { it.toIntOrNull() }
    var s = state
    var i = 0
    while (i < codes.size) {
        when (val c = codes[i]) {
            0 -> s = SgrState()
            1 -> s = s.copy(bold = true, dim = false)
            2 -> s = s.copy(dim = true, bold = false)
            3 -> s = s.copy(italic = true)
            4 -> s = s.copy(underline = true)
            22 -> s = s.copy(bold = false, dim = false)
            23 -> s = s.copy(italic = false)
            24 -> s = s.copy(underline = false)
            39 -> s = s.copy(fg = null)
            49 -> s = s.copy(bg = null)
            in 30..37 -> s = s.copy(fg = ansiStandard[c - 30])
            in 40..47 -> s = s.copy(bg = ansiStandard[c - 40])
            in 90..97 -> s = s.copy(fg = ansiBright[c - 90])
            in 100..107 -> s = s.copy(bg = ansiBright[c - 100])
            38 -> {
                if (i + 2 < codes.size && codes[i + 1] == 5) {
                    s = s.copy(fg = ansi256(codes[i + 2])); i += 2
                }
            }
            48 -> {
                if (i + 2 < codes.size && codes[i + 1] == 5) {
                    s = s.copy(bg = ansi256(codes[i + 2])); i += 2
                }
            }
        }
        i++
    }
    return s
}

/** Parse a chunk of terminal output (may contain partial ANSI escape sequences). */
fun parseAnsi(text: String, default: Color): AnnotatedString {
    if (!text.contains('\u001B')) return AnnotatedString(text, SpanStyle(color = default))
    val b = AnnotatedString.Builder()
    var state = SgrState()
    val buf = StringBuilder()
    var i = 0
    val len = text.length
    fun flush() {
        if (buf.isNotEmpty()) {
            b.withStyle(state.toSpanStyle(default)) { append(buf) }
            buf.clear()
        }
    }
    while (i < len) {
        if (text[i] == '\u001B' && i + 1 < len) {
            when (text[i + 1]) {
                '[' -> {
                    flush(); i += 2
                    val start = i
                    while (i < len && text[i] in '\u0020'..'\u003F') i++
                    val params = if (i > start) text.substring(start, i) else ""
                    if (i < len && text[i] in '\u0040'..'\u007E') {
                        val fin = text[i]; i++
                        if (fin == 'm') state = applySgr(state, params)
                    }
                }
                ']' -> {
                    flush(); i += 2
                    while (i < len) {
                        if (text[i] == '\u0007') { i++; break }
                        if (text[i] == '\u001B' && i + 1 < len && text[i + 1] == '\\') { i += 2; break }
                        i++
                    }
                }
                else -> i += 2
            }
        } else {
            buf.append(text[i]); i++
        }
    }
    flush()
    return b.toAnnotatedString()
}
