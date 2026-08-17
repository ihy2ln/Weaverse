package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ihy2ln.weaverse.core.text.MediaGridTemplate
import com.ihy2ln.weaverse.core.text.MediaItemRef
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaType

/**
 * "Insert Media Grid" flow (spec §8): picks a template and a set of existing media items, then
 * hands back a ready-to-insert [com.ihy2ln.weaverse.core.text.MediaGrid]. Picks from the whole
 * on-device media library ([allMedia], typically [com.ihy2ln.weaverse.data.repo.MediaRepository.observeAll])
 * rather than only media already used in this book — no book-scoped media query exists yet
 * (tracked alongside rev02-09b).
 */
@Composable
fun MediaGridCreatorDialog(
    allMedia: List<MediaEntity>,
    onCreate: (MediaGridTemplate, List<MediaItemRef>) -> Unit,
    onDismiss: () -> Unit,
) {
    var template by remember { mutableStateOf(MediaGridTemplate.TwoUp) }
    var templateMenuExpanded by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateOf(linkedSetOf<String>()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert media grid") },
        text = {
            Column {
                Box {
                    TextButton(onClick = { templateMenuExpanded = true }) {
                        Text("Template: ${template.creatorLabel}")
                    }
                    DropdownMenu(expanded = templateMenuExpanded, onDismissRequest = { templateMenuExpanded = false }) {
                        MediaGridTemplate.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.creatorLabel) },
                                onClick = { template = entry; templateMenuExpanded = false },
                            )
                        }
                    }
                }
                Text(
                    "${selectedIds.value.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
                if (allMedia.isEmpty()) {
                    Text("No media imported yet — insert an image or video first.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(280.dp)) {
                        items(items = allMedia, key = { it.id }) { entity ->
                            val selected = entity.id in selectedIds.value
                            Box(
                                modifier = Modifier
                                    .padding(Spacing.xxs)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedIds.value = LinkedHashSet(selectedIds.value).apply {
                                            if (selected) remove(entity.id) else add(entity.id)
                                        }
                                    },
                            ) {
                                val fileUri = remember(entity.relativePath) { Uri.fromFile(MediaPaths.resolve(context, entity.relativePath)) }
                                AsyncImage(model = fileUri, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                                if (selected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val items = allMedia.filter { it.id in selectedIds.value }.map { entity ->
                        MediaItemRef(mediaId = entity.id, kind = if (entity.type == MediaType.Video) MediaKind.Video else MediaKind.Image)
                    }
                    onCreate(template, items)
                },
                enabled = selectedIds.value.isNotEmpty(),
            ) { Text("Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val MediaGridTemplate.creatorLabel: String
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
