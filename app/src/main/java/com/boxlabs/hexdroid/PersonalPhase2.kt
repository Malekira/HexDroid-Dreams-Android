/*
* HexDroidIRC - An IRC Client for Android
* Copyright (C) 2026 boxlabs
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.boxlabs.hexdroid

/** Server data only, scoped to one WHOIS request. No IRC target or outgoing action. */
data class WhoisWindow(
    val id: Long, val network: String, val nick: String,
    val fields: Map<String, String>, val lines: List<String>, val minimized: Boolean = false, val offsetX: Float = 0f, val offsetY: Float = 0f,
)

internal fun whoisFields(code: String?, params: List<String>, trailing: String?, nowSeconds: Long): Map<String, String> {
    fun p(i: Int) = params.getOrNull(i)?.let(::stripIrcFormatting)?.takeIf { it.isNotBlank() }
    val text = trailing?.let(::stripIrcFormatting)?.takeIf { it.isNotBlank() }
    return buildMap {
        when (code) {
            "311" -> { p(1)?.let { put("nick", it) }; p(2)?.let { put("user", it) }; p(3)?.let { put("host", it) }; text?.let { put("realname", it) } }
            "312" -> p(2)?.let { put("server", listOfNotNull(it, text).joinToString(" — ")) }
            "319" -> text?.let { put("channels", it) }
            "330" -> p(2)?.let { put("account", it) }
            "301" -> text?.let { put("away", it) }
            "317" -> {
                whoisDuration(p(2)?.toLongOrNull())?.let { put("idle", it) }
                p(3)?.toLongOrNull()?.takeIf { it in 1..nowSeconds }?.let { signon ->
                    put("signon", java.time.Instant.ofEpochSecond(signon).toString())
                    whoisDuration(nowSeconds - signon)?.let { put("connected", it) }
                }
            }
        }
    }
}

internal fun migrateWhoisDestinations(value: Int, presentationVersion: Int): Int =
    if (presentationVersion < 2 && value == (ReplyDestination.STATUS or ReplyDestination.WINDOW)) ReplyDestination.FLOATING
    else ReplyDestination.sanitize(if (value and ReplyDestination.PRIVATE != 0) (value and ReplyDestination.PRIVATE.inv()) or ReplyDestination.FLOATING else value, ReplyDestination.WHOIS_MASK, ReplyDestination.WHOIS_DEFAULT)

internal data class NickToken(val start: Int, val cursor: Int, val end: Int, val prefix: String, val hadAt: Boolean)
/** Command routing arguments are never decorated with message-completion templates. */
internal fun plainNickArgument(text: String, start: Int): Boolean {
    if (!text.startsWith("/") || text.startsWith("//") || start == 0) return false
    val previous = text.substring(0, start).trim().split(Regex("\\s+"))
    val command = previous.first().removePrefix("/").lowercase(java.util.Locale.ROOT)
    val commandEnd = text.indexOfFirst { it.isWhitespace() }.let { if (it < 0) text.length else it }
    var firstStart = commandEnd
    while (firstStart < text.length && text[firstStart].isWhitespace()) firstStart++
    var firstEnd = firstStart
    while (firstEnd < text.length && !text[firstEnd].isWhitespace()) firstEnd++
    val service = text.substring(firstStart,firstEnd).substringBefore('@').lowercase(java.util.Locale.ROOT)
    val services = setOf("nick","chan","nickserv","chanserv","authserv","operserv","memoserv","botserv","hostserv","ipvirtual")
    return when (command) {
        "msg", "m", "notice", "query" -> start in firstStart..firstEnd || service in services
        "me", "action", "say" -> false
        else -> true
    }
}

internal fun nickToken(text: String, cursor: Int): NickToken? {
    if (cursor !in 0..text.length) return null
    val commaSeparates = text.startsWith("/") && !text.startsWith("//")
    fun separator(c: Char) = c.isWhitespace() || (commaSeparates && c == ',')
    var start = cursor
    while (start > 0 && !separator(text[start - 1])) start--
    var end = cursor
    while (end < text.length && !separator(text[end])) end++
    val word = stripIrcFormatting(text.substring(start, cursor))
    if (word.startsWith("/")) return null
    val hadAt = word.startsWith('@')
    val prefix = word.removePrefix("@")
    if (prefix.isEmpty() && !plainNickArgument(text,start)) return null
    return NickToken(start, cursor, end, prefix, hadAt)
}
internal fun bareMember(nick: String) = nick.trimStart('~', '&', '!', '@', '%', '+')
internal fun memberNames(members: List<String>): List<String> = members.map(::bareMember).filter(::isReplyNick).distinctBy { it.lowercase() }
internal fun completeNickToken(text: String, token: NickToken, nick: String, style: NickCompletionStyle = NickCompletionStyle()): Pair<String, Int> {
    val followedBySpace = token.end < text.length && text[token.end].isWhitespace()
    val replacement = if (plainNickArgument(text,token.start))
        bareMember(nick) + if (followedBySpace || text.getOrNull(token.end) == ',') "" else " "
    else style.render(bareMember(nick), token.start == 0, token.hadAt, followedBySpace)
    return (text.substring(0, token.start) + replacement + text.substring(token.end)) to (token.start + replacement.length)
}

/** Visibility is independent of notification and visual-highlight preferences. */
internal fun mayNotifyVisibleMention(visible: Boolean, allowVisible: Boolean, highlighted: Boolean, own: Boolean, replay: Boolean) =
    !visible || (allowVisible && highlighted && !own && !replay)

internal fun greeting(members: List<String>, ownFold: String, bots: Set<String>, fold: (String) -> String): String =
    "Buenos días" + memberNames(members).filter { fold(it) != ownFold && fold(it) !in bots }.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = " ", separator = " ").orEmpty()

/** Keep conditional constructor fields out of the socket loop's large suspend state machine. */
internal fun whoisServerOutput(text: String, message: IrcMessage, modeBuffer: String?, request: WhoisRequest?): IrcEvent.ServerText =
    IrcEvent.ServerText(
        text = text, code = message.command, bufferName = modeBuffer ?: request?.buffer,
        isWhoisReply = request != null,
        whoisNick = if (request != null) message.params.getOrNull(1) else null,
        whoisRequestId = request?.id,
        whoisParams = message.params,
        whoisTrailing = if (request != null || message.command == "311") message.trailing else null,
    )
