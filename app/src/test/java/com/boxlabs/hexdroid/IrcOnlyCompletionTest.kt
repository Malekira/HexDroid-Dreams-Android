package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class IrcOnlyCompletionTest {
    @Test fun onlyIrcIndexesAreUsedAndTheDecoratedNicknameIsPreserved() {
        val style = NickCompletionStyle(startTemplate = "\u000304_- \u000309^\u0060{nick}\u0060^\u000313 💜✨ ",
            colors = "12,6,4,7,8,9", bold = true)
        val result = style.render("TinKeR", true, false, false)
        assertEquals("_- ^\u0060TinKeR\u0060^ 💜✨ ", stripIrcFormatting(result))
        assertTrue(result.contains("\u000312T\u000306i\u000304n\u000307K\u000308e\u000309R"))
        assertFalse(result.contains('\u0004'))
        assertEquals(listOf(0,98), NickCompletionStyle(colors = "0,98,99,-1,#FF0000").palette())
    }
    @Test fun templatesRejectInjectionAndHexColorControlsWhileKeepingIrcFormatting() {
        val raw = "\r\n\u0004FF0000\u0002\u000303{nick}\u0000"
        val clean = cleanCompletionTemplate(raw)
        assertFalse(clean.contains('\n')); assertFalse(clean.contains('\r')); assertFalse(clean.contains('\u0004'))
        assertTrue(clean.contains("\u000303")); assertTrue(clean.contains('\u0002'))
    }
    @Test fun previousHexPaletteMigratesToAnIrcPalette() {
        assertEquals("1,0",migrateCompletionPalette("000000,FFFFFF"))
        assertEquals("4,9,12", migrateCompletionPalette("#FF0000,#00FC00,#0000FC"))
        val migrated = NickCompletionStyle(colors = migrateCompletionPalette("FF3399,33CCFF"))
        assertEquals(2,migrated.palette().size)
        assertFalse(migrated.render("Alice", true, false, false).contains('\u0004'))
    }
    @Test fun fileOffersAreConsumedButChatRemainsAvailableThroughTheRealParser() {
        val h = IrcTestHarness("echo-message message-tags")
        try {
            for (verb in listOf("SEND", "SSEND", "TSEND", "RSEND", "RESUME", "ACCEPT")) {
                h.server.send(":bob!u@h PRIVMSG me :\u0001DCC " + verb + " test.bin 2130706433 9000 10\u0001")
            }
            h.sync("offers-consumed")
            assertFalse(h.events.filterIsInstance<IrcEvent.CtcpRequest>().any { it.command == "DCC" })
            assertFalse(h.events.filterIsInstance<IrcEvent.ChatMessage>().any { "test.bin" in it.text })
            h.server.send(":bob!u@h PRIVMSG me :\u0001DCC CHAT chat 2130706433 9000\u0001")
            assertEquals("bob",h.await<IrcEvent.DccChatOfferEvent> { true }.offer.from)
        } finally { h.close() }
    }
}
