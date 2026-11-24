/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import com.boxlabs.hexdroid.data.NetworkProfile
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class ChatZonaRun(val client: IrcClient, var pending: Boolean = true, var finished: Boolean = false)

private fun IrcViewModel.currentChatZonaRun(net: String, run: ChatZonaRun) =
    runtimes[net]?.client === run.client && chatZonaRuns[net] === run

/** LOGIN is completed by the server's account confirmation, never by a fixed login delay. */
internal suspend fun IrcViewModel.prepareChatZona(net: String, profile: NetworkProfile): Boolean {
    if (!isChatZonaHost(profile.host)) return true
    val client = runtimes[net]?.client ?: return false
    val run = ChatZonaRun(client)
    chatZonaRuns[net] = run
    val passwordResult = withContext(Dispatchers.IO) { runCatching { repo.secretStore.getChatZonaPassword(net) } }
    if (passwordResult.isFailure) {
        run.pending = false
        if (currentChatZonaRun(net,run)) append(bufKey(net,"*server*"),null,
            "*** No se pudo leer la clave cifrada de ChatZona. Revisa la configuración del servidor.",doNotify=false)
        return false
    }
    val password = passwordResult.getOrNull().orEmpty()
    if (profile.chatZonaAccount.isBlank() && password.isEmpty()) {
        run.pending = false
        if (_state.value.connections[net]?.myAccount != null) finishChatZona(net, profile, run)
        return true
    }
    if (!validChatZonaLogin(profile.chatZonaAccount, password)) {
        run.pending = false
        append(bufKey(net, "*server*"), null, "*** Revisa el nick y la clave de ChatZona.", doNotify = false)
        return false
    }
    if (!currentChatZonaRun(net,run)) return false
    client.sendRaw("LOGIN " + profile.chatZonaAccount + " " + password)
    val confirmed = withTimeoutOrNull(20_000L) {
        _state.first { !currentChatZonaRun(net,run) || it.connections[net]?.myAccount?.let { account ->
            casefoldText(net,account) == casefoldText(net,profile.chatZonaAccount)
        } == true }
    }
    if (confirmed == null || !currentChatZonaRun(net,run)) {
        run.pending = false
        if (currentChatZonaRun(net,run)) append(bufKey(net,"*server*"),null,
            "*** ChatZona no confirmó el login. No se enviaron entradas automáticas.",doNotify=false)
        return false
    }
    run.pending = false
    finishChatZona(net,profile,run)
    return currentChatZonaRun(net,run)
}

private suspend fun IrcViewModel.finishChatZona(net: String, profile: NetworkProfile, run: ChatZonaRun) {
    if (!currentChatZonaRun(net,run) || run.finished || run.pending) return
    run.finished = true
    val desired = profile.chatZonaAccount.ifBlank { profile.nick }
    if (!isReplyNick(desired)) return
    suspend fun send(line: String) { if (currentChatZonaRun(net,run)) run.client.sendRaw(line) }
    suspend fun gotNick(): Boolean = withTimeoutOrNull(2_000L) {
        _state.first { !currentChatZonaRun(net,run) || it.connections[net]?.myNick?.let { nick ->
            casefoldText(net,nick) == casefoldText(net,desired)
        } == true }
    } != null && currentChatZonaRun(net,run) &&
        _state.value.connections[net]?.myNick?.let { casefoldText(net,it)==casefoldText(net,desired) } == true
    send("NICK " + desired)
    if (!gotNick() && currentChatZonaRun(net,run)) {
        send("PRIVMSG Nick :Ghost " + desired)
        delay(1000L)
        send("NICK " + desired)
        gotNick() // bounded retry; keep the working numbered nick when recovery is refused
    }
    if (!currentChatZonaRun(net,run)) return
    val actual = _state.value.connections[net]?.myNick ?: return
    for (line in chatZonaModeCommands(actual,profile.chatZonaModes,profile.chatZonaKeepModes,profile.chatZonaExternalMessages)) send(line)
}

/** Also handle a LOGIN typed manually, once on this connection. */
internal fun IrcViewModel.onChatZonaAccount(net: String) {
    val profile = _state.value.networks.firstOrNull { it.id == net && isChatZonaHost(it.host) } ?: return
    val run = chatZonaRuns[net] ?: return
    if (run.pending || run.finished || _state.value.connections[net]?.myAccount == null) return
    viewModelScope.launch { finishChatZona(net,profile,run) }
}

/** Apply changed checkboxes to a currently authenticated connection, without logging in again. */
internal suspend fun IrcViewModel.applyChatZonaModes(profile: NetworkProfile) {
    if (!isChatZonaHost(profile.host)) return
    val connection = _state.value.connections[profile.id] ?: return
    if (!connection.connected || connection.myAccount == null) return
    val client = runtimes[profile.id]?.client ?: return
    for (line in chatZonaModeCommands(connection.myNick, profile.chatZonaModes, profile.chatZonaKeepModes, profile.chatZonaExternalMessages))
        client.sendRaw(line)
}
