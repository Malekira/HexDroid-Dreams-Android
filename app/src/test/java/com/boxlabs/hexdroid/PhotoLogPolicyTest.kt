package com.boxlabs.hexdroid

import java.io.StringReader
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PhotoLogPolicyTest {
    private fun gecos(photo: String) = Base64.getEncoder().encodeToString("#|#|#|$photo".toByteArray())
    @Test fun webPhotoUsesFourthFieldAndCanonicalHost() {
        assertEquals("https://chat-avatars.chateagratis.net/photos/new.jpg", webPhotoFromGecos(gecos("http://images.chateagratis.net/photos/new.jpg"))!!.url)
        assertEquals("https://chat-avatars.chateagratis.net/photos/new.jpg", webPhotoFromGecos(gecos("/photos/new.jpg"))!!.url)
        assertNull(webPhotoFromGecos("ordinary real name"))
        assertNull(webPhotoFromGecos(gecos("//evil.example/photo"))!!.url)
        assertNull(webPhotoFromGecos(gecos("https://evil.example/photo"))!!.url)
        assertNull(webPhotoFromGecos(gecos("#"))!!.url)
    }
    @Test fun metadataRemovalSuppressesLegacyAndNickFallback() {
        assertNull(profileAvatarSource(null, "", "irc.chatzona.org", null, "Nick", legacyUrl="https://chat-avatars.chateagratis.net/old.jpg", metadataKnown=true))
        assertNull(profileAvatarSource(null, null, "irc.chatzona.org", null, "Nick", legacyKnown=true))
        assertEquals("https://example.org/manual.jpg", profileAvatarSource("https://example.org/manual.jpg", "", "irc.chatzona.org", null, "Nick", metadataKnown=true))
    }
    @Test fun officialCacheVersionsRefreshWithoutChangingSignedThirdPartyUrls() {
        val official="https://chat-avatars.chateagratis.net/a.jpg?size=2&v=old"
        assertEquals(official, avatarRequestUrl(official, 123456, false))
        assertEquals("https://chat-avatars.chateagratis.net/a.jpg?size=2&v=123456", avatarRequestUrl(official, 123456, true))
        assertEquals("https://chat-avatars.chateagratis.net/a.jpg?v=2", avatarRequestUrl("https://images.chateagratis.net/a.jpg", 120000, false))
        val signed="https://example.org/a.jpg?sig=a%2Fb&v=original"
        assertEquals(signed, avatarRequestUrl(signed, 123456, true))
    }
    @Test fun logSearchRequiresEveryWordAndPreservesOriginalLineOffsets() {
        val log="uno\nALICIA llegó a sala\notro\nalicia SALA dos\nfinal"
        val first=readLogPage(StringReader(log), "alicia sala", 0, 1)
        assertEquals(listOf("ALICIA llegó a sala"), first.lines)
        assertEquals(3L, first.next)
        val second=readLogPage(StringReader(log), "alicia sala", first.next!!, 1)
        assertEquals(listOf("alicia SALA dos"), second.lines)
        assertNull(second.next)
        assertTrue(readLogPage(StringReader(log), "inexistente", 0).lines.isEmpty())
    }
    @Test fun logPagingClosesInputAndLimitsResults() {
        var closed=false
        val reader=object:StringReader((1..1000).joinToString("\n")) { override fun close() { closed=true;super.close() } }
        val page=readLogPage(reader, "", 0, 100)
        assertTrue(closed);assertEquals(100,page.lines.size);assertEquals(100L,page.next)
    }
}
