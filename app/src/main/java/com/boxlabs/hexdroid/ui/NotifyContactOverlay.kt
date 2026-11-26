/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.PrivateAttemptAlert
import kotlinx.coroutines.delay

/** In-app banner; no draw-over-other-apps permission. Details remain in Notify. */
@Composable
internal fun NotifyContactOverlay(alert: PrivateAttemptAlert?, network: String,
    onDismiss: (Long) -> Unit, onOpen: () -> Unit) {
    if (alert == null) return
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(alert.id) { delay(6000); dismiss(alert.id) }
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),contentAlignment = Alignment.TopCenter) {
        ElevatedCard(Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("notify-floating")) {
            Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notify · " + network,style = MaterialTheme.typography.labelMedium)
                Text(alert.text,style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { onDismiss(alert.id) }) { Text("Cerrar") }
                    TextButton(onClick = { onDismiss(alert.id); onOpen() }) { Text("Ver Notify") }
                }
            }
        }
    }
}
