package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class NickCompletionCustomizationTest {
    @Test fun defaultsAndMiddleOfSentenceStayCompatible() {
        val first = advanceNickCompletion("Ali", 3, listOf("@Alice"), null)!!
        assertEquals("Alice ", first.text)
        val text = "Hola Ali después"
        val token = nickToken(text, 8)!!
        assertEquals("Hola Alice después", completeNickToken(text, token, "Alice").first)
    }
    @Test fun customTemplatesColorsAndStylesDoNotColorTheRemainingMessage() {
        val style = NickCompletionStyle(startTemplate = "«{nick}» ", middleTemplate = "[{nick}] ",
            colors = "13,11", bold = true, italic = true, underline = true)
        val result = advanceNickCompletion("Ali resto", 3, listOf("@Alice"), null, style = style)!!
        assertEquals("«Alice» resto", stripIrcFormatting(result.text))
        assertTrue(result.text.contains("\u000313A\u000311l"))
        assertTrue(result.text.contains('\u0002'))
        assertTrue(result.text.contains('\u001d'))
        assertTrue(result.text.contains('\u001f'))
        assertEquals("«Alice» resto", stripIrcFormatting(result.text))
    }
    @Test fun cyclingBothDirectionsWorksWithColoredTemplatesAndChangedMembership() {
        val style = NickCompletionStyle(startTemplate = "<{nick}> ", colors = "4,9")
        val first = advanceNickCompletion("Al", 2, listOf("@Alice", "+Alfred"), null, style = style)!!
        val second = advanceNickCompletion(first.text, first.cursor, listOf("@Alice", "+Alfred"), first, style = style)!!
        assertEquals("<Alice> ", stripIrcFormatting(second.text))
        val back = advanceNickCompletion(second.text, second.cursor, listOf("@Alice", "+Alfred"), second, true, style)!!
        assertEquals(first.text, back.text)
        val edited = advanceNickCompletion("Otro Al", 7, listOf("Alice"), second, style = style)!!
        assertEquals("Otro Alice ", stripIrcFormatting(edited.text))
    }
    @Test fun disabledCompletionAndInvalidPaletteAreHandledWithoutInjectingControlLines() {
        assertNull(advanceNickCompletion("Ali", 3, listOf("Alice"), null, style = NickCompletionStyle(enabled = false)))
        val style = NickCompletionStyle(startTemplate = "\r\nPRIVMSG {nick}\u0000", colors = "garbage,-1,99,04")
        assertEquals(listOf(4), style.palette())
        val result = style.render("Alice", true, true, false)
        assertEquals("PRIVMSG @Alice", stripIrcFormatting(result))
        assertFalse(result.contains('\r')); assertFalse(result.contains('\n')); assertFalse(result.contains('\u0000'))
    }
    @Test fun specialNicksAndUnicodeAreNotSplitOrRewritten() {
        val result = NickCompletionStyle(colors = "4,7").render("[Álice]😀", true, false, false)
        assertEquals("[Álice]😀 ", stripIrcFormatting(result))
        assertFalse(result.contains("\uD83D\u0004"))
    }
    @Test fun systemClassificationUsesTheNetworksCaseMappingAndNotNickSubstrings() {
        assertTrue(isSystemConversation("NickServ", emptySet(), "rfc1459"))
        assertTrue(isSystemConversation("Photo{Bot}", setOf("photo[bot]"), "rfc1459"))
        assertFalse(isSystemConversation("Photo{Bot}", setOf("photo[bot]"), "ascii"))
        assertFalse(isSystemConversation("Photo[Bot]", emptySet(), "rfc1459"))
        assertFalse(isSystemConversation("robotico", emptySet(), "rfc1459"))
        assertFalse(isSystemConversation(MENTIONS_BUFFER, emptySet(), "rfc1459"))
        assertTrue(isSystemConversation(COMMANDS_BUFFER, emptySet(), "rfc1459"))
    }
}
