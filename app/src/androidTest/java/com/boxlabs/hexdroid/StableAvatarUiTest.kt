package com.boxlabs.hexdroid

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.connection.ProxyType
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.*
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import kotlinx.collections.immutable.persistentListOf
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StableAvatarUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Composable private fun Chat(state:UiState) {
        ChatScreen(state,onSelectBuffer={},onSend={},onDisconnect={},onReconnect={},onExit={},onToggleBufferList={},onToggleNickList={},onToggleChannelsOnly={},onWhois={},
            onIgnoreNick={_,_->},onUnignoreNick={_,_->},onIgnoreNotifications={_,_->},onUnignoreNotifications={_,_->},onRefreshNicklist={},onRefreshChannelModes={},
            onOpenList={},onOpenSettings={},onOpenNetworks={},onOpenTransfers={},onSysInfo={},onAbout={},onUpdateSettings={})
    }
    @Test fun otherUsersMetadataDoesNotReloadVisiblePhotosAndOnlyChangedSourceReloads() {
        val id="stable-photo-demo";val key="$id::Alice"
        val profile=SettingsRepository(context).defaultNetworks().first().copy(id=id,host="irc.chatzona.org",proxyType=ProxyType.NONE)
        val alice="https://chat-avatars.chateagratis.net/alice.png";val bob="https://chat-avatars.chateagratis.net/bob.png"
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).asImageBitmap();val calls=CopyOnWriteArrayList<String>()
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={url,_->calls+=url;bitmap}
        var state by mutableStateOf(UiState(networks=listOf(profile),activeNetworkId=id,selectedBuffer=key,
            buffers=mapOf(key to UiBuffer("Alice",log=BufferLog(messages=persistentListOf(UiMessage(1,System.currentTimeMillis(),"Alice","uno"),UiMessage(2,System.currentTimeMillis(),"Bob","dos"))))),
            connections=mapOf(id to NetConnState(myNick="OwnNick",avatarUrls=mapOf("alice" to alice,"bob" to bob))),
            settings=UiSettings(imagePreviewsEnabled=true,imagePreviewsWifiOnly=false,chatZonaAvatarsEnabled=true,chatZonaAvatarsInMessages=true),showBufferList=false,showNickList=false))
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme { Chat(state) } } }
        compose.waitUntil(5000) { calls.contains(alice) && calls.contains(bob) };compose.waitForIdle()
        val initial=calls.toList()
        compose.runOnIdle { state=state.copy(connections=mapOf(id to state.connections[id]!!.copy(avatarRevision=1,avatarUrls=state.connections[id]!!.avatarUrls+("carol" to "https://chat-avatars.chateagratis.net/carol.png")))) }
        compose.waitForIdle();assertEquals(initial,calls.toList())
        val updated="https://chat-avatars.chateagratis.net/alice-new.png"
        compose.runOnIdle { state=state.copy(connections=mapOf(id to state.connections[id]!!.copy(avatarRevision=2,avatarUrls=state.connections[id]!!.avatarUrls+("alice" to updated)))) }
        compose.waitUntil(5000) { calls.contains(updated) };compose.waitForIdle()
        assertEquals(initial.count { it==bob },calls.count { it==bob })
    }
    @Test fun thumbnailsDoNotPollOrBlankAnExistingPhotoWhenRefreshFails() {
        val calls=java.util.concurrent.atomic.AtomicInteger();var token by mutableIntStateOf(0)
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).asImageBitmap()
        val loader:suspend(String,Long)->androidx.compose.ui.graphics.ImageBitmap?={_,_->if(calls.incrementAndGet()==1)bitmap else null}
        compose.setContent { CompositionLocalProvider(LocalAvatarLoader provides loader) { HexDroidIRCTheme {
            AvatarThumbnail("https://chat-avatars.chateagratis.net/stable.png","Nick",Modifier,refreshToken=token,refreshEveryMs=20L,onOpen={})
        } } }
        compose.waitUntil(5000) { calls.get()==1 };compose.waitForIdle()
        val description=context.getString(R.string.dreams_expand_avatar,"Nick")
        compose.onNodeWithContentDescription(description).assertExists()
        compose.mainClock.advanceTimeBy(2000);compose.waitForIdle();assertEquals(1,calls.get())
        compose.runOnIdle { token++ };compose.waitUntil(5000) { calls.get()==2 };compose.waitForIdle()
        compose.onNodeWithContentDescription(description).assertExists()
    }
    @Test fun oldSavedTabsAreDisabledOnceAndLaterUserChoicesArePreserved() {
        val repo=SettingsRepository(context)
        val parse=SettingsRepository::class.java.getDeclaredMethod("parseSettings",String::class.java).apply { isAccessible=true }
        assertFalse(UiSettings().networkTabsAtBottom)
        assertFalse((parse.invoke(repo,"{\"chatDefaultsVersion\":2,\"networkTabsAtBottom\":true}") as UiSettings).networkTabsAtBottom)
        assertTrue((parse.invoke(repo,"{\"chatDefaultsVersion\":3,\"networkTabsAtBottom\":true}") as UiSettings).networkTabsAtBottom)
    }
}
