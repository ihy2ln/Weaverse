package com.ihy2ln.weaverse.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val media: StateFlow<List<MediaEntity>> = mediaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun fileFor(entity: MediaEntity): File = mediaRepository.resolveFile(entity)
}

@Composable
fun MediaGalleryScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaGalleryViewModel = hiltViewModel(),
) {
    val items by viewModel.media.collectAsState()
    var focused by remember { mutableStateOf<MediaEntity?>(null) }
    val tokens = inkTokens()
    Box(modifier = modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "No pictures yet. Import a Novelcrafter ZIP or add media in Write / Roleplay.",
                color = tokens.secondaryText,
                modifier = Modifier.padding(InkSpacing.lg),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(InkSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { entity ->
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
                            text = entity.id.removePrefix("art-").replace('-', ' '),
                            color = tokens.secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        focused?.let { entity ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { focused = null },
                contentAlignment = Alignment.Center,
            ) {
                ZoomableMedia(
                    path = viewModel.fileFor(entity).absolutePath,
                    isVideo = entity.type == "video",
                    fillPanel = true,
                    modifier = Modifier.fillMaxSize().padding(InkSpacing.md),
                )
            }
        }
    }
}

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
                entity.id.removePrefix("art-").replace('-', ' '),
                color = tokens.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
    }
}
