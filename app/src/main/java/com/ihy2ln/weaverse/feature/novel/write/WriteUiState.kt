package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.FindHit

data class SelectionState(
    val blockIndex: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
) {
    val hasSelection: Boolean get() = start != end
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
}

data class AiOverlayState(
    val commandId: String = "",
    val label: String = "",
    val prompt: String = "",
    val systemInstructions: String = "",
    val promptId: String? = null,
    val outputWords: Int = 100,
    /** Block the result is anchored to; resolved by id at accept so edits during generation can't misplace it. */
    val anchorBlockId: String? = null,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val usageLog: String = "",
    val insertAfterIndex: Int = 0,
    val replaceBlockIndex: Int? = null,
    val replaceStart: Int? = null,
    val replaceEnd: Int? = null,
    val imageMediaId: String? = null,
    val imagePath: String? = null,
    val pickBeatImageRequestId: Long = 0L,
    val contextMeter: ContextMeterReading? = null,
)

data class FindReplaceState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val caseSensitive: Boolean = false,
    val matches: List<FindHit> = emptyList(),
    val matchIndex: Int = 0,
) {
    val matchLabel: String
        get() = if (matches.isEmpty()) "0 matches" else "${matchIndex + 1} / ${matches.size}"
}

data class SceneRevisionUi(
    val id: String,
    val createdAt: Long,
    val wordCount: Int,
    val preview: String,
)

data class WriteUiState(
    val sceneId: String = "scene-1",
    val sceneTitle: String = "",
    val blocks: List<Block> = emptyList(),
    val mediaPaths: Map<String, String> = emptyMap(),
    val wordCount: Int = 0,
    val slashBlockIndex: Int? = null,
    val slashFilter: String = "",
    val pickImageBlockIndex: Int? = null,
    val pickImageRequestId: Long = 0L,
    val pickAudioRequestId: Long = 0L,
    val aiOverlay: AiOverlayState? = null,
    val selection: SelectionState = SelectionState(),
    val selectedMediaBlockIndex: Int? = null,
    val canPasteMedia: Boolean = false,
    val editPopupBlockIndex: Int? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String = "",
    val isSummarizing: Boolean = false,
    val showColorPicker: Boolean = false,
    val pendingCodexEntryId: String? = null,
    val codexNames: List<String> = emptyList(),
    val codexMentionTargets: List<CodexMentionTarget> = emptyList(),
    val showInlineWritingPrompt: Boolean = false,
    val showSceneBeatCard: Boolean = false,
    val showContinuationBox: Boolean = false,
    val findReplace: FindReplaceState = FindReplaceState(),
    val revisions: List<SceneRevisionUi> = emptyList(),
    val showHistory: Boolean = false,
    val contextMeter: ContextMeterReading? = null,
)
