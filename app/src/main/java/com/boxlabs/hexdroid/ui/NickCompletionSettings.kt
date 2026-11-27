/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxlabs.hexdroid.NickCompletionStyle
import com.boxlabs.hexdroid.cleanCompletionTemplate

/** Keep the placeholder intact when its displayed text is selected for an IRC color. */
internal fun colorCompletionSelection(value: TextFieldValue, color: Int): TextFieldValue {
    val token = value.text.indexOf("{nick}")
    val selection = value.selection
    val whole = if (!selection.collapsed && token >= 0 && selection.max > token && selection.min < token + 6)
        TextRange(minOf(selection.min, token), maxOf(selection.max, token + 6)) else selection
    return colourFormat(value.copy(selection = whole), color, null)
}

@Composable
fun NickCompletionSettings(value: NickCompletionStyle, onChange: (NickCompletionStyle) -> Unit, sampleNick: String = "TuNick") {
    var expanded by remember { mutableStateOf(false) }
    var example by remember { mutableStateOf(sampleNick.ifBlank { "TuNick" }) }
    var start by remember { mutableStateOf(TextFieldValue(value.startTemplate)) }
    var middle by remember { mutableStateOf(TextFieldValue(value.middleTemplate)) }
    var activeField by remember { mutableStateOf(0) }
    var picker by remember { mutableStateOf<Int?>(null) } // 0: palette, 1: selected text
    val transformation = remember { MircInputTransformation() }
    LaunchedEffect(value.startTemplate) { if (start.text != value.startTemplate) start = TextFieldValue(value.startTemplate, TextRange(value.startTemplate.length)) }
    LaunchedEffect(value.middleTemplate) { if (middle.text != value.middleTemplate) middle = TextFieldValue(value.middleTemplate, TextRange(value.middleTemplate.length)) }
    fun update(field: Int, edited: TextFieldValue) {
        val text = cleanCompletionTemplate(edited.text)
        val safe = edited.copy(text = text, selection = TextRange(edited.selection.start.coerceIn(0,text.length), edited.selection.end.coerceIn(0,text.length)))
        if (field == 0) { start = safe; onChange(value.copy(startTemplate = text)) }
        else { middle = safe; onChange(value.copy(middleTemplate = text)) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("completion-settings")) {
            Text(if (expanded) "Autocompletado de nicks ▴" else "Autocompletado de nicks ▾")
        }
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activar autocompletado", Modifier.weight(1f))
                Switch(value.enabled, { onChange(value.copy(enabled = it)) })
            }
            Text("Usa {nick} para el nick. Añade adornos, símbolos o emojis. Selecciona texto y pulsa Colorear selección.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(start, { activeField = 0; update(0,it) },
                label = { Text("Al inicio del mensaje") }, singleLine = true, visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeField = 0 }.testTag("completion-start"))
            OutlinedTextField(middle, { activeField = 1; update(1,it) },
                label = { Text("Dentro del mensaje") }, singleLine = true, visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeField = 1 }.testTag("completion-middle"))
            OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.testTag("completion-color-selection")) { Text("Colorear selección") }
            Text("Colores del nick (se alternan por letra)", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                value.palette().forEachIndexed { position, code ->
                    val color = Color(MIRC_PALETTE[code])
                    Surface(color = color, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable {
                            onChange(value.copy(colors = value.palette().filterIndexed { i,_ -> i != position }.joinToString(",")))
                        }.semantics { contentDescription = "Quitar color IRC " + code.toString().padStart(2,'0') }) {
                        Text(code.toString().padStart(2,'0') + " ×", color = if (color.luminance() > .4f) Color.Black else Color.White,
                            modifier = Modifier.padding(8.dp))
                    }
                }
                OutlinedButton(onClick = { picker = 0 }, enabled = value.palette().size < 16,
                    modifier = Modifier.testTag("completion-add-color")) { Text("Añadir color IRC") }
            }
            Text("Solo colores IRC 00–98. Vacío: sin color propio.", style = MaterialTheme.typography.bodySmall)
            for ((label, checked, change) in listOf(
                Triple("Negrita", value.bold, { b: Boolean -> onChange(value.copy(bold = b)) }),
                Triple("Cursiva", value.italic, { b: Boolean -> onChange(value.copy(italic = b)) }),
                Triple("Subrayado", value.underline, { b: Boolean -> onChange(value.copy(underline = b)) }),
            )) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); Switch(checked, change)
            }
            OutlinedTextField(example, { example = it.filterNot { c -> c.isISOControl() }.take(40) },
                label = { Text("Nick para la vista previa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Vista previa", style = MaterialTheme.typography.labelMedium)
            for ((background, foreground) in listOf(Color.White to Color.Black, Color(0xff161b22) to Color.White)) {
                Surface(color = background, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text(buildAnnotatedString {
                        appendIrcStyledLinkified(value.render(example.ifBlank { "TuNick" }, activeField == 0, false, false),
                            SpanStyle(), true, false)
                    }, color = foreground, fontSize = 22.sp, modifier = Modifier.padding(12.dp).testTag("nick-completion-preview"))
                }
            }
            TextButton(onClick = { onChange(NickCompletionStyle()) }) { Text("Restablecer autocompletado") }
        }
    }
    picker?.let { destination ->
        AlertDialog(onDismissRequest = { picker = null }, title = { Text("Colores IRC") }, text = {
            LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(MIRC_PALETTE.indices.toList()) { code ->
                    val color = Color(MIRC_PALETTE[code])
                    Box(Modifier.size(32.dp).background(color, MaterialTheme.shapes.extraSmall)
                        .clickable {
                            if (destination == 0) onChange(value.copy(colors = (value.palette() + code).joinToString(",")))
                            else update(activeField, colorCompletionSelection(if (activeField == 0) start else middle, code))
                            picker = null
                        }.testTag("completion-irc-color-" + code),
                        contentAlignment = Alignment.Center) {
                        Text(code.toString().padStart(2,'0'), fontSize = 11.sp,
                            color = if (color.luminance() > .4f) Color.Black else Color.White)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = null }) { Text("Cerrar") } })
    }
}
