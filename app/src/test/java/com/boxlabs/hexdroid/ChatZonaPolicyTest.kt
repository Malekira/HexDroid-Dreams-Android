package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class ChatZonaPolicyTest {
    @Test fun onlyChatZonaHostsUseThisLoginFlow() {
        assertTrue(isChatZonaHost(" IRC.ChatZona.org. "))
        assertTrue(isChatZonaHost("lima.chatzona.org"))
        assertFalse(isChatZonaHost("chatzona.org.evil.example"))
        assertFalse(isChatZonaHost("notchatzona.org"))
    }
    @Test fun numberedNicksKeepCaseAndRespectAdvertisedLength() {
        assertEquals("MiNick01", chatZonaFallbackNick("MiNick", 1))
        assertEquals("MiNick02", chatZonaFallbackNick("MiNick", 2))
        assertEquals("MiNick101", chatZonaFallbackNick("MiNick", 101))
        assertEquals("NickLo01", chatZonaFallbackNick("NickLargo", 1, 8))
        assertEquals("[Nick]01", chatZonaFallbackNick("[Nick]", 1))
    }
    @Test fun credentialsAreOptionalButCannotInjectCommands() {
        assertTrue(validChatZonaLogin("", ""))
        assertTrue(validChatZonaLogin("MiNick", "s3cret!"))
        assertFalse(validChatZonaLogin("MiNick", ""))
        assertFalse(validChatZonaLogin("", "secret"))
        assertFalse(validChatZonaLogin("Mi Nick", "secret"))
        assertFalse(validChatZonaLogin("MiNick", "secret\r\nJOIN #room"))
    }
    @Test fun modeCommandsUseTheCurrentNickAndKeepCaseSensitiveModes() {
        assertEquals(listOf("MODE MiNick01 +iIgR", "PRIVMSG Nick :set keepmodes on", "MODE MiNick01 -c"),
            chatZonaModeCommands("MiNick01", "iIgR", true, true))
        assertEquals(listOf("MODE MiNick +i-IgR", "PRIVMSG Nick :set keepmodes off"),
            chatZonaModeCommands("MiNick", "i", false, false))
    }
    @Test fun loginCredentialsAreHiddenInEveryRawForm() {
        for (line in listOf("LOGIN MiNick secret42", "@label=one LOGIN MiNick secret42",
            "PRIVMSG Nick :LOGIN MiNick secret42", "MSG Nick :IDENTIFY secret42")) {
            assertFalse(redactRawLine(line).contains("secret42"))
            assertTrue(redactRawLine(line).contains("<hidden>"))
        }
        assertEquals("NICK MiNick", redactRawLine("NICK MiNick"))
    }
    @Test fun listColorsRepresentRolesInsteadOfIndividualNickHashes() {
        val connection = NetConnState(prefixModes = "Yov", prefixSymbols = "!@+",
            botNicks = setOf("Robot"), operNicks = setOf("GlobalOper"))
        assertEquals(NickListRole.NORMAL, nickListRole("Alice", connection))
        assertEquals(NickListRole.NORMAL, nickListRole("Bob", connection))
        assertEquals(NickListRole.OP, nickListRole("@Alice", connection))
        assertEquals(NickListRole.VOICE, nickListRole("+Alice", connection))
        assertEquals(NickListRole.ADMIN, nickListRole("!Alice", connection))
        assertEquals(NickListRole.IRC_OPER, nickListRole("GlobalOper", connection))
        assertEquals(NickListRole.BOT, nickListRole("Robot", connection))
        assertEquals(NickListRole.BOT, nickListRole("Nick", connection))
        assertEquals(NickListRole.NORMAL, nickListRole("GlobalOper", NetConnState()))
        assertTrue(isSystemConversation("Chan", emptySet(), "rfc1459"))
    }
    @Test fun whoFlagsTeachGlobalOperatorStatusWithoutWhois() {
        val h = IrcTestHarness("echo-message")
        try {
            h.server.send(":srv 352 me #room user host srv Alice H* :0 Alice Real")
            assertEquals(true, h.await<IrcEvent.WhoxReply> { it.nick == "Alice" }.isOper)
            h.server.send(":srv 352 me #room user host srv Bob H :0 Bob Real")
            assertEquals(false, h.await<IrcEvent.WhoxReply> { it.nick == "Bob" }.isOper)
        } finally { h.close() }
    }
}
