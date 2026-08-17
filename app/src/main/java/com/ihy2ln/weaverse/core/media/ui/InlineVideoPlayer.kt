package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ihy2ln.weaverse.core.media.VideoPlayerPool

/**
 * Inline video playback via the shared [VideoPlayerPool] — "only one player
 * is active at a time" (spec §7). Releases the pool's player when this
 * composable leaves composition (scrolled off-screen, block deleted, etc.),
 * matching "pauses when scrolled off-screen."
 */
@Composable
fun InlineVideoPlayer(
    mediaId: String,
    uri: Uri,
    muted: Boolean,
    loop: Boolean,
    autoplay: Boolean,
    modifier: Modifier = Modifier,
) {
    val pool = rememberVideoPlayerPool()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                useController = true
            }
        },
        update = { playerView ->
            val player = pool.acquire(mediaId, uri)
            player.volume = if (muted) 0f else 1f
            player.repeatMode = if (loop) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
            player.playWhenReady = autoplay
            playerView.player = player
        },
    )

    DisposableEffect(mediaId) {
        onDispose { pool.releaseIfActive(mediaId) }
    }
}

/**
 * [VideoPlayerPool] is a Hilt `@Singleton`, but this module's plain Compose
 * components (like [InlineVideoPlayer]) deliberately don't assume a Hilt
 * entry point is available in every call site (see BUILD_NOTES.md "Media
 * block DI") — [LocalVideoPlayerPool] lets a Hilt-aware screen provide the
 * real instance, falling back to a fresh one so previews/tests don't crash.
 */
@Composable
private fun rememberVideoPlayerPool(): VideoPlayerPool {
    val fromComposition = LocalVideoPlayerPool.current
    if (fromComposition != null) return fromComposition
    val context = LocalContext.current
    return remember(context) { VideoPlayerPool(context.applicationContext) }
}
