/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

internal const val MAX_NOTIFY_CONTACTS = 64

internal fun cleanNotifyContacts(nicks: List<String>, caseMapping: String = "ascii"): List<String> =
    nicks.map { bareMember(it.trim()) }.filter { it.length <= 130 && isReplyNick(it) }
        .distinctBy { ircCasefold(it,caseMapping) }.take(MAX_NOTIFY_CONTACTS)

internal fun toggleNotifyContact(contacts: List<String>, nick: String, mapping: String): List<String> {
    val key = ircCasefold(nick,mapping)
    return if (contacts.any { ircCasefold(it,mapping) == key })
        contacts.filterNot { ircCasefold(it,mapping) == key }
    else cleanNotifyContacts(contacts + nick,mapping)
}

internal fun contactTransition(previous: Boolean?, online: Boolean): Boolean =
    previous != null && previous != online

/** Keep each IRC line comfortably below the legacy 512-byte limit. */
internal fun monitorContactCommands(operation: Char, nicks: List<String>): List<String> = buildList {
    var line = "MONITOR " + operation + " "
    val prefix = line
    for (nick in nicks) {
        if (!isReplyNick(nick)) continue
        val separator = if (line == prefix) "" else ","
        if ((line + separator + nick).toByteArray(Charsets.UTF_8).size > 400 && line != prefix) {
            add(line); line = prefix + nick
        } else line += separator + nick
    }
    if (line != prefix) add(line)
}

internal suspend fun IrcViewModel.syncNotifyContacts(net: String) {
    val client = runtimes[net]?.client ?: return
    val connection = _state.value.connections[net] ?: return
    if (!client.registered || !connection.connected || connection.monitorLimit < 0) return
    val profile = _state.value.networks.firstOrNull { it.id == net } ?: return
    if (isChatZonaHost(profile.host) && profile.chatZonaAccount.isNotBlank() && connection.myAccount == null) return
    val fold: (String) -> String = { casefoldText(net,it) }
    val desired = cleanNotifyContacts(profile.notifyContacts, connection.avatarCaseMapping)
        .take(connection.monitorLimit).associateBy(fold)
    val previous = (if (notifySubscriptionClients[net] === client) notifySubscriptions[net].orEmpty() else emptySet()).associateBy(fold)
    val removed = previous.filterKeys { it !in desired }.values.toList()
    val added = desired.filterKeys { it !in previous }.values.toList()
    notifySubscriptionClients[net] = client
    notifySubscriptions[net] = desired.values.toSet()
    try {
        for (line in monitorContactCommands('-',removed) + monitorContactCommands('+',added)) {
            if (runtimes[net]?.client !== client) return
            client.sendRaw(line)
        }
    } catch (_: Exception) {
        if (runtimes[net]?.client === client) notifySubscriptions.remove(net)
    }
}

fun IrcViewModel.saveNotifyContacts(net: String, nicks: List<String>, alerts: Boolean) {
    val caseMapping = _state.value.connections[net]?.avatarCaseMapping ?: "ascii"
    val cleaned = cleanNotifyContacts(nicks,caseMapping)
    viewModelScope.launch {
        repo.updateNetworkProfile(net) { it.copy(notifyContacts = cleaned, notifyContactAlerts = alerts) }
        _state.update { state -> state.copy(networks = state.networks.map {
            if (it.id == net) it.copy(notifyContacts = cleaned, notifyContactAlerts = alerts) else it
        }) }
        val keys = cleaned.map { casefoldText(net,it) }.toSet()
        setNetConn(net) { it.copy(contactPresence = it.contactPresence.filterKeys { key -> key in keys }) }
        syncNotifyContacts(net)
    }
}

/** Updates only this network, suppressing duplicate events and initial snapshots on reconnect. */
internal fun IrcViewModel.noteNotifyContact(net: String, event: IrcEvent.MonitorStatus): Boolean {
    val profile = _state.value.networks.firstOrNull { it.id == net } ?: return false
    val key = casefoldText(net,event.nick)
    val contact = profile.notifyContacts.firstOrNull { casefoldText(net,it) == key } ?: return false
    val previous = _state.value.connections[net]?.contactPresence?.get(key)
    if (previous == event.online) return true
    setNetConn(net) { it.copy(contactPresence = it.contactPresence + (key to event.online)) }
    if (contactTransition(previous,event.online) && profile.notifyContactAlerts && _state.value.settings.notificationsEnabled) {
        val message = appContext.getString(if (event.online) R.string.contact_online_event else R.string.contact_offline_event,contact)
        runCatching { notifier.notifyContact(net,contact,profile.name,message) }
    }
    return false
}
