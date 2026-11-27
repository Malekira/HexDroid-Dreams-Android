/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.*
import com.boxlabs.hexdroid.R
import com.boxlabs.hexdroid.data.NetworkProfile

@Composable
internal fun NotifyContactsDialog(
    profile: NetworkProfile, connection: NetConnState?, notificationsEnabled: Boolean,
    onSave: (List<String>, Boolean) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportSnapshot by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("No output stream")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(exportSnapshot) }
            }.isSuccess }
            Toast.makeText(context,context.getString(if (ok) R.string.contact_export_ok else R.string.contact_export_failed),Toast.LENGTH_LONG).show()
        }
    }
    val mapping = connection?.avatarCaseMapping ?: "ascii"
    var contacts by remember(profile.id) { mutableStateOf(profile.notifyContacts) }
    var alerts by remember(profile.id) { mutableStateOf(profile.notifyContactAlerts) }
    var entry by remember { mutableStateOf("") }
    fun entries(): List<String> = entry.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
    val invalid = entry.isNotBlank() && entries().any { !isReplyNick(bareMember(it)) || it.length > 130 }
    fun add() {
        if (!invalid) { contacts = cleanNotifyContacts(contacts + entries(),mapping); entry = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_notify)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.name,style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.contacts_notify_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(entry, { entry = it }, singleLine = true, isError = invalid,
                    label = { Text(stringResource(R.string.contact_nick)) },
                    modifier = Modifier.fillMaxWidth().testTag("notify-contact-input"))
                TextButton(onClick = ::add, enabled = entry.isNotBlank() && !invalid && contacts.size < MAX_NOTIFY_CONTACTS,
                    modifier = Modifier.testTag("notify-add-contact")) { Text(stringResource(R.string.contact_add)) }
                OutlinedButton(onClick = {
                    exportSnapshot = notifyContactsDocument(profile,cleanNotifyContacts(contacts + entries(),mapping),alerts)
                    exporter.launch("HexDreams-notify.json")
                },enabled = !invalid,modifier = Modifier.testTag("notify-export")) { Text(stringResource(R.string.contact_export)) }
                if (invalid) Text(stringResource(R.string.contact_invalid),color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.contact_alerts),Modifier.weight(1f))
                    Switch(alerts,{ alerts = it },modifier = Modifier.testTag("notify-contact-alerts"))
                }
                if (!notificationsEnabled) Text(stringResource(R.string.contact_notifications_disabled),
                    style = MaterialTheme.typography.bodySmall)
                if (connection?.connected != true) Text(stringResource(R.string.contact_connect_to_track),
                    style = MaterialTheme.typography.bodySmall)
                else if (connection.monitorLimit < 0) Text(stringResource(R.string.contact_monitor_unsupported),
                    style = MaterialTheme.typography.bodySmall)
                else if (contacts.size > connection.monitorLimit) Text(stringResource(R.string.contact_monitor_limit,connection.monitorLimit),
                    style = MaterialTheme.typography.bodySmall)
                if (contacts.isEmpty()) Text(stringResource(R.string.contact_empty))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    itemsIndexed(contacts,key = { _,nick -> ircCasefold(nick,mapping) }) { index,nick ->
                        val presence = if (connection?.connected == true && index < connection.monitorLimit)
                            connection.contactPresence[ircCasefold(nick,mapping)] else null
                        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(nick)
                                Text(stringResource(when(presence) {
                                    true -> R.string.contact_online
                                    false -> R.string.contact_offline
                                    null -> R.string.contact_unknown
                                }),style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { contacts = contacts - nick },
                                modifier = Modifier.testTag("notify-remove-" + nick)) { Text(stringResource(R.string.contact_remove)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !invalid,
            onClick = { onSave(cleanNotifyContacts(contacts + entries(),mapping),alerts) },
            modifier = Modifier.testTag("notify-save")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
