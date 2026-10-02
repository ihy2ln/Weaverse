package com.ihy2ln.weaverse.core.ui.components

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * A muted, looping video drawn as a background layer. Crops to fill the given space (or
 * fits inside it), plays automatically, and releases the player when it leaves composition.
 * It draws through a TextureView, so it can be faded and blurred like any other layer.
 * [path] is a file path or a URI (`asset:///…`, `content://…`).
 */
@Composable
fun LoopingVideoBackground(
    path: String,
    modifier: Modifier = Modifier,
    fitInside: Boolean = false,
    /** False pauses it where it is. */
    playing: Boolean = true,
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
    androidx.compose.runtime.LaunchedEffect(player, playing) { player.playWhenReady = playing }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                fun fit() = fitVideo(view, player.videoSize, fitInside)
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = fit()
                })
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit() }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/** A TextureView stretches the video to its bounds; this scales it back to crop or fit. */
private fun fitVideo(view: TextureView, size: VideoSize, fitInside: Boolean) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f || size.width <= 0 || size.height <= 0) return
    val videoW = size.width * size.pixelWidthHeightRatio
    val videoH = size.height.toFloat()
    val scale = if (fitInside) minOf(vw / videoW, vh / videoH) else maxOf(vw / videoW, vh / videoH)
    val matrix = Matrix().apply { setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f) }
    view.setTransform(matrix)
    view.invalidate()
}
