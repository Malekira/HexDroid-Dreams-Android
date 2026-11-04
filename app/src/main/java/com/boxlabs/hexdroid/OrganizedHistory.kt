/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.time.Instant
import java.time.ZoneId

data class OrganizedHistoryTarget(val network: String, val buffer: String, val line: String)

/** Presentation files are independent of the canonical archive used for replay, search and backups. */
internal fun organizedHistoryTarget(
    networkId: String, networkName: String, buffer: String, timeMs: Long,
    daily: Boolean, combined: Boolean, line: String, zone: ZoneId = ZoneId.systemDefault(),
): OrganizedHistoryTarget {
    val day = if (daily) "--" + Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate() else ""
    fun label(value: String) = value.replace(Regex("[\\r\\n\\t]"), " ")
    return if (combined) OrganizedHistoryTarget("Historial", "todo$day",
        "[${label(networkName)} · ${historyDigest(networkId).take(8)} / ${label(buffer)}] $line")
    else OrganizedHistoryTarget("${label(networkName)}--${historyDigest(networkId).take(8)}", "$buffer$day", line)
}
