/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

internal enum class NickListRole { NORMAL, VOICE, HALFOP, OP, BOT, IRC_OPER, ADMIN }

internal fun nickListRole(display: String, connection: NetConnState): NickListRole {
    val symbols = connection.prefixSymbols
    val prefixes = display.takeWhile { it in symbols || it in "~&!@%+" }
    val nick = display.drop(prefixes.length)
    val modes = prefixes.mapNotNull { symbol ->
        symbols.indexOf(symbol).takeIf { it >= 0 }?.let { connection.prefixModes.getOrNull(it) }
    }
    fun known(nicks: Set<String>) = nicks.any { ircCasefold(it, connection.avatarCaseMapping) == ircCasefold(nick, connection.avatarCaseMapping) }
    return when {
        modes.any { it in "YqaA" } || prefixes.any { it in "~&!" } || known(connection.adminNicks) -> NickListRole.ADMIN
        known(connection.operNicks) -> NickListRole.IRC_OPER
        known(connection.botNicks) || nick.equals("Nick", true) || nick.equals("Chan", true) || nick.equals("IPVirtual", true) -> NickListRole.BOT
        'o' in modes || '@' in prefixes -> NickListRole.OP
        'h' in modes || '%' in prefixes -> NickListRole.HALFOP
        'v' in modes || '+' in prefixes -> NickListRole.VOICE
        else -> NickListRole.NORMAL
    }
}

internal fun IrcViewModel.noteOper(netId: String, nick: String, oper: Boolean, admin: Boolean? = null) {
    val key = casefoldText(netId, nick)
    setNetConn(netId) { connection ->
        connection.copy(
            operNicks = if (oper) connection.operNicks + key else connection.operNicks - key,
            adminNicks = when {
                !oper || admin == false -> connection.adminNicks - key
                admin == true -> connection.adminNicks + key
                else -> connection.adminNicks
            }
        )
    }
}
