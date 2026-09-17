package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import com.ihy2ln.weaverse.feature.novel.write.CaretRequest
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@Composable
fun DocumentEditor(
    blocks: List<Block>,
    mediaPaths: Map<String, String>,
    onParagraphChange: (Int, Paragraph) -> Unit,
    onMediaWidthChange: (Int, Float) -> Unit,
    onMediaSelect: (Int?) -> Unit = {},
    onMediaRemove: (Int) -> Unit = {},
    onMediaMoveBy: (Int, Int) -> Unit = { _, _ -> },
    onStackMedia: (Int) -> Unit = {},
    onCycleStack: (Int) -> Unit = {},
    onMediaDragRelease: (Int, Float) -> Unit = { index, dy ->
        when {
            dy < -48f -> onMediaMoveBy(index, -1)
            dy > 48f -> onMediaMoveBy(index, 1)
        }
    },
    canPasteMedia: Boolean = false,
    onMediaEditAction: (Int, MediaEditAction) -> Unit = { _, _ -> },
    selectedMediaBlockIndex: Int? = null,
    onSlashTrigger: (Int) -> Unit,
    onBackslashTrigger: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    focusedBlockIndex: Int? = null,
    editPopupBlockIndex: Int? = null,
    onSelectionChange: (Int, TextRange) -> Unit = { _, _ -> },
    onEditAction: (Int, EditTextAction, TextFieldValue) -> Unit = { _, _, _ -> },
    onShowEditPopup: (Int?) -> Unit = {},
    popupConfig: EditTextPopupConfig = EditTextPopupConfig(),
    onSceneBeatPromptChange: (Int, String) -> Unit = { _, _ -> },
    onToggleSceneBeat: (Int) -> Unit = {},
    onGenerateSceneBeat: (Int) -> Unit = {},
    onClearSceneBeat: (Int) -> Unit = {},
    onAcceptSceneBeat: () -> Unit = {},
    onRetrySceneBeat: () -> Unit = {},
    onRequestBeatImage: () -> Unit = {},
    beatImageAttached: Boolean = false,
    sceneBeatResultIndex: Int? = null,
    generatingSceneBeatIndex: Int? = null,
    codexNames: List<String> = emptyList(),
    codexMentionTargets: List<CodexMentionTarget> = emptyList(),
    onMentionClick: (String) -> Unit = {},
    onContinuationSubmit: (String) -> Unit = {},
    showInlineWritingPrompt: Boolean = false,
    showSceneBeatCard: Boolean = false,
    showContinuationBox: Boolean = false,
    /** The AI draft shown inline in the story, if one is waiting. */
    generatedProse: GeneratedProse? = null,
    onAcceptGenerated: () -> Unit = {},
    onRetryGenerated: () -> Unit = {},
    onDiscardGenerated: () -> Unit = {},
    onCopyGenerated: () -> Unit = {},
    onChooseEarlierGenerated: (Int) -> Unit = {},
    onEditGenerated: (String) -> Unit = {},
    /** Rises whenever the caret should jump into a block after a tap off the text. */
    caretRequest: CaretRequest? = null,
    /** A tap that missed the text of a line; the block it landed on takes the caret. */
    onPlaceCaret: (Int) -> Unit = {},
    onPlaceCaretAtEnd: () -> Unit = {},
) {
    val tokens = inkTokens()
    val proseAnchor = generatedProse?.let { prose ->
        blocks.indexOfFirst { it.id == prose.afterBlockId }.takeIf { it >= 0 } ?: blocks.lastIndex
    }
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (block) {
                        is Paragraph -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // A tap anywhere on this row — including the gap around the
                                // text — drops the caret into this line. Taps that land on the
                                // text itself are consumed by the field before they reach here.
                                .pointerInput(index) {
                                    detectTapGestures { onPlaceCaret(index) }
                                }
                                .padding(vertical = InkSpacing.xs),
                        ) {
                            BlockEditorField(
                                paragraph = block,
                                textColor = tokens.primaryText,
                                onTextChange = { onParagraphChange(index, it) },
                                onSlashDetected = { onSlashTrigger(index) },
                                onBackslashDetected = { onBackslashTrigger(index) },
                                onSelectionChange = { onSelectionChange(index, it) },
                                onEditAction = { action, value -> onEditAction(index, action, value) },
                                popupConfig = popupConfig,
                                showEditPopup = editPopupBlockIndex == index,
                                onShowEditPopupChange = { show ->
                                    onShowEditPopup(if (show) index else null)
                                },
                                showPromptPlaceholder = showInlineWritingPrompt,
                                codexMentionTargets = codexMentionTargets,
                                onMentionClick = onMentionClick,
                                caretRequest = caretRequest?.takeIf { it.blockIndex == index },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is MediaBlock -> MediaBlockView(
                            block = block,
                            mediaPath = mediaPaths[block.mediaId],
                            selected = selectedMediaBlockIndex == index,
                            canPaste = canPasteMedia,
                            onSelect = { onMediaSelect(index) },
                            onRemove = { onMediaRemove(index) },
                            onWidthChange = { onMediaWidthChange(index, it) },
                            onMoveBy = { delta -> onMediaMoveBy(index, delta) },
                            onStackAdjacent = { onStackMedia(index) },
                            onMediaEditAction = { onMediaEditAction(index, it) },
                            onDragRelease = { dy -> onMediaDragRelease(index, dy) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is MediaStackBlock -> MediaStackBlockView(
                            block = block,
                            mediaPaths = mediaPaths,
                            selected = selectedMediaBlockIndex == index,
                            canPaste = canPasteMedia,
                            onSelect = { onMediaSelect(index) },
                            onRemove = { onMediaRemove(index) },
                            onCycle = { onCycleStack(index) },
                            onStackAdjacent = { onStackMedia(index) },
                            onMediaEditAction = { onMediaEditAction(index, it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is SceneBeatBlock -> {
                            if (showSceneBeatCard) {
                                SceneBeatBlockView(
                                    block = block,
                                    onPromptChange = { onSceneBeatPromptChange(index, it) },
                                    onToggleCollapsed = { onToggleSceneBeat(index) },
                                    onGenerate = { onGenerateSceneBeat(index) },
                                    onClearText = { onClearSceneBeat(index) },
                                    onAccept = onAcceptSceneBeat,
                                    onRetry = onRetrySceneBeat,
                                    onRequestImage = onRequestBeatImage,
                                    hasImage = beatImageAttached,
                                    hasResult = sceneBeatResultIndex == index,
                                    generating = generatingSceneBeatIndex == index,
                                    codexNames = codexNames,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = InkSpacing.sm),
                                )
                            } else if (block.prompt.isNotBlank()) {
                                androidx.compose.material3.Text(
                                    block.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.secondaryText,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = InkSpacing.xs),
                                )
                            }
                        }
                        else -> Unit
                    }
                    if (generatedProse != null && proseAnchor == index) {
                        GeneratedProseCard(
                            prose = generatedProse,
                            onAccept = onAcceptGenerated,
                            onRetry = onRetryGenerated,
                            onDiscard = onDiscardGenerated,
                            onCopy = onCopyGenerated,
                            onChooseEarlier = onChooseEarlierGenerated,
                            onTextChange = onEditGenerated,
                        )
                    }
                }
            }
            if (generatedProse != null && (proseAnchor == null || blocks.isEmpty())) {
                item(key = "__generated") {
                    GeneratedProseCard(
                        prose = generatedProse,
                        onAccept = onAcceptGenerated,
                        onRetry = onRetryGenerated,
                        onDiscard = onDiscardGenerated,
                        onCopy = onCopyGenerated,
                        onChooseEarlier = onChooseEarlierGenerated,
                        onTextChange = onEditGenerated,
                    )
                }
            }
            if (showContinuationBox) {
                item(key = "__continuation") {
                    ContinuationInput(onSubmit = onContinuationSubmit)
                }
            }
            item(key = "__tap_to_write") {
                // The empty page below the last line is still the page: tapping it puts
                // the caret at the end of the manuscript instead of doing nothing.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TapToWriteHeight)
                        .pointerInput(Unit) { detectTapGestures { onPlaceCaretAtEnd() } },
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

/** Tappable dead space kept under the manuscript so the caret is always reachable. */
private val TapToWriteHeight = 220.dp

@Composable
private fun ContinuationInput(
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(inkRadiusMd())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = InkSpacing.lg)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, InkAccentBlue, shape)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        VoiceToTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "Continue under the last line…  / AI · \\ manual",
            minLines = 1,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            InkConfirmButton(
                onClick = {
                    val value = text
                    text = ""
                    onSubmit(value)
                },
                enabled = text.isNotBlank(),
                label = "Add",
                contentDescription = "Add continuation",
            )
        }
    }
}
