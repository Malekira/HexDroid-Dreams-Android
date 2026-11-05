/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.R
import com.boxlabs.hexdroid.RemoteBitmaps
import com.boxlabs.hexdroid.validAvatarUrl

@Composable
internal fun ProfileAvatarField(url: String, onChange: (String) -> Unit,
                                source: String? = url.trim().takeIf { it.isNotEmpty() && validAvatarUrl(it) },
                                nick: String = "", downloadsAllowed: Boolean = false, proxied: Boolean = false) {
    val valid = validAvatarUrl(url.trim())
    val mayLoad = downloadsAllowed && !proxied
    val loader = LocalAvatarLoader.current
    var attempt by remember(source) { mutableIntStateOf(0) }
    var loading by remember(source, mayLoad, valid) { mutableStateOf(mayLoad && source != null && valid) }
    var image by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(source) { mutableStateOf<com.boxlabs.hexdroid.AvatarFailure?>(null) }
    var expanded by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source, mayLoad, valid, attempt, loader) {
        image = null; failure = null; loading = false
        if (source == null || !valid || !mayLoad) return@LaunchedEffect
        loading = true
        try {
            if (attempt == 0) kotlinx.coroutines.delay(350)
            if (attempt > 0) RemoteBitmaps.invalidate(source)
            image = loader(source, if (attempt > 0) 0L else com.boxlabs.hexdroid.CHATZONA_AVATAR_REFRESH_MS)
            if (image == null) failure = RemoteBitmaps.failure(source)
        } finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(url, onChange, label = { Text(stringResource(R.string.dreams_avatar_url)) },
            singleLine = true, isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag("network-profile-avatar"))
        if (!valid) Text(stringResource(R.string.dreams_avatar_invalid), color = MaterialTheme.colorScheme.error)
        if (source != null && valid) {
            if (url.isBlank()) {
                Text(stringResource(R.string.dreams_avatar_automatic), style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(source, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-avatar-source")) }
                TextButton(onClick = { onChange(source) }, modifier = Modifier.testTag("profile-avatar-use-source")) {
                    Text(stringResource(R.string.dreams_avatar_use_source))
                }
            }
            when {
                !mayLoad -> Text(stringResource(if (proxied) R.string.dreams_avatar_proxy_disabled else R.string.dreams_avatar_option_disabled), modifier = Modifier.testTag("profile-avatar-disabled"))
                loading -> CircularProgressIndicator(Modifier.size(32.dp).testTag("profile-avatar-loading"))
                image != null -> {
                    Image(image!!, stringResource(R.string.dreams_full_avatar), contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clickable { expanded = true }.testTag("profile-avatar-preview"))
                    TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.dreams_avatar_enlarge)) }
                }
                else -> Text(stringResource(avatarFailureText(failure)), modifier = Modifier.testTag("profile-avatar-error"))
            }
            if (mayLoad) TextButton(onClick = { attempt++ }, enabled = !loading, modifier = Modifier.testTag("profile-avatar-refresh")) {
                Text(stringResource(R.string.dreams_avatar_refresh))
            }
        }
        Text(stringResource(R.string.dreams_avatar_profile_help), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dreams_avatar_privacy), style = MaterialTheme.typography.bodySmall)
    }
    if (expanded && source != null && mayLoad) AvatarViewer(source, nick, onDismiss = { expanded = false; attempt++ })
}
