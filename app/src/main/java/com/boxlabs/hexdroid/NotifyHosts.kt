/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** USERHOST is a single query for a missing host, never a presence polling loop. */
internal suspend fun IrcClient.lookupNotifyUserHost(nick: String): String? {
    if (!isReplyNick(nick)) return null
    val key = casefold(nick)
    val result = CompletableDeferred<String?>()
    synchronized(notifyHostRequests) {
        if (notifyHostRequests.containsKey(key)) return null
        notifyHostRequests[key] = result
    }
    return try {
        sendBackground("USERHOST " + nick)
        withTimeoutOrNull(8000) { result.await() }
    } finally {
        synchronized(notifyHostRequests) {
            if (notifyHostRequests[key] === result) notifyHostRequests.remove(key)
        }
    }
}

/** RFC 1459: nick[*]=[+-]user@host. The '*' marks an IRC operator, not part of the nick. */
internal fun notifyUserHosts(raw: String): List<Pair<String,String>> = raw.split(Regex("\\s+")).mapNotNull { token ->
    val separator = token.indexOf('=')
    if (separator < 1) return@mapNotNull null
    val nick = token.substring(0,separator).removeSuffix("*")
    val value = token.substring(separator+1).removePrefix("+").removePrefix("-")
    val at = value.indexOf('@')
    if (!isReplyNick(nick) || at <= 0 || at == value.lastIndex ||
        value.any { it.isISOControl() || it.isWhitespace() }) null else nick to value
}

internal fun IrcClient.consumeNotifyUserHosts(msg: IrcMessage): Boolean {
    val entries = notifyUserHosts(msg.trailing ?: msg.params.drop(1).joinToString(" "))
    var consumed = 0
    for ((nick,host) in entries) {
        val result = synchronized(notifyHostRequests) { notifyHostRequests[casefold(nick)] }
        if (result != null) { result.complete(host); consumed++ }
    }
    return entries.isNotEmpty() && consumed == entries.size
}
