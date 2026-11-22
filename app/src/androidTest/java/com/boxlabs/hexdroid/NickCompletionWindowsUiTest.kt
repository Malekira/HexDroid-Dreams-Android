/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.ChatScreen
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NickCompletionWindowsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = "tree-demo-a"
    private val second = "tree-demo-b"
    private var state by mutableStateOf(UiState())
    private val selections = mutableListOf<String>()
    private val commands = mutableListOf<String>()

    private fun fixture(tabs: Boolean) {
        val base = SettingsRepository(context).defaultNetworks().first().copy(host = "irc.example.invalid", autoConnect = false)
        state = UiState(networks = listOf(base.copy(id = first, name = "Red de prueba A"), base.copy(id = second, name = "Red de prueba B")),
            activeNetworkId = first, selectedBuffer = "$first::#demo", showBufferList = true, showNickList = false,
            settings = UiSettings(networkTabs = tabs, networkTabsAtBottom = false),
            connections = mapOf(first to NetConnState(connected = true, chanTypes = "#&"), second to NetConnState(connected = true, chanTypes = "#&")),
            buffers = linkedMapOf("$first::*server*" to UiBuffer("*server*"),
                "$first::#demo" to UiBuffer("#demo", unread = 2), "$first::DemoNick01" to UiBuffer("DemoNick01", unread = 3, highlights = 1),
                "$first::$WHOIS_BUFFER" to UiBuffer(WHOIS_BUFFER), "$first::$NOTICES_BUFFER" to UiBuffer(NOTICES_BUFFER),
                "$second::*server*" to UiBuffer("*server*"), "$second::&other" to UiBuffer("&other"), "$second::DemoNick02" to UiBuffer("DemoNick02")))
    }

    @Composable private fun Chat(phone: Boolean) {
        val configuration = Configuration(context.resources.configuration).apply {
            screenWidthDp = if (phone) 400 else 900
            orientation = if (phone) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) { HexDroidIRCTheme {
            ChatScreen(state, onSelectBuffer = { selections += it; state = state.copy(selectedBuffer = it) }, onSend = { commands += it },
                onDisconnect = {}, onReconnect = {}, onExit = {}, onToggleBufferList = { state = state.copy(showBufferList = !state.showBufferList) },
                onToggleNickList = {}, onToggleChannelsOnly = {}, onWhois = {}, onIgnoreNick = { _, _ -> }, onUnignoreNick = { _, _ -> },
                onIgnoreNotifications = { _, _ -> }, onUnignoreNotifications = { _, _ -> }, onRefreshNicklist = {}, onRefreshChannelModes = {},
                onOpenList = {}, onOpenSettings = {}, onOpenNetworks = {}, onOpenTransfers = {}, onSysInfo = {}, onAbout = {}, onUpdateSettings = {},
                onToggleNetworkExpanded = { id -> state = state.copy(collapsedNetworkIds = if (id in state.collapsedNetworkIds) state.collapsedNetworkIds - id else state.collapsedNetworkIds + id) })
        } }
    }

    private fun openDrawer() { compose.onNodeWithText("☰").performClick() }
    private fun group(kind: String, network: String = first) = compose.onNodeWithTag("sidebar-group-$network-$kind")
    private fun row(name: String, network: String = first) = compose.onNodeWithTag("sidebar-buffer-$network::$name")
    private fun collapsed(kind: String, network: String = first) {
        group(kind, network).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.dreams_sidebar_collapsed)))
    }


    @Test fun systemKeepsBotsAndCommandsWhileMentionsRemainWithPrivateConversations() {
        fixture(tabs = false)
        state = state.copy(connections = state.connections + (first to state.connections.getValue(first).copy(botNicks = setOf("custombot"))),
            buffers = state.buffers + mapOf(
                "$first::$MENTIONS_BUFFER" to UiBuffer(MENTIONS_BUFFER, unread = 1),
                "$first::$COMMANDS_BUFFER" to UiBuffer(COMMANDS_BUFFER),
                "$first::CustomBot" to UiBuffer("CustomBot"), "$first::NickServ" to UiBuffer("NickServ")))
        compose.setContent { Chat(phone = false) }
        group("other").assertTextContains("Sistema")
        row(MENTIONS_BUFFER).assertTextContains("Menciones")
        group("privates").performClick()
        row(MENTIONS_BUFFER).assertDoesNotExist(); row("DemoNick01").assertDoesNotExist()
        row("CustomBot").assertIsDisplayed(); row("NickServ").assertIsDisplayed(); row(COMMANDS_BUFFER).assertIsDisplayed()
        group("privates").performClick(); group("other").performClick()
        row(MENTIONS_BUFFER).assertIsDisplayed(); row("DemoNick01").assertIsDisplayed()
        row("CustomBot").assertDoesNotExist(); row("NickServ").assertDoesNotExist()
        compose.runOnIdle { assertTrue(selections.isEmpty()); assertTrue(commands.isEmpty()) }
    }

    @Test fun customizedCompletionCyclesAndSendsSeveralColorsThroughTheRealComposer() {
        fixture(tabs = false)
        state = state.copy(showBufferList = false, nicklists = mapOf("$first::#demo" to listOf("@Alice", "+Alfred")),
            settings = state.settings.copy(nickCompletion = NickCompletionStyle(
                startTemplate = "«{nick}» ", colors = "FF3399,33CCFF", bold = true)))
        compose.setContent { Chat(phone = true) }
        val input = compose.onNodeWithTag("chat-input")
        input.performTextReplacement("Al")
        compose.onNodeWithTag("nick-tab-button").performClick()
        compose.onNodeWithTag("nick-tab-button").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.chat_send_message)).performClick()
        compose.runOnIdle {
            assertEquals("«Alice»", stripIrcFormatting(commands.single()).trim())
            assertTrue(commands.single().contains("\u0004FF3399A\u000433CCFFl"))
        }
    }

    @Test fun quickMenuContainsOnlyTheRequestedEntries() {
        fixture(tabs = false); state = state.copy(showBufferList = false)
        compose.setContent { Chat(phone = true) }
        compose.onNodeWithTag("general-menu-button").performClick()
        compose.onNodeWithText(context.getString(R.string.menu_channel_list)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.menu_settings)).assertIsDisplayed()
        for (id in listOf(R.string.menu_file_transfers, R.string.menu_search_logs,
            R.string.menu_conversation_settings, R.string.menu_edit_metadata,
            R.string.menu_about, R.string.menu_float)) {
            compose.onNodeWithText(context.getString(id)).assertDoesNotExist()
        }
        compose.onNodeWithText("Secure Chat").assertDoesNotExist()
    }

    @Test fun nickColorsRemainDistinctWithACustomThemeAndRefreshWhenMetadataChanges() {
        fixture(tabs = false)
        val key = "$first::#demo"
        state = state.copy(showBufferList = false, showNickList = true,
            nicklists = mapOf(key to listOf("@Alice")),
            buffers = mapOf(key to UiBuffer("#demo", log = BufferLog(messages = persistentListOf(
                UiMessage(1, System.currentTimeMillis(), "Alice", "mensaje de prueba"))))),
            connections = mapOf(first to NetConnState(myNick = "Own")),
            settings = state.settings.copy(colorizeNicks = true))
        val theme = PersonalTheme(lightColors = mapOf("CHAT" to 0xffffffff.toInt(), "NICKLIST" to 0xff000000.toInt(), "NICK" to 0xff123456.toInt()),
            darkColors = mapOf("CHAT" to 0xffffffff.toInt(), "NICKLIST" to 0xff000000.toInt(), "NICK" to 0xff123456.toInt()))
        val configuration = Configuration(context.resources.configuration).apply { screenWidthDp = 900; orientation = Configuration.ORIENTATION_LANDSCAPE }
        compose.setContent { CompositionLocalProvider(LocalConfiguration provides configuration) { HexDroidIRCTheme(personalTheme = theme) {
            ChatScreen(state, onSelectBuffer = {}, onSend = {}, onDisconnect = {}, onReconnect = {}, onExit = {},
                onToggleBufferList = {}, onToggleNickList = {}, onToggleChannelsOnly = {}, onWhois = {},
                onIgnoreNick = { _, _ -> }, onUnignoreNick = { _, _ -> }, onIgnoreNotifications = { _, _ -> },
                onUnignoreNotifications = { _, _ -> }, onRefreshNicklist = {}, onRefreshChannelModes = {},
                onOpenList = {}, onOpenSettings = {}, onOpenNetworks = {}, onOpenTransfers = {}, onSysInfo = {},
                onAbout = {}, onUpdateSettings = {})
        } } }
        val nicklistLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("@Alice", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nicklistLayouts) }
        assertEquals(com.boxlabs.hexdroid.ui.NickColors.nicklistColors(listOf("alice"), 0f).single(), nicklistLayouts.single().layoutInput.style.color)
        fun nickColor(): Color {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("mensaje de prueba", substring = true, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val text = layouts.single().layoutInput.text
            val start = text.text.indexOf("Alice")
            return text.spanStyles.lastOrNull { it.start <= start && it.end >= start + 5 && it.item.color != Color.Unspecified }
                ?.item?.color ?: Color.Unspecified
        }
        assertEquals(com.boxlabs.hexdroid.ui.NickColors.colorForNick("alice", 1f), nickColor())
        compose.runOnIdle { state = state.copy(connections = state.connections + (first to state.connections.getValue(first).copy(nickColors = mapOf("alice" to "0055CC")))) }
        assertEquals(Color(0xff0055cc), nickColor())
        compose.runOnIdle { state = state.copy(connections = state.connections + (first to state.connections.getValue(first).copy(nickColors = mapOf("alice" to "AA0055")))) }
        assertEquals(Color(0xffaa0055), nickColor())
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(colorizeNicks = false)) }
        assertEquals(Color.Unspecified, nickColor())
    }
}
