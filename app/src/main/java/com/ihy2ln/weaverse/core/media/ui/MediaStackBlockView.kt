package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStack
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import kotlinx.coroutines.delay

/**
 * A [MediaStack] block (spec §8): several images/videos in one slot. "Number wheel" renders as
 * prev/next chevrons plus a `3/7` counter — Compose has no built-in spinning-wheel picker widget,
 * and a real one would be a significant custom-gesture component to build blind in a sandbox with
 * no device to tune it on; the counter+chevrons give the same "see position, cycle through it"
 * function. Swipe-to-cycle lives in the full-screen pager ([MediaViewer]'s list overload), opened
 * by tapping the slot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaStackBlockView(
    block: MediaStack,
    mediaRepository: MediaRepository,
    onUpdate: (MediaStack) -> Unit,
    onUngroup: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entities by remember(block.id) { mutableStateOf<List<MediaEntity?>>(emptyList()) }
    LaunchedEffect(block.items, mediaRepository) {
        entities = block.items.map { mediaRepository.getById(it.mediaId) }
    }
    var currentIndex by remember(block.id) { mutableIntStateOf(0) }
    var showViewer by remember(block.id) { mutableStateOf(false) }
    var menuExpanded by remember(block.id) { mutableStateOf(false) }
    var isPlaying by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, block.autoplayIntervalMs, entities.size) {
        val interval = block.autoplayIntervalMs
        if (isPlaying && interval != null && entities.size > 1) {
            while (true) {
                delay(interval.toLong())
                currentIndex = (currentIndex + 1) % entities.size
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(block.widthPercent / 100f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = { showViewer = true }),
        ) {
            val entity = entities.getOrNull(currentIndex)
            if (entity != null) {
                val fileUri = remember(entity.relativePath) { Uri.fromFile(MediaPaths.resolve(context, entity.relativePath)) }
                val kind = block.items.getOrNull(currentIndex)?.kind ?: MediaKind.Image
                if (kind == MediaKind.Video) {
                    InlineVideoPlayer(mediaId = entity.id, uri = fileUri, muted = true, loop = false, autoplay = false, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(model = fileUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }

            if (entities.size > 1) {
                IconButton(
                    onClick = { currentIndex = (currentIndex - 1 + entities.size) % entities.size },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(
                    onClick = { currentIndex = (currentIndex + 1) % entities.size },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.sm),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ) {
                Text(
                    "${currentIndex + 1}/${entities.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            if (block.autoplayIntervalMs != null) {
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Toggle slideshow")
                }
            }
            Text(
                "Stack — ${entities.size} items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.padding(start = Spacing.xs)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Stack options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Set as cover") },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        onClick = { menuExpanded = false; onUpdate(block.copy(coverIndex = currentIndex)) },
                    )
                    if (currentIndex > 0) {
                        DropdownMenuItem(
                            text = { Text("Move item earlier") },
                            onClick = {
                                menuExpanded = false
                                val items = block.items.toMutableList()
                                items[currentIndex] = items[currentIndex - 1].also { items[currentIndex - 1] = items[currentIndex] }
                                onUpdate(block.copy(items = items))
                                currentIndex -= 1
                            },
                        )
                    }
                    if (currentIndex < block.items.lastIndex) {
                        DropdownMenuItem(
                            text = { Text("Move item later") },
                            onClick = {
                                menuExpanded = false
                                val items = block.items.toMutableList()
                                items[currentIndex] = items[currentIndex + 1].also { items[currentIndex + 1] = items[currentIndex] }
                                onUpdate(block.copy(items = items))
                                currentIndex += 1
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Ungroup") },
                        onClick = { menuExpanded = false; onUngroup() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete stack") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }

    if (showViewer) {
        // Adjust for any null entries (media whose row was deleted out from under a stale
        // reference) so the pager opens on the same item the user tapped, not a shifted one.
        val nonNullEntities = entities.filterNotNull()
        val adjustedIndex = entities.take(currentIndex).count { it != null }
        MediaViewer(items = nonNullEntities, startIndex = adjustedIndex, onDismiss = { showViewer = false })
    }
}
