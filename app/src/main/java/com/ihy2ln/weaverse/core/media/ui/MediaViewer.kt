package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaType

/**
 * Full-screen viewer opened by double-tapping a [MediaBlockView] (spec §7):
 * pinch-zoom/pan for images, full player controls (via [InlineVideoPlayer]'s
 * `PlayerView`, which already ships default transport controls) for video.
 */
@Composable
fun MediaViewer(media: MediaEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fileUri = remember(media.relativePath) { Uri.fromFile(MediaPaths.resolve(context, media.relativePath)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (media.type == MediaType.Video) {
                InlineVideoPlayer(
                    mediaId = media.id,
                    uri = fileUri,
                    muted = false,
                    loop = false,
                    autoplay = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ZoomableImage(uri = fileUri, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * List-of-media variant (spec §8's `MediaStack`/`MediaGrid` "tap to open the full-screen
 * pager") — swipe through [items] starting at [startIndex] via [HorizontalPager]. Only the
 * currently visible page autoplays video, matching [com.ihy2ln.weaverse.core.media.VideoPlayerPool]'s
 * one-active-player-at-a-time design (every other page's [InlineVideoPlayer] just isn't asked to
 * autoplay — it still has full transport controls if the user taps play manually).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(items: List<MediaEntity>, startIndex: Int, onDismiss: () -> Unit) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val media = items[page]
                val fileUri = remember(media.relativePath) { Uri.fromFile(MediaPaths.resolve(context, media.relativePath)) }
                if (media.type == MediaType.Video) {
                    InlineVideoPlayer(
                        mediaId = media.id,
                        uri = fileUri,
                        muted = false,
                        loop = false,
                        autoplay = page == pagerState.currentPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableImage(uri = fileUri, modifier = Modifier.fillMaxSize())
                }
            }
            if (items.size > 1) {
                Text(
                    "${pagerState.currentPage + 1}/${items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = uri,
        contentDescription = null,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset += pan
                }
            },
    )
}
