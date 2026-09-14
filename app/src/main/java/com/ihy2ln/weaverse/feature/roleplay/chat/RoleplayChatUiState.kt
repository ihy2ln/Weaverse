package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.RectF
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.media.TypesetAlign
import com.ihy2ln.weaverse.core.media.TypesetLayer
import com.ihy2ln.weaverse.core.media.TypesetWriting
import com.ihy2ln.weaverse.core.media.hexToColorInt
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
import com.ihy2ln.weaverse.core.ui.components.NewWorkDetails
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatPreview
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatState
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgStartupState
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignState

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
    /** Resolved immutable source path when this panel points at a derived edit. */
    val originalPath: String = "",
    val variantKind: String = "original",
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

enum class MangaEditorTool { Select, Text, Brush, Eraser, ColorPicker, Remove, Pan }

enum class MangaProcessStage { Detection, Ocr, Translation, Proofreading, Cleanup }

/** One editable text layer on a manga page. Source OCR and translation stay separate. */
data class PanelTextRegion(
    /** Normalized 0..1 box. */
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val original: String,
    val translation: String,
    val id: String = "",
    val visible: Boolean = true,
    val autoFit: Boolean = true,
    val fontSizePx: Float = 0f,
    val fillHex: String = "#111111",
    val strokeHex: String = "#FFFFFF",
    val strokeWidth: Float = 0f,
    val alignment: String = "Center",
    val writingMode: String = "Horizontal",
    val edited: Boolean = false,
)

fun PanelTextRegion.toEditableOverlay(index: Int = 0): TextOverlay = TextOverlay(
    id = id.ifBlank { "manga-translation-$index" },
    text = translation.trim(),
    style = TextOverlayStyle.Plain,
    xPercent = ((x + w / 2f) * 100f).coerceIn(0f, 100f),
    yPercent = ((y + h / 2f) * 100f).coerceIn(0f, 100f),
    widthPercent = (w * 100f).coerceIn(8f, 100f),
    fontSizeSp = (if (fontSizePx > 0f) fontSizePx * 0.55f else h * 145f).coerceIn(9f, 34f),
    colorHex = fillHex,
    backgroundHex = null,
    backgroundAlpha = 0f,
    source = "manga-translation",
)

fun PanelTextRegion.toTypesetLayer(): TypesetLayer = TypesetLayer(
    normalized = RectF(x, y, x + w, y + h),
    text = translation,
    fillColor = hexToColorInt(fillHex, android.graphics.Color.BLACK),
    strokeColor = hexToColorInt(strokeHex, android.graphics.Color.WHITE),
    strokeWidthPx = strokeWidth,
    autoFit = autoFit,
    fontSizePx = fontSizePx,
    alignment = when (alignment) {
        "Start" -> TypesetAlign.Start
        "End" -> TypesetAlign.End
        else -> TypesetAlign.Center
    },
    writing = if (writingMode == "Vertical") TypesetWriting.Vertical else TypesetWriting.Horizontal,
    visible = visible,
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
    val selectedRegionId: String? = null,
    val pendingCleanup: Boolean = false,
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
    /** Per-editor choices; blank refs follow the best available/default model. */
    val editorVisionModels: List<ModelInfo> = emptyList(),
    val editorTextModels: List<ModelInfo> = emptyList(),
    val editorImageModels: List<ModelInfo> = emptyList(),
    val editorVisionModelRef: String = "",
    val editorTextModelRef: String = "",
    val editorImageModelRef: String = "",
    val editorModelsRefreshing: Boolean = false,
    val editorModelsStatus: String = "",
    /** Exact backend roll currently being animated for the submitted action. */
    val activeRoll: AdventureRoll? = null,
    val rollAnimationId: Long = 0L,
    val selectedMediaKey: String? = null,
    val canPasteMedia: Boolean = false,
    val canUndoStoryboard: Boolean = false,
    val canRedoStoryboard: Boolean = false,
    val presetId: String = "preset-balanced",
    val showExtraPromptSurfaces: Boolean = false,
    val pages: List<RpPageMeta> = emptyList(),
    val activePageId: String = "",
    val activeTemplateId: String = "classic-6",
    val editingOverlay: Triple<String, String, String>? = null,
    val contextMeter: ContextMeterReading? = null,
    /** Adventure scene markers are hidden storage records, not chat messages. */
    val adventureStartupPhase: AdventureStartupPhase = AdventureStartupPhase.None,
    /** Stage-based progress for campaign-outline generation (1..100, 0 when inactive). */
    val adventurePlanProgress: Int = 0,
    val rpgStartup: RpgStartupState? = null,
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
    /** Persona actually bound to this campaign; used by the in-scene character-card gallery. */
    val activeCampaignPersonaId: String = "",
    /** Non-null opens the campaign options dialog pre-filled from the setup note. */
    val campaignSetupInitial: NewWorkDetails? = null,
    /** True while the campaign options dialog is open. */
    val showCampaignOptions: Boolean = false,
    /** User-defined setting templates for the campaign setup sheet. */
    val customSettingTemplates: List<com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplate> = emptyList(),
    val customSettingDetailTemplates: List<com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplate> = emptyList(),
    val favoriteSettingTemplateIds: Set<String> = emptySet(),
    val favoriteSettingDetailIds: Set<String> = emptySet(),
    /** Non-null opens the full-screen picture editor for that panel. */
    val imageEditor: PanelEditorUi? = null,
    /** Short status for the storyboard tools (panel separation results…). */
    val storyboardStatus: String = "",
    /** Long-running imported-manga translation/colorization progress. */
    val mangaEditBusy: Boolean = false,
    val mangaEditAction: String = "",
    val mangaEditCurrent: Int = 0,
    val mangaEditTotal: Int = 0,
    /** Recoverable AI/offline storyboard page creation state. */
    val storyboardGeneration: StoryboardGenerationUiState = StoryboardGenerationUiState(),
    val storyboardGenerationOpen: Boolean = false,
    /** ☁️ AI picture generation dialog. */
    val showImageGen: Boolean = false,
    val imageGenPrompt: String = "",
    val imageGenBusy: Boolean = false,
    val imageGenStatus: String = "",
    val imageGenModels: List<ModelInfo> = emptyList(),
    val imageGenModelRef: String = "",
    /** Three deterministic-looking DM proposals for the current RPG scene. */
    val rpgActionChoices: List<RpgActionChoice> = emptyList(),
    /** AI-selected local art metadata for the current scene. */
    val rpgSceneArt: RpgSceneArtChoice? = null,
    /** Persistent campaign battle mode (encounter overrides never replace it). */
    val rpgCombatMode: RpgCombatRuleset = RpgCombatRuleset.DndD20,
    /** Non-null replaces ordinary scene input with the RPG-native encounter screen. */
    val activeRpgCombat: RpgCombatState? = null,
    val selectedCombatCardId: String? = null,
    val selectedCombatTargetId: String? = null,
    val combatActionPreview: RpgCombatPreview? = null,
    val combatTextAction: String = "",
    val rpgChapterRecap: String = "",
    /** Authoritative RPG campaign state used by the adventure map and resume UI. */
    val rpgCampaign: RpgCampaignState? = null,
)
