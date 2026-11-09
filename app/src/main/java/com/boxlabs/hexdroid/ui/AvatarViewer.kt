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

/** Shared loader boundary keeps UI behavior testable while production uses the bounded HTTPS cache. */
internal val DefaultAvatarLoader: suspend (String, Long) -> ImageBitmap? = { url, age -> RemoteBitmaps.fetchFresh(url, if(age>0) age else 60_000L)?.asImageBitmap() }
internal val LocalAvatarLoader = staticCompositionLocalOf { DefaultAvatarLoader }
@Composable
internal fun avatarLoader(scope: String, configuredHost: String?, wifiOnly: Boolean = false): suspend (String,Long)->ImageBitmap? {
    val injected=LocalAvatarLoader.current
    val context=androidx.compose.ui.platform.LocalContext.current
    return remember<suspend (String,Long)->ImageBitmap?>(injected,scope,configuredHost,wifiOnly,context) {
        if(injected !== DefaultAvatarLoader) injected else { url: String, age: Long ->
            if(wifiOnly && !isOnWifi(context)) null else RemoteBitmaps.fetchFresh(url,if(age>0) age else 60_000L,scope,configuredHost)?.asImageBitmap()
        }
    }
}

@Composable
fun AvatarThumbnail(url: String, nick: String, modifier: Modifier = Modifier, refreshToken: Int = 0, refreshEveryMs: Long = 0L, scope: String = "", configuredHost: String? = null, wifiOnly: Boolean = false, onOpen: () -> Unit) {
    val loader = avatarLoader(scope,configuredHost,wifiOnly)
    // Retain the displayed bitmap during refresh, and never poll on a timer.
    // The legacy refreshEveryMs argument is only a cache-age budget now.
    var image by remember(url,scope) { mutableStateOf(RemoteBitmaps.cached(url,scope=scope)?.asImageBitmap()) }
    LaunchedEffect(url,scope,refreshToken,loader) {
        val loaded=loader(url,if(refreshEveryMs>0L) refreshEveryMs else 60_000L)
        if(loaded!=null) image=loaded
    }
    Box(modifier.clip(CircleShape).clickable(onClick = onOpen).testTag("avatar-$nick"), contentAlignment = Alignment.Center) {
        image?.let {
            Image(it, stringResource(R.string.dreams_expand_avatar, nick), contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        } ?: Text(nick.trimStart('~','&','@','%','+').take(2).uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AvatarViewer(url: String, nick: String, onDismiss: () -> Unit,
                 scope: String = "", refreshToken: Int = 0, load: (suspend (String, Boolean) -> ImageBitmap?)? = null) {
    var attempt by remember(url,scope) { mutableIntStateOf(0) }
    var loading by remember(url,scope,refreshToken) { mutableStateOf(true) }
    var image by remember(url,scope,refreshToken) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, scope, refreshToken, attempt) {
        loading = true
        image = if(load!=null) load(url, attempt > 0) else RemoteBitmaps.fetch(url,fullSize=true,retry=true,scope=scope)?.asImageBitmap()
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
                        Text(nick.trimStart('~','&','@','%','+').take(2).uppercase(),style=MaterialTheme.typography.headlineMedium)
                        Text(stringResource(avatarFailureText(RemoteBitmaps.failure(url, fullSize = true,scope=scope))))
                        TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("avatar-retry")) { Text(stringResource(R.string.dreams_retry)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { attempt++ }) { Text("Actualizar foto") } })
}

@Composable
internal fun AvatarMessageRow(url: String?, nick: String, refreshEveryMs: Long, refreshToken: Int = 0, scope: String = "", configuredHost: String? = null, wifiOnly: Boolean = false, showAvatar: Boolean = url != null, onOpen: () -> Unit, content: @Composable () -> Unit) {
    if (!showAvatar) content()
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if(url==null) Box(Modifier.padding(end=6.dp).size(28.dp),contentAlignment=Alignment.Center) { Text(nick.trimStart('~','&','@','%','+').take(2).uppercase(),style=MaterialTheme.typography.labelSmall) }
        else AvatarThumbnail(url, nick, Modifier.padding(end = 6.dp).size(28.dp), refreshToken = refreshToken, refreshEveryMs = refreshEveryMs, scope=scope, configuredHost=configuredHost, wifiOnly=wifiOnly, onOpen = onOpen)
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
