/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

/** Restore the actual surrounding IRC style, including locally coloured text before the nick. */
internal fun completionWritingState(text: String, initial: WritingStyle): WritingStyle {
    var style = initial
    var i = 0
    while (i < text.length) {
        when (text[i++]) {
            '\u000f' -> style = WritingStyle()
            '\u0002' -> style = style.copy(bold = !style.bold)
            '\u001d' -> style = style.copy(italic = !style.italic)
            '\u001f' -> style = style.copy(underline = !style.underline)
            '\u0016' -> style = style.copy(reverse = !style.reverse)
            '\u0003' -> {
                val start = i
                while (i < text.length && i - start < 2 && text[i] in '0'..'9') i++
                if (i == start) style = style.copy(foreground = null, background = null)
                else {
                    val fg = text.substring(start,i).toInt().takeIf { it in 0..98 }
                    var bg = style.background
                    if (text.getOrNull(i) == ',' && text.getOrNull(i+1)?.let { it in '0'..'9' } == true) {
                        val begin = ++i
                        while (i < text.length && i - begin < 2 && text[i] in '0'..'9') i++
                        bg = text.substring(begin,i).toInt().takeIf { it in 0..98 }
                    }
                    style = style.copy(foreground = fg, background = bg)
                }
            }
        }
    }
    return style
}
