/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxlabs.hexdroid.R
import com.boxlabs.hexdroid.ThemeRole
import com.boxlabs.hexdroid.ui.theme.LocalPersonalColors
import com.boxlabs.hexdroid.ui.theme.PersonalArea

internal data class ConversationTab(
    val key: String,
    val label: String,
    val unread: Int,
    val highlighted: Boolean,
    val isServer: Boolean,
)

/** Small top strip; Close is a long-press action so it cannot steal a selection tap. */
@Composable
internal fun CompactConversationTabs(
    tabs: List<ConversationTab>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    if (tabs.isEmpty()) return
    val scroll = rememberLazyListState()
    LaunchedEffect(selected, tabs.map { it.key }) {
        val index = tabs.indexOfFirst { it.key == selected }
        if (index >= 0) scroll.scrollToItem(index)
    }
    PersonalArea(ThemeRole.TABS) {
        val colors = LocalPersonalColors.current
        val background = colors.color(ThemeRole.TABS, MaterialTheme.colorScheme.surface)
        LazyRow(
            state = scroll,
            modifier = Modifier.fillMaxWidth().height(28.dp).clipToBounds()
                .background(background).testTag("conversation-tabs"),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(tabs, key = { it.key }) { tab ->
                val active = tab.key == selected
                var menu by remember { mutableStateOf(false) }
                val fill = if (active) MaterialTheme.colorScheme.primaryContainer else background
                val foreground = colors.text(ThemeRole.UI_TEXT, fill, MaterialTheme.colorScheme.onSurface)
                val shape = RoundedCornerShape(4.dp)
                Box {
                    Row(
                        modifier = Modifier.height(28.dp).widthIn(min = 32.dp, max = 160.dp)
                            .background(fill, shape)
                            .border(1.dp, if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape)
                            .focusHighlight(shape)
                            .semantics { this.selected = active }
                            .combinedClickable(
                                role = Role.Tab,
                                onClick = { onSelect(tab.key) },
                                onLongClick = if (tab.isServer) null else ({ menu = true }),
                            )
                            .testTag("conversation-tab:${tab.key}")
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(tab.label, fontSize = 11.sp, lineHeight = 13.sp, color = foreground,
                            fontWeight = if (active || tab.isServer) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (tab.unread > 0) Text(if (tab.unread > 99) "99+" else tab.unread.toString(),
                            fontSize = 10.sp, lineHeight = 12.sp,
                            color = if (tab.highlighted) MaterialTheme.colorScheme.error else foreground)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_cd_close_label, tab.label)) },
                            onClick = { menu = false; onClose(tab.key) },
                        )
                    }
                }
            }
        }
    }
}
