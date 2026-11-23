package com.boxlabs.hexdroid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.script.HexDroidScriptHost
import com.boxlabs.hexdroid.script.ScriptHttpResponse
import com.boxlabs.hexdroid.script.ScriptUploadRequest
import com.boxlabs.hexdroid.ui.NickCompletionSettings
import com.boxlabs.hexdroid.ui.colorCompletionSelection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IrcCompletionEditorAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ircSwatchesColorTheNickAndDecorationsAndUpdateBothPreviews() {
        var style by mutableStateOf(NickCompletionStyle(startTemplate = "_- {nick} 💜✨ "))
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NickCompletionSettings(style, { style = it }, "TinKeR")
            }
        } }
        compose.onNodeWithTag("completion-settings").performClick()
        compose.onNodeWithTag("completion-start").performSemanticsAction(SemanticsActions.SetSelection) { it(0,2,false) }
        compose.onNodeWithTag("completion-color-selection").performScrollTo().performClick()
        compose.onNodeWithTag("completion-irc-color-4").performClick()
        compose.runOnIdle { assertTrue(style.startTemplate.startsWith("\u000304_-")) }
        for (code in listOf(12,9)) {
            compose.onNodeWithTag("completion-add-color").performScrollTo().performClick()
            compose.onNodeWithTag("completion-irc-color-" + code).performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(12,9),style.palette())
            val result = style.render("TinKeR",true,false,false)
            assertEquals("_- TinKeR 💜✨ ",stripIrcFormatting(result))
            assertTrue(result.contains("\u000312T\u000309i"))
            assertFalse(result.contains('\u0004'))
        }
        compose.onAllNodesWithTag("nick-completion-preview")[0].performScrollTo().assertTextEquals("_- TinKeR 💜✨ ")
        compose.onAllNodesWithTag("nick-completion-preview")[1].performScrollTo().assertTextEquals("_- TinKeR 💜✨ ")
        compose.onNodeWithText("Colores HEX separados por comas").assertDoesNotExist()
    }

    @Test fun selectingPartOfThePlaceholderKeepsItIntactAndOnlyInsertsIrcColors() {
        val raw = "✨ {nick} 💜"
        val colored = colorCompletionSelection(TextFieldValue(raw, TextRange(4,7)),4)
        assertTrue(colored.text.contains("{nick}"))
        val rendered = NickCompletionStyle(startTemplate = colored.text).render("Alice",true,false,false)
        assertEquals("✨ Alice 💜",stripIrcFormatting(rendered))
        assertEquals(1,Regex("Alice").findAll(stripIrcFormatting(rendered)).count())
        assertFalse(rendered.contains('\u0004'))
    }

    @Test fun legacyScriptsCannotPickOrUploadFilesAndFileTransferEngineIsAbsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        assertFalse(instrumentation.targetContext.assets.list("hex").orEmpty().contains("imgpaste.hex"))
        lateinit var vm: IrcViewModel
        lateinit var host: HexDroidScriptHost
        instrumentation.runOnMainSync {
            vm = IrcViewModel(SettingsRepository(instrumentation.targetContext),instrumentation.targetContext)
            store.put("test",vm); host = HexDroidScriptHost(vm)
        }
        try {
            var picked = false
            host.mediaPick(null,null,"*/*","legacy",true) { picked = true; assertNull(it) }
            assertTrue(picked)
            var response: ScriptHttpResponse? = null
            host.mediaUpload(ScriptUploadRequest("https://example.invalid/upload","legacy-token")) { response = it }
            assertEquals(false,response?.ok)
            assertTrue(response?.error.orEmpty().contains("not supported"))
            assertFalse(DccManager::class.java.methods.any { it.name in setOf("sendFile","sendFileConnect","receive","receivePassive","createDccOutputStream") })
        } finally {
            host.shutdown(); instrumentation.runOnMainSync { store.clear() }
        }
    }
}
