package com.ihy2ln.weaverse.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val media: StateFlow<List<MediaEntity>> = mediaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun fileFor(entity: MediaEntity): File = mediaRepository.resolveFile(entity)

    fun moveToCategory(entity: MediaEntity, category: String) {
        viewModelScope.launch { mediaRepository.moveToCategory(entity.id, category) }
    }
}

@Composable
fun MediaGalleryScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaGalleryViewModel = hiltViewModel(),
) {
    val items by viewModel.media.collectAsState()
    var focused by remember { mutableStateOf<MediaEntity?>(null) }
    var categoryEditor by remember { mutableStateOf<MediaEntity?>(null) }
    var collectionFilter by remember { mutableStateOf("all") }
    val tokens = inkTokens()
    val bundledCategories = items.mapNotNull(::pictureCategory).distinct()
    val visibleItems = if (collectionFilter == "all") items else items.filter {
        pictureCategory(it) == collectionFilter
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "No pictures yet. Import a Novelcrafter ZIP or add media in Write / Roleplay.",
                color = tokens.secondaryText,
                modifier = Modifier.padding(InkSpacing.lg),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                if (bundledCategories.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = InkSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                    ) {
                        listOf("all" to "All")
                            .plus(bundledCategories.sorted().map { it to pictureCategoryLabel(it) })
                            .forEach { (id, label) ->
                                FilterChip(
                                    selected = collectionFilter == id,
                                    onClick = { collectionFilter = id },
                                    label = { Text(label) },
                                )
                            }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(InkSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    items(visibleItems, key = { it.id }) { entity ->
                    val file = viewModel.fileFor(entity)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(inkRadiusMd()))
                            .clickable { focused = entity },
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = entity.id,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(inkRadiusMd())),
                        )
                        Text(
                            text = entity.displayName.ifBlank { entity.id.removePrefix("art-").replace('-', ' ') },
                            color = tokens.secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        pictureCategory(entity)?.let { category ->
                            Text(
                                text = pictureCategoryLabel(category),
                                color = tokens.activePill,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
            }
        }
        focused?.let { entity ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .72f))
                    .clickable { focused = null },
                contentAlignment = Alignment.Center,
            ) {
                ZoomableMedia(
                    path = viewModel.fileFor(entity).absolutePath,
                    isVideo = entity.type == "video",
                    fillPanel = true,
                    modifier = Modifier.fillMaxSize().padding(InkSpacing.md),
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(InkSpacing.md)
                        .clickable { },
                    colors = CardDefaults.cardColors(containerColor = tokens.panel),
                ) {
                    Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                        Text(entity.displayName.ifBlank { entity.id }, color = tokens.primaryText, maxLines = 1)
                        Text(
                            "${entity.type.replaceFirstChar(Char::uppercase)} · ${pictureCategory(entity)?.let(::pictureCategoryLabel) ?: "Uncategorized"}",
                            color = tokens.secondaryText,
                            fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                            Button(onClick = { categoryEditor = entity }) { Text("Move category") }
                            OutlinedButton(onClick = { focused = null }) { Text("Close") }
                        }
                    }
                }
            }
        }
        categoryEditor?.let { entity ->
            MoveMediaCategoryDialog(
                entity = entity,
                categories = bundledCategories.sorted(),
                onMove = { category ->
                    viewModel.moveToCategory(entity, category)
                    categoryEditor = null
                    focused = null
                },
                onDismiss = { categoryEditor = null },
            )
        }
    }
}

@Composable
private fun MoveMediaCategoryDialog(
    entity: MediaEntity,
    categories: List<String>,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var destination by remember(entity.id) {
        mutableStateOf(entity.category.ifBlank { pictureCategory(entity).orEmpty() })
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
            Column(Modifier.padding(InkSpacing.md), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                Text("MOVE ${entity.type.uppercase()}", color = inkTokens().activePill, fontSize = 12.sp)
                Text(entity.displayName.ifBlank { entity.id }, color = inkTokens().primaryText, maxLines = 2)
                Text(
                    "Choose an existing category or type a new path. Scene categories automatically become available to AI scene lookup.",
                    color = inkTokens().secondaryText,
                    fontSize = 12.sp,
                )
                if (categories.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = destination == category,
                                onClick = { destination = category },
                                label = { Text(pictureCategoryLabel(category)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Category") },
                    placeholder = { Text("Adams Haven / Scene / Town") },
                    supportingText = { Text("Use / to create nested category labels. Leave blank for Uncategorized.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    Button(onClick = { onMove(destination) }) { Text("Move") }
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

internal fun pictureCategory(entity: MediaEntity): String? {
    entity.category.takeIf(String::isNotBlank)?.let { return it }
    val prefix = "images/adams_haven/"
    if (!entity.relativePath.startsWith(prefix)) return null
    return entity.relativePath.removePrefix(prefix).substringBefore('/').takeIf { it.isNotBlank() }
}

internal fun pictureCategoryLabel(category: String): String = category
    .removePrefix("Adams Haven / ")
    .replace(" / ", " · ")
    .replaceFirstChar(Char::uppercase)

@Composable
fun PicturesRailScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaGalleryViewModel = hiltViewModel(),
    onOpenGallery: () -> Unit = {},
) {
    val items by viewModel.media.collectAsState()
    val tokens = inkTokens()
    Column(modifier = modifier.padding(InkSpacing.sm)) {
        Text(
            "Shared pictures · ${items.size}",
            color = tokens.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Text(
            "Open gallery",
            color = tokens.primaryText,
            modifier = Modifier
                .padding(top = InkSpacing.sm)
                .clickable(onClick = onOpenGallery),
            fontSize = 14.sp,
        )
        items.take(8).forEach { entity ->
            Text(
                entity.displayName.ifBlank { entity.id.removePrefix("art-").replace('-', ' ') },
                color = tokens.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
    }
}
