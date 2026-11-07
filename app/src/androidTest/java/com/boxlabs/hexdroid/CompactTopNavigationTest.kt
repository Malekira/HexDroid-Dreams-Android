/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.ChatScreen
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactTopNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val network = "compact-demo"
    private val room = "$network::#one"
    private val pv = "$network::DemoNick01"
    private var state by mutableStateOf(UiState())
    private val commands = mutableListOf<String>()

    private fun start() {
        val profile = SettingsRepository(context).defaultNetworks().first()
            .copy(id = network, name = "Red de prueba", host = "irc.example.invalid", autoConnect = false)
        state = UiState(networks = listOf(profile), selectedBuffer = room, activeNetworkId = network,
            buffers = linkedMapOf("$network::*server*" to UiBuffer("*server*"),
                room to UiBuffer("#one"), pv to UiBuffer("DemoNick01")),
            connections = mapOf(network to NetConnState(myNick = "OwnDemoNick")),
            nicklists = mapOf(room to listOf("@DemoNick01", "DemoNick02")),
            settings = UiSettings(networkTabsAtBottom = true), showBufferList = false, showNickList = false)
        compose.setContent {
            HexDroidIRCTheme {
                ChatScreen(state, onSelectBuffer = { state = state.copy(selectedBuffer = it) },
                    onSend = { commands += it }, onDisconnect = {}, onReconnect = {}, onExit = {},
                    onToggleBufferList = { state = state.copy(showBufferList = !state.showBufferList) },
                    onToggleNickList = {}, onToggleChannelsOnly = {}, onWhois = {},
                    onIgnoreNick = { _, _ -> }, onUnignoreNick = { _, _ -> },
                    onIgnoreNotifications = { _, _ -> }, onUnignoreNotifications = { _, _ -> },
                    onRefreshNicklist = {}, onRefreshChannelModes = {}, onOpenList = {}, onOpenSettings = {},
                    onOpenNetworks = {}, onOpenTransfers = {}, onSysInfo = {}, onAbout = {}, onUpdateSettings = {})
            }
        }
    }

    @Test fun compactTabsAreAboveMessagesAndTabAndBothTreesRemainAvailable() {
        start()
        val strip = compose.onNodeWithTag("conversation-tabs").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val messages = compose.onNodeWithTag("chat-messages").fetchSemanticsNode().boundsInRoot
        assertTrue("Tabs must be above the messages", strip.bottom <= messages.top)
        assertTrue("Top strip must stay compact", strip.height / compose.density.density <= 32f)
        val chip = compose.onNodeWithTag("conversation-tab:$room").fetchSemanticsNode().boundsInRoot
        assertTrue("Short room labels must not have wide fixed tabs", chip.width / compose.density.density <= 80f)
        compose.onNodeWithTag("nick-tab-button").assertIsDisplayed().assertTextContains("Tab").assertIsEnabled()
        compose.onNodeWithTag("chat-input").performTextInput("Demo")
        compose.onNodeWithTag("nick-tab-button").performClick()
        compose.onNodeWithTag("chat-input").assertTextEquals("DemoNick01: ")
        compose.onNodeWithTag("buffer-tree-button").performClick()
        compose.onNodeWithTag("sidebar-group-$network-rooms").assertIsDisplayed().performClick()
        compose.onNodeWithTag("sidebar-buffer-$room").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-buffer-$pv").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-group-$network-rooms").performClick()
        compose.onNodeWithTag("sidebar-group-$network-privates").assertIsDisplayed().performClick()
        compose.onNodeWithTag("sidebar-buffer-$pv").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-buffer-$room").assertIsDisplayed()
    }

    @Test fun privateTabsCompleteNicksAndCloseOnlyAfterAnExplicitLongPressMenuChoice() {
        start()
        compose.onNodeWithTag("conversation-tabs").performScrollToNode(hasTestTag("conversation-tab:$pv"))
        val privateTab = compose.onNodeWithTag("conversation-tab:$pv")
        privateTab.performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(commands.isEmpty()) }
        compose.onNodeWithTag("chat-input").performTextInput("Demo")
        compose.onNodeWithTag("nick-tab-button").performClick()
        compose.onNodeWithTag("chat-input").assertTextEquals("DemoNick01: ")
        privateTab.performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.chat_cd_close_label, "DemoNick01")).performClick()
        compose.runOnIdle { assertEquals(listOf("/closekey $pv"), commands) }
        compose.runOnIdle { state = state.copy(selectedBuffer = "$network::*server*") }
        compose.onNodeWithTag("nick-tab-button").assertIsDisplayed().assertTextContains("Tab").assertIsNotEnabled()
    }
}
