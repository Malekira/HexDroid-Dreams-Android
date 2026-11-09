package com.boxlabs.hexdroid

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.connection.ProxyType
import com.boxlabs.hexdroid.data.SettingsRepository
import com.boxlabs.hexdroid.ui.ChatScreen
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AvatarLoadingAndroidTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun png()=ByteArrayOutputStream().also { Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
    private class Connection(url:URL,val bytes:ByteArray,val status:Int=200,val location:String?=null):HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy()=false
        override fun getResponseCode()=status
        override fun getHeaderField(name:String?)=if(name.equals("Location",true))location else null
        override fun getContentLengthLong()=bytes.size.toLong()
        override fun getInputStream()=ByteArrayInputStream(bytes)
    }
    @Composable private fun Chat(state:UiState) {
        ChatScreen(state,onSelectBuffer={},onSend={},onDisconnect={},onReconnect={},onExit={},onToggleBufferList={},onToggleNickList={},onToggleChannelsOnly={},onWhois={},
            onIgnoreNick={_,_->},onUnignoreNick={_,_->},onIgnoreNotifications={_,_->},onUnignoreNotifications={_,_->},onRefreshNicklist={},onRefreshChannelModes={},
            onOpenList={},onOpenSettings={},onOpenNetworks={},onOpenTransfers={},onSysInfo={},onAbout={},onUpdateSettings={})
    }
    @Test fun webPhotoDownloadsDecodesAndDisplaysWithDefaultAvatarSettingsAndLinkPreviewsOff() {
        val original=RemoteBitmaps.connectionFactory;val requests=CopyOnWriteArrayList<String>();val bytes=png()
        val id="loading-${System.nanoTime()}";val key="$id::Alice"
        val profile=SettingsRepository(context).defaultNetworks().first().copy(id=id,host="irc.chatzona.org",proxyType=ProxyType.NONE,avatarUrl=null)
        val encoded=java.util.Base64.getEncoder().encodeToString("#|#|#|/photos/$id.png".toByteArray())
        val source=webPhotoFromGecos(encoded)!!.url!!
        var state by mutableStateOf(UiState(networks=listOf(profile),activeNetworkId=id,selectedBuffer=key,buffers=mapOf(key to UiBuffer("Alice")),
            connections=mapOf(id to NetConnState(myNick="OwnNick",legacyAvatarUrls=mapOf("alice" to source))),showBufferList=false,showNickList=false))
        assertFalse(state.settings.imagePreviewsEnabled);assertTrue(state.settings.imagePreviewsWifiOnly)
        RemoteBitmaps.connectionFactory={ url -> requests+=url.toString();Connection(url,bytes) }
        try {
            // Production avatar loader, HTTP response handling and bitmap decoder; no mock UI loader.
            compose.setContent { HexDroidIRCTheme { Chat(state) } }
            val description=context.getString(R.string.dreams_expand_avatar,"Alice")
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
            assertEquals(1,requests.size)
            compose.runOnIdle { state=state.copy(settings=state.settings.copy(imagePreviewsEnabled=true)) }
            compose.waitForIdle();assertEquals(1,requests.size)
            compose.runOnIdle { state=state.copy(settings=state.settings.copy(chatZonaAvatarsEnabled=false)) }
            compose.onNodeWithContentDescription(description).assertDoesNotExist()
        } finally { RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidateScope(id) }
    }
    @Test fun avatarWifiPreferencePersistsIndependentlyOfLinkPreviewPreferences()=runBlocking {
        val repo=SettingsRepository(context);val previous=repo.settingsFlow.first()
        try {
            repo.updateSettings { it.copy(avatarWifiOnly=true,chatZonaAvatarsEnabled=true,imagePreviewsEnabled=false,imagePreviewsWifiOnly=true) }
            val restored=SettingsRepository(context).settingsFlow.first()
            assertTrue(restored.avatarWifiOnly);assertTrue(restored.chatZonaAvatarsEnabled)
            assertFalse(restored.imagePreviewsEnabled);assertTrue(restored.imagePreviewsWifiOnly)
        } finally { repo.updateSettings { previous } }
    }
    @Test fun officialRedirectDownloadsFromActualDestinationWithoutCanonicalizationLoop()=runBlocking {
        val original=RemoteBitmaps.connectionFactory;val requests=CopyOnWriteArrayList<String>();val bytes=png()
        val name="redirect-${System.nanoTime()}";val source="https://chat-avatars.chateagratis.net/$name.png"
        val target="https://images.chateagratis.net/photos/$name.png?sig=a%2Fb"
        RemoteBitmaps.connectionFactory={ url -> requests+=url.toString();if(url.host=="chat-avatars.chateagratis.net")Connection(url,bytes,302,target) else Connection(url,bytes) }
        try {
            assertNotNull(RemoteBitmaps.fetch(source,scope=name))
            assertEquals(2,requests.size);assertEquals(target,requests.last())
        } finally { RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidateScope(name) }
    }
    @Test fun transientInitialFailureRetriesOnceThenUsesTheCacheWhilePermanentFailureStops()=runBlocking {
        val original=RemoteBitmaps.connectionFactory;val bytes=png();var requests=0
        val scope="retry-${System.nanoTime()}";val source="https://chat-avatars.chateagratis.net/$scope.png"
        RemoteBitmaps.connectionFactory={ url -> requests++;if(requests==1)throw IOException("Transient test failure") else Connection(url,bytes) }
        try {
            assertNotNull(RemoteBitmaps.fetch(source,scope=scope));assertEquals(2,requests)
            assertNotNull(RemoteBitmaps.fetch(source,scope=scope));assertEquals(2,requests)
            requests=0;RemoteBitmaps.connectionFactory={ _ -> requests++;throw IOException("Offline") }
            assertNull(RemoteBitmaps.fetch(source+"?offline=1",scope=scope));assertEquals(2,requests)
            assertNull(RemoteBitmaps.fetch(source+"?offline=1",scope=scope));assertEquals(2,requests)
        } finally { RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidateScope(scope) }
    }
    @Test fun redirectsToPrivateDestinationsAreStillBlockedBeforeAnyConnection()=runBlocking {
        val original=RemoteBitmaps.connectionFactory;var requests=0;val bytes=png()
        val source="https://chat-avatars.chateagratis.net/private-${System.nanoTime()}.png"
        RemoteBitmaps.connectionFactory={ url -> requests++;Connection(url,bytes,302,"https://127.0.0.1/photo.png") }
        try { assertNull(RemoteBitmaps.fetch(source,fullSize=true));assertEquals(1,requests) }
        finally { RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidate(source) }
    }
}
