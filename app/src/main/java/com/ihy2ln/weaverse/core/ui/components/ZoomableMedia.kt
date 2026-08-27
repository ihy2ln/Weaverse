package com.ihy2ln.weaverse.core.ui.components

import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.ihy2ln.weaverse.core.media.isRemoteOrContentUri
import com.ihy2ln.weaverse.core.media.mediaLoadTarget
import java.io.File

/**
 * Shared pinch-to-zoom media surface for images (and video players when [isVideo]).
 * Single-finger vertical drags are not consumed when unzoomed so parent lists can scroll.
 * Double-tap resets zoom/pan. Optional [onLongPress]/[onLongPressAt] for menus
 * (e.g. Stack pictures) at the press point.
 *
 * @param decodeOriginal When true, Coil decodes at [Size.ORIGINAL] so expand/zoom stays sharp
 *   instead of upscaling a bitmap sized to a tiny cell.
 * @param fillPanel When true, skip [minHeight]/[maxHeight] constraints and fill the parent
 *   (manga snap panels already size the host box).
 */
@Composable
fun ZoomableMedia(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isVideo: Boolean = false,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = 280.dp,
    contentScale: ContentScale = ContentScale.Fit,
    decodeOriginal: Boolean = false,
    fillPanel: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onLongPressAt: ((Offset) -> Unit)? = null,
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestLongPressAt by rememberUpdatedState(onLongPressAt)
    val context = LocalContext.current
    val loadTarget = remember(path) { mediaLoadTarget(path) }
    val file = remember(path) { File(path) }
    val exists = loadTarget != null

    val sizeModifier = if (fillPanel) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .pointerInput(path, scale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = event.changes.size >= 2
                        val zoomed = scale > 1.02f
                        if (multiTouch || zoomed) {
                            val next = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = next
                            offset = if (next > 1f) offset + panChange else Offset.Zero
                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                        }
                        // Unzoomed single-finger: leave unconsumed so LazyColumn scrolls.
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .pointerInput(path, onLongPress != null, onLongPressAt != null) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    onLongPress = if (onLongPress != null || onLongPressAt != null) {
                        { press ->
                            latestLongPressAt?.invoke(press)
                            latestLongPress?.invoke()
                        }
                    } else {
                        null
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            !exists -> Unit
            isVideo -> {
                val player = remember(path) {
                    ExoPlayer.Builder(context).build().apply {
                        val uri = if (isRemoteOrContentUri(path)) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(file)
                        }
                        setMediaItem(MediaItem.fromUri(uri))
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
                            useController = true
                        }
                    },
                    modifier = if (fillPanel) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(maxHeight)
                    },
                )
            }
            else -> {
                val request = remember(loadTarget, decodeOriginal) {
                    ImageRequest.Builder(context)
                        .data(loadTarget)
                        .apply {
                            if (decodeOriginal) size(Size.ORIGINAL)
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

@Composable
fun ZoomableAsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = 280.dp,
    onLongPress: (() -> Unit)? = null,
    onLongPressAt: ((Offset) -> Unit)? = null,
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestLongPressAt by rememberUpdatedState(onLongPressAt)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
            .pointerInput(model, scale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = event.changes.size >= 2
                        val zoomed = scale > 1.02f
                        if (multiTouch || zoomed) {
                            val next = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = next
                            offset = if (next > 1f) offset + panChange else Offset.Zero
                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                        }
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .pointerInput(model, onLongPress != null, onLongPressAt != null) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    onLongPress = if (onLongPress != null || onLongPressAt != null) {
                        { press ->
                            latestLongPressAt?.invoke(press)
                            latestLongPress?.invoke()
                        }
                    } else {
                        null
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
