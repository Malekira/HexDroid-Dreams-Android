package com.boxlabs.hexdroid

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ConversationWindowsAndroidTest {
    @Test fun mentionsAggregateOnceWithOriginAndDoNotMixNetworksOrMirrorReplayAndOwnMessages() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = ViewModelStore()
        val a = "mention-a-" + System.nanoTime(); val b = "mention-b-" + System.nanoTime()
        lateinit var vm: IrcViewModel
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        main { vm = IrcViewModel(SettingsRepository(context), context); store.put("test", vm) }
        try {
            withTimeout(10000) { while (!vm.state.value.settingsLoaded) delay(25) }
            main {
                vm._state.update { it.copy(
                    settings = it.settings.copy(loggingEnabled = false, highlightOnNick = true, extraHighlightWords = emptyList()),
                    connections = mapOf(a to NetConnState(myNick = "MyNick"), b to NetConnState(myNick = "OtherNick")),
                    selectedBuffer = "$a::#unrelated", screen = AppScreen.CHAT) }
                for (key in listOf("$a::#room", "$a::Alice", "$b::#room", "$a::$MENTIONS_BUFFER", "$b::$MENTIONS_BUFFER")) {
                    vm.scrollbackRequested.add(key); vm.ensureBuffer(key)
                }
                vm.append("$a::#room", "Alice", "Hola MyNick", msgId = "live-1", doNotify = false)
                vm.append("$a::#room", "Alice", "Hola MyNick", msgId = "live-1", doNotify = false)
                vm.append("$a::Alice", "Alice", "Hola sin mención", isPrivate = true, isHighlight = true, doNotify = false)
                vm.append("$a::Alice", "Alice", "MyNick, ven al privado", isPrivate = true, doNotify = false)
                vm.append("$a::#room", "MyNick", "MyNick habla", doNotify = false)
                vm.append("$a::#room", "Bob", "MyNick antiguo", isHistory = true, timeMs = 1L, doNotify = false)
                vm.append("$b::#room", "Alice", "Hola MyNick", doNotify = false)
                vm.append("$b::#room", "Bob", "Hola OtherNick", doNotify = false)
                assertEquals(listOf("[Sala #room] Hola MyNick", "[PV Alice] MyNick, ven al privado"),
                    vm.state.value.buffers.getValue("$a::$MENTIONS_BUFFER").messages.map { it.text })
                assertEquals(listOf("Alice", "Alice"),
                    vm.state.value.buffers.getValue("$a::$MENTIONS_BUFFER").messages.map { it.from })
                assertEquals(listOf("[Sala #room] Hola OtherNick"),
                    vm.state.value.buffers.getValue("$b::$MENTIONS_BUFFER").messages.map { it.text })
                assertEquals("$a::#unrelated", vm.state.value.selectedBuffer)
                assertTrue(vm.isPseudoBuffer(MENTIONS_BUFFER)); assertTrue(vm.isPseudoBuffer(COMMANDS_BUFFER))
                vm.sendTypingNow("$a::$MENTIONS_BUFFER", "active")
                assertFalse("$a::$MENTIONS_BUFFER" in vm.typingSentAtMs)
            }
        } finally { main { store.clear() } }
    }

    @Test fun numericAndBotNoticeResponsesGoToSystemWithoutChangingTheActiveConversation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = ViewModelStore(); val net = "system-" + System.nanoTime()
        lateinit var vm: IrcViewModel
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        main { vm = IrcViewModel(SettingsRepository(context), context); store.put("test", vm) }
        try {
            withTimeout(10000) { while (!vm.state.value.settingsLoaded) delay(25) }
            main {
                vm._state.update { it.copy(settings = it.settings.copy(loggingEnabled = false),
                    selectedBuffer = "$net::#room", connections = mapOf(net to NetConnState(myNick = "Own"))) }
                vm.handleServerEvent(net, IrcEvent.ServerText("Respuesta de comando", code = "351", bufferName = "#room"))
                vm.handleMessageEvent(net, IrcEvent.Notice(from = "NickServ", target = "Own", text = "Respuesta de servicio", isPrivate = true))
                vm.handleMessageEvent(net, IrcEvent.Notice(from = "CustomBot", target = "Own", text = "Respuesta de bot", fromBot = true, isPrivate = true))
                assertEquals(listOf("Respuesta de comando"), vm.state.value.buffers.getValue("$net::$COMMANDS_BUFFER").messages.map { it.text })
                val notices = vm.state.value.buffers.getValue("$net::$NOTICES_BUFFER").messages
                assertEquals(2, notices.size); assertTrue(notices.all { "Respuesta" in it.text })
                assertTrue("custombot" in vm.state.value.connections.getValue(net).botNicks)
                assertEquals("$net::#room", vm.state.value.selectedBuffer)
                assertTrue(vm.state.value.buffers["$net::#room"]?.messages.orEmpty().isEmpty())
            }
        } finally { main { store.clear() } }
    }

    @Test fun nickCompletionConfigurationSurvivesRepositoryReloadAndBackupJson() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = SettingsRepository(context); val previous = repo.settingsFlow.first()
        val style = NickCompletionStyle(startTemplate = "«{nick}»: ", middleTemplate = "[{nick}] ",
            colors = "#FF3399,#33CCFF", bold = true, italic = true, underline = true)
        try {
            repo.updateSettings { it.copy(nickCompletion = style) }
            assertEquals(style, SettingsRepository(context).settingsFlow.first().nickCompletion)
            assertEquals(style, NickCompletionStyle.fromJson(style.toJson()))
            assertEquals(NickCompletionStyle(), NickCompletionStyle.fromJson(null))
        } finally { repo.updateSettings { previous } }
    }
}
