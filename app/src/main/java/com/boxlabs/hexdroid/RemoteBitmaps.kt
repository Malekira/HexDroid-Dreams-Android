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
    private val downloads = Semaphore(3)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val RETRY_AFTER_MS = 30_000L
    internal var connectionFactory: ((URL) -> HttpURLConnection)? = null
    private val http = okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .dns(object : okhttp3.Dns { override fun lookup(host: String): List<java.net.InetAddress> {
            val addresses = java.net.InetAddress.getAllByName(host).toList()
            if (addresses.isEmpty() || addresses.any { !publicAvatarAddress(it) }) throw java.net.UnknownHostException("Destino de avatar no público")
            return addresses
        } }).build()
    private fun scoped(url: String, scope: String) = scope + "\u0000" + url
    fun invalidateScope(scope: String) = synchronized(lock) {
        versions.keys.filter { it.startsWith(scope + "\u0000") }.toList().forEach { invalidateKey(it) }
    }
    private fun invalidateKey(id: String) {
        if (versions.containsKey(id)) refreshRequired[id] = true
        versions[id] = Any()
        cache.remove(id); largeCache.remove(id)
        for (full in listOf(false, true)) { val k=key(id,full);failedAt.remove(k);failures.remove(k);downloadedAt.remove(k) }
    }
    private fun key(url: String, full: Boolean) = (if (full) "large:" else "small:") + url

    internal fun failure(url: String, fullSize: Boolean = false, scope: String = ""): AvatarFailure? =
        synchronized(lock) { failures[key(scoped(url,scope), fullSize)] }

    fun invalidate(url: String, scope: String? = null) = synchronized(lock) {
        val ids=versions.keys.filter { it.substringAfter('\u0000')==url && (scope==null || it.startsWith(scope+"\u0000")) }.toList()
        if (ids.isEmpty()) invalidateKey(scoped(url,scope ?: "")) else ids.forEach(::invalidateKey)
    }

    fun cached(url: String, fullSize: Boolean = false, scope: String = ""): Bitmap? =
        synchronized(lock) {
            val id=scoped(url,scope)
            if(!fullSize && downloadedAt[key(id,false)]?.let { System.currentTimeMillis()-it>=60_000L }==true) null
            else (if(fullSize) largeCache else cache)[id]
        }

    suspend fun fetchFresh(url: String, maxAgeMs: Long, scope: String = "", configuredHost: String? = null): Bitmap? {
        val stale = synchronized(lock) {
            val id=scoped(url,scope)
            val at = downloadedAt[key(id, false)]
            val expired = at != null && System.currentTimeMillis() - at >= maxAgeMs
            if (expired) invalidateKey(id)
            expired
        }
        return fetchImage(url, false, stale, scope, configuredHost)
    }

    suspend fun fetch(url: String, fullSize: Boolean = false, retry: Boolean = false, scope: String = "", configuredHost: String? = null): Bitmap? {
        if (retry) invalidate(url,scope)
        return fetchImage(url, fullSize, retry, scope, configuredHost)
    }

    private suspend fun fetchImage(url: String, full: Boolean, refresh: Boolean, scope: String, configuredHost: String?): Bitmap? {
        if (url.isBlank() || !validAvatarUrl(url) || (!full && !automaticAvatarAllowed(url, configuredHost))) return null
        val cacheId=scoped(url,scope)
        val request = synchronized(lock) {
            if(!full && downloadedAt[key(cacheId,false)]?.let { System.currentTimeMillis()-it>=60_000L }==true) invalidateKey(cacheId)
            (if (full) largeCache else cache)[cacheId]?.let { return it }
            val k = key(cacheId, full)
            if (failedAt[k]?.let { System.currentTimeMillis() - it < RETRY_AFTER_MS } == true) return null
            val version = versions.getOrPut(cacheId) { Any() }
            val id = Request(cacheId, full, version)
            val force = refresh || refreshRequired[cacheId] == true
            inFlight[id] ?: downloadScope.async(start = CoroutineStart.LAZY) {
                val result = downloads.withPermit {
                    if(synchronized(lock) { versions[cacheId] !== version }) null to AvatarFailure.CONNECTION
                    else download(url, full, force, configuredHost)
                }
                synchronized(lock) {
                    // A metadata change/retry invalidates older responses, even for the same URL.
                    if (versions[cacheId] !== version) return@synchronized null
                    val image = result.first
                    if (image == null) {
                        failedAt[k] = System.currentTimeMillis()
                        failures[k] = result.second ?: AvatarFailure.CONNECTION
                    } else {
                        (if (full) largeCache else cache)[cacheId] = image
                        if (full) {
                            val ratio = minOf(1f, 96f / maxOf(image.width, image.height))
                            cache[cacheId] = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * ratio).toInt()),
                                maxOf(1, (image.height * ratio).toInt()), true)
                            downloadedAt[key(cacheId, false)] = System.currentTimeMillis()
                        }
                        downloadedAt[k] = System.currentTimeMillis()
                        refreshRequired.remove(cacheId)
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

    fun load(url: String, scope: String = "", configuredHost: String? = null, onLoaded: (Bitmap) -> Unit) {
        downloadScope.launch { fetchFresh(url,60_000L,scope,configuredHost)?.let { runCatching { onLoaded(it) } } }
    }

    private fun download(source: String, full: Boolean, refresh: Boolean, configuredHost: String?, redirects: Int = 0): Pair<Bitmap?, AvatarFailure?> = try {
        val requestUrl = avatarRequestUrl(source, System.currentTimeMillis(), refresh)
        if (!validAvatarUrl(requestUrl) || !publicAvatarLiteral(requestUrl) || (!full && !automaticAvatarAllowed(requestUrl,configuredHost))) null to AvatarFailure.REDIRECT else {
            fun redirect(location: String?): Pair<Bitmap?,AvatarFailure?> {
                val target=location?.let { normalizeWebPhotoUrl(URL(URL(requestUrl),it).toString()) }
                return if (redirects>=3 || target==null) null to AvatarFailure.REDIRECT else download(target,full,refresh,configuredHost,redirects+1)
            }
            fun image(input: java.io.InputStream, length: Long): Pair<Bitmap?,AvatarFailure?> {
                val bytes=input.use { readAvatarBytes(it,length,if(full) 5*1024*1024 else AVATAR_MAX_BYTES) }
                val decoded=decodeSampled(bytes,if(full) 1024 else 96)
                return decoded to if(decoded==null) AvatarFailure.NOT_IMAGE else null
            }
            val factory=connectionFactory
            if(factory!=null) {
                // Instrumentation seam; production always uses the DNS-pinned HTTPS client below.
                val conn=factory(URL(requestUrl));conn.connectTimeout=5000;conn.readTimeout=5000;conn.instanceFollowRedirects=false;conn.useCaches=false
                if(refresh) { conn.setRequestProperty("Cache-Control","no-cache");conn.setRequestProperty("Pragma","no-cache") }
                try { when(conn.responseCode) {
                    301,302,303,307,308 -> redirect(conn.getHeaderField("Location"))
                    in 200..299 -> image(conn.inputStream,conn.contentLengthLong)
                    else -> null to AvatarFailure.HTTP
                } } finally { conn.disconnect() }
            } else {
                val request=okhttp3.Request.Builder().url(requestUrl).header("Cache-Control", "no-cache").build()
                http.newCall(request).execute().use { response -> when(response.code) {
                    301,302,303,307,308 -> redirect(response.header("Location"))
                    in 200..299 -> response.body.let { body -> image(body.byteStream(),body.contentLength()) }
                    else -> null to AvatarFailure.HTTP
                } }
            }
        }
    } catch (_: AvatarTooLargeException) { null to AvatarFailure.TOO_LARGE }
      catch (_: java.net.SocketTimeoutException) { null to AvatarFailure.TIMEOUT }
      catch (_: Exception) { null to AvatarFailure.CONNECTION }

    private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong()*bounds.outHeight > 32_000_000L) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val image=BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale=minOf(1f,maxEdge.toFloat()/maxOf(image.width,image.height))
        return if(scale<1f) Bitmap.createScaledBitmap(image,maxOf(1,(image.width*scale).toInt()),maxOf(1,(image.height*scale).toInt()),true) else image
    }
}
