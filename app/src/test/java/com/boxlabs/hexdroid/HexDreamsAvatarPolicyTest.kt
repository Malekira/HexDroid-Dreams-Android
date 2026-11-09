package com.boxlabs.hexdroid

import java.util.Base64
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class HexDreamsAvatarPolicyTest {
    private fun encoded(value:String)=Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    @Test fun gecosSupportsExtraFieldsButDoesNotInventARealName() {
        val value=encoded("avatar|M|28|/photos/ñ.png|extra")
        assertEquals("https://chat-avatars.chateagratis.net/photos/ñ.png",webPhotoFromGecos(value)!!.url)
        assertEquals("",displayRealname(value))
        assertEquals("María de la Cruz",displayRealname("María de la Cruz"))
        assertNull(webPhotoFromGecos(encoded("only|three|fields")))
        assertNull(webPhotoFromGecos("wyg=")) // malformed UTF-8
        for(photo in listOf("#","","javascript:x","//evil.example/x","https://u:p@images.chateagratis.net/x"))
            assertNull(webPhotoFromGecos(encoded("#|#|#|$photo"))!!.url)
    }
    @Test fun metadataPriorityAndRemovalAreIndependentOfArrivalOrder() {
        fun resolve(primary:String?,known:Boolean,alias:String?,aliasKnown:Boolean,explicit:String?=null)=
            profileAvatarSource(explicit,primary,"irc.example",null,"Nick",legacyUrl="https://chat-avatars.chateagratis.net/legacy.png",legacyKnown=true,
                metadataKnown=known,aliasUrl=alias,aliasKnown=aliasKnown)
        assertEquals("https://example.org/manual.png",resolve("",true,"https://example.org/alias.png",true,"https://example.org/manual.png"))
        assertNull(resolve("",true,"https://example.org/alias.png",true))
        assertEquals("https://example.org/avatar.png",resolve("https://example.org/avatar.png",true,"https://example.org/alias.png",true))
        assertEquals("https://example.org/alias.png",resolve(null,false,"https://example.org/alias.png",true))
        assertEquals("https://chat-avatars.chateagratis.net/legacy.png",resolve(null,false,null,false))
        assertNull(resolve(null,false,"",true))
    }
    @Test fun templatesEncodeNickComponentsWithoutLosingCaseOrRangePrefixes() {
        for(token in listOf("{nick}","%nick%","%s"))
            assertEquals("https://photos.example/U%5BN%5D%7C%C3%B1.png",templateAvatarUrl("https://photos.example/$token.png","@+U[N]|ñ"))
        assertEquals("https://photos.example/u%5Bn%5D.png",templateAvatarUrl("https://photos.example/{nickLower}.png","%U[N]"))
        assertEquals("https://chat-avatars.chateagratis.net/avatar/Chat/Nick.png",templateAvatarUrl(DEFAULT_AVATAR_TEMPLATE,"Nick"))
        for(value in listOf("","http://photos.example/{nick}","https://u:p@photos.example/{nick}","https://photos.example/no-placeholder")) assertNull(templateAvatarUrl(value,"Nick"))
        assertEquals("a{b}~",ircCasefold("A[B]^","rfc1459"))
        assertEquals("a[b]^",ircCasefold("A[B]^","ascii"))
    }
    @Test fun whoxFieldsFollowWireOrderAndRejectMismatchedTokens() {
        val spec=WhoxPhotoFields.fromLine("WHO #room %nrt,77")!!
        assertEquals("tnr",spec.fields)
        assertEquals("Name with spaces",spec.values(listOf("77","Nick","Name with spaces"))!!['r'])
        assertNull(spec.values(listOf("42","Nick","Name")))
        assertNull(spec.values(listOf("77","Nick")))
    }
    @Test fun automaticDownloadsOnlyUseOfficialOrConfiguredHostsAndPublicAddresses() {
        assertTrue(automaticAvatarAllowed("https://images.chateagratis.net/x",null))
        assertTrue(automaticAvatarAllowed("https://photos.example/x","photos.example"))
        assertFalse(automaticAvatarAllowed("https://photos.example.evil/x","photos.example"))
        for(ip in listOf("127.0.0.1","10.1.2.3","172.16.1.2","192.168.1.1","169.254.1.2","100.64.0.1","0.0.0.0","::1","fc00::1","fe80::1","::ffff:192.168.0.1"))
            assertFalse(ip,publicAvatarAddress(InetAddress.getByName(ip)))
        for(url in listOf("https://127.0.0.1/a","https://[::1]/a","https://192.168.1.2/a")) assertFalse(publicAvatarLiteral(url))
        assertTrue(publicAvatarLiteral("https://8.8.8.8/a"))
        assertTrue(publicAvatarAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(publicAvatarAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
