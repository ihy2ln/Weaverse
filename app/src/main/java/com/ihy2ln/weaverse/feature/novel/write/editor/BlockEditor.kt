package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.media.ui.MediaBlockView
import com.ihy2ln.weaverse.core.media.ui.MediaGridBlockView
import com.ihy2ln.weaverse.core.media.ui.MediaStackBlockView
import com.ihy2ln.weaverse.core.media.ui.MediaViewer
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodeBlock
import com.ihy2ln.weaverse.core.text.Divider
import com.ihy2ln.weaverse.core.text.Heading
import com.ihy2ln.weaverse.core.text.ListItem
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.MediaItemRef
import com.ihy2ln.weaverse.core.text.MediaStack
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Quote
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import com.ihy2ln.weaverse.feature.novel.write.WriteViewModel

/**
 * The block-based Compose editor (spec §6): a [LazyColumn] of blocks, text
 * blocks rendered as [androidx.compose.foundation.text.BasicTextField]s that
 * split/merge into neighboring blocks on Enter/Backspace via [state]; media
 * blocks rendered by [MediaBlockView] (Phase 6) with resize/align/delete and
 * a double-tap full-screen viewer.
 *
 * Known scope cut: focus doesn't automatically follow the cursor to a
 * newly-split block, or back to the merge point after a backspace-merge —
 * the underlying split/merge/undo/redo *data* operations are correct and
 * unit-tested (`EditorStateTest`), but chaining `FocusRequester`s across a
 * `LazyColumn` whose item count changes every keystroke is exactly the kind
 * of Compose code that's easy to get subtly wrong in ways that only show up
 * at runtime — not something to guess at from a sandbox with no device to
 * run it on. See BUILD_NOTES.md "Block editor focus handling".
 */
@Composable
fun BlockEditor(
    state: EditorState,
    mediaRepository: MediaRepository,
    writeViewModel: WriteViewModel,
    codexEntries: List<CodexEntryEntity>,
    showSceneBeats: Boolean,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenCodexEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewerRequest by remember { mutableStateOf<MediaViewerRequest?>(null) }

    LazyColumn(modifier = modifier) {
        items(items = state.blocks, key = { it.id }) { block ->
            if (block is SceneBeatBlock && !showSceneBeats) return@items
            val blockIndex = state.blocks.indexOfFirst { it.id == block.id }
            val nextBlock = state.blocks.getOrNull(blockIndex + 1)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                BlockGutter(
                    onMoveUp = { state.moveBlock(block.id, delta = -1) },
                    onMoveDown = { state.moveBlock(block.id, delta = 1) },
                )
                Box(modifier = Modifier.weight(1f)) {
                    BlockRow(
                        block = block,
                        state = state,
                        mediaRepository = mediaRepository,
                        writeViewModel = writeViewModel,
                        codexEntries = codexEntries,
                        onPickImage = onPickImage,
                        onPickVideo = onPickVideo,
                        onOpenCodexEntry = onOpenCodexEntry,
                        onOpenMediaViewer = { _, media -> viewerRequest = MediaViewerRequest(listOf(media), 0) },
                        onOpenMediaViewerList = { items, index -> viewerRequest = MediaViewerRequest(items, index) },
                        onStackWithNext = (nextBlock as? MediaBlock)?.let { next ->
                            {
                                val current = block as MediaBlock
                                val stack = MediaStack(
                                    id = newId(),
                                    items = listOf(
                                        MediaItemRef(current.mediaId, current.kind, current.caption, current.autoplay, current.loop, current.muted),
                                        MediaItemRef(next.mediaId, next.kind, next.caption, next.autoplay, next.loop, next.muted),
                                    ),
                                )
                                state.replaceBlock(current.id, stack)
                                state.removeBlock(next.id)
                            }
                        },
                    )
                }
            }
        }

        // Tapping empty space below the last block appends a new paragraph (spec §7: "tapping
        // empty space below the last block appends a new paragraph and focuses it"). A fixed-
        // height tap zone rather than filling all remaining viewport space — simpler and avoids
        // depending on the exact fillParentMaxHeight/weight interaction inside a LazyColumn item,
        // which this sandbox has no device to verify pixel-for-pixel. Focus-follow is the same
        // documented gap as splitParagraph/mergeWithPrevious above (no FocusRequester chain built
        // yet) — the block is created and ready to tap into, just not auto-focused.
        item(key = "append_zone") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable {
                        state.insertBlockAfter(state.blocks.lastOrNull()?.id.orEmpty(), Paragraph(id = newId()))
                    },
            )
        }
    }

    viewerRequest?.let { request ->
        MediaViewer(items = request.items, startIndex = request.startIndex, onDismiss = { viewerRequest = null })
    }
}

/** Backs the full-screen viewer for both a single [MediaBlock] (a one-item list) and a
 * [MediaStack]/[MediaGrid]'s multi-item pager — one state shape for both. */
private data class MediaViewerRequest(val items: List<MediaEntity>, val startIndex: Int)

/**
 * Left-gutter Move controls (spec §7's "drag handle appears in the left gutter of every block
 * for direct reordering," simplified to explicit Up/Down buttons — see [EditorState.moveBlock]'s
 * KDoc for why a continuous drag gesture with live elevation/parting isn't attempted here).
 */
@Composable
private fun BlockGutter(onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    // IconButton keeps its default 48dp touch target (spec §7: "handles are large enough for
    // touch (48dp targets)") — only the glyph inside is shrunk, so the gutter stays narrow
    // visually without shrinking what's actually tappable.
    Column {
        IconButton(onClick = onMoveUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move block up",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move block down",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    state: EditorState,
    mediaRepository: MediaRepository,
    writeViewModel: WriteViewModel,
    codexEntries: List<CodexEntryEntity>,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenCodexEntry: (String) -> Unit,
    onOpenMediaViewer: (MediaBlock, MediaEntity) -> Unit,
    onOpenMediaViewerList: (List<MediaEntity>, Int) -> Unit,
    onStackWithNext: (() -> Unit)?,
) {
    when (block) {
        is Paragraph -> ParagraphBlockView(block, state, writeViewModel, codexEntries, onPickImage, onPickVideo)
        is Heading -> HeadingBlockView(block, state)
        is Quote -> QuoteBlockView(block, state)
        is ListItem -> ListItemBlockView(block, state)
        is CodeBlock -> CodeBlockView(block, state)
        is SceneBeatBlock -> SceneBeatBlockView(block, state, writeViewModel, onOpenCodexEntry)
        is Divider -> DividerBlockView(block, state)
        is MediaBlock -> MediaBlockView(
            block = block,
            mediaRepository = mediaRepository,
            onUpdate = { updated -> state.replaceBlock(block.id, updated) },
            onDelete = { state.removeBlock(block.id) },
            onOpenViewer = onOpenMediaViewer,
            onStackWithNext = onStackWithNext,
        )
        is MediaStack -> MediaStackBlockView(
            block = block,
            mediaRepository = mediaRepository,
            onUpdate = { updated -> state.replaceBlock(block.id, updated) },
            onUngroup = {
                var afterId = block.id
                block.items.forEach { item ->
                    val newBlockId = newId()
                    state.insertBlockAfter(
                        afterId,
                        MediaBlock(
                            id = newBlockId,
                            mediaId = item.mediaId,
                            kind = item.kind,
                            caption = item.caption,
                            autoplay = item.autoplay,
                            loop = item.loop,
                            muted = item.muted,
                        ),
                    )
                    afterId = newBlockId
                }
                state.removeBlock(block.id)
            },
            onDelete = { state.removeBlock(block.id) },
        )
        is MediaGrid -> MediaGridBlockView(
            block = block,
            mediaRepository = mediaRepository,
            onUpdate = { updated -> state.replaceBlock(block.id, updated) },
            onDelete = { state.removeBlock(block.id) },
            onOpenViewer = onOpenMediaViewerList,
        )
    }
}
