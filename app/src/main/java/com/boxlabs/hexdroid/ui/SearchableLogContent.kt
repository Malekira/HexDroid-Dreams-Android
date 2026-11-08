/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.HistoryPage
import kotlinx.coroutines.*

/** Shared viewer for the private archive and user-selected TXT/LOG document streams. */
@Composable
internal fun SearchableLogContent(
    title: String,
    readPage: suspend (String, Long) -> HistoryPage,
    onExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    var offset by rememberSaveable(title) { mutableLongStateOf(0L) }
    var previous by remember(title) { mutableStateOf<List<Long>>(emptyList()) }
    var page by remember(title) { mutableStateOf(HistoryPage(emptyList(), null)) }
    var loading by remember(title) { mutableStateOf(true) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(title, query, offset, refresh) {
        loading = true
        error = null
        try {
            delay(200)
            page = withContext(Dispatchers.IO) { readPage(query, offset) }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (e: Exception) { page = HistoryPage(emptyList(), null); error = "No se pudo leer el log: ${e.message}" }
        finally { loading = false }
    }
    val terms = remember(query) { query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val mark = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        OutlinedTextField(query, { query = it; offset = 0; previous = emptyList() },
            label = { Text("Buscar palabra, nick o fecha (AAAA-MM-DD)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("log-query"))
        Row {
            if (onExport != null) TextButton(onClick = onExport) { Text("Exportar TXT") }
            TextButton(onClick = { offset = 0; previous = emptyList(); refresh++ }, enabled = !loading) { Text("Actualizar") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("log-loading"))
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (!loading && error == null) {
            Text(if (terms.isNotEmpty()) "Coincidencias en esta página: ${page.lines.size}" else "Líneas en esta página: ${page.lines.size}",
                modifier = Modifier.testTag("log-result-count"))
            if (page.lines.isEmpty()) Text(if (terms.isEmpty()) "Log vacío" else "Sin coincidencias", Modifier.testTag("log-empty"))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("log-lines")) {
            if (!loading) items(page.lines.size) { index ->
                val line = page.lines[index]
                val text = remember(line, terms, mark) {
                    buildAnnotatedString {
                        append(line)
                        for (term in terms) Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(line).forEach {
                            addStyle(mark, it.range.first, it.range.last + 1)
                        }
                    }
                }
                SelectionContainer { Text(text, Modifier.fillMaxWidth().padding(vertical = 5.dp)) }
            }
        }
        Row {
            TextButton(onClick = { offset = 0; previous = emptyList() }, enabled = !loading && offset != 0L) { Text("Inicio") }
            TextButton(onClick = { offset = previous.last(); previous = previous.dropLast(1) }, enabled = !loading && previous.isNotEmpty()) { Text("Anterior") }
            TextButton(onClick = { page.next?.let { previous = previous + offset; offset = it } },
                enabled = !loading && page.next != null, modifier = Modifier.testTag("log-next")) { Text("Siguiente") }
        }
    }
}
