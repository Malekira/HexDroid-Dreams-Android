package com.boxlabs.hexdroid

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.NotifyContactsDialog
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContactsCompletionAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun contactsPersistPerNetworkAndSurviveRepositoryReconstruction() = runBlocking {
        val repo = SettingsRepository(context)
        val old = repo.networksFlow.first()
        val a = repo.defaultNetworks().single().copy(id = "notify-a",notifyContacts = listOf("Alice","[Bob]"),notifyContactAlerts = false)
        val b = a.copy(id = "notify-b",notifyContacts = listOf("Carol"),notifyContactAlerts = true)
        try {
            repo.saveNetworks(listOf(a,b))
            val saved = SettingsRepository(context).networksFlow.first()
            assertEquals(listOf("Alice","[Bob]"),saved.single { it.id == a.id }.notifyContacts)
            assertFalse(saved.single { it.id == a.id }.notifyContactAlerts)
            assertEquals(listOf("Carol"),saved.single { it.id == b.id }.notifyContacts)
            assertTrue(saved.single { it.id == b.id }.notifyContactAlerts)
        } finally { repo.saveNetworks(old) }
    }

    @Test fun presenceEventsAreDeduplicatedAndRemainWithinTheirOwnNetwork() = runBlocking {
        val repo = SettingsRepository(context); val store = ViewModelStore()
        lateinit var vm: IrcViewModel
        withContext(Dispatchers.Main) { vm = IrcViewModel(repo,context); store.put("notify",vm) }
        try {
            withTimeout(10000) { vm.state.first { it.settingsLoaded } }
            val base = repo.defaultNetworks().single().copy(notifyContacts = listOf("Alice"))
            withContext(Dispatchers.Main) {
                vm._state.update { it.copy(networks = listOf(base.copy(id = "notify-a"),base.copy(id = "notify-b")),
                    settings = it.settings.copy(notificationsEnabled = false),
                    connections = mapOf("notify-a" to NetConnState(connected = true),"notify-b" to NetConnState(connected = true))) }
                assertFalse(vm.noteNotifyContact("notify-a",IrcEvent.MonitorStatus("Alice",true)))
                assertEquals(true,vm.state.value.connections.getValue("notify-a").contactPresence["alice"])
                assertTrue(vm.state.value.connections.getValue("notify-b").contactPresence.isEmpty())
                assertTrue(vm.noteNotifyContact("notify-a",IrcEvent.MonitorStatus("Alice",true)))
                assertFalse(vm.noteNotifyContact("notify-a",IrcEvent.MonitorStatus("Alice",false)))
                assertEquals(false,vm.state.value.connections.getValue("notify-a").contactPresence["alice"])
                assertFalse(vm.noteNotifyContact("notify-a",IrcEvent.MonitorStatus("NotAContact",true)))
                assertFalse(vm.state.value.connections.getValue("notify-a").contactPresence.containsKey("notacontact"))
            }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun monitorSubscriptionsAreRestoredForANewConnectionAndRemovedWhenContactIsDeleted() = runBlocking {
        val repo = SettingsRepository(context)
        val old = repo.networksFlow.first()
        val id = "notify-wire-" + System.nanoTime()
        val profile = repo.defaultNetworks().single().copy(id = id,host = "irc.example.invalid",notifyContacts = listOf("Alice"))
        val store = ViewModelStore()
        lateinit var vm: IrcViewModel
        var client: IrcClient? = null
        var collector: Job? = null
        var server: ContactServer? = null
        withContext(Dispatchers.Main) { vm = IrcViewModel(repo,context); store.put("wire",vm) }
        try {
            repo.upsertNetwork(profile)
            withTimeout(10000) { vm.state.first { it.settingsLoaded && it.networks.any { p -> p.id == id } } }
            suspend fun connect(): Pair<IrcClient,ContactServer> {
                val peer = ContactServer()
                val active = IrcClient(IrcConfig(host = "127.0.0.1",port = peer.port,useTls = false,
                    allowInvalidCerts = false,nick = "me",altNick = null,username = "u",realname = "r",isBouncer = true))
                active.strings = { resource, _ -> "s" + resource }
                active.plurals = { resource, _, _ -> "p" + resource }
                withContext(Dispatchers.Main) {
                    vm.runtimes[id] = IrcViewModel.NetRuntime(id,active)
                    vm._state.update { it.copy(connections = it.connections + (id to NetConnState(connected = true))) }
                }
                collector = CoroutineScope(Dispatchers.IO).launch {
                    active.events().collect { event ->
                        if (event is IrcEvent.ISupport || event is IrcEvent.MonitorStatus)
                            withContext(Dispatchers.Main) { vm.handleEvent(id,event) }
                    }
                }
                withContext(Dispatchers.IO) { peer.awaitLine("MONITOR + Alice") }
                return active to peer
            }
            val first = connect(); client = first.first; server = first.second
            withContext(Dispatchers.Main) { vm.syncNotifyContacts(id) }
            client!!.sendRaw("PING :contact-check")
            withContext(Dispatchers.IO) { server!!.awaitLine("PING :contact-check") }
            assertEquals(1,server!!.lines.count { it == "MONITOR + Alice" })
            val host = activeNotifyHost(client!!)
            assertEquals("ident@full.visible.host",host)
            assertFalse(server!!.lines.any { it.startsWith("WHOIS ") })
            client!!.forceClose(); server!!.close(); collector?.cancelAndJoin()
            // Deliberately do not deliver Disconnected: forced reconnection may cancel that event.
            val second = connect(); client = second.first; server = second.second
            assertEquals(1,server!!.lines.count { it == "MONITOR + Alice" })
            withContext(Dispatchers.Main) { vm.saveNotifyContacts(id,emptyList(),true) }
            withContext(Dispatchers.IO) { server!!.awaitLine("MONITOR - Alice") }
            assertTrue(vm.state.value.networks.single { it.id == id }.notifyContacts.isEmpty())
        } finally {
            client?.forceClose(); server?.close(); collector?.cancelAndJoin()
            withContext(Dispatchers.Main) { vm.runtimes.remove(id); store.clear() }
            repo.saveNetworks(old)
        }
    }

    private suspend fun activeNotifyHost(client: IrcClient): String? = client.lookupNotifyUserHost("Alice")

    @Test fun onlineContactCreatesOneFullHostBannerAndNotifyLogWithoutOpeningPeer() = runBlocking {
        val repo = SettingsRepository(context); val holder = ViewModelStore()
        lateinit var vm: IrcViewModel
        withContext(Dispatchers.Main) { vm = IrcViewModel(repo,context); holder.put("banner",vm) }
        try {
            withTimeout(10000) { vm.state.first { it.settingsLoaded } }
            val profile = repo.defaultNetworks().single().copy(id = "notify-banner",notifyContacts = listOf("Alice"))
            withContext(Dispatchers.Main) {
                vm._state.update { it.copy(networks = listOf(profile),settings = it.settings.copy(notificationsEnabled = false),
                    connections = mapOf(profile.id to NetConnState(connected = true,contactPresence = mapOf("alice" to false)))) }
                val event = IrcEvent.MonitorStatus("Alice",true,ident = "ident",host = "full.visible.host")
                vm.noteNotifyContact(profile.id,event)
                vm.noteNotifyContact(profile.id,event)
            }
            withTimeout(5000) { vm.state.first { it.notifyAlerts.isNotEmpty() } }
            val state = vm.state.value
            assertTrue(vm.isPseudoBuffer(NOTIFY_BUFFER))
            assertEquals(1,state.notifyAlerts.size)
            assertTrue(state.notifyAlerts.single().text.contains("Alice!ident@full.visible.host"))
            assertTrue(state.buffers.getValue(profile.id + "::" + NOTIFY_BUFFER).messages.any { it.text.contains("Alice!ident@full.visible.host") })
            assertFalse(state.buffers.containsKey(profile.id + "::Alice"))
            assertFalse(state.selectedBuffer.endsWith("::Alice"))
        } finally { withContext(Dispatchers.Main) { holder.clear() } }
    }

    @Test fun exportedContactsContainNoLoginSecretsAndDefaultColonMigrates() {
        val profile = SettingsRepository(context).defaultNetworks().single().copy(
            notifyContacts = listOf("Alice","[Bob]"),chatZonaAccount = "secret-account",chatZonaPassword = "secret-password")
        val document = org.json.JSONObject(notifyContactsDocument(profile,profile.notifyContacts,false))
        assertEquals("hexdreams-notify",document.getString("format"))
        assertEquals(2,document.getJSONArray("contacts").length())
        assertFalse(document.getBoolean("alerts"))
        assertFalse(document.toString().contains("secret-"))
        val style = NickCompletionStyle.fromJson(org.json.JSONObject().put("startTemplate","{nick}: "))
        assertEquals("{nick} ",style.startTemplate)
        val custom = NickCompletionStyle.fromJson(org.json.JSONObject().put("startTemplate","«{nick}» "))
        assertEquals("«{nick}» ",custom.startTemplate)
        val explicitColon = NickCompletionStyle(startTemplate = "{nick}: ")
        assertEquals(explicitColon,NickCompletionStyle.fromJson(explicitColon.toJson()))
    }

    @Test fun contactOnlineCreatesNotifyLogAndFloatingBannerWithFullHostWithoutOpeningPeer() = runBlocking {
        val repo = SettingsRepository(context); val store = ViewModelStore()
        lateinit var vm: IrcViewModel
        withContext(Dispatchers.Main) { vm = IrcViewModel(repo,context); store.put("notify-log",vm) }
        try {
            withTimeout(10000) { vm.state.first { it.settingsLoaded } }
            val profile = repo.defaultNetworks().single().copy(id = "notify-log",notifyContacts = listOf("Alice"))
            withContext(Dispatchers.Main) {
                vm._state.update { it.copy(networks = listOf(profile),settings = it.settings.copy(notificationsEnabled = false),
                    connections = mapOf(profile.id to NetConnState(connected = true))) }
                vm.noteNotifyContact(profile.id,IrcEvent.MonitorStatus("Alice",false))
                vm.noteNotifyContact(profile.id,IrcEvent.MonitorStatus("Alice",true,ident = "ident",host = "full.visible.host"))
            }
            withTimeout(5000) { vm.state.first { it.notifyAlerts.any { alert -> alert.nick == "Alice" } } }
            withContext(Dispatchers.Main) {
                val state = vm.state.value
                val alert = state.notifyAlerts.single()
                assertTrue(alert.text.contains("Alice!ident@full.visible.host"))
                assertTrue(state.buffers.getValue(profile.id + "::" + NOTIFY_BUFFER).messages.any { it.text.contains("Alice!ident@full.visible.host") })
                assertFalse(state.buffers.containsKey(profile.id + "::Alice"))
                assertTrue(vm.noteNotifyContact(profile.id,IrcEvent.MonitorStatus("Alice",true)))
                assertEquals(1,vm.state.value.notifyAlerts.size)
            }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    private class ContactServer {
        private val socket = java.net.ServerSocket(0)
        val port get() = socket.localPort
        val lines = java.util.concurrent.CopyOnWriteArrayList<String>()
        private val queue = java.util.concurrent.LinkedBlockingQueue<String>()
        private var peer: java.net.Socket? = null
        private var writer: java.io.BufferedWriter? = null
        init {
            kotlin.concurrent.thread(isDaemon = true) {
                runCatching {
                    peer = socket.accept()
                    writer = peer!!.getOutputStream().bufferedWriter()
                    peer!!.getInputStream().bufferedReader().useLines { input ->
                        input.forEach { line ->
                            lines.add(line); queue.add(line)
                            when {
                                line.startsWith("CAP LS") -> send(":srv CAP * LS :echo-message")
                                line.startsWith("CAP REQ") -> send(":srv CAP * ACK :" + line.substringAfter(':'))
                                line == "CAP END" -> { send(":srv 001 me :Welcome"); send(":srv 005 me MONITOR=30 CASEMAPPING=ascii :supported") }
                                line == "USERHOST Alice" -> send(":srv 302 me :Alice*=+ident@full.visible.host")
                                line.startsWith("PING ") -> send(":srv PONG srv :" + line.substringAfter(':'))
                            }
                        }
                    }
                }
            }
        }
        @Synchronized fun send(line: String) { writer!!.write(line + "\r\n"); writer!!.flush() }
        fun awaitLine(expected: String) {
            val deadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < deadline) {
                if (queue.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS) == expected) return
            }
            error("Expected command missing: " + expected)
        }
        fun close() { runCatching { peer?.close() }; runCatching { socket.close() } }
    }

    @Test fun contactDialogAddsRemovesAndSavesAlertPreference() {
        val profile = SettingsRepository(context).defaultNetworks().single().copy(notifyContacts = listOf("Alice"))
        var saved: List<String>? = null; var alerts: Boolean? = null
        compose.setContent { HexDroidIRCTheme {
            NotifyContactsDialog(profile,NetConnState(connected = true,monitorLimit = 30,
                contactPresence = mapOf("alice" to true)),true,
                onSave = { contacts,enabled -> saved = contacts; alerts = enabled },onDismiss = {})
        } }
        compose.onNodeWithText(context.getString(R.string.contact_online)).assertIsDisplayed()
        compose.onNodeWithTag("notify-contact-input").performTextReplacement("Bob, Carol")
        compose.onNodeWithTag("notify-add-contact").performClick()
        compose.onNodeWithTag("notify-remove-Alice").performClick()
        compose.onNodeWithTag("notify-contact-alerts").performClick()
        compose.onNodeWithTag("notify-save").performClick()
        compose.runOnIdle { assertEquals(listOf("Bob","Carol"),saved); assertEquals(false,alerts) }
    }
}
