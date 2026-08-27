package com.ihy2ln.weaverse.feature.novel.write

import android.net.Uri
import com.ihy2ln.weaverse.core.media.MediaClipboard
import com.ihy2ln.weaverse.core.media.MediaClipboardPayload
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.isMediaBlockAt
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class WriteMediaDragAction {
    data class StackOnto(val fromIndex: Int, val ontoIndex: Int) : WriteMediaDragAction()
    data class Move(val index: Int, val delta: Int) : WriteMediaDragAction()
    data object None : WriteMediaDragAction()
}

@Singleton
class WriteMediaOps @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaClipboard: MediaClipboard,
) {
    val clipboardPayload: MediaClipboardPayload? get() = mediaClipboard.payload
    val canPaste: Boolean get() = mediaClipboard.hasPayload

    fun copyToClipboard(block: Block): Boolean {
        val payload = clipboardFromBlock(block) ?: return false
        mediaClipboard.set(payload)
        return true
    }

    suspend fun resolvePaths(ids: List<String>): Map<String, String> {
        val paths = mutableMapOf<String, String>()
        ids.distinct().forEach { id ->
            mediaRepository.getById(id)?.let { media ->
                mediaRepository.resolveReadablePath(media)?.let { paths[id] = it }
            }
        }
        return paths
    }

    suspend fun importFromUri(uri: Uri): MediaEntity = mediaRepository.importFromUri(uri)

    suspend fun importFromUris(uris: List<Uri>): List<MediaEntity> =
        mediaRepository.importFromUris(uris)

    suspend fun registerPlaceholderImage(): MediaEntity = mediaRepository.registerPlaceholderImage()

    fun resolveFile(media: MediaEntity) = mediaRepository.resolveFile(media)

    companion object {
        fun clipboardFromBlock(block: Block): MediaClipboardPayload? = when (block) {
            is MediaBlock -> MediaClipboardPayload(
                mediaId = block.mediaId,
                kind = block.kind,
                widthPercent = block.widthPercent,
                gridColSpan = block.gridColSpan,
                gridRowSpan = block.gridRowSpan,
            )
            is MediaStackBlock -> MediaClipboardPayload(
                mediaId = block.mediaIds.firstOrNull().orEmpty(),
                kind = MediaKind.Image,
                gridColSpan = block.gridColSpan,
                gridRowSpan = block.gridRowSpan,
                stackedMediaIds = block.mediaIds,
            )
            else -> null
        }?.takeIf { it.mediaId.isNotBlank() || it.stackedMediaIds.isNotEmpty() }

        fun blockFromPayload(payload: MediaClipboardPayload): Block =
            if (payload.stackedMediaIds.size > 1) {
                MediaStackBlock(
                    id = UUID.randomUUID().toString(),
                    mediaIds = payload.stackedMediaIds,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                )
            } else {
                MediaBlock(
                    id = UUID.randomUUID().toString(),
                    mediaId = payload.mediaId,
                    kind = payload.kind,
                    widthPercent = payload.widthPercent,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                )
            }

        fun mediaIdsOf(block: Block): List<String> = when (block) {
            is MediaBlock -> listOf(block.mediaId)
            is MediaStackBlock -> block.mediaIds
            else -> emptyList()
        }

        fun newMediaBlock(mediaId: String, kind: MediaKind): MediaBlock = MediaBlock(
            id = UUID.randomUUID().toString(),
            mediaId = mediaId,
            kind = kind,
        )

        fun adjustWidth(block: Block, delta: Float): Block? = when (block) {
            is MediaBlock -> block.copy(
                widthPercent = (block.widthPercent + delta).coerceIn(25f, 100f),
            )
            is MediaStackBlock -> if (delta < 0) {
                block.copy(
                    gridColSpan = (block.gridColSpan - 1).coerceAtLeast(1),
                    gridRowSpan = (block.gridRowSpan - 1).coerceAtLeast(1),
                )
            } else {
                block.copy(
                    gridColSpan = (block.gridColSpan + 1).coerceAtMost(6),
                    gridRowSpan = (block.gridRowSpan + 1).coerceAtMost(6),
                )
            }
            else -> null
        }

        fun setCollapsed(block: Block, collapsed: Boolean): Block? = when (block) {
            is MediaBlock -> block.copy(collapsed = collapsed)
            is MediaStackBlock -> block.copy(collapsed = collapsed)
            else -> null
        }

        fun cycleStack(stack: MediaStackBlock): MediaStackBlock {
            if (stack.mediaIds.isEmpty()) return stack
            val nextIndex = (stack.currentIndex + 1) % stack.mediaIds.size
            return stack.copy(currentIndex = nextIndex)
        }

        fun setWidthPercent(block: Block, widthPercent: Float): Block? =
            (block as? MediaBlock)?.copy(widthPercent = widthPercent)

        fun dragRelease(
            blocks: List<Block>,
            index: Int,
            dragOffsetY: Float,
        ): WriteMediaDragAction {
            if (index !in blocks.indices || !blocks.isMediaBlockAt(index)) return WriteMediaDragAction.None
            val approxRow = 220f
            val steps = (dragOffsetY / approxRow).toInt()
            if (steps != 0) {
                var target = index
                var remaining = steps
                val direction = if (remaining > 0) 1 else -1
                while (remaining != 0) {
                    val next = target + direction
                    if (next !in blocks.indices) break
                    target = next
                    remaining -= direction
                    if (blocks.isMediaBlockAt(target) && target != index) {
                        return WriteMediaDragAction.StackOnto(index, target)
                    }
                }
            }
            val threshold = 48f
            return when {
                dragOffsetY < -threshold -> WriteMediaDragAction.Move(index, -1)
                dragOffsetY > threshold -> WriteMediaDragAction.Move(index, 1)
                else -> WriteMediaDragAction.None
            }
        }
    }
}
