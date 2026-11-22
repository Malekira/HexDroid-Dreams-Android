/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

const val MENTIONS_BUFFER = "*mentions*"
const val COMMANDS_BUFFER = "*commands*"

/** Classification changes the tree only; bot detection never bypasses private-message policy. */
internal fun isSystemConversation(name: String, bots: Set<String>, caseMapping: String): Boolean {
    if (name == MENTIONS_BUFFER) return false
    if (name.startsWith("*") || name.startsWith("DCCCHAT:")) return true
    val folded = ircCasefold(name, caseMapping)
    val services = setOf("NickServ", "ChanServ", "MemoServ", "OperServ", "HostServ", "BotServ", "AuthServ", "HelpServ", "Global", "BouncerServ")
    return services.any { ircCasefold(it, caseMapping) == folded } ||
        bots.any { ircCasefold(it, caseMapping) == folded }
}

internal fun mentionSummary(source: String, privateMessage: Boolean, text: String): String =
    "[" + (if (privateMessage) "PV" else "Sala") + " " + source + "] " + text
