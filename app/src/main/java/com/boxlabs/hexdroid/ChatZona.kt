/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

internal fun isChatZonaHost(host: String): Boolean {
    val name = host.trim().trimEnd('.').lowercase(java.util.Locale.ROOT)
    return name == "chatzona.org" || name.endsWith(".chatzona.org")
}

internal fun chatZonaFallbackNick(nick: String, attempt: Int, limit: Int = 30): String {
    val suffix = attempt.coerceAtLeast(1).toString().padStart(2,'0')
    return nick.take((limit.coerceAtLeast(3) - suffix.length).coerceAtLeast(1)) + suffix
}

internal fun validChatZonaLogin(account: String, password: String): Boolean =
    (account.isBlank() && password.isEmpty()) ||
        (isReplyNick(account) && password.isNotEmpty() && password.none { it.isWhitespace() || it.isISOControl() })

internal fun chatZonaModeCommands(nick: String, modes: String, keep: Boolean, external: Boolean): List<String> = buildList {
    val enabled = "iIgR".filter { it in modes }
    val disabled = "iIgR".filter { it !in modes }
    val flags = (if (enabled.isNotEmpty()) "+" + enabled else "") + (if (disabled.isNotEmpty()) "-" + disabled else "")
    add("MODE " + nick + " " + flags)
    add("PRIVMSG Nick :set keepmodes " + if (keep) "on" else "off")
    if (external) add("MODE " + nick + " -c")
}
