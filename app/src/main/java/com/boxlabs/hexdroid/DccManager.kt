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

@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package com.boxlabs.hexdroid

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import com.boxlabs.hexdroid.connection.ProxyConfig
import com.boxlabs.hexdroid.connection.SocksProxy
import java.util.Collections

/** Parsed CTCP DCC CHAT offer. */
data class DccChatOffer(
    /** Network id this offer belongs to (filled by the ViewModel). */
    val netId: String = "",
    val from: String,
    val protocol: String = "chat",
    val ip: String,
    val port: Int,
    /** Passive/reverse token (rare; included for forward compatibility). */
    val token: Long? = null,
    /** SDCC / SCHAT: session is TLS-wrapped. */
    val secure: Boolean = false,
) {
    val isPassive: Boolean get() = port == 0 && token != null
}



class DccManager(ctx: Context) {

    // Avoid leaking an Activity context.
    private val ctx: Context = ctx.applicationContext

    
    private fun openOutbound(host: String, port: Int, proxy: ProxyConfig): Socket {
        require(port in 1..65535) { "DCC: invalid port $port" }
        return if (proxy.enabled) {
            // The proxy resolves [host] remotely, so a literal IP or a hostname both work and
            // no on-device DNS lookup leaks. Reuse the IRC socket timeouts for parity.
            SocksProxy.connect(
                cfg = proxy,
                destHost = host,
                destPort = port,
                connectTimeoutMs = com.boxlabs.hexdroid.connection.ConnectionConstants.SOCKET_CONNECT_TIMEOUT_MS,
                soTimeoutMs = 0,  // DCC transfers are long-lived; rely on the transfer loop, not soTimeout
                tcpNoDelay = false,
                keepAlive = true,
            )
        } else {
            val sock = Socket()
            try {
                sock.connect(
                    InetSocketAddress(resolveConnectTarget(host), port),
                    com.boxlabs.hexdroid.connection.ConnectionConstants.SOCKET_CONNECT_TIMEOUT_MS,
                )
            } catch (t: Throwable) {
                runCatching { sock.close() }
                throw t
            }
            sock
        }
    }

    /**
     * Resolve a connect target, attaching a scope id when [host] is a bare IPv6 link-local literal
     * (fe80::...). DCC advertises link-local without a zone id (it is meaningful only on the sender's
     * own host); connecting to one requires telling the kernel which interface to egress on. On the
     * single-link networks where link-local DCC happens there is exactly one such interface, so we
     * bind the address to it.
     */
    private fun resolveConnectTarget(host: String): java.net.InetAddress {
        val addr = java.net.InetAddress.getByName(host)
        if (addr is Inet6Address && addr.isLinkLocalAddress && addr.scopeId == 0 && addr.scopedInterface == null) {
            linkLocalInterface()?.let { nif ->
                return Inet6Address.getByAddress(null, addr.address, nif)
            }
        }
        return addr
    }

    /**
     * The single up, non-loopback interface with an IPv6 link-local address, or null. Used to scope
     * a bare fe80:: DCC target. Falls back to ConnectivityManager when NetworkInterface finds none.
     */
    private fun linkLocalInterface(): NetworkInterface? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
                for (iface in Collections.list(ifaces)) {
                    try {
                        if (iface.isUp && !iface.isLoopback &&
                            Collections.list(iface.inetAddresses).any { it is Inet6Address && it.isLinkLocalAddress }
                        ) return iface
                    } catch (_: Throwable) {
                        // skip this interface, keep scanning the rest
                    }
                }
            }
        } catch (_: Throwable) {
            // fall through to the ConnectivityManager name fallback
        }
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val name = if (cm != null && net != null) cm.getLinkProperties(net)?.interfaceName else null
            if (!name.isNullOrEmpty()) NetworkInterface.getByName(name) else null
        } catch (_: Throwable) { null }
    }

    /**
     * Rejects IPs that cannot be a DCC target: loopback, link-local, wildcard and multicast.
     * Private (RFC 1918) addresses are allowed for LAN DCC.
     */
    private fun validateRemoteIp(ip: String) {
        val addr = try {
            java.net.InetAddress.getByName(ip)
        } catch (_: Throwable) {
            throw IllegalArgumentException("DCC: invalid IP address: $ip")
        }
        if (addr.isLoopbackAddress)
            throw IllegalArgumentException("DCC: refusing connection to loopback address $ip")
        if (addr.isAnyLocalAddress)
            throw IllegalArgumentException(
                "DCC: peer advertised an unusable address ($ip). Its client could not determine a " +
                "routable local address, often an IPv6-only network with no global/ULA address. " +
                "Ask them to retry, or use a network with a routable address."
            )
        if (addr.isMulticastAddress)
            throw IllegalArgumentException("DCC: refusing connection to multicast address $ip")
    }

    
    private fun enumInterfaceAddrs(): List<Pair<String, java.net.InetAddress>> {
        val out = ArrayList<Pair<String, java.net.InetAddress>>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
                for (iface in Collections.list(ifaces)) {
                    try {
                        if (!iface.isUp || iface.isLoopback) continue
                        for (a in Collections.list(iface.inetAddresses)) out.add(iface.name to a)
                    } catch (_: Throwable) {
                        // skip this interface, keep the rest
                    }
                }
            }
        } catch (_: Throwable) {
            // fall through to the ConnectivityManager fallback below
        }
        if (out.isNotEmpty()) return out
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val lp = if (cm != null && net != null) cm.getLinkProperties(net) else null
            if (lp != null) {
                val ifName = lp.interfaceName ?: ""
                for (la in lp.linkAddresses) {
                    val a = la.address ?: continue
                    out.add(ifName to a)
                }
            }
        } catch (_: Throwable) {
            // nothing usable from either path
        }
        return out
    }

    // local IPv4 in dotted notation
    fun localIpv4OrNull(): String? =
        enumInterfaceAddrs().firstOrNull { (_, a) ->
            a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress
        }?.second?.hostAddress

    // local IPv4 as an unsigned 32-bit integer (decimal string in CTCP)
    fun localIpv4AsInt(): Long {
        val ip = localIpv4OrNull() ?: return 0L
        return ipv4ToLongBestEffort(ip)
    }

    /**
     * First global or ULA IPv6 address, or null when the device has none. Loopback, link-local,
     * wildcard and multicast are skipped, and any scope id is stripped.
     */
    fun localIpv6OrNull(): String? =
        enumInterfaceAddrs().firstOrNull { (_, a) ->
            a is Inet6Address &&
                !a.isLoopbackAddress && !a.isLinkLocalAddress &&
                !a.isAnyLocalAddress && !a.isMulticastAddress
        }?.second?.hostAddress?.substringBefore('%')

    /**
     * First IPv6 link-local address (fe80::...) in colon notation with any scope id stripped, or
     * null. Used ONLY as a last resort for DCC on an isolated link (Wi-Fi Direct / ad-hoc LAN /
     * a switch with no router handing out ULA or global prefixes), where link-local is the sole
     * usable address. The scope is stripped for the wire because a zone id is meaningful only on
     * the originating host; the connecting side re-attaches its own scope (see resolveConnectTarget).
     */
    fun localIpv6LinkLocalOrNull(): String? =
        enumInterfaceAddrs().firstOrNull { (_, a) ->
            a is Inet6Address && a.isLinkLocalAddress && !a.isMulticastAddress
        }?.second?.hostAddress?.substringBefore('%')

    /**
     * The address to advertise in a DCC offer: the IPv4 integer form whenever an IPv4 address
     * exists, since more clients understand it, otherwise the IPv6 literal. "0" when none is
     * usable.
     */
    fun dccAddressField(): String {
        localIpv4OrNull()?.let { return ipv4ToLongBestEffort(it).toString() }
        localIpv6OrNull()?.let { return it }
        // Isolated IPv6 link (no IPv4, ULA or global IPv6): advertise the link-local address rather
        // than "0", which a peer would parse as the 0.0.0.0 wildcard and refuse.
        localIpv6LinkLocalOrNull()?.let { return it }
        // Last resort: interface enumeration returned nothing usable (observed on some IPv6-only
        // Android Wi-Fi setups, where it left us advertising "0" and the peer then refused the
        // resulting 0.0.0.0). Ask the OS routing table which local address it would use to reach
        // the outside world. This does not enumerate interfaces and sends no packet (a UDP
        // "connect" only pins the route), so it works where both enumeration paths fail.
        routedLocalAddress()?.let { return it }
        return "0"
    }

    /**
     * Local address the OS routing table would use to reach the internet, formatted for the DCC
     * address field (IPv4 as a 32-bit integer, IPv6 as a colon literal), or null if none.
     * Prefers IPv4 (understood by far more clients), then IPv6.
     */
    private fun routedLocalAddress(): String? {
        routedLocalFor("8.8.8.8")?.let { return it }              // IPv4 route probe
        routedLocalFor("2001:4860:4860::8888")?.let { return it } // IPv6 route probe
        return null
    }

    private fun routedLocalFor(target: String): String? {
        return try {
            java.net.DatagramSocket().use { s ->
                // No DNS (target is a literal) and no packet leaves the device; connect() only
                // resolves the route so localAddress reflects the chosen source address.
                s.connect(java.net.InetAddress.getByName(target), 9)
                val a = s.localAddress ?: return null
                if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isMulticastAddress || a.isLinkLocalAddress) return null
                when (a) {
                    is Inet4Address -> a.hostAddress?.let { ipv4ToLongBestEffort(it).toString() }
                    is Inet6Address -> a.hostAddress?.substringBefore('%')
                    else -> null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    
    suspend fun startChat(
        portMin: Int,
        portMax: Int,
        secure: Boolean = false,
        onClient: suspend (addrField: String, port: Int) -> Unit
    ): Socket {
        val caller = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) {
            val ss = bindFirstAvailable(portMin, portMax)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { runCatching { ss.close() } }
            val rawSock = try {
                onClient(dccAddressField(), ss.localPort)
                ss.soTimeout = 45_000
                try {
                    ss.accept()
                } catch (_: SocketTimeoutException) {
                    throw IOException("DCC CHAT timed out waiting for peer to connect")
                }
            } finally {
                cancelHandle?.dispose()
                runCatching { ss.close() }
            }
            closeIfAbandoned(caller, rawSock)
            try {
                ensureActive()
                rawSock.tcpNoDelay = true
                rawSock.keepAlive = true
            } catch (t: Throwable) {
                runCatching { rawSock.close() }
                throw t
            }
            if (secure) DccTls.wrap(rawSock, server = true) else rawSock
        }
    }

    /**
     * Active DCC CHAT: connect to the offered ip:port, TLS-wrapped as the client for SCHAT. The
     * socket is closed if the caller is cancelled or fails before it finishes.
     */
    suspend fun connectChat(offer: DccChatOffer, proxy: ProxyConfig = ProxyConfig()): Socket {
        val caller = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) {
            validateRemoteIp(offer.ip)
            val rawSock = openOutbound(offer.ip, offer.port, proxy)
            closeIfAbandoned(caller, rawSock)
            try {
                rawSock.tcpNoDelay = true
                rawSock.keepAlive = true
            } catch (t: Throwable) {
                runCatching { rawSock.close() }
                throw t
            }
            if (offer.secure) DccTls.wrap(rawSock, server = false) else rawSock
        }
    }

    /**
     * Close [sock] when [caller] is cancelled or fails, including after it has been returned but
     * before the caller resumed. Also aborts a TLS handshake in progress.
     */
    private fun closeIfAbandoned(caller: Job?, sock: Socket) {
        caller?.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) runCatching { sock.close() }
        }
    }

    
    private fun bindFirstAvailable(min: Int, max: Int): ServerSocket {
        val a = minOf(min, max).coerceIn(1, 65535)
        val b = maxOf(min, max).coerceIn(1, 65535)
        for (p in a..b) {
            try {
                return ServerSocket(p)
            } catch (_: Throwable) {
                // try next
            }
        }
        throw IllegalStateException("No free port in $a..$b")
    }

    private fun ipv4ToLongBestEffort(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        return try {
            var out = 0L
            for (p in parts) out = (out shl 8) or (p.toLong() and 0xFFL)
            out
        } catch (_: Throwable) {
            0L
        }
    }

}
