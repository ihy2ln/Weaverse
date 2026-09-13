package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Turns one imported manga page into durable panel media.
 *
 * The original page is never changed. A page with no reliable split keeps the
 * original media as its only panel; a successful split creates cropped child
 * media and leaves the source available in the media library for comparison
 * and re-processing.
 */
data class MangaPageProcessingResult(
    val source: MediaEntity,
    val panels: List<MediaEntity>,
    val boxes: List<NormalizedPanelBox>,
    val usedFallback: Boolean,
)

object MangaPageProcessor {
    suspend fun splitPage(
        source: MediaEntity,
        mediaRepository: MediaRepository,
        rightToLeft: Boolean,
        preferredBoxes: List<NormalizedPanelBox>? = null,
    ): MangaPageProcessingResult = withContext(Dispatchers.IO) {
        val sourceFile = mediaRepository.resolveFile(source)
        val bitmap = ImageOps.loadBitmap(sourceFile.absolutePath) ?: return@withContext MangaPageProcessingResult(
            source = source,
            panels = listOf(source),
            boxes = listOf(NormalizedPanelBox(0f, 0f, 1f, 1f)),
            usedFallback = true,
        )

        try {
            val boxes = orderedBoxes(
                preferredBoxes?.takeIf { it.size > 1 }
                    ?: ImageOps.detectPanelsByGuttersDetailed(bitmap).boxes,
                rightToLeft,
            )
            if (boxes.size <= 1) {
                return@withContext MangaPageProcessingResult(
                    source = source,
                    panels = listOf(source),
                    boxes = listOf(NormalizedPanelBox(0f, 0f, 1f, 1f)),
                    usedFallback = true,
                )
            }

            val cropped = boxes.mapIndexed { index, box ->
                val panel = ImageOps.crop(bitmap, box.toRectF())
                try {
                    mediaRepository.importFromBytes(
                        bytes = ImageOps.toJpegBytes(panel),
                        id = "${source.id}-panel-${index + 1}-${UUID.randomUUID()}",
                        fileName = "${source.id}-panel-${index + 1}.jpg",
                        mimeType = "image/jpeg",
                    )
                } finally {
                    panel.recycle()
                }
            }
            MangaPageProcessingResult(
                source = source,
                panels = cropped,
                boxes = boxes,
                usedFallback = false,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MangaPageProcessingResult(
                source = source,
                panels = listOf(source),
                boxes = listOf(NormalizedPanelBox(0f, 0f, 1f, 1f)),
                usedFallback = true,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun orderedBoxes(
        boxes: List<NormalizedPanelBox>,
        rightToLeft: Boolean,
    ): List<NormalizedPanelBox> = boxes.sortedWith(
        compareBy<NormalizedPanelBox> { (it.top * 100f).toInt() }
            .thenComparator { left, right ->
                if (rightToLeft) right.left.compareTo(left.left) else left.left.compareTo(right.left)
            },
    )
}
