/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.net.URI
import java.util.Base64
import java.util.Locale

private val photoHosts = setOf("images.chateagratis.net", "chat-avatars.chateagratis.net")
internal data class WebPhoto(val url: String?)

/** Web v92 stores avatar|gender|age|photo in Base64 GECOS; only the photo is used. */
internal fun webPhotoFromGecos(value: String?): WebPhoto? = runCatching {
    if (value.isNullOrEmpty() || value.length > 4096 || !value.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) return null
    val decoded = Base64.getDecoder().decode(value).toString(Charsets.UTF_8)
    if (decoded.any { it == '\uFFFD' || it.isISOControl() }) return null
    val parts = decoded.split('|')
    if (parts.size != 4) return null
    WebPhoto(normalizeWebPhotoUrl(parts[3], legacy = true))
}.getOrNull()

internal fun normalizeWebPhotoUrl(value: String?, legacy: Boolean = false): String? = runCatching {
    if (value.isNullOrBlank() || value == "#" || value.length > 2048 || value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    val raw = URI(value)
    val uri = if (legacy && !raw.isAbsolute) {
        if (value.startsWith("//") || value.substringBefore('?').substringBefore('#').trim('/').isEmpty()) return null
        URI("https://chat-avatars.chateagratis.net").resolve(if (value.startsWith('/')) value else "/$value")
    } else raw
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    if (uri.rawUserInfo != null || (legacy && host !in photoHosts)) return null
    val result = if (host in photoHosts) {
        if (uri.scheme !in listOf("http", "https")) return null
        "https://chat-avatars.chateagratis.net" + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")
    } else {
        if (!uri.scheme.equals("https", true)) return null
        uri.toASCIIString().substringBefore('#')
    }
    result.takeIf(::validAvatarUrl)
}.getOrNull()

/** Version only the official endpoint. Signed/third-party query strings remain untouched. */
internal fun avatarRequestUrl(source: String, nowMs: Long, refresh: Boolean): String {
    val normalized = normalizeWebPhotoUrl(source) ?: return source
    val uri = URI(normalized)
    if (uri.host !in photoHosts || uri.path.endsWith("/default.png")) return normalized
    val query = uri.rawQuery?.split('&').orEmpty()
    if (!refresh && query.any { it.substringBefore('=') == "v" }) return normalized
    val remaining = query.filterNot { it.substringBefore('=') == "v" || it.isEmpty() }
    val version = if (refresh) nowMs else nowMs / 60_000L
    return normalized.substringBefore('?') + "?" + (remaining + "v=$version").joinToString("&")
}
