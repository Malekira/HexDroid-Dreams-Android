/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.validChatZonaLogin

@Composable
internal fun ChatZonaAccountSettings(
    account: String, password: String, modes: String, keepModes: Boolean, external: Boolean,
    onAccount: (String)->Unit, onPassword: (String)->Unit, onModes: (String)->Unit,
    onKeep: (Boolean)->Unit, onExternal: (Boolean)->Unit,
) {
    Text("Login opcional. Se envía /login y se espera la confirmación antes de recuperar el nick y entrar a salas.",
        style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(account, onAccount, label = { Text("Nick de la cuenta (opcional)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("chatzona-account"))
    OutlinedTextField(password, onPassword, label = { Text("Clave de la cuenta (opcional)") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("chatzona-password"))
    if (!validChatZonaLogin(account.trim(),password))
        Text("Completa ambos campos o deja los dos vacíos. La clave no puede contener espacios ni saltos de línea.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Text("Modos después del login", style = MaterialTheme.typography.titleSmall)
    for ((mode,label) in listOf('i' to "+i · Invisible", 'I' to "+I", 'g' to "+g · Control de privados", 'R' to "+R · Solo usuarios registrados")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f))
            Switch(mode in modes, { enabled -> onModes(if (enabled) (modes + mode).toSet().joinToString("") else modes.replace(mode.toString(),"")) },
                modifier = Modifier.testTag("chatzona-mode-" + mode))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Guardar modos",Modifier.weight(1f)); Switch(keepModes,onKeep,modifier = Modifier.testTag("chatzona-keep-modes"))
    }
    Text("Configura set keepmodes on/off en el bot Nick.",style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
        Text("Permitir mensajes de usuarios de otras salas",modifier = Modifier.weight(1f))
        Switch(external,onExternal,modifier = Modifier.testTag("chatzona-external-messages"))
    }
}
