package com.ihy2ln.weaverse.core.media

import android.content.Context
import com.ihy2ln.weaverse.data.repo.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** "A maintenance action removes orphaned files" (spec §7). */
@Singleton
class MediaMaintenance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {
    suspend fun deleteOrphanedMedia(): Int = withContext(Dispatchers.IO) {
        val orphaned = mediaRepository.getOrphaned()
        for (media in orphaned) {
            MediaPaths.resolve(context, media.relativePath).delete()
            media.thumbnailPath?.let { MediaPaths.resolveThumb(context, it).delete() }
            mediaRepository.delete(media)
        }
        orphaned.size
    }
}
