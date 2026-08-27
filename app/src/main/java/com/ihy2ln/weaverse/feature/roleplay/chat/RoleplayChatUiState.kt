package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta

data class RpMediaRef(
    val messageId: String,
    val blockId: String,
    val path: String,
    val caption: String,
    val speaker: String,
    val role: String,
    val stackedPaths: List<String> = emptyList(),
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    val collapsed: Boolean = false,
    val isAudio: Boolean = false,
    val mediaId: String = "",
    val mediaKind: com.ihy2ln.weaverse.core.text.MediaKind =
        com.ihy2ln.weaverse.core.text.MediaKind.Image,
    val isTextTile: Boolean = false,
    val mediaScale: Float = 1f,
    val mediaOffsetXPercent: Float = 0f,
    val mediaOffsetYPercent: Float = 0f,
    val overlays: List<TextOverlay> = emptyList(),
    val panelRotationDeg: Float = 0f,
)

data class RpMessageUi(
    val id: String,
    val swipeGroupId: String,
    val swipeIndex: Int,
    val swipeCount: Int,
    val speaker: String,
    val text: String,
    val role: String,
    val createdAt: Long = 0L,
    val avatarColorHex: String = "",
    val mediaPaths: List<String> = emptyList(),
    val mediaBlockIds: List<String> = emptyList(),
    val mediaIsAudio: List<Boolean> = emptyList(),
    val mediaStackPaths: Map<String, List<String>> = emptyMap(),
    val mediaCollapsed: Map<String, Boolean> = emptyMap(),
    val usageText: String = "",
)

data class RoleplayChatUiState(
    val chatId: String = "",
    val title: String = "",
    val input: String = "",
    val messages: List<RpMessageUi> = emptyList(),
    val mediaPanels: List<RpMediaRef> = emptyList(),
    val displayMode: String = "messenger",
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val lastUsage: String = "",
    val mediaPickRequestId: Long = 0L,
    val audioPickRequestId: Long = 0L,
    val composerMinLines: Int = 1,
    val ttsStatus: String = "",
    val generationVisible: Boolean = true,
    val entryMode: String = "ai",
    val outputWords: Int = 400,
    val selectedMediaKey: String? = null,
    val canPasteMedia: Boolean = false,
    val presetId: String = "preset-balanced",
    val showExtraPromptSurfaces: Boolean = false,
    val pages: List<RpPageMeta> = emptyList(),
    val activePageId: String = "",
    val activeTemplateId: String = "classic-6",
    val editingOverlay: Triple<String, String, String>? = null,
    val contextMeter: ContextMeterReading? = null,
)
