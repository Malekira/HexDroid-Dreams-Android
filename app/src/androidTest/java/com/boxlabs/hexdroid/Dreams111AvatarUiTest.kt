/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.connection.ProxyType
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.*
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Dreams111AvatarUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val image get()=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun awaitTag(tag:String):SemanticsNodeInteraction {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithTag(tag)
    }

    @Test fun dreams111IdentityShowsTheAutomaticPhotoAndCanSaveItsResolvedAddress() {
        val net=SettingsRepository(context).defaultNetworks().first().copy(id="photo-identity-demo",host="irc.chatzona.org",nick="DemoNick01",avatarUrl=null,proxyType=ProxyType.NONE)
        val urls=CopyOnWriteArrayList<String>();val bitmap=image
        var saved:com.boxlabs.hexdroid.data.NetworkProfile?=null
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->urls+=url;bitmap}
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            NetworkEditScreen(UiState(settings=UiSettings(settingsOnePage=true,chatZonaAvatarsEnabled=true,imagePreviewsEnabled=true,imagePreviewsWifiOnly=false),editingNetwork=net),onCancel={},onSave={p,_,_->saved=p})
        } } }
        awaitTag("profile-avatar-preview").performScrollTo().assertIsDisplayed().assertHasClickAction()
        val expected="https://chat-avatars.chateagratis.net/avatar/Chat/DemoNick01.png"
        compose.waitUntil(5_000){urls.contains(expected)}
        compose.onNodeWithTag("profile-avatar-source").assertTextEquals(expected)
        compose.onNodeWithTag("profile-avatar-use-source").performScrollTo().performClick()
        compose.onNodeWithTag("network-save").performClick()
        compose.runOnIdle { assertEquals(expected,saved!!.avatarUrl);assertEquals(net.username,saved!!.username);assertEquals(net.realname,saved!!.realname) }
    }
    @Test fun dreams111RetryLoadsTheSamePhotoAgainAfterFailureWithoutWaitingFiveMinutes() {
        val calls=CopyOnWriteArrayList<String>();val bitmap=image
        val source="https://images.example.invalid/retry-identity.png"
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->calls+=url;if(calls.size==1)null else bitmap}
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            ProfileAvatarField(source,{},nick="DemoNick01",downloadsAllowed=true)
        } } }
        awaitTag("profile-avatar-error").assertIsDisplayed()
        compose.onNodeWithTag("profile-avatar-refresh").performClick()
        awaitTag("profile-avatar-preview").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(source,source),calls.toList()) }
    }
    @Test fun dreams111DisabledAvatarsAndProxyProfilesNeverStartPhotoDownloads() {
        var allowed by mutableStateOf(false);var proxied by mutableStateOf(false)
        val calls=CopyOnWriteArrayList<String>();val bitmap=image
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->calls+=url;bitmap}
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            ProfileAvatarField("https://images.example.invalid/privacy-identity.png",{},downloadsAllowed=allowed,proxied=proxied)
        } } }
        compose.onNodeWithTag("profile-avatar-disabled").assertIsDisplayed()
        compose.runOnIdle { assertTrue(calls.isEmpty());proxied=true;allowed=true }
        compose.onNodeWithText(context.getString(R.string.dreams_avatar_proxy_disabled)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(calls.isEmpty());proxied=false;allowed=true }
        awaitTag("profile-avatar-preview").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,calls.size) }
    }
    @Test fun dreams111ServerMetadataPhotoWinsOverTheAutomaticAddressWithoutChangingTheSavedProfile() {
        val net=SettingsRepository(context).defaultNetworks().first().copy(id="photo-metadata-demo",host="irc.chatzona.org",nick="DemoNick01",avatarUrl=null,proxyType=ProxyType.NONE)
        val metadata="https://images.example.invalid/web-profile.png";val calls=CopyOnWriteArrayList<String>();val bitmap=image
        var requests=0
        var saved:com.boxlabs.hexdroid.data.NetworkProfile?=null
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->calls+=url;bitmap}
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            NetworkEditScreen(UiState(settings=UiSettings(settingsOnePage=true,chatZonaAvatarsEnabled=true,imagePreviewsEnabled=true,imagePreviewsWifiOnly=false),editingNetwork=net,
                connections=mapOf(net.id to NetConnState(myNick="DemoNick01",connected=true,hasUserMetadata=true,avatarUrls=mapOf("demonick01" to metadata)))),
                onCancel={},onSave={p,_,_->saved=p},onRequestAvatarMetadata={_,_->requests++})
        } } }
        awaitTag("profile-avatar-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile-avatar-source").assertTextEquals(metadata)
        compose.runOnIdle { assertEquals(listOf(metadata),calls.toList());assertTrue(requests>0) }
        compose.onNodeWithTag("network-save").performClick()
        compose.runOnIdle { assertNotNull(saved);assertNull(saved!!.avatarUrl) }
    }
    @Test fun dreams111TheOwnChatPhotoFromMetadataIsNotHiddenByAnEmptyProfileUrl() {
        val id="own-chat-photo-demo";val nick="OwnDemoNick";val key="$id::#demo"
        val net=SettingsRepository(context).defaultNetworks().first().copy(id=id,host="irc.chatzona.org",nick=nick,avatarUrl="",proxyType=ProxyType.NONE)
        val metadata="https://images.example.invalid/own-web-profile.png";val calls=CopyOnWriteArrayList<String>();val bitmap=image
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->calls+=url;bitmap}
        val state=UiState(networks=listOf(net),activeNetworkId=id,selectedBuffer=key,
            buffers=mapOf(key to UiBuffer("#demo",log=BufferLog(messages=kotlinx.collections.immutable.persistentListOf(UiMessage(1,System.currentTimeMillis(),nick,"Mensaje propio de prueba"))))),
            connections=mapOf(id to NetConnState(myNick=nick,avatarUrls=mapOf(nick.lowercase() to metadata))),profileAvatarUrls=mapOf(id to ""),
            settings=UiSettings(chatZonaAvatarsEnabled=true,chatZonaAvatarsInMessages=true,imagePreviewsEnabled=true,imagePreviewsWifiOnly=false),showBufferList=false,showNickList=false)
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            ChatScreen(state,onSelectBuffer={},onSend={},onDisconnect={},onReconnect={},onExit={},
                onToggleBufferList={},onToggleNickList={},onToggleChannelsOnly={},onWhois={},onIgnoreNick={_,_->},onUnignoreNick={_,_->},
                onIgnoreNotifications={_,_->},onUnignoreNotifications={_,_->},onRefreshNicklist={},onRefreshChannelModes={},
                onOpenList={},onOpenSettings={},onOpenNetworks={},onOpenTransfers={},onSysInfo={},onAbout={},onUpdateSettings={})
        } } }
        awaitTag("avatar-$nick").assertIsDisplayed()
        compose.waitUntil(5_000){calls.contains(metadata)}
        compose.runOnIdle { assertTrue(calls.all { it==metadata }) }
    }

}
