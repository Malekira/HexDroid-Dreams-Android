/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class Dreams109AvatarTest {
    @Test fun automaticAvatarDetectionMatchesActualDomainsOrServerIdentityWithoutLookalikes() {
        for (host in listOf("irc.chatzona.org", "CHATZONA.ORG.", "irc.chateagratis.net")) assertTrue(isChatZonaNetwork(host))
        for (host in listOf("irc.other.invalid", "chatzona.org.evil.invalid", "evilchatzona.org")) assertFalse(isChatZonaNetwork(host))
        assertTrue(isChatZonaNetwork("bouncer.example.invalid", "ChatZona"))
        assertFalse(isChatZonaNetwork("bouncer.example.invalid", "Otra ChatZona"))
        assertNull(chatZonaAvatarUrl("irc.other.invalid", null, "DemoNick01"))
    }

    @Test fun nickPrefixesAndSpecialCharactersAreEncodedAsOneSafeUrlSegment() {
        assertEquals("https://chat-avatars.chateagratis.net/avatar/Chat/DemoNick01", chatZonaAvatarUrl("irc.chatzona.org", null, "@+DemoNick01"))
        assertEquals("https://chat-avatars.chateagratis.net/avatar/Chat/Demo%5BNick%5D", chatZonaAvatarUrl("irc.chatzona.org", null, "Demo[Nick]"))
        for (nick in listOf("", "..", "#demo", "*server*", "bad\r\nname", "two words")) assertNull(chatZonaAvatarUrl("irc.chatzona.org", null, nick))
    }

    @Test fun nickChangesProduceANewAvatarUrlWithoutReusingTheOldNick() {
        assertNotEquals(chatZonaAvatarUrl("irc.chatzona.org", null, "DemoNick01"), chatZonaAvatarUrl("irc.chatzona.org", null, "DemoNick02"))
    }

    @Test fun metadataAvatarSubscriptionAndPrivateGetUseTheRealIrcProtocol() {
        val welcome = DEFAULT_WELCOME.map { if (" 005 " in it) it.replace(" :are supported", " NETWORK=ChatZona :are supported") else it }
        val h = IrcTestHarness("echo-message draft/metadata-2", welcome)
        try {
            h.server.expect { it.startsWith("METADATA * SUB ") && "avatar" in it }
            assertEquals("ChatZona", h.await<IrcEvent.ISupport> { it.networkName != null }.networkName)
            h.run { h.client.sendMetadata("DemoNick01", "GET", "avatar") }
            h.server.expect { it == "METADATA DemoNick01 GET avatar" }
            val url = "https://images.example.invalid/avatar.jpg"
            h.server.send(":srv METADATA DemoNick01 avatar * :$url")
            assertEquals(url, h.await<IrcEvent.MetadataChanged> { it.target == "DemoNick01" && it.key == "avatar" }.value)
        } finally { h.close() }
    }
}
