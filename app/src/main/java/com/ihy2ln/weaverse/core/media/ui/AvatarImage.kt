package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository

/**
 * Circular avatar thumbnail resolved from a [MediaEntity] id — shared by Roleplay's
 * Personas/Characters screens (Phase 11). Falls back to a plain surface-colored circle
 * when [mediaId] is null or hasn't resolved yet, so callers can drop a placeholder icon
 * inside via [placeholder]. Optionally clickable ([onClick]) — real-device feedback was
 * that "how do I add a photo" wasn't obvious with only a small text button next to the
 * avatar, so the avatar itself is now a tap target too (ripple gives interactivity
 * feedback) wherever a caller passes [onClick].
 */
@Composable
fun AvatarImage(
    mediaId: String?,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    placeholder: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var entity by remember(mediaId) { mutableStateOf<MediaEntity?>(null) }
    LaunchedEffect(mediaId) {
        entity = mediaId?.let { mediaRepository.getById(it) }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = "Change photo", role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    val resolved = entity
    if (resolved != null) {
        val fileUri = remember(resolved.relativePath) { Uri.fromFile(MediaPaths.resolve(context, resolved.relativePath)) }
        AsyncImage(
            model = fileUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape).then(clickableModifier),
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).then(clickableModifier),
            contentAlignment = Alignment.Center,
        ) {
            placeholder()
        }
    }
}
