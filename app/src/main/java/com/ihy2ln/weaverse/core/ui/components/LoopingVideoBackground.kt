package com.ihy2ln.weaverse.core.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * A muted, looping video drawn as a background layer. Crops to fill the given
 * space, plays automatically, and releases the player when it leaves composition.
 */
@OptIn(UnstableApi::class)
@Suppress("UnsafeOptInUsageError")
@Composable
fun LoopingVideoBackground(
    path: String,
    modifier: Modifier = Modifier,
    fitInside: Boolean = false,
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                setResizeMode(
                    if (fitInside) AspectRatioFrameLayout.RESIZE_MODE_FIT
                    else AspectRatioFrameLayout.RESIZE_MODE_FILL,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
