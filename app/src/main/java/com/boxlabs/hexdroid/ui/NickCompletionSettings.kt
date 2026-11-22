/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.NickCompletionStyle

@Composable
fun NickCompletionSettings(value: NickCompletionStyle, onChange: (NickCompletionStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("completion-settings")) {
            Text(if (expanded) "Autocompletado de nicks ▴" else "Autocompletado de nicks ▾")
        }
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activar autocompletado", Modifier.weight(1f))
                Switch(value.enabled, { onChange(value.copy(enabled = it)) })
            }
            Text("Usa {nick} para colocar el nick. Puedes cambiar símbolos, texto y espacios.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value.startTemplate, { onChange(value.copy(startTemplate = it.take(160))) },
                label = { Text("Al inicio del mensaje") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("completion-start"))
            OutlinedTextField(value.middleTemplate, { onChange(value.copy(middleTemplate = it.take(160))) },
                label = { Text("Dentro del mensaje") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("completion-middle"))
            OutlinedTextField(value.colors, { onChange(value.copy(colors = it.take(256))) },
                label = { Text("Colores HEX separados por comas") },
                supportingText = { Text("Ejemplo: #FF3399, #33CCFF, #9966FF. Vacío: sin color.") },
                modifier = Modifier.fillMaxWidth().testTag("completion-colors"))
            if (value.colors.isNotBlank() && value.palette().isEmpty())
                Text("Introduce colores de seis cifras: #RRGGBB", color = MaterialTheme.colorScheme.error)
            for ((label, checked, change) in listOf(
                Triple("Negrita", value.bold, { b: Boolean -> onChange(value.copy(bold = b)) }),
                Triple("Cursiva", value.italic, { b: Boolean -> onChange(value.copy(italic = b)) }),
                Triple("Subrayado", value.underline, { b: Boolean -> onChange(value.copy(underline = b)) }),
            )) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); Switch(checked, change)
            }
            Text("Vista previa", style = MaterialTheme.typography.labelMedium)
            Text(androidx.compose.ui.text.buildAnnotatedString {
                appendIrcStyledLinkified(value.render("MiNick", true, false, false),
                    SpanStyle(color = Color.Unspecified), true, false)
            })
        }
    }
}
