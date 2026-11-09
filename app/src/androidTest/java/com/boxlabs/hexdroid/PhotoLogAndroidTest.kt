package com.boxlabs.hexdroid

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.boxlabs.hexdroid.ui.SearchableLogContent
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoLogAndroidTest {
    @get:Rule val compose=createComposeRule()
    private fun png(color:Int):ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG,100,out)
    }.toByteArray()
    private class Connection(url:URL,val bytes:ByteArray,val gate:CountDownLatch?=null):HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy()=false
        override fun getResponseCode()=200
        override fun getContentLengthLong()=bytes.size.toLong()
        override fun getInputStream():ByteArrayInputStream { check(gate?.await(10,TimeUnit.SECONDS) != false);return ByteArrayInputStream(bytes) }
    }
    @Test fun obsoleteDownloadCannotReplaceRefreshedPhoto() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val connections=CopyOnWriteArrayList<Connection>()
        val url="https://chat-avatars.chateagratis.net/test-${System.nanoTime()}.png?v=announced"
        RemoteBitmaps.connectionFactory={ target ->
            val first=connections.isEmpty()
            Connection(target,png(if(first) Color.RED else Color.BLUE),if(first) release else null).also { connections+=it;if(first)entered.countDown() }
        }
        try {
            val old=async(Dispatchers.IO) { RemoteBitmaps.fetch(url) }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val fresh=RemoteBitmaps.fetch(url,retry=true)
            assertEquals(Color.BLUE,fresh!!.getPixel(0,0))
            release.countDown()
            assertNull(old.await())
            assertEquals(Color.BLUE,RemoteBitmaps.cached(url)!!.getPixel(0,0))
            assertTrue(connections[0].url.query.contains("v=announced"))
            assertFalse(connections[1].url.query.contains("announced"))
            assertEquals("no-cache",connections[1].getRequestProperty("Cache-Control"))
            RemoteBitmaps.fetch(url)
            assertEquals(2,connections.size)
        } finally { release.countDown();RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidate(url) }
    }
    @Test fun livePhotoUpdatesAndRemovalReachConnectionState() = runBlocking {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val store=androidx.lifecycle.ViewModelStore()
        lateinit var vm:IrcViewModel
        val net="photo-state-${System.nanoTime()}"
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        main { vm=IrcViewModel(com.boxlabs.hexdroid.data.SettingsRepository(context),context);store.put("photos",vm) }
        try {
            withTimeout(10000) { while(!vm.state.value.settingsLoaded) delay(25) }
            main {
                val encoded=java.util.Base64.getEncoder().encodeToString("#|#|#|/new.jpg".toByteArray())
                vm.handleStateEvent(net,IrcEvent.Setname("Alice",encoded))
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar",null,null,absent=true))
                assertFalse(vm.state.value.connections[net]!!.avatarUrls.containsKey("alice"))
                assertNotNull(profileAvatarSource(null,null,"irc.chatzona.org",null,"Alice",legacyUrl=vm.state.value.connections[net]!!.legacyAvatarUrls["alice"],legacyKnown=true))
                assertEquals("https://chat-avatars.chateagratis.net/new.jpg",vm.state.value.connections[net]!!.legacyAvatarUrls["alice"])
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*","https://example.org/current.jpg"))
                val revision=vm.state.value.connections[net]!!.avatarRevision
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*","https://example.org/current.jpg"))
                assertTrue(vm.state.value.connections[net]!!.avatarRevision>revision)
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*",null))
                val state=vm.state.value.connections[net]!!
                assertTrue(state.avatarUrls.containsKey("alice"))
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar",null,null,absent=true))
                assertEquals("",vm.state.value.connections[net]!!.avatarUrls["alice"])
                assertNull(profileAvatarSource(null,state.avatarUrls["alice"],"irc.chatzona.org",null,"Alice",state.legacyAvatarUrls["alice"],metadataKnown=true,legacyKnown=true))
                val moved=vm.withMetadataMovedForNick(state,"Alice","Bob")
                assertFalse(moved.avatarUrls.containsKey("alice"));assertEquals("",moved.avatarUrls["bob"])
                assertTrue("bob" in moved.primaryAvatarNicks)
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar-url","*","https://example.org/alias.png"))
                assertEquals("",vm.state.value.connections[net]!!.avatarUrls["alice"])
                assertEquals("https://example.org/alias.png",vm.state.value.connections[net]!!.avatarAliasUrls["alice"])
                val other=net+"-other"
                vm.handleStateEvent(other,IrcEvent.MetadataChanged("Alice","avatar","*","https://example.org/other.png"))
                assertEquals("https://example.org/other.png",vm.state.value.connections[other]!!.avatarUrls["alice"])
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("N[ick]","avatar","*","https://example.org/special.png"))
                assertEquals("https://example.org/special.png",vm.state.value.connections[net]!!.avatarUrls["n{ick}"])
                vm.dropMetadataForNick(net,"Alice")
                assertFalse(vm.state.value.connections[net]!!.legacyAvatarUrls.containsKey("alice"))
            }
        } finally { main { store.clear() } }
    }
    @Test fun networkCachesAndInvalidationAreIsolatedAndThumbnailsStayWithin96Pixels() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        val url="https://chat-avatars.chateagratis.net/scoped-${System.nanoTime()}.png"
        var calls=0
        val bytes=ByteArrayOutputStream().also { Bitmap.createBitmap(500,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        RemoteBitmaps.connectionFactory={ target -> calls++;Connection(target,bytes) }
        try {
            val first=RemoteBitmaps.fetchFresh(url,60000,scope="network-a")!!
            assertTrue(first.width<=96 && first.height<=96)
            assertEquals(2.5,first.width.toDouble()/first.height,0.1)
            assertNull(RemoteBitmaps.cached(url,scope="network-b"))
            RemoteBitmaps.fetchFresh(url,60000,scope="network-a");assertEquals(1,calls)
            RemoteBitmaps.fetchFresh(url,60000,scope="network-b");assertEquals(2,calls)
            RemoteBitmaps.invalidateScope("network-a")
            assertNull(RemoteBitmaps.cached(url,scope="network-a"));assertNotNull(RemoteBitmaps.cached(url,scope="network-b"))
            RemoteBitmaps.fetchFresh(url,60000,scope="network-a");assertEquals(3,calls)
            RemoteBitmaps.fetchFresh(url,0,scope="network-a");assertEquals(4,calls)
        } finally { RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidateScope("network-a");RemoteBitmaps.invalidateScope("network-b") }
    }
    @Test fun automaticDownloadRejectsUnconfiguredHostsBeforeOpeningAConnection() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        var opened=false
        RemoteBitmaps.connectionFactory={ target -> opened=true;Connection(target,png(Color.RED)) }
        try {
            val url="https://unconfigured.example/${System.nanoTime()}.png"
            assertNull(RemoteBitmaps.fetch(url));assertFalse(opened)
            assertNotNull(RemoteBitmaps.fetch(url,configuredHost="unconfigured.example"));assertTrue(opened)
        } finally { RemoteBitmaps.connectionFactory=original }
    }
    @Test fun downloadsAllowThreeWorkersAndKeepTheRestQueued() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        val gate=CountDownLatch(1);val entered=CountDownLatch(3)
        val count=java.util.concurrent.atomic.AtomicInteger()
        val bytes=png(Color.GREEN)
        val prefix="https://chat-avatars.chateagratis.net/workers-${System.nanoTime()}"
        RemoteBitmaps.connectionFactory={ target -> count.incrementAndGet();entered.countDown();Connection(target,bytes,gate) }
        try {
            val jobs=(1..6).map { async(Dispatchers.IO) { RemoteBitmaps.fetch("$prefix-$it.png",scope="workers") } }
            assertTrue(entered.await(5000,TimeUnit.MILLISECONDS));assertEquals(3,count.get())
            gate.countDown()
            jobs.forEach { assertNotNull(it.await()) };assertEquals(6,count.get())
        } finally { gate.countDown();RemoteBitmaps.connectionFactory=original;RemoteBitmaps.invalidateScope("workers") }
    }
    @Test fun automaticAndManualByteLimitsDifferAndInvalidContentIsRejected() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        val prefix="https://chat-avatars.chateagratis.net/limits-${System.nanoTime()}"
        var bytes=png(Color.BLUE).copyOf(2*1024*1024+1)
        RemoteBitmaps.connectionFactory={ target -> Connection(target,bytes) }
        try {
            assertNull(RemoteBitmaps.fetch("$prefix-auto.png"))
            assertEquals(AvatarFailure.TOO_LARGE,RemoteBitmaps.failure("$prefix-auto.png"))
            assertNotNull(RemoteBitmaps.fetch("$prefix-manual.png",fullSize=true))
            bytes=ByteArray(5*1024*1024+1)
            assertNull(RemoteBitmaps.fetch("$prefix-too-large.png",fullSize=true))
            assertEquals(AvatarFailure.TOO_LARGE,RemoteBitmaps.failure("$prefix-too-large.png",fullSize=true))
            bytes="This is HTML, not an image".toByteArray()
            assertNull(RemoteBitmaps.fetch("$prefix-not-image.png"))
            assertEquals(AvatarFailure.NOT_IMAGE,RemoteBitmaps.failure("$prefix-not-image.png"))
        } finally { RemoteBitmaps.connectionFactory=original }
    }
    @Test fun brandUsesHexDreamsNameAndTransparentLogoExterior() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("HexDreams IRC",context.getString(R.string.personal_app_name))
        val logo=android.graphics.BitmapFactory.decodeResource(context.resources,R.drawable.hexdroid_logo)
        assertTrue(logo.hasAlpha());assertEquals(0,Color.alpha(logo.getPixel(0,0)))
        assertEquals(0,Color.alpha(context.getColor(R.color.ic_launcher_background)))
        assertTrue(Color.alpha(logo.getPixel(logo.width/2,logo.height/2))>0)
    }
    @Test fun imageDimensionsAreCheckedBeforeAllocatingTheDecodedBitmap() = runBlocking {
        val original=RemoteBitmaps.connectionFactory
        val bytes=png(Color.BLUE)
        // PNG IHDR reports 64 million pixels, beyond the 32-million-pixel ceiling.
        val buffer=java.nio.ByteBuffer.wrap(bytes)
        buffer.putInt(16,8000);buffer.putInt(20,8000)
        val crc=java.util.zip.CRC32().apply { update(bytes,12,17) }
        buffer.putInt(29,crc.value.toInt())
        val url="https://chat-avatars.chateagratis.net/dimensions-${System.nanoTime()}.png"
        RemoteBitmaps.connectionFactory={ target -> Connection(target,bytes) }
        try {
            assertNull(RemoteBitmaps.fetch(url,fullSize=true))
            assertEquals(AvatarFailure.NOT_IMAGE,RemoteBitmaps.failure(url,fullSize=true))
        } finally { RemoteBitmaps.connectionFactory=original }
    }
    @Test fun openPhotoViewerReloadsWhenMetadataRevisionChangesAtTheSameUrl() {
        val token=androidx.compose.runtime.mutableIntStateOf(0)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        val image=bitmap.asImageBitmap()
        compose.setContent { HexDroidIRCTheme {
            com.boxlabs.hexdroid.ui.AvatarViewer("https://chat-avatars.chateagratis.net/same.png","Nick",{},
                load={ _,_->calls.incrementAndGet();image },refreshToken=token.intValue)
        } }
        compose.waitUntil(5000) { calls.get()==1 }
        compose.runOnIdle { token.intValue++ }
        compose.waitUntil(5000) { calls.get()==2 }
        compose.onNodeWithTag("avatar-full-image").assertIsDisplayed()
    }
    @Test fun viewerSearchesMultipleWordsAndShowsNoMatches() {
        val log="ALICIA llegó a sala\nBob privado\nalicia SALA dos"
        compose.setContent { HexDroidIRCTheme { SearchableLogContent("Prueba.log", { query,offset -> readLogPage(StringReader(log),query,offset) }) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Bob privado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("log-query").performTextInput("alicia sala")
        compose.waitUntil(5000) { compose.onAllNodesWithText("Coincidencias en esta página: 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bob privado").assertDoesNotExist()
        compose.onNodeWithTag("log-query").performTextReplacement("inexistente")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("log-empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("log-empty").assertTextEquals("Sin coincidencias")
    }
}
