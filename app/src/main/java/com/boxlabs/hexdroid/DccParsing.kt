/*
* HexDroidIRC - An IRC Client for Android
* Copyright (C) 2026 boxlabs
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.boxlabs.hexdroid

import java.util.Locale

internal fun parseDccChat(textRaw: String): DccChatOffer? {
// CTCP wrapper: \u0001DCC CHAT|SCHAT <proto> <ip> <port>\u0001
if (!textRaw.startsWith("\u0001DCC ") || !textRaw.endsWith("\u0001")) return null

val inner = textRaw.removePrefix("\u0001").removeSuffix("\u0001").trim()
if (!inner.startsWith("DCC ", ignoreCase = true)) return null

val afterDcc = inner.drop(3).trimStart() // remove "DCC"
val verbRaw = afterDcc.substringBefore(' ')
val verb = verbRaw.uppercase(Locale.ROOT)
if (verb != "CHAT" && verb != "SCHAT") return null

// SCHAT: TLS-wrapped chat session.
val secure = verb == "SCHAT"

val rest = afterDcc.substring(verbRaw.length).trimStart()
val parts = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
if (parts.size < 3) return null

val proto = parts[0]
val ipField = parts[1]
val port = parts[2].toIntOrNull() ?: return null
// Passive (port 0) chat is not supported.
if (port !in 1..65535) return null
val ip = parseDccAddress(ipField) ?: return null

return DccChatOffer(from = "?", protocol = proto, ip = ip, port = port, secure = secure)
}

private fun ipFromLong(v: Long): String {
val b1 = (v shr 24) and 255
val b2 = (v shr 16) and 255
val b3 = (v shr 8) and 255
val b4 = v and 255
return "$b1.$b2.$b3.$b4"
}

/**
* Parse a DCC offer's address field: a 32-bit integer, a dotted IPv4 quad or an IPv6 literal.
* Hostnames are rejected, so a crafted offer can't trigger a DNS lookup that would leak outside
* a SOCKS/Tor tunnel.
*/
internal fun parseDccAddress(ipField: String): String? {
val f = ipField.trim()
// IPv6 literal. May arrive bracketed (some clients wrap it in [ ]); strip those.
if (f.contains(':')) {
    val literal = f.removePrefix("[").removeSuffix("]")
    return if (isIpv6Literal(literal)) literal else null
}
if (f.contains('.')) {
    // Dotted quad: exactly four octets of one to three ASCII digits, each 0..255.
    val octets = f.split('.')
    if (octets.size != 4) return null
    if (octets.any { it.length !in 1..3 || !it.all { c -> c.isAsciiDigit() } || it.toInt() > 255 }) return null
    return f
}
// Classic 32-bit integer form (IPv4 only).
if (f.isEmpty() || !f.all { it.isAsciiDigit() }) return null
val v = f.toLongOrNull() ?: return null
if (v !in 0L..0xFFFFFFFFL) return null
return ipFromLong(v)
}

/**
* Syntactic IPv6 literal check with no name resolution. Accepts "::" compression and a trailing
* dotted IPv4 part; rejects scope ids. [validateRemoteIp] still screens the address afterwards.
*/
private fun isIpv6Literal(s: String): Boolean {
if (s.isEmpty() || s.length > 45 || s.contains('%')) return false

// Detach an optional embedded IPv4 tail and validate it; replace with two synthetic
// hex groups so the remainder can be counted as plain 16-bit groups below.
var work = s
val lastColon = s.lastIndexOf(':')
if (lastColon >= 0 && s.substring(lastColon + 1).contains('.')) {
    val v4 = s.substring(lastColon + 1)
    val octets = v4.split('.')
    if (octets.size != 4) return false
    for (p in octets) {
        if (p.isEmpty() || p.length > 3 || p.any { !it.isAsciiDigit() }) return false
        if ((p.toIntOrNull() ?: -1) !in 0..255) return false
    }
    work = s.substring(0, lastColon) + ":0:0"
}

// At most one "::".
val dc = work.indexOf("::")
if (dc != work.lastIndexOf("::")) return false

fun countGroups(part: String): Int? {
    if (part.isEmpty()) return 0
    val groups = part.split(':')
    for (g in groups) {
        if (g.isEmpty() || g.length > 4 || g.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    }
    return groups.size
}

return if (dc >= 0) {
    val l = countGroups(work.substring(0, dc)) ?: return false
    val r = countGroups(work.substring(dc + 2)) ?: return false
    // "::" stands in for >= 1 zero group, so the explicit groups must total < 8.
    l + r <= 7
} else {
    countGroups(work) == 8
}
}
