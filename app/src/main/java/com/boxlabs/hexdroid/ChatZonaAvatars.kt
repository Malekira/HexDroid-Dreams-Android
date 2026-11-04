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
