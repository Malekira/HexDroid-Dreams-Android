/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class Dreams111AvatarTest {
    @Test fun ownPhotoUsesExplicitUrlThenMetadataThenTheActualNickOfChatZonaOnly() {
        val explicit = "https://images.example.invalid/own.png"
        val metadata = "https://images.example.invalid/server.png"
        assertEquals(explicit, profileAvatarSource(explicit, metadata, "irc.chatzona.org", null, "DemoNick01"))
        assertEquals(metadata, profileAvatarSource("", metadata, "irc.chatzona.org", null, "DemoNick01"))
        assertEquals("https://chat-avatars.chateagratis.net/avatar/Chat/DemoNick02", profileAvatarSource(null, null, "irc.chatzona.org", null, "@DemoNick02"))
        assertNull(profileAvatarSource(null, null, "irc.other.invalid", null, "DemoNick01"))
        assertEquals(metadata, profileAvatarSource(null, metadata, "irc.other.invalid", null, "DemoNick01"))
    }
    @Test fun invalidOrEmptyMetadataNeverBecomesADownloadTargetAndNickChangesKeepAutomaticResolutionFresh() {
        assertNull(profileAvatarSource("http://invalid.example/photo", "javascript:invalid", "irc.other.invalid", null, "DemoNick01"))
        assertNotEquals(profileAvatarSource("", "", "irc.chatzona.org", null, "DemoNick01"), profileAvatarSource("", null, "irc.chatzona.org", null, "DemoNick02"))
    }
    @Test fun anUnchangedEmptyIdentityDoesNotClearAnAvatarPublishedByTheWebClient() {
        assertNull(profileAvatarEdit(null, " "));assertEquals("", profileAvatarEdit("https://images.example.invalid/a", " "))
        assertFalse(profileAvatarChanged(null, ""));assertFalse(profileAvatarChanged("", ""))
        assertFalse(profileAvatarChanged("https://images.example.invalid/a", " https://images.example.invalid/a "))
        assertTrue(profileAvatarChanged("https://images.example.invalid/a", ""))
        assertTrue(profileAvatarChanged(null, "https://images.example.invalid/a"))
    }
    @Test fun photoBodyKeepsEveryByteUpToTheLimitWithOrWithoutAContentLength() {
        val bytes = ByteArray(AVATAR_MAX_BYTES) { (it % 251).toByte() }
        assertArrayEquals(bytes, readAvatarBytes(ByteArrayInputStream(bytes)))
        assertArrayEquals(bytes, readAvatarBytes(ByteArrayInputStream(bytes), bytes.size.toLong()))
    }
    @Test fun oversizeContentLengthIsRejectedBeforeReadingTheBody() {
        val input=object:InputStream(){override fun read():Int=error("Must not read an oversized response")}
        assertThrows(AvatarTooLargeException::class.java){readAvatarBytes(input,AVATAR_MAX_BYTES+1L)}
    }
    @Test fun chunkedOversizeBodyStopsAfterTheFirstExcessByte() {
        var consumed=0
        val input=object:InputStream(){
            override fun read():Int { consumed++;return 1 }
            override fun read(b:ByteArray,off:Int,len:Int):Int { consumed+=len;b.fill(1,off,off+len);return len }
        }
        assertThrows(AvatarTooLargeException::class.java){readAvatarBytes(input)}
        assertEquals(AVATAR_MAX_BYTES+1,consumed)
    }
}
