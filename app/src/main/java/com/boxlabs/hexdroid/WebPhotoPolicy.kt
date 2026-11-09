/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.net.URI
import java.util.Base64
import java.util.Locale

internal val photoHosts = setOf("images.chateagratis.net", "chat-avatars.chateagratis.net")
internal data class WebPhoto(val url: String?)

/** Web v92 stores avatar|gender|age|photo in Base64 GECOS; only the photo is used. */
internal fun webPhotoFromGecos(value: String?): WebPhoto? = runCatching {
    if (value.isNullOrEmpty() || value.length > 4096 || !value.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) return null
    val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString()
    if (decoded.any { it.isISOControl() }) return null
    val parts = decoded.split('|')
    if (parts.size < 4) return null
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

/** A web payload carries no displayable real name. Ordinary real names are untouched. */
internal fun displayRealname(value: String): String = if (webPhotoFromGecos(value) != null) "" else value

internal const val DEFAULT_AVATAR_TEMPLATE = "https://chat-avatars.chateagratis.net/avatar/Chat/{nick}.png"
internal fun templateAvatarUrl(template: String, nick: String, prefixes: String = "~&@%+"): String? {
    val bare = nick.trimStart { it in prefixes }
    if (bare.isBlank() || bare.length > 128 || bare.any { it.isWhitespace() || it.isISOControl() } || bare in listOf(".", "..") || bare.startsWith('#') || bare.startsWith('*')) return null
    fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    if (listOf("{nick}", "{nickLower}", "%nick%", "%s").none { it in template }) return null
    val url = template.replace("{nickLower}", encode(bare.lowercase(Locale.ROOT)))
        .replace("{nick}", encode(bare)).replace("%nick%", encode(bare)).replace("%s", encode(bare))
    return normalizeWebPhotoUrl(url)
}
internal fun avatarTemplateHost(template: String): String? = templateAvatarUrl(template, "AvatarProbe")?.let { URI(it).host?.lowercase(Locale.ROOT) }

internal fun automaticAvatarAllowed(url: String, configuredHost: String?): Boolean = runCatching {
    validAvatarUrl(url) && URI(url).host.lowercase(Locale.ROOT).let { it in photoHosts || it == configuredHost?.lowercase(Locale.ROOT) }
}.getOrDefault(false)

internal fun publicAvatarAddress(address: java.net.InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
    val b=address.address.map { it.toInt() and 255 }
    if (b.size==4) return !(b[0]==0 || b[0]>=224 || b[0]==127 || b[0]==10 ||
        (b[0]==100 && b[1] in 64..127) || (b[0]==169 && b[1]==254) ||
        (b[0]==172 && b[1] in 16..31) || (b[0]==192 && (b[1]==168 || b[1]==0)) ||
        (b[0]==198 && b[1] in 18..19))
    // Only globally routable unicast IPv6; excludes ULA, mapped, NAT64 and local ranges.
    return b.size==16 && (b[0] and 0xe0)==0x20 && !(b[0]==0x20 && b[1]==0x01 && b[2]==0x0d && b[3]==0xb8)
}

/** OkHttp may bypass custom DNS for literal IPs, so validate those before opening a socket. */
internal fun publicAvatarLiteral(url: String): Boolean = runCatching {
    val host=URI(url).host ?: return false
    if (host.contains(':') || host.matches(Regex("[0-9.]+"))) publicAvatarAddress(java.net.InetAddress.getByName(host)) else true
}.getOrDefault(false)
