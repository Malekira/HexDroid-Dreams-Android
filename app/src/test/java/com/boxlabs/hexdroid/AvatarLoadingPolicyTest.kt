package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class AvatarLoadingPolicyTest {
    @Test fun avatarControlIsIndependentAndWifiAndProxyControlsRemainEffective() {
        assertTrue(automaticAvatarDownloadsAllowed(true,false,false,false))
        assertFalse(automaticAvatarDownloadsAllowed(false,false,true,false))
        assertFalse(automaticAvatarDownloadsAllowed(true,true,false,false))
        assertTrue(automaticAvatarDownloadsAllowed(true,true,true,false))
        assertFalse(automaticAvatarDownloadsAllowed(true,false,true,true))
    }
    @Test fun malformedOrUnapprovedMetadataDoesNotHideAnEligibleWebPhotoButRemovalDoes() {
        val photo="https://chat-avatars.chateagratis.net/web.png"
        fun source(metadata:String?,known:Boolean)=profileAvatarSource(null,metadata,"irc.chatzona.org",null,"Nick",legacyUrl=photo,legacyKnown=true,metadataKnown=known,eligible={ automaticAvatarAllowed(it,null) })
        assertEquals(photo,source("javascript:invalid",true))
        assertEquals(photo,source("https://unapproved.example/photo.png",true))
        assertNull(source("",true));assertNull(source(null,true))
    }
    @Test fun redirectLocationsPreserveTheActualHttpsDestinationAndSignedQuery() {
        val source="https://chat-avatars.chateagratis.net/avatar/Chat/Nick.png?v=100"
        val target="https://images.chateagratis.net/photos/Nick.png?sig=a%2Fb&v=remote"
        assertEquals(target,avatarRedirectTarget(source,target))
        assertEquals("https://chat-avatars.chateagratis.net/photos/Nick.png",avatarRedirectTarget(source,"/photos/Nick.png"))
        assertNull(avatarRedirectTarget(source,"http://images.chateagratis.net/Nick.png"))
        assertNull(avatarRedirectTarget(source,"https://user:pass@images.chateagratis.net/Nick.png"))
    }
}
