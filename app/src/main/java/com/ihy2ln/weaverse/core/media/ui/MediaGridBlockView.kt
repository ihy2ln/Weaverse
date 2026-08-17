package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.MediaGridTemplate
import com.ihy2ln.weaverse.core.text.MediaItemRef
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository

/**
 * A [MediaGrid] block (spec §8): the five regular templates plus three manga/webtoon/4-koma
 * presets, laid out row-by-row from [MediaGridTemplate.rowCellCounts] — trailing rows simply have
 * fewer cells if [MediaGrid.items] has fewer entries than the template's full cell count, rather
 * than showing empty placeholder boxes. Drag-to-swap panels isn't implemented (tracked as
 * rev02-09b, same gesture-avoidance judgment as block Move in rev02-08); tapping a panel opens
 * Expand/Remove instead.
 */
@Composable
fun MediaGridBlockView(
    block: MediaGrid,
    mediaRepository: MediaRepository,
    onUpdate: (MediaGrid) -> Unit,
    onDelete: () -> Unit,
    onOpenViewer: (List<MediaEntity>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var entities by remember(block.id) { mutableStateOf<List<MediaEntity?>>(emptyList()) }
    LaunchedEffect(block.items, mediaRepository) {
        entities = block.items.map { mediaRepository.getById(it.mediaId) }
    }
    var settingsExpanded by remember(block.id) { mutableStateOf(false) }
    val backgroundColor = block.backgroundColorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(block.cornerRadiusDp.dp))
                .background(backgroundColor)
                .padding(block.gutterDp.dp),
            verticalArrangement = Arrangement.spacedBy(block.gutterDp.dp),
        ) {
            var cursor = 0
            block.template.rowCellCounts().forEach { cellCount ->
                if (cursor >= block.items.size) return@forEach
                val rowItems = block.items.subList(cursor, minOf(cursor + cellCount, block.items.size))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(block.gutterDp.dp),
                ) {
                    rowItems.forEachIndexed { offset, item ->
                        val index = cursor + offset
                        GridCell(
                            item = item,
                            entity = entities.getOrNull(index),
                            cornerRadiusDp = block.cornerRadiusDp,
                            aspectLocked = block.aspectLocked,
                            onExpand = { onOpenViewer(entities.filterNotNull(), entities.take(index).count { it != null }) },
                            onRemove = {
                                val newItems = block.items.toMutableList().also { it.removeAt(index) }
                                if (newItems.isEmpty()) onDelete() else onUpdate(block.copy(items = newItems))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                cursor += cellCount
            }
        }

        Row(modifier = Modifier.padding(top = Spacing.xs)) {
            Text(
                block.template.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { settingsExpanded = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Grid settings")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete grid")
            }
        }

        if (settingsExpanded) {
            GridSettingsPanel(
                block = block,
                onUpdate = onUpdate,
                onDismiss = { settingsExpanded = false },
            )
        }
    }
}

@Composable
private fun GridCell(
    item: MediaItemRef,
    entity: MediaEntity?,
    cornerRadiusDp: Int,
    aspectLocked: Boolean,
    onExpand: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val cellModifier = modifier
        .aspectRatio(if (aspectLocked) 1f else 4f / 3f)
        .clip(RoundedCornerShape(cornerRadiusDp.dp))
        .clickable { menuExpanded = true }

    Box(modifier = cellModifier) {
        if (entity != null) {
            val fileUri = remember(entity.relativePath) { Uri.fromFile(MediaPaths.resolve(context, entity.relativePath)) }
            if (item.kind == MediaKind.Video) {
                InlineVideoPlayer(mediaId = entity.id, uri = fileUri, muted = true, loop = false, autoplay = false, modifier = Modifier.fillMaxWidth())
            } else {
                AsyncImage(model = fileUri, contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Expand") },
                leadingIcon = { Icon(Icons.Filled.OpenInFull, contentDescription = null) },
                onClick = { menuExpanded = false; onExpand() },
            )
            DropdownMenuItem(
                text = { Text("Remove from grid") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { menuExpanded = false; onRemove() },
            )
        }
    }
}

@Composable
private fun GridSettingsPanel(block: MediaGrid, onUpdate: (MediaGrid) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid settings") },
        text = {
            Column {
                Text("Template", style = MaterialTheme.typography.labelLarge)
                var templateExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { templateExpanded = true }) {
                        Text(block.template.label)
                    }
                    DropdownMenu(expanded = templateExpanded, onDismissRequest = { templateExpanded = false }) {
                        MediaGridTemplate.entries.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.label) },
                                onClick = { templateExpanded = false; onUpdate(block.copy(template = template)) },
                            )
                        }
                    }
                }
                Text("Gutter: ${block.gutterDp}dp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = block.gutterDp.toFloat(),
                    onValueChange = { onUpdate(block.copy(gutterDp = it.toInt())) },
                    valueRange = 0f..24f,
                )
                Text("Corner radius: ${block.cornerRadiusDp}dp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = block.cornerRadiusDp.toFloat(),
                    onValueChange = { onUpdate(block.copy(cornerRadiusDp = it.toInt())) },
                    valueRange = 0f..24f,
                )
                Row {
                    Text("Lock panel aspect", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = block.aspectLocked,
                        onCheckedChange = { onUpdate(block.copy(aspectLocked = it)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private val MediaGridTemplate.label: String
    get() = when (this) {
        MediaGridTemplate.TwoUp -> "2-up"
        MediaGridTemplate.ThreeUp -> "3-up"
        MediaGridTemplate.TwoByTwo -> "2×2"
        MediaGridTemplate.OnePlusTwo -> "1+2"
        MediaGridTemplate.ThreeByThree -> "3×3"
        MediaGridTemplate.WebtoonStrip -> "Webtoon strip"
        MediaGridTemplate.MangaPage -> "Manga page"
        MediaGridTemplate.FourKoma -> "4-koma"
    }

/** How many cells each row of the template wants, in order — [MediaGridBlockView] renders items
 * into these rows left-to-right/top-to-bottom, stopping early if there aren't enough items to
 * fill every row rather than showing empty placeholder boxes. [MediaGridTemplate.WebtoonStrip]
 * is unbounded (one cell per row, repeated for every item) since it's meant to hold an arbitrary
 * strip length. */
private fun MediaGridTemplate.rowCellCounts(): List<Int> = when (this) {
    MediaGridTemplate.TwoUp -> listOf(2)
    MediaGridTemplate.ThreeUp -> listOf(3)
    MediaGridTemplate.TwoByTwo -> listOf(2, 2)
    MediaGridTemplate.OnePlusTwo -> listOf(1, 2)
    MediaGridTemplate.ThreeByThree -> listOf(3, 3, 3)
    MediaGridTemplate.WebtoonStrip -> List(64) { 1 }
    MediaGridTemplate.MangaPage -> listOf(1, 2)
    MediaGridTemplate.FourKoma -> listOf(1, 1, 1, 1)
}
