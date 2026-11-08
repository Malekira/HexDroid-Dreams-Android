package com.boxlabs.hexdroid

import android.graphics.Bitmap
import android.graphics.Color
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
                assertEquals("https://chat-avatars.chateagratis.net/new.jpg",vm.state.value.connections[net]!!.legacyAvatarUrls["alice"])
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*","https://example.org/current.jpg"))
                val revision=vm.state.value.connections[net]!!.avatarRevision
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*","https://example.org/current.jpg"))
                assertTrue(vm.state.value.connections[net]!!.avatarRevision>revision)
                vm.handleStateEvent(net,IrcEvent.MetadataChanged("Alice","avatar","*",null))
                val state=vm.state.value.connections[net]!!
                assertTrue(state.avatarUrls.containsKey("alice"))
                assertNull(profileAvatarSource(null,state.avatarUrls["alice"],"irc.chatzona.org",null,"Alice",state.legacyAvatarUrls["alice"],metadataKnown=true,legacyKnown=true))
                val moved=vm.withMetadataMovedForNick(state,"Alice","Bob")
                assertFalse(moved.avatarUrls.containsKey("alice"));assertEquals("",moved.avatarUrls["bob"])
                assertTrue("bob" in moved.primaryAvatarNicks)
                vm.dropMetadataForNick(net,"Alice")
                assertFalse(vm.state.value.connections[net]!!.legacyAvatarUrls.containsKey("alice"))
            }
        } finally { main { store.clear() } }
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
