/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import org.json.JSONObject

/** Only standard mIRC color indexes and IRC style controls are emitted. */
data class NickCompletionStyle(
    val enabled: Boolean = true,
    val startTemplate: String = "{nick}: ",
    val middleTemplate: String = "{nick} ",
    val colors: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    fun palette(): List<Int> = colors.split(',', ';', ' ', '\n')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 0..98 } }.take(16)

    internal fun render(nick: String, atStart: Boolean, hadAt: Boolean, followedBySpace: Boolean): String {
        val palette = palette()
        val styled = buildString {
            if (bold) append('\u0002')
            if (italic) append('\u001d')
            if (underline) append('\u001f')
            nick.codePoints().toArray().forEachIndexed { index, point ->
                if (palette.isNotEmpty()) append('\u0003').append(palette[index % palette.size].toString().padStart(2, '0'))
                appendCodePoint(point)
            }
            if (palette.isNotEmpty()) append("\u0003\u0002\u0002")
            if (underline) append('\u001f')
            if (italic) append('\u001d')
            if (bold) append('\u0002')
        }
        var template = cleanCompletionTemplate(if (atStart) startTemplate else middleTemplate)
        if (!template.contains("{nick}")) template += "{nick}"
        if (followedBySpace) template = template.trimEnd()
        return template.replace("{nick}", (if (hadAt) "@" else "") + styled)
    }

    fun toJson(): JSONObject = JSONObject().put("paletteVersion", 2)
        .put("enabled", enabled).put("startTemplate", cleanCompletionTemplate(startTemplate))
        .put("middleTemplate", cleanCompletionTemplate(middleTemplate))
        .put("colors", palette().joinToString(","))
        .put("bold", bold).put("italic", italic).put("underline", underline)

    companion object {
        fun fromJson(o: JSONObject?): NickCompletionStyle {
            if (o == null) return NickCompletionStyle()
            val raw = o.optString("colors", "").take(256)
            return NickCompletionStyle(
                o.optBoolean("enabled", true), cleanCompletionTemplate(o.optString("startTemplate", "{nick}: ")),
                cleanCompletionTemplate(o.optString("middleTemplate", "{nick} ")),
                if (o.optInt("paletteVersion", 1) < 2) migrateCompletionPalette(raw) else raw,
                o.optBoolean("bold", false), o.optBoolean("italic", false), o.optBoolean("underline", false),
            )
        }
    }
}

internal fun cleanCompletionTemplate(raw: String): String =
    raw.replace(Regex("\u0004[0-9a-fA-F]{0,6}(?:,[0-9a-fA-F]{0,6})?"), "")
        .filter { !it.isISOControl() || it in "\u0002\u0003\u000f\u001d\u001f" }.take(320)

/** Convert the previous HEX preference once; new editors accept IRC indexes only. */
internal fun migrateCompletionPalette(raw: String): String {
    val classic = intArrayOf(0xffffff,0x000000,0x00007f,0x009300,0xff0000,0x7f0000,0x9c009c,0xfc7f00,
        0xffff00,0x00fc00,0x009393,0x00ffff,0x0000fc,0xff00ff,0x7f7f7f,0xd2d2d2)
    return raw.split(',', ';', ' ', '\n').mapNotNull { token ->
        val value = token.trim().removePrefix("#")
        value.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }
            ?.toInt(16)?.let { rgb ->
                classic.indices.minByOrNull { index ->
                    val target = classic[index]
                    listOf(0,8,16).sumOf { shift ->
                        val delta = ((rgb shr shift) and 255) - ((target shr shift) and 255); delta * delta
                    }
                }
            } ?: value.toIntOrNull()?.takeIf { it in 0..98 }
    }.take(16).joinToString(",")
}
