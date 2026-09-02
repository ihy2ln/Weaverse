package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.ui.components.NewWorkDetails
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
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
    /** Resolved check outcome paired with the player-visible calculation below. */
    val actionResult: String = "",
    /** Persisted player-facing FOR versus AGAINST tabletop calculation. */
    val rollResult: AdventureRoll? = null,
    /** Campaign-opening choice/interview content, before ordinary scene actions begin. */
    val isAdventureSetup: Boolean = false,
)

/** One detected character/item awaiting the user's confirmation. */
data class CaptureCandidate(
    val name: String,
    val summary: String,
    val selected: Boolean = true,
)

/**
 * Confirmation step for the ➕👤 / ➕🎒 composer actions: the AI's read of the
 * scene is shown as checkable candidates before anything touches the roster or
 * inventories. [sourceText] is what was scanned (recent scene or a highlighted
 * message).
 */
data class CaptureDialogState(
    val kind: String,
    val sourceText: String,
    val candidates: List<CaptureCandidate>,
    val extraction: AdventureCapture.Extraction,
)

/** One text region the AI found in a picture, with its translation. */
data class PanelTextRegion(
    /** Normalized 0..1 box. */
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val original: String,
    val translation: String,
)

/**
 * State for the full-screen picture editor (erase text, translate, re-draw).
 * The bitmap itself lives in the editor composable; the VM tracks which
 * panel is open plus AI-detected text regions.
 */
data class PanelEditorUi(
    val messageId: String,
    val blockId: String,
    val mediaId: String,
    val path: String,
    val busy: Boolean = false,
    val status: String = "",
    /** AI-detected text regions (original + translation). */
    val regions: List<PanelTextRegion> = emptyList(),
    val targetLanguage: String = "English",
    /** Pending cropped panels waiting to be placed (separate-panels flow). */
    val pendingPanelCount: Int = 0,
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
    /** Short-lived confirmation for composer hold-menu actions (roster/inventory adds). */
    val composerStatus: String = "",
    /** Non-null shows the add-to-roster / add-to-inventory confirmation dialog. */
    val captureDialog: CaptureDialogState? = null,
    val generationVisible: Boolean = true,
    val entryMode: String = "ai",
    val minimumOutputWords: Int = 50,
    val outputWords: Int = 100,
    /** Blank means follow the Writing model selected in Settings. */
    val selectedModelRef: String = "",
    val defaultModelRef: String = "",
    val writingModels: List<ModelInfo> = emptyList(),
    /** Exact backend roll currently being animated for the submitted action. */
    val activeRoll: AdventureRoll? = null,
    val rollAnimationId: Long = 0L,
    val selectedMediaKey: String? = null,
    val canPasteMedia: Boolean = false,
    val presetId: String = "preset-balanced",
    val showExtraPromptSurfaces: Boolean = false,
    val pages: List<RpPageMeta> = emptyList(),
    val activePageId: String = "",
    val activeTemplateId: String = "classic-6",
    val editingOverlay: Triple<String, String, String>? = null,
    val contextMeter: ContextMeterReading? = null,
    /** Adventure scene markers are hidden storage records, not chat messages. */
    val adventureStartupPhase: AdventureStartupPhase = AdventureStartupPhase.None,
    val sceneNumber: Int = 1,
    val totalScenes: Int = 1,
    val canGoToPreviousScene: Boolean = false,
    val viewingCurrentScene: Boolean = true,
    val canUndoSceneAdvance: Boolean = false,
    /** True when the human runs the world and the AI plays the party. */
    val userIsDungeonMaster: Boolean = false,
    /** Codex entries indexed for clickable mention links in adventure prose. */
    val codexTargets: List<CodexMentionTarget> = emptyList(),
    /** Campaign options sheet: the character options available to select. */
    val campaignCharacterOptions: List<WorkCharacterOption> = emptyList(),
    /** Non-null opens the campaign options dialog pre-filled from the setup note. */
    val campaignSetupInitial: NewWorkDetails? = null,
    /** True while the campaign options dialog is open. */
    val showCampaignOptions: Boolean = false,
    /** User-defined setting templates for the campaign setup sheet. */
    val customSettingTemplates: List<com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplate> = emptyList(),
    /** Non-null opens the full-screen picture editor for that panel. */
    val imageEditor: PanelEditorUi? = null,
    /** Short status for the storyboard tools (panel separation results…). */
    val storyboardStatus: String = "",
    /** ☁️ AI picture generation dialog. */
    val showImageGen: Boolean = false,
    val imageGenPrompt: String = "",
    val imageGenBusy: Boolean = false,
    val imageGenStatus: String = "",
    val imageGenModels: List<ModelInfo> = emptyList(),
    val imageGenModelRef: String = "",
)
