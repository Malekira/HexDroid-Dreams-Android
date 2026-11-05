/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal const val CHATZONA_AVATAR_REFRESH_MS = 5 * 60 * 1000L

/** Match network identity, never a user-editable display name or a substring/lookalike domain. */
internal fun isChatZonaNetwork(host: String, advertisedNetwork: String? = null): Boolean {
    val actual = host.trim().trimEnd('.').lowercase(Locale.ROOT)
    return advertisedNetwork?.trim()?.equals("ChatZona", ignoreCase = true) == true ||
        listOf("chatzona.org", "chatzona.net", "chateagratis.net").any { actual == it || actual.endsWith(".$it") }
}

internal fun chatZonaAvatarUrl(host: String, advertisedNetwork: String?, nick: String): String? {
    if (!isChatZonaNetwork(host, advertisedNetwork)) return null
    val bare = nick.trimStart('~', '&', '@', '%', '+')
    if (bare.isBlank() || bare.length > 128 || bare in setOf(".", "..") || bare.any { it.isWhitespace() || it.isISOControl() } || bare.startsWith('#') || bare.startsWith('*')) return null
    return "https://chat-avatars.chateagratis.net/avatar/Chat/" + URLEncoder.encode(bare, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

/** Resolve a profile without downloading or persisting a nick-derived URL. */
internal fun profileAvatarSource(explicitUrl: String?, metadataUrl: String?, host: String, network: String?, nick: String): String? =
    sequenceOf(explicitUrl, metadataUrl).mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() && validAvatarUrl(value) } }.firstOrNull()
        ?: chatZonaAvatarUrl(host, network, nick)

/** An untouched empty field must not publish METADATA DEL or erase a web avatar. */
internal fun profileAvatarChanged(previous: String?, edited: String?): Boolean = previous.orEmpty().trim() != edited.orEmpty().trim()

internal fun profileAvatarEdit(previous: String?, edited: String): String? =
    edited.trim().let { if (it.isEmpty() && previous == null) null else it }

internal enum class AvatarFailure { TOO_LARGE, HTTP, NOT_IMAGE, TIMEOUT, CONNECTION, REDIRECT }
internal const val AVATAR_MAX_BYTES = 2 * 1024 * 1024

internal class AvatarTooLargeException : java.io.IOException()
internal fun readAvatarBytes(input: java.io.InputStream, declaredSize: Long = -1L): ByteArray {
    if (declaredSize > AVATAR_MAX_BYTES) throw AvatarTooLargeException()
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(chunk, 0, minOf(chunk.size, AVATAR_MAX_BYTES - out.size() + 1))
        if (count < 0) break
        if (count == 0) continue
        if (out.size() + count > AVATAR_MAX_BYTES) throw AvatarTooLargeException()
        out.write(chunk, 0, count)
    }
    return out.toByteArray()
}
