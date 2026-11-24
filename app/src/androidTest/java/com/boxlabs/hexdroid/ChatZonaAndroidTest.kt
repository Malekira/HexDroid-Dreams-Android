package com.boxlabs.hexdroid

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ChatZonaAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun freshDefaultsAreOneTlsChatZonaNetwork() {
        val profile = SettingsRepository(context).defaultNetworks().single()
        assertEquals("irc.chatzona.org", profile.host)
        assertEquals(6697, profile.port)
        assertTrue(profile.useTls)
        assertFalse(profile.allowInvalidCerts)
        assertFalse(profile.saslEnabled)
        assertTrue(profile.serviceAuthCommand.isNullOrBlank())
    }

    @Test fun accountAndModesPersistWithoutPuttingThePasswordInNetworkJson() = runBlocking {
        val repo = SettingsRepository(context)
        val old = repo.networksFlow.first()
        val id = "chatzona-config-" + System.nanoTime()
        val profile = repo.defaultNetworks().single().copy(id = id, chatZonaAccount = "MiNick",
            chatZonaPassword = "not-in-json", chatZonaModes = "iIgR", chatZonaKeepModes = true, chatZonaExternalMessages = true)
        try {
            repo.secretStore.setChatZonaPassword(id, "secret42")
            repo.upsertNetwork(profile)
            val restored = SettingsRepository(context).networksFlow.first().single { it.id == id }
            assertEquals("MiNick", restored.chatZonaAccount)
            assertEquals("iIgR", restored.chatZonaModes)
            assertTrue(restored.chatZonaKeepModes); assertTrue(restored.chatZonaExternalMessages)
            assertNull(restored.chatZonaPassword)
            assertEquals("secret42", SettingsRepository(context).secretStore.getChatZonaPassword(id))
            assertEquals("secret42", repo.collectSecrets(listOf(restored)).getValue(id).chatZona)
        } finally {
            repo.secretStore.clearChatZonaPassword(id)
            repo.saveNetworks(old)
        }
    }

    @Test fun liteMigrationPreservesChatZonaProfileAndAppliesOnlyOnce() = runBlocking {
        val repo = SettingsRepository(context)
        val old = repo.networksFlow.first()
        val oldDesired = repo.desiredNetworkIdsFlow.first()
        val oldLast = repo.lastNetworkIdFlow.first()
        val existing = repo.defaultNetworks().single().copy(id = "existing-cz", host = "lima.chatzona.org",
            port = 6667, useTls = false, nick = "MySavedNick", chatZonaAccount = "MySavedNick")
        val other = existing.copy(id = "other", host = "irc.other.example")
        try {
            repo.saveNetworks(listOf(other, existing))
            repo.setDesiredNetworkIds(setOf("other"))
            repo.ensureChatZonaOnly()
            val saved = repo.networksFlow.first().single()
            assertEquals("existing-cz", saved.id)
            assertEquals("MySavedNick", saved.nick)
            assertEquals("irc.chatzona.org", saved.host)
            assertEquals(6697, saved.port)
            assertTrue(saved.useTls)
            assertTrue(repo.desiredNetworkIdsFlow.first().isEmpty())
            repo.saveNetworks(listOf(saved, other))
            repo.ensureChatZonaOnly()
            assertEquals(2, repo.networksFlow.first().size)
        } finally {
            repo.saveNetworks(old)
            repo.setDesiredNetworkIds(oldDesired)
            repo.setLastNetworkId(oldLast)
        }
    }

    @Test fun loginWaitsForServerConfirmationBeforeNickAndModes() = runBlocking {
        flow(busy = false)
    }

    @Test fun occupiedNickIsRecoveredThroughNickBotBeforeModes() = runBlocking {
        flow(busy = true)
    }

    @Test fun staleConnectionCannotApplyModesOrRecoverANick() = runBlocking {
        flow(busy = false, replaceConnection = true)
    }

    private suspend fun flow(busy: Boolean, replaceConnection: Boolean = false) = coroutineScope {
        val server = LocalServer(busy)
        val client = IrcClient(IrcConfig(host = "127.0.0.1", port = server.port, useTls = false,
            allowInvalidCerts = false, nick = "MiNick01", altNick = null, username = "test", realname = "test", isBouncer = true))
        client.strings = { id, _ -> "s" + id }
        client.plurals = { id, _, _ -> "p" + id }
        val repo = SettingsRepository(context)
        val store = ViewModelStore()
        val id = "chatzona-flow-" + System.nanoTime()
        val profile = repo.defaultNetworks().single().copy(id = id, nick = "MiNick", chatZonaAccount = "MiNick",
            chatZonaModes = "iIgR", chatZonaKeepModes = true, chatZonaExternalMessages = true)
        lateinit var vm: IrcViewModel
        withContext(Dispatchers.Main) { vm = IrcViewModel(repo, context); store.put("test", vm) }
        var collector: Job? = null
        try {
            withTimeout(10000) { vm.state.first { it.settingsLoaded } }
            repo.secretStore.setChatZonaPassword(id, "test-secret")
            withContext(Dispatchers.Main) {
                vm._state.update { it.copy(networks = it.networks + profile,
                    connections = it.connections + (id to NetConnState(connected = true, myNick = "MiNick01"))) }
                vm.runtimes[id] = IrcViewModel.NetRuntime(id, client)
            }
            val ready = CompletableDeferred<Unit>()
            collector = launch(Dispatchers.IO) {
                client.events().collect { event ->
                    if (event is IrcEvent.Registered) ready.complete(Unit)
                    if (event is IrcEvent.AccountChanged || event is IrcEvent.NickChanged)
                        withContext(Dispatchers.Main) { vm.handleEvent(id, event) }
                }
            }
            withTimeout(10000) { ready.await() }
            val login = async(Dispatchers.Main) { vm.prepareChatZona(id, profile) }
            assertEquals("LOGIN MiNick test-secret", withContext(Dispatchers.IO) { server.next { it.startsWith("LOGIN ") } })
            assertFalse(server.lines.any { it.startsWith("MODE ") || it == "NICK MiNick" })
            if (replaceConnection) {
                withContext(Dispatchers.Main) { vm.runtimes.remove(id) }
                server.send(":srv 900 MiNick01 u@host MiNick :You are logged in")
                assertFalse(withTimeout(5000) { login.await() })
                assertFalse(server.lines.any { it.startsWith("MODE ") || it.contains(":Ghost ") })
            } else {
                server.send(":srv 900 MiNick01 u@host MiNick :You are logged in")
                assertTrue(withTimeout(12000) { login.await() })
                withContext(Dispatchers.IO) { server.next { it == "MODE MiNick -c" } }
                val commands = server.lines.filter { it.startsWith("LOGIN ") || it == "NICK MiNick" || it.startsWith("MODE ") || it.startsWith("PRIVMSG Nick") }
                assertEquals("LOGIN MiNick test-secret", commands.first())
                assertEquals(busy, commands.contains("PRIVMSG Nick :Ghost MiNick"))
                assertTrue(commands.indexOf("NICK MiNick") < commands.indexOf("MODE MiNick +iIgR"))
                assertEquals(1, commands.count { it == "PRIVMSG Nick :set keepmodes on" })
                assertFalse(commands.any { it.contains("NickServ", true) || it.contains("IDENTIFY", true) })
            }
        } finally {
            collector?.cancelAndJoin()
            client.forceClose(); server.close()
            withContext(Dispatchers.Main) { vm.runtimes.remove(id); store.clear() }
            repo.secretStore.clearChatZonaPassword(id)
        }
    }

    private class LocalServer(private val busy: Boolean) {
        private val socket = ServerSocket(0)
        val port get() = socket.localPort
        val lines = java.util.concurrent.CopyOnWriteArrayList<String>()
        private val queue = LinkedBlockingQueue<String>()
        private var peer: java.net.Socket? = null
        private var writer: java.io.BufferedWriter? = null
        private var recovered = !busy
        init {
            thread(isDaemon = true) {
                runCatching {
                    val connection = socket.accept(); peer = connection
                    writer = connection.getOutputStream().bufferedWriter()
                    connection.getInputStream().bufferedReader().useLines { input ->
                        input.forEach { line ->
                            lines.add(line); queue.add(line)
                            when {
                                line.startsWith("CAP LS") -> send(":srv CAP * LS :echo-message")
                                line.startsWith("CAP REQ") -> send(":srv CAP * ACK :" + line.substringAfter(':'))
                                line == "CAP END" -> send(":srv 001 MiNick01 :Welcome")
                                line == "PRIVMSG Nick :Ghost MiNick" -> recovered = true
                                line == "NICK MiNick" -> if (recovered) send(":MiNick01!u@host NICK :MiNick")
                                    else send(":srv 433 MiNick01 MiNick :Nick in use")
                                line.startsWith("PING ") -> send(":srv PONG srv :" + line.substringAfter(':'))
                            }
                        }
                    }
                }
            }
        }
        @Synchronized fun send(line: String) { writer!!.write(line + "\r\n"); writer!!.flush() }
        fun next(matches: (String) -> Boolean): String {
            val deadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < deadline) {
                val line = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (matches(line)) return line
            }
            error("No matching command: " + lines.map { redactRawLine(it) })
        }
        fun close() { runCatching { peer?.close() }; runCatching { socket.close() } }
    }
}
