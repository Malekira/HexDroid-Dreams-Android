/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.R
import com.boxlabs.hexdroid.RemoteBitmaps
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Shared loader boundary keeps UI behavior testable while production uses the bounded HTTPS cache. */
internal val LocalAvatarLoader = staticCompositionLocalOf<suspend (String, Long) -> ImageBitmap?> {
    { url, maxAge -> if (maxAge > 0L) RemoteBitmaps.fetchFresh(url, maxAge)?.asImageBitmap() else RemoteImage.fetch(url) }
}

@Composable
fun AvatarThumbnail(url: String, nick: String, modifier: Modifier = Modifier, refreshToken: Int = 0, refreshEveryMs: Long = 0L, onOpen: () -> Unit) {
    val loader = LocalAvatarLoader.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var image by remember(url, refreshToken) { mutableStateOf(RemoteImage.cached(url)) }
    LaunchedEffect(url, refreshToken, refreshEveryMs, loader, lifecycle) {
        if (refreshEveryMs <= 0L) { if (image == null) image = loader(url, 0L) }
        else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val loaded = loader(url, refreshEveryMs)
                image = loaded
                delay(if (loaded == null) minOf(refreshEveryMs, 30_000L) else refreshEveryMs)
            }
        }
    }
    Box(modifier.clip(CircleShape).clickable(onClick = onOpen).testTag("avatar-$nick"), contentAlignment = Alignment.Center) {
        image?.let {
            Image(it, stringResource(R.string.dreams_expand_avatar, nick), contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        } ?: Icon(Icons.Default.Person, stringResource(R.string.dreams_expand_avatar, nick), Modifier.fillMaxSize())
    }
}

@Composable
fun AvatarViewer(url: String, nick: String, onDismiss: () -> Unit,
                 load: suspend (String, Boolean) -> ImageBitmap? = { source, retry -> RemoteImage.fetchFull(source, true) }) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var loading by remember(url) { mutableStateOf(true) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, attempt) {
        loading = true
        image = load(url, attempt > 0)
        loading = false
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("avatar-viewer"),
        title = { Text(nick) },
        text = {
            Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(Modifier.testTag("avatar-loading"))
                    image != null -> Image(image!!, stringResource(R.string.dreams_full_avatar),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize().testTag("avatar-full-image"))
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(avatarFailureText(RemoteBitmaps.failure(url, fullSize = true))))
                        TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("avatar-retry")) { Text(stringResource(R.string.dreams_retry)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { attempt++ }) { Text("Actualizar foto") } })
}

@Composable
internal fun AvatarMessageRow(url: String?, nick: String, refreshEveryMs: Long, refreshToken: Int = 0, onOpen: () -> Unit, content: @Composable () -> Unit) {
    if (url == null) content()
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AvatarThumbnail(url, nick, Modifier.padding(end = 6.dp).size(28.dp), refreshToken = refreshToken, refreshEveryMs = refreshEveryMs, onOpen = onOpen)
        Box(Modifier.weight(1f)) { content() }
    }
}

internal fun avatarFailureText(failure: com.boxlabs.hexdroid.AvatarFailure?): Int = when (failure) {
    com.boxlabs.hexdroid.AvatarFailure.TOO_LARGE -> R.string.dreams_avatar_too_large
    com.boxlabs.hexdroid.AvatarFailure.HTTP -> R.string.dreams_avatar_http_error
    com.boxlabs.hexdroid.AvatarFailure.NOT_IMAGE -> R.string.dreams_avatar_not_image
    com.boxlabs.hexdroid.AvatarFailure.TIMEOUT -> R.string.dreams_avatar_timeout
    com.boxlabs.hexdroid.AvatarFailure.CONNECTION -> R.string.dreams_avatar_connection_error
    com.boxlabs.hexdroid.AvatarFailure.REDIRECT -> R.string.dreams_avatar_redirect_error
    null -> R.string.dreams_avatar_load_error
}
