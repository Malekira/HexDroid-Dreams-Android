/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.R
import com.boxlabs.hexdroid.UiSettings
import com.boxlabs.hexdroid.readableThemeText

@Composable
internal fun MentionColorSettings(s: UiSettings, update: (UiSettings.() -> UiSettings) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var hex by remember(s.mentionHighlightColor) { mutableStateOf("#%06X".format(s.mentionHighlightColor and 0xFFFFFF)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(hex, { value ->
            hex = value
            if (value.matches(Regex("#[0-9a-fA-F]{6}")))
                update { copy(mentionHighlightColor = (0xFF000000L or value.drop(1).toLong(16)).toInt()) }
        }, singleLine = true, label = { Text(stringResource(R.string.p2_color)) },
            isError = !hex.matches(Regex("#[0-9a-fA-F]{6}")), modifier = Modifier.fillMaxWidth().testTag("mention-color-hex"))
        Surface(color = Color(s.mentionHighlightColor),
            contentColor = Color(readableThemeText(0xff000000.toInt(), s.mentionHighlightColor)),
            modifier = Modifier.fillMaxWidth().testTag("mention-color-preview")) {
            Text(stringResource(R.string.mention_color_preview), Modifier.padding(12.dp))
        }
        OutlinedButton(onClick = { picker = true },modifier = Modifier.testTag("mention-color-button")) { Text("Elegir color") }
        if (picker) AlertDialog(onDismissRequest = { picker = false },title = { Text("Color de menciones") },text = {
            LazyVerticalGrid(columns = GridCells.Fixed(8),modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(MIRC_PALETTE.indices.toList()) { index ->
                    Box(Modifier.size(32.dp).background(Color(MIRC_PALETTE[index]),MaterialTheme.shapes.extraSmall)
                        .clickable { update { copy(mentionHighlightColor = MIRC_PALETTE[index]) }; picker = false }
                        .testTag("mention-palette-" + index),contentAlignment = Alignment.Center) { }
                }
            }
        },confirmButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.close)) } })
    }
}
