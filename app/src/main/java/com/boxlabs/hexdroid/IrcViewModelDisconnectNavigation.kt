/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import kotlinx.coroutines.flow.update

/** Called only after the user's disconnect has shut down its collector and socket. */
internal fun IrcViewModel.closeDisconnectedNetworkViews(netId: String) {
    val prefix = "$netId::"
    val before = _state.value
    // If another network was opened while QUIT was being delivered, keep that view.
    val returnToNetworks = before.selectedBuffer.startsWith(prefix) ||
        (before.selectedBuffer.isBlank() && before.activeNetworkId == netId)
    whoisBlockTimeouts.keys.filter { it.first == netId }.toList().forEach { (_, id) -> flushWhoisBlock(netId, id) }
    // Closing a session must not discard unsent text or remove logs on disk.
    _state.value.buffers.keys.filter { it.startsWith(prefix) }.forEach { removeBuffer(it, preserveDraft = true) }
    _state.update { st ->
        syncActiveNetworkSummary(st.copy(
            screen = if (returnToNetworks) AppScreen.NETWORKS else st.screen,
            activeNetworkId = if (st.activeNetworkId == netId) {
                splitKey(st.selectedBuffer).first.takeIf { it.isNotBlank() && it != netId }
            } else st.activeNetworkId,
            closedNetworkViews = st.closedNetworkViews + netId,
            whoisWindows = st.whoisWindows.filterNot { it.network == netId },
            privateAttemptAlerts = st.privateAttemptAlerts.filterNot { it.network == netId },
            findOverlay = st.findOverlay?.takeUnless { it.bufferKey.startsWith(prefix) },
        ))
    }
}
