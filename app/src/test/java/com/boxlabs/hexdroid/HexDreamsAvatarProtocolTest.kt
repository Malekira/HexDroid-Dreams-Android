package com.boxlabs.hexdroid

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class HexDreamsAvatarProtocolTest {
    @Test fun whoWhoxWhoisExtendedJoinSetnameAnd761PreserveTheirFinalParameters() {
        val h=IrcTestHarness("echo-message extended-join setname draft/metadata-2")
        val gecos=Base64.getEncoder().encodeToString("#|#|#|/fresh.png|extra".toByteArray())
        try {
            h.server.expect { it.startsWith("METADATA * SUB ") && "avatar" in it && "avatar-url" in it }
            h.server.expect { it=="METADATA * GET avatar avatar-url" }
            h.server.send(":srv 352 me #room u host srv Alice H :0 $gecos")
            assertEquals(gecos,h.await<IrcEvent.WhoxReply> { it.nick=="Alice" }.realname)
            h.server.send(":srv 354 me 42 u host srv Bob H 0 :$gecos")
            assertEquals(gecos,h.await<IrcEvent.WhoxReply> { it.nick=="Bob" }.realname)
            h.server.send(":srv 354 me 42 u host srv Carol H 0 :Normal name with spaces")
            assertEquals("Normal name with spaces",h.await<IrcEvent.WhoxReply> { it.nick=="Carol" }.realname)
            h.server.send(":srv 311 me Alice u host * :$gecos")
            val whois=h.await<IrcEvent.ServerText> { it.code=="311" }
            assertEquals(gecos,whois.whoisTrailing)
            assertFalse(whois.text.contains(gecos))
            h.server.send(":Dave!u@host JOIN #room account :$gecos")
            assertEquals(gecos,h.await<IrcEvent.Joined> { it.nick=="Dave" }.realname)
            h.server.send(":Dave!u@host SETNAME :Normal name with spaces")
            assertEquals("Normal name with spaces",h.await<IrcEvent.Setname> { it.nick=="Dave" }.newRealname)
            h.server.send(":srv 761 me Alice avatar * :https://chat-avatars.chateagratis.net/new.png")
            assertEquals("https://chat-avatars.chateagratis.net/new.png",h.await<IrcEvent.MetadataChanged> { it.target=="Alice" && it.key=="avatar" }.value)
            h.server.send(":srv 761 me Alice avatar * :")
            h.sync("photos-done")
            assertTrue(h.events.filterIsInstance<IrcEvent.MetadataChanged>().any { it.target=="Alice" && it.key=="avatar" && it.value.isNullOrEmpty() })
            assertFalse(h.server.pending().any { it.startsWith("WHO ") || it.startsWith("WHOIS ") })
            h.server.send(":me!u@host JOIN #room account :My normal name")
            h.server.expect { it=="METADATA #room SYNC" }
        } finally { h.close() }
    }
    @Test fun serverWithoutMetadataCapabilityStillEmitsGecosAndSendsNoMetadataCommands() {
        val h=IrcTestHarness("echo-message")
        try {
            h.server.send(":srv 352 me #room u host srv Alice H :0 Real name with spaces")
            assertEquals("Real name with spaces",h.await<IrcEvent.WhoxReply> { it.nick=="Alice" }.realname)
            h.sync("no-metadata")
            assertFalse(h.server.pending().any { it.startsWith("METADATA ") })
        } finally { h.close() }
    }
}
