package com.ihy2ln.weaverse.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/** Lets the user pick one image from the app's own Pictures library. */
@HiltViewModel
class MediaLibraryPickerViewModel @Inject constructor(
    mediaRepository: MediaRepository,
) : ViewModel() {
    val images: StateFlow<List<MediaLibraryImage>> = mediaRepository.observeAll()
        .map { media ->
            media.filter { it.type == "image" }
                .sortedByDescending { it.createdAt }
                .mapNotNull { entity ->
                    val file = mediaRepository.resolveFile(entity)
                    if (file.isFile) MediaLibraryImage(entity.id, file.absolutePath) else null
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class MediaLibraryImage(val id: String, val path: String)

@Composable
fun MediaLibraryPickerDialog(
    title: String,
    onSelect: (MediaLibraryImage) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MediaLibraryPickerViewModel = hiltViewModel(),
) {
    val images by viewModel.images.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (images.isEmpty()) {
                    Text(
                        "No pictures in the library yet. Import some via Pictures or the Text Games cards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 92.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(images, key = { it.id }) { image ->
                            AsyncImage(
                                model = File(image.path),
                                contentDescription = image.id,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(image) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
