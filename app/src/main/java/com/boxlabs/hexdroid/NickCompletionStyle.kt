/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import org.json.JSONObject

/** Formatting applies only to the inserted nick, never to the rest of the message. */
data class NickCompletionStyle(
    val enabled: Boolean = true,
    val startTemplate: String = "{nick}: ",
    val middleTemplate: String = "{nick} ",
    val colors: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    fun palette(): List<String> = colors.split(',', ';', ' ', '\n')
        .map { it.trim().removePrefix("#").uppercase(java.util.Locale.ROOT) }
        .filter { it.matches(Regex("[0-9A-F]{6}")) }.take(16)

    internal fun render(nick: String, atStart: Boolean, hadAt: Boolean, followedBySpace: Boolean): String {
        val palette = palette()
        val styled = buildString {
            if (bold) append('\u0002')
            if (italic) append('\u001d')
            if (underline) append('\u001f')
            val points = nick.codePoints().toArray()
            points.forEachIndexed { index, point ->
                if (palette.isNotEmpty()) append('\u0004').append(palette[index % palette.size])
                appendCodePoint(point)
            }
            if (palette.isNotEmpty()) append("\u0003\u0002\u0002")
            if (underline) append('\u001f')
            if (italic) append('\u001d')
            if (bold) append('\u0002')
        }
        val raw = if (atStart) startTemplate else middleTemplate
        var template = raw.filterNot { it.isISOControl() }.take(160)
        if (!template.contains("{nick}")) template += "{nick}"
        if (followedBySpace) template = template.trimEnd()
        return template.replace("{nick}", (if (hadAt) "@" else "") + styled)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled).put("startTemplate", startTemplate.take(160))
        .put("middleTemplate", middleTemplate.take(160)).put("colors", colors.take(256))
        .put("bold", bold).put("italic", italic).put("underline", underline)

    companion object {
        fun fromJson(o: JSONObject?): NickCompletionStyle = if (o == null) NickCompletionStyle() else NickCompletionStyle(
            o.optBoolean("enabled", true), o.optString("startTemplate", "{nick}: ").take(160),
            o.optString("middleTemplate", "{nick} ").take(160), o.optString("colors", "").take(256),
            o.optBoolean("bold", false), o.optBoolean("italic", false), o.optBoolean("underline", false),
        )
    }
}
