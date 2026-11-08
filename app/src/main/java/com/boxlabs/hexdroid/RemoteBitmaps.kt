/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL

/** Bounded HTTPS images. Callers still enforce preview preferences and the proxy boundary. */
object RemoteBitmaps {
    private val lock = Any()
    private fun <V> lru(max: Int) = object : LinkedHashMap<String, V>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>) = size > max
    }
    private val cache = lru<Bitmap>(64)
    private val largeCache = lru<Bitmap>(4)
    private val downloadedAt = lru<Long>(256)
    private val failedAt = lru<Long>(256)
    private val failures = lru<AvatarFailure>(256)
    private val versions = lru<Any>(256)
    private val refreshRequired = lru<Boolean>(256)
    private data class Request(val url: String, val full: Boolean, val version: Any)
    private val inFlight = HashMap<Request, Deferred<Bitmap?>>()
    private val downloads = Semaphore(4)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val RETRY_AFTER_MS = 30_000L
    internal var connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
    private fun key(url: String, full: Boolean) = (if (full) "large:" else "small:") + url

    internal fun failure(url: String, fullSize: Boolean = false): AvatarFailure? =
        synchronized(lock) { failures[key(url, fullSize)] }

    fun invalidate(url: String) = synchronized(lock) {
        if (versions.containsKey(url)) refreshRequired[url] = true
        versions[url] = Any()
        cache.remove(url); largeCache.remove(url)
        for (full in listOf(false, true)) {
            val k = key(url, full)
            failedAt.remove(k); failures.remove(k); downloadedAt.remove(k)
        }
    }

    fun cached(url: String, fullSize: Boolean = false): Bitmap? =
        synchronized(lock) { (if (fullSize) largeCache else cache)[url] }

    suspend fun fetchFresh(url: String, maxAgeMs: Long): Bitmap? {
        val stale = synchronized(lock) {
            val at = downloadedAt[key(url, false)]
            val expired = at != null && System.currentTimeMillis() - at >= maxAgeMs
            if (expired) { cache.remove(url); largeCache.remove(url) }
            expired
        }
        return fetchImage(url, false, stale)
    }

    suspend fun fetch(url: String, fullSize: Boolean = false, retry: Boolean = false): Bitmap? {
        if (retry) invalidate(url)
        return fetchImage(url, fullSize, retry)
    }

    private suspend fun fetchImage(url: String, full: Boolean, refresh: Boolean): Bitmap? {
        if (url.isBlank() || !validAvatarUrl(url)) return null
        val request = synchronized(lock) {
            (if (full) largeCache else cache)[url]?.let { return it }
            val k = key(url, full)
            if (failedAt[k]?.let { System.currentTimeMillis() - it < RETRY_AFTER_MS } == true) return null
            val version = versions.getOrPut(url) { Any() }
            val id = Request(url, full, version)
            val force = refresh || refreshRequired[url] == true
            inFlight[id] ?: scope.async(start = CoroutineStart.LAZY) {
                val result = downloads.withPermit { download(url, full, force) }
                synchronized(lock) {
                    // A metadata change/retry invalidates older responses, even for the same URL.
                    if (versions[url] !== version) return@synchronized null
                    val image = result.first
                    if (image == null) {
                        failedAt[k] = System.currentTimeMillis()
                        failures[k] = result.second ?: AvatarFailure.CONNECTION
                    } else {
                        (if (full) largeCache else cache)[url] = image
                        if (full) {
                            val ratio = minOf(1f, 256f / maxOf(image.width, image.height))
                            cache[url] = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * ratio).toInt()),
                                maxOf(1, (image.height * ratio).toInt()), true)
                            downloadedAt[key(url, false)] = System.currentTimeMillis()
                        }
                        downloadedAt[k] = System.currentTimeMillis()
                        refreshRequired.remove(url)
                        failures.remove(k); failedAt.remove(k)
                    }
                    image
                }
            }.also { job ->
                inFlight[id] = job
                job.invokeOnCompletion { synchronized(lock) { inFlight.remove(id) } }
            }
        }
        return request.await()
    }

    fun load(url: String, onLoaded: (Bitmap) -> Unit) {
        scope.launch { fetch(url)?.let { runCatching { onLoaded(it) } } }
    }

    private fun download(source: String, full: Boolean, refresh: Boolean, redirects: Int = 0): Pair<Bitmap?, AvatarFailure?> = try {
        val requestUrl = avatarRequestUrl(source, System.currentTimeMillis(), refresh)
        if (!validAvatarUrl(requestUrl)) null to AvatarFailure.REDIRECT else {
            val conn = connectionFactory(URL(requestUrl))
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            if (refresh) {
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.setRequestProperty("Pragma", "no-cache")
            }
            try {
                if (conn.responseCode in setOf(301, 302, 303, 307, 308)) {
                    val target = conn.getHeaderField("Location")?.let { normalizeWebPhotoUrl(URL(URL(requestUrl), it).toString()) }
                    if (redirects >= 3 || target == null) null to AvatarFailure.REDIRECT
                    else download(target, full, refresh, redirects + 1)
                } else if (conn.responseCode !in 200..299) null to AvatarFailure.HTTP
                else {
                    val bytes = conn.inputStream.use { readAvatarBytes(it, conn.contentLengthLong) }
                    val decoded = decodeSampled(bytes, if (full) 1024 else 256)
                    decoded to if (decoded == null) AvatarFailure.NOT_IMAGE else null
                }
            } finally { conn.disconnect() }
        }
    } catch (_: AvatarTooLargeException) { null to AvatarFailure.TOO_LARGE }
      catch (_: java.net.SocketTimeoutException) { null to AvatarFailure.TIMEOUT }
      catch (_: Exception) { null to AvatarFailure.CONNECTION }

    private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
