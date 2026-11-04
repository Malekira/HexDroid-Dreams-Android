/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.AppRoot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Dreams109DisconnectTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private suspend fun until(condition: () -> Boolean) { withTimeout(15_000) { while (!condition()) delay(25) } }

    private suspend fun withModel(block: suspend (IrcViewModel, SettingsRepository, String, String) -> Unit) {
        val repo = SettingsRepository(context)
        val networks = repo.networksFlow.first()
        val settings = repo.settingsFlow.first()
        val id = "disconnect-${java.util.UUID.randomUUID()}"
        val other = "$id-other"
        val base = repo.defaultNetworks().first().copy(autoConnect = false, host = "irc.example.invalid")
        val store = ViewModelStore()
        lateinit var vm: IrcViewModel
        var created = false
        try {
            repo.saveNetworks(listOf(base.copy(id = id, name = "Red de prueba A", avatarUrl = "https://images.example.invalid/avatar.jpg"),
                base.copy(id = other, name = "Red de prueba B")))
            repo.updateSettings { it.copy(loggingEnabled = false, notificationsEnabled = false, keepAliveInBackground = false,
                showConnectionStatusNotification = false, autoReconnectEnabled = false,
                welcomeCompleted = true, introTourSeenVersion = INTRO_TOUR_VERSION) }
            main { vm = IrcViewModel(repo, context); store.put("disconnect", vm); created = true }
            until { vm.state.value.settingsLoaded && vm.state.value.networks.any { it.id == id } }
            main { vm._state.value = vm.state.value.copy(screen = AppScreen.CHAT, activeNetworkId = id,
                selectedBuffer = "$id::#demo", buffers = mapOf("$id::*server*" to UiBuffer("*server*"),
                    "$id::#demo" to UiBuffer("#demo"), "$id::DemoNick01" to UiBuffer("DemoNick01")),
                connections = mapOf(id to NetConnState(connected = true, myNick = "OwnDemoNick")),
                nicklists = mapOf("$id::#demo" to listOf("OwnDemoNick", "+DemoNick01")),
                showBufferList = false, showNickList = false) }
            block(vm, repo, id, other)
        } finally {
            if (created) {
                main { store.clear(); vm.privateRequests.close(); vm.historyArchive.close() }
            }
            repo.saveNetworks(networks)
            repo.updateSettings { settings }
        }
    }

    @Test fun dreams109ActualDisconnectMenuClosesTheServerAndShowsNetworksWithoutReopeningAnEmptyChat() = runBlocking {
        withModel { vm, _, id, _ ->
            compose.setContent { AppRoot(vm) }
            compose.onNodeWithTag("general-menu-button").performClick()
            compose.onNodeWithText(context.getString(R.string.menu_disconnect)).performClick()
            until { vm.state.value.screen == AppScreen.NETWORKS && vm.state.value.buffers.keys.none { it.startsWith("$id::") } }
            compose.onNodeWithText(context.getString(R.string.networks_title)).assertIsDisplayed()
            compose.onNodeWithTag("general-menu-button").assertDoesNotExist()
            main { vm.backToChat() }
            assertEquals(AppScreen.NETWORKS, vm.state.value.screen)
            assertTrue(id in vm.state.value.closedNetworkViews)
            assertEquals("", vm.state.value.selectedBuffer)
            assertFalse(vm.state.value.connections.getValue(id).connected)
        }
    }

    @Test fun dreams109DisconnectKeepsOtherNetworksDraftsProfileSecretsAndHistoryOnDisk() = runBlocking {
        withModel { vm, repo, id, other ->
            val path = "$id/#demo.txt"
            val line = "[04/11/2026 12:00:00] <DemoNick01> Texto completo ñ 🙂"
            val file = HistoryFiles.resolve(vm.historyArchive.root, path)
            repo.secretStore.setServerPassword(id, "test-only-disconnect-password")
            try {
                vm.historyArchive.append(path, line)
                val bytes = file.readBytes()
                main {
                    vm.saveDraft("$id::#demo", "Borrador ñ 🙂", 5)
                    vm._state.value = vm.state.value.copy(buffers = vm.state.value.buffers + ("$other::#other" to UiBuffer("#other")),
                        connections = vm.state.value.connections + (other to NetConnState(connected = true)),
                        whoisWindows = listOf(WhoisWindow(1, id, "DemoNick01", emptyMap(), emptyList()), WhoisWindow(2, other, "DemoNick02", emptyMap(), emptyList())))
                    vm.disconnectActive()
                }
                until { vm.state.value.buffers.keys.none { it.startsWith("$id::") } }
                assertEquals(AppScreen.NETWORKS, vm.state.value.screen)
                assertTrue(id in vm.state.value.closedNetworkViews)
                assertFalse(other in vm.state.value.closedNetworkViews)
                assertTrue(vm.state.value.connections.getValue(other).connected)
                assertTrue(vm.state.value.buffers.containsKey("$other::#other"))
                assertEquals(listOf(other), vm.state.value.whoisWindows.map { it.network })
                assertEquals("Borrador ñ 🙂", vm.draftFor("$id::#demo").text)
                assertEquals("https://images.example.invalid/avatar.jpg", repo.networksFlow.first().first { it.id == id }.avatarUrl)
                assertEquals("test-only-disconnect-password", repo.secretStore.getServerPassword(id))
                assertArrayEquals(bytes, file.readBytes())
                assertEquals(listOf(line), vm.historyArchive.page(path, "", 0).lines)
            } finally { vm.historyArchive.delete(setOf(path)); repo.secretStore.clearServerPassword(id) }
        }
    }

    @Test fun dreams109QuitAndDisconnectCommandsUseTheSameClosingPathEvenWhenAlreadyOffline() = runBlocking {
        withModel { vm, _, id, _ ->
            for (command in listOf("/quit Hasta luego", "/disconnect")) {
                main {
                    vm._state.value = vm.state.value.copy(screen = AppScreen.CHAT, activeNetworkId = id, selectedBuffer = "$id::#demo",
                        buffers = mapOf("$id::#demo" to UiBuffer("#demo")))
                    vm.sendInput(command)
                }
                until { vm.state.value.screen == AppScreen.NETWORKS && vm.state.value.buffers.isEmpty() }
            }
        }
    }

    @Test fun dreams109ConnectionLossAndInternalTeardownKeepTheirViewsAndNavigation() = runBlocking {
        withModel { vm, _, id, _ ->
            main { vm.handleEvent(id, IrcEvent.Disconnected("Corte de prueba", DisconnectCode.CONNECTION_RESET)) }
            assertEquals(AppScreen.CHAT, vm.state.value.screen)
            assertTrue(vm.state.value.buffers.containsKey("$id::#demo"))
            main {
                vm._state.value = vm.state.value.copy(connections = vm.state.value.connections + (id to NetConnState(connected = true)))
                vm.disconnectNetwork(id)
            }
            until { !vm.state.value.connections.getValue(id).connected }
            assertEquals(AppScreen.CHAT, vm.state.value.screen)
            assertTrue(vm.state.value.buffers.containsKey("$id::#demo"))
        }
    }

    @Test fun dreams109ExplicitlyReopeningAClosedServerRestoresItsViewAndSavedDraft() = runBlocking {
        withModel { vm, _, id, _ ->
            main { vm.saveDraft("$id::#demo", "Borrador guardado", 4); vm.disconnectActive() }
            until { id in vm.state.value.closedNetworkViews }
            main { vm.openBuffer("$id::#demo") }
            assertEquals(AppScreen.CHAT, vm.state.value.screen)
            assertFalse(id in vm.state.value.closedNetworkViews)
            assertEquals("Borrador guardado", vm.draftFor("$id::#demo").text)
            assertTrue(vm.state.value.buffers.containsKey("$id::#demo"))
        }
    }

    @Test fun dreams109DisconnectFinishingAfterSwitchingNetworksDoesNotStealTheNewChat() = runBlocking {
        withModel { vm, _, id, other ->
            // Hold the real per-network lock so the user can change views before teardown completes.
            val lock = vm.netLock(id)
            lock.lock()
            try {
                main {
                    vm.disconnectAndCloseNetwork(id)
                    vm._state.value = vm.state.value.copy(selectedBuffer = "$other::#other", activeNetworkId = other,
                        buffers = vm.state.value.buffers + ("$other::#other" to UiBuffer("#other")))
                }
            } finally { lock.unlock() }
            until { vm.state.value.buffers.keys.none { it.startsWith("$id::") } }
            assertEquals(AppScreen.CHAT, vm.state.value.screen)
            assertEquals("$other::#other", vm.state.value.selectedBuffer)
            assertEquals(other, vm.state.value.activeNetworkId)
        }
    }
}
