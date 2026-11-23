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

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import com.boxlabs.hexdroid.IrcViewModel.DccChatSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.Socket
import kotlin.random.Random

internal fun IrcViewModel.isDccChatBufferName(name: String): Boolean = name.startsWith("DCCCHAT:")

internal fun IrcViewModel.dccChatBufferKey(netId: String, peerNick: String): String = bufKey(netId, "DCCCHAT:$peerNick")

/** Key of [netId]'s DCC chat buffer for [nick], matching an open one case-insensitively. */
internal fun IrcViewModel.findDccChatKey(netId: String, nick: String): String {
    val exact = dccChatBufferKey(netId, nick)
    return sequenceOf(dccChatSessions.keys, dccChatPending.keys, _state.value.buffers.keys)
        .flatten()
        .firstOrNull { it.equals(exact, ignoreCase = true) } ?: exact
}

/** Port text for DCC chat messages, marked when the chat is SDCC. */
internal fun IrcViewModel.dccChatPortLabel(port: Int, secure: Boolean): String =
    if (secure) "$port, SDCC/TLS" else port.toString()

internal fun IrcViewModel.closeDccChatSession(bufferKey: String, reason: String? = null) {
    val ses = dccChatSessions.remove(bufferKey) ?: return
    runCatching { ses.readJob.cancel() }
    viewModelScope.launch(Dispatchers.IO) { runCatching { ses.socket.close() } }
    val r = reason?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
    append(bufferKey, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_disconnected, r), doNotify = false)
}

internal fun IrcViewModel.startDccChatSession(netId: String, peer: String, bufferKey: String, socket: Socket) {
    // Replace any existing session for this buffer.
    closeDccChatSession(bufferKey, reason = "replaced")

    runCatching {
        socket.tcpNoDelay = true
        socket.keepAlive = true
    }

    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

    val job = viewModelScope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
        try {
            val reader = EncodingLineReader(java.io.BufferedInputStream(socket.getInputStream()), "auto")
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                val isAction = line.length > 9 && line.startsWith("\u0001ACTION ") && line.endsWith("\u0001")
                val text = if (isAction) {
                    line.removePrefix("\u0001ACTION ").removeSuffix("\u0001")
                } else line
                withContext(Dispatchers.Main) {
                    append(bufferKey, from = peer, text = text, isAction = isAction, isPrivate = true)
                }
            }
        } catch (t: Throwable) {
            if (isActive) {
                withContext(Dispatchers.Main) {
                    append(bufferKey, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_failed, t.message ?: t::class.java.simpleName), isHighlight = true)
                }
            }
        } finally {
            runCatching { socket.close() }
            withContext(NonCancellable + Dispatchers.Main) {
                if (dccChatSessions[bufferKey]?.socket === socket) {
                    dccChatSessions.remove(bufferKey)
                    append(bufferKey, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_closed), doNotify = false)
                }
            }
        }
    }

    dccChatSessions[bufferKey] = DccChatSession(netId, peer, bufferKey, socket, writer, job)
    append(bufferKey, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_connected, peer), doNotify = false)
    job.start()
}

/** Show the protocol and certificate fingerprint of an SDCC chat; no-op for plain sockets. */
internal fun IrcViewModel.appendDccTlsInfo(bufferKey: String, socket: Socket, server: Boolean) {
    val ssl = socket as? javax.net.ssl.SSLSocket ?: return
    val info = runCatching { DccTls.describe(ssl, server) }.getOrNull() ?: return
    val res = if (server) R.string.vm_dcc_chat_tls_local else R.string.vm_dcc_chat_tls_peer
    append(bufferKey, from = null, text = "*** " + appContext.getString(res, info.protocol, info.serverFingerprint ?: "?"), doNotify = false)
}

/** Run [block] as [bufferKey]'s pending DCC chat connect, replacing any earlier one. */
internal fun IrcViewModel.launchDccChatConnect(bufferKey: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
        try {
            block()
        } finally {
            coroutineContext[Job]?.let { dccChatPending.remove(bufferKey, it) }
        }
    }
    dccChatPending.put(bufferKey, job)?.cancel()
    job.start()
}

internal fun IrcViewModel.sendDccChatLine(bufferKey: String, line: String, isAction: Boolean) {
    val ses = dccChatSessions[bufferKey]
    if (ses == null) {
        append(bufferKey, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_not_connected), isHighlight = true)
        return
    }

    val text = line.replace("\r", "").replace('\n', ' ')
    val payload = if (isAction) "\u0001ACTION $text\u0001" else text

    // Writing to a socket on the main thread can throw (StrictMode / NetworkOnMainThreadException)
    // and will also make typing feel laggy if the peer/network is slow. Always write on IO.
    viewModelScope.launch(Dispatchers.IO) {
        try {
            synchronized(ses.writer) {
                ses.writer.write(payload)
                ses.writer.write("\r\n")
                ses.writer.flush()
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main.immediate) {
                append(
                    bufferKey,
                    from = null,
                    text = "*** " + appContext.getString(R.string.vm_dcc_chat_send_failed, t.message ?: t::class.java.simpleName),
                    isHighlight = true
                )
                if (dccChatSessions[bufferKey] === ses) closeDccChatSession(bufferKey, reason = t.message)
            }
            return@launch
        }

        withContext(Dispatchers.Main.immediate) {
            val myNick = _state.value.connections[ses.netId]?.myNick ?: _state.value.myNick
            append(bufferKey, from = myNick, text = text, isAction = isAction)
        }
    }
}

/**
 * Offer a DCC chat to [targetNick] on [netIdArg] (default: the active network). [secure] forces
 * SCHAT on or off; null follows the SDCC setting.
 */
fun IrcViewModel.startDccChatFlow(targetNick: String, secure: Boolean? = null, netIdArg: String? = null) {
    val st = _state.value
    val netId = netIdArg ?: st.activeNetworkId ?: return
    val rt = runtimes[netId] ?: return
    val c = rt.client
    val peer = targetNick.trim().trimStart('~', '&', '@', '%', '+')
    if (peer.isBlank()) return

    if (!st.settings.dccEnabled) {
        append(bufKey(netId, "*server*"), from = "DCC", text = appContext.getString(R.string.vm_dcc_disabled), isHighlight = true)
        return
    }

    // Active DCC CHAT requires us to listen for the peer to connect in. A CONNECT-only
    // proxy can't do that, and listening locally while proxied would expose our real
    // address. There's no passive/reverse CHAT equivalent that's widely supported, so
    // refuse outright while a proxy is active.
    if (isProxiedNetwork(netId)) {
        append(bufKey(netId, "*server*"), from = "DCC",
            text = appContext.getString(R.string.vm_dcc_chat_proxy),
            isHighlight = true)
        return
    }

    val key = findDccChatKey(netId, peer)
    openBuffer(key)

    val minP = st.settings.dccIncomingPortMin
    val maxP = st.settings.dccIncomingPortMax
    val useTls = secure ?: st.settings.dccSecure

    launchDccChatConnect(key) {
        try {
            val chatVerb = if (useTls) "SCHAT" else "CHAT"
            val secureLabel = if (useTls) " (SDCC/TLS)" else ""
            append(key, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_offering, secureLabel, peer), doNotify = false)
            val socket = dcc.startChat(
                portMin = minP,
                portMax = maxP,
                secure = useTls,
                onClient = { addrField, port ->
                    val payload = "DCC $chatVerb chat $addrField $port"
                    c.ctcp(peer, payload)
                    append(bufKey(netId, "*server*"), from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_sent, secureLabel, peer, port), doNotify = false)
                }
            )
            startDccChatSession(netId, peer, key, socket)
            appendDccTlsInfo(key, socket, server = true)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (isActive) {
                append(key, from = null, text = "*** " + appContext.getString(R.string.vm_dcc_chat_offer_failed, t.message ?: t::class.java.simpleName), isHighlight = true)
            }
        }
    }
}

