package com.ihy2ln.weaverse.feature.roleplay.chat

import android.net.Uri
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.core.media.MediaClipboard
import com.ihy2ln.weaverse.core.media.MediaClipboardPayload
import com.ihy2ln.weaverse.core.media.MangaFileImporter
import com.ihy2ln.weaverse.core.media.MangaPageProcessor
import com.ihy2ln.weaverse.core.media.MangaPageProcessingResult
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.manga.MangaDownloadRepository
import com.ihy2ln.weaverse.core.media.ImageOps
import com.ihy2ln.weaverse.core.media.StoryboardExportPanel
import com.ihy2ln.weaverse.core.media.StoryboardPageExporter
import com.ihy2ln.weaverse.core.media.NormalizedPanelBox
import com.ihy2ln.weaverse.core.media.OfflinePanelDetectionKind
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
import com.ihy2ln.weaverse.core.media.TopicMediaLibrary
import com.ihy2ln.weaverse.core.media.TopicMediaSnapshot
import com.ihy2ln.weaverse.core.media.SceneMediaLibrary
import com.ihy2ln.weaverse.core.media.SceneMediaRequest
import com.ihy2ln.weaverse.core.media.parseTopicMediaReply
import com.ihy2ln.weaverse.core.media.topicMediaRequestsFor
import com.ihy2ln.weaverse.core.media.topicMediaVisibleText
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.CampaignPerspectiveTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignRulesetTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplate
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplate
import com.ihy2ln.weaverse.core.ui.components.decodeCampaignSettingTemplates
import com.ihy2ln.weaverse.core.ui.components.decodeCampaignSettingDetailTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignHouseRuleTemplates
import com.ihy2ln.weaverse.core.ui.components.NewWorkDetails
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.savedMangaVersions
import com.ihy2ln.weaverse.core.text.selectMangaVersion
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.PanelTemplates
import com.ihy2ln.weaverse.core.text.PanelSlot
import com.ihy2ln.weaverse.core.text.StoryboardGridItem
import com.ihy2ln.weaverse.core.text.buildPanelSeparationOutput
import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.stackMediaOnto
import com.ihy2ln.weaverse.core.text.stackMediaWithAdjacent
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.gridColOrUnset
import com.ihy2ln.weaverse.core.text.gridColSpanOrOne
import com.ihy2ln.weaverse.core.text.gridRowOrUnset
import com.ihy2ln.weaverse.core.text.gridRowSpanOrOne
import com.ihy2ln.weaverse.core.text.planStoryboardAssetPlacements
import kotlin.math.roundToInt
import com.ihy2ln.weaverse.core.text.withGridCell
import com.ihy2ln.weaverse.core.text.withGridPlacement
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.data.db.entities.encodePages
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.roleplay.presets.defaultPresets
import com.ihy2ln.weaverse.feature.roleplay.textgame.adamsHavenSceneCatalog
import com.ihy2ln.weaverse.feature.roleplay.textgame.pickGkomVariant
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatAction
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatState
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatant
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgEncounterSetup
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgStatusEffect
import com.ihy2ln.weaverse.feature.roleplay.combat.createRpgEncounter
import com.ihy2ln.weaverse.feature.roleplay.combat.previewRpgCombatAction
import com.ihy2ln.weaverse.feature.roleplay.combat.resolveRpgCombatAction
import com.ihy2ln.weaverse.feature.roleplay.combat.rpgCombatRulesetFromSetup
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgAdventurePlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignRepository
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignSetupSnapshot
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignState
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgGenerationStatus
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgPlanAnswer
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgStartupState
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgStartupStep
import com.ihy2ln.weaverse.feature.roleplay.campaign.chapterPlanPrompt
import com.ihy2ln.weaverse.feature.roleplay.campaign.completeChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.completeSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.createRpgCampaign
import com.ihy2ln.weaverse.feature.roleplay.campaign.cyoaSuggestionPrompt
import com.ihy2ln.weaverse.feature.roleplay.campaign.fallbackChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.fallbackCyoaSuggestions
import com.ihy2ln.weaverse.feature.roleplay.campaign.fallbackSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.openingScenePrompt
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseCyoaSuggestions
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.applyCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.campaign.enterFreeformExploration
import com.ihy2ln.weaverse.feature.roleplay.campaign.enterRpgSceneNode
import com.ihy2ln.weaverse.feature.roleplay.campaign.returnToChapterNode
import com.ihy2ln.weaverse.feature.roleplay.campaign.stopRpgSetupGeneration
import com.ihy2ln.weaverse.feature.roleplay.campaign.updateRpgPartyFromCombat
import com.ihy2ln.weaverse.feature.roleplay.characters.abilityModifier
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.createRpgCharacterSheet
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.ai.prompt.PromptRenderer
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/** Sentinel mediaId for DM text-only tiles placed on the 3×3 grid. */
const val DM_TEXT_TILE_MEDIA_ID = "__dm_text__"
private const val ADVENTURE_SCENE_ROLE = "scene"
/**
 * Luna is the inexpensive default Vision model for manga import/translation.
 * Keep this provider-qualified so the storyboard path cannot accidentally pick
 * the first unrelated Vision model returned by the cache.
 */
private const val MANGA_TRANSLATION_LUNA_REF = "openrouter/openai/gpt-5.6-luna"

/** Share of a resolved container that lettering may occupy, inside the balloon curve. */
private const val TextSafeWidthFraction = 0.84f
private const val TextSafeHeightFraction = 0.78f
private val ExplicitSceneAdvance = Regex(
    "\\b(next scene|advance (?:the )?scene|move (?:on|forward)|leave this scene|go to the next)\\b",
    RegexOption.IGNORE_CASE,
)
private val ExplicitStayInScene = Regex(
    "\\b(stay (?:here|in (?:this|the) scene)|do not (?:advance|move on)|don't (?:advance|move on)|remain here|go back (?:a scene|to the previous scene))\\b",
    RegexOption.IGNORE_CASE,
)
private val AiSceneAdvanceMarker = Regex(
    "\\[\\[ADVANCE_SCENE(?::\\s*([^]]+))?]]",
    RegexOption.IGNORE_CASE,
)
private val riskyCombatText = Regex(
    "\\b(attack|strike|shoot|cast|charge|grapple|dodge|parry|disarm|intimidate|escape)\\b",
    RegexOption.IGNORE_CASE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoleplayChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val mangaFileImporter: MangaFileImporter,
    private val mangaDownloadRepository: MangaDownloadRepository,
    private val mediaRepository: MediaRepository,
    private val storyboardPageExporter: StoryboardPageExporter,
    private val topicMediaLibrary: TopicMediaLibrary,
    private val sceneMediaLibrary: SceneMediaLibrary,
    private val settings: SettingsRepository,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
    private val mediaClipboard: MediaClipboard,
    private val workspaceHistory: WorkspaceHistory,
    private val generation: RoleplayGeneration,
    private val modelCache: OpenRouterModelCache,
    private val openRouterRepository: OpenRouterRepository,
    private val adventureCapture: AdventureCapture,
    private val promptRepository: com.ihy2ln.weaverse.data.repo.PromptRepository,
    private val codexQuickAdd: com.ihy2ln.weaverse.feature.novel.codex.CodexQuickAdd,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayChatUiState())
    val uiState: StateFlow<RoleplayChatUiState> = _uiState.asStateFlow()
    private var bindJob: Job? = null
    private var generateJob: Job? = null
    private var storyboardGenerationJob: Job? = null
    private var mangaEditJob: Job? = null
    private var cyoaSuggestionJob: Job? = null
    private var rpgDraftSaveJob: Job? = null
    private var composerStatusJob: Job? = null
    /** Live instructions from the Prompt Collection's RPG → Adventure Scene Beat prompt. */
    private val rpgSceneBeatDirective = MutableStateFlow("")

    /** Lorebook-style codex activation for adventure prompts. */
    private val contextBuilder = ContextBuilder()
    private var rawMessages: List<RpMessageEntity> = emptyList()
    private var boundChat: RpChatEntity? = null
    private val rpgCampaignRepository by lazy { RpgCampaignRepository(db.roleplayDao()) }
    private var rpgCampaignState: RpgCampaignState? = null
    private val rpgCombatJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedRpgCampaignId: String? = null
    private var customBangCommands: Map<String, String> = emptyMap()
    private var removedBangKeywords: Set<String> = emptySet()
    private var starCommands: List<RpgTurnCommand> = RpgTurnCommands.all
    private var customSettingTemplates: List<CampaignSettingTemplate> = emptyList()
    private var customSettingDetailTemplates: List<CampaignSettingDetailTemplate> = emptyList()
    private var bundledAdventureSceneMediaReady = false
    private var storyboardFallbackMediaId: String? = null

    /** Built-in campaign setting templates plus the user's own, in menu order. */
    private fun effectiveSettingTemplates(): List<CampaignSettingTemplate> =
        CampaignSettingTemplates + customSettingTemplates
    private fun effectiveSettingDetailTemplates(): List<CampaignSettingDetailTemplate> =
        CampaignSettingDetailTemplates + customSettingDetailTemplates
    private var boundCharacter: RpCharacterEntity? = null
    private var boundPersona: RpPersonaEntity? = null
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    /** Null follows the newest scene; a number browses saved scene history. */
    private var viewedSceneNumber: Int? = null

    fun bindChat(chatId: String) {
        if (_uiState.value.chatId == chatId && bindJob?.isActive == true) return
        viewedSceneNumber = null
        _uiState.update { it.copy(chatId = chatId) }
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            launch {
                workspaceHistory.state.collect { history ->
                    _uiState.update {
                        it.copy(
                            canUndoStoryboard = history.canUndo,
                            canRedoStoryboard = history.canRedo,
                        )
                    }
                }
            }
            launch {
                // Codex entries indexed for clickable mention links in adventure prose.
                db.codexDao().observeAllEntries().collect { entries ->
                    val targets = entries.filter { !it.disabled && it.trackMentions && it.name.length >= 2 }
                        .map { entry ->
                            CodexMentionTarget(
                                entryId = entry.id,
                                name = entry.name,
                                aliases = runCatching { decodeAliases(entry.aliasesJson) }.getOrDefault(emptyList()),
                                caseSensitive = entry.caseSensitiveMatching,
                            )
                        }
                    _uiState.update { it.copy(codexTargets = targets) }
                }
            }
            launch {
                // The same persona/roster/codex-character options the campaign creation menu offers.
                combine(
                    db.roleplayDao().observePersonas(),
                    db.roleplayDao().observeCharacters(),
                    db.codexDao().observeAllCategories(),
                    db.codexDao().observeAllEntries(),
                ) { personas, roster, categories, entries ->
                    val playerNames = personas.map { it.name.trim().lowercase() }.toSet()
                    val characterCategoryIds = categories
                        .filter { it.name.equals("Characters", ignoreCase = true) }
                        .map { it.id }
                        .toSet()
                    buildList {
                        personas.forEach { add(WorkCharacterOption("persona:${it.id}", it.name, "You")) }
                        roster.filterNot {
                            it.defaultCodexId?.startsWith("persona:") == true ||
                                it.name.trim().lowercase() in playerNames
                        }.forEach { add(WorkCharacterOption("roster:${it.id}", it.name, "Roster")) }
                        entries.filter { it.categoryId in characterCategoryIds }.forEach {
                            add(WorkCharacterOption("codex:${it.id}", it.name, "Codex"))
                        }
                    }.distinctBy { it.name.trim().lowercase() to it.source }
                }.collect { options ->
                    _uiState.update { it.copy(campaignCharacterOptions = options) }
                }
            }
            launch {
                // The RPG → Adventure Scene Beat prompt steers every DM generation live.
                promptRepository.observeByType("rpg_scene_beat").collect { prompts ->
                    val prompt = prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
                    val text = prompt?.let {
                        PromptRenderer.render(it, PromptRenderContext()).systemText
                            .ifBlank { it.description }
                    }.orEmpty()
                    rpgSceneBeatDirective.value = text
                }
            }
            launch {
                combine(settings.preferences, modelCache.models, openRouterRepository.imageEditingModels) { prefs, dtos, images ->
                    Triple(prefs, modelCache.toModelInfo(dtos), images)
                }.collect { (prefs, models, imageModels) ->
                    val visionModels = com.ihy2ln.weaverse.ai.MangaEditorModels.vision(models)
                    val textModels = com.ihy2ln.weaverse.ai.MangaEditorModels.text(models)
                    val defaultModelRef = prefs.defaultModelRef
                    val preferredVision = prefs.mangaVisionModelRef.takeIf { ref ->
                        visionModels.any { PromptModelSelection.modelRef(it.id) == ref }
                    } ?: visionModels.firstOrNull { it.id.contains("gpt-5.6-luna", ignoreCase = true) }
                        ?.let { PromptModelSelection.modelRef(it.id) }
                        ?: visionModels.firstOrNull()?.let { PromptModelSelection.modelRef(it.id) }.orEmpty()
                    val preferredText = prefs.mangaTextModelRef.takeIf { ref ->
                        textModels.any { PromptModelSelection.modelRef(it.id) == ref }
                    } ?: defaultModelRef.takeIf { ref ->
                        textModels.any { PromptModelSelection.modelRef(it.id) == ref }
                    } ?: textModels.firstOrNull()?.let { PromptModelSelection.modelRef(it.id) }.orEmpty()
                    val preferredImage = prefs.mangaImageModelRef.takeIf { ref ->
                        imageModels.any { PromptModelSelection.modelRef(it.id) == ref }
                    } ?: imageModels.firstOrNull { it.id.contains("gpt-5-image-mini", ignoreCase = true) }
                        ?.let { PromptModelSelection.modelRef(it.id) }
                        ?: imageModels.firstOrNull()?.let { PromptModelSelection.modelRef(it.id) }.orEmpty()
                    _uiState.update {
                        it.copy(
                            defaultModelRef = defaultModelRef,
                            writingModels = models,
                            editorVisionModels = visionModels,
                            editorTextModels = textModels,
                            editorImageModels = imageModels,
                            editorVisionModelRef = preferredVision,
                            editorTextModelRef = preferredText,
                            editorImageModelRef = preferredImage,
                            mangaColorStyleGuide = prefs.mangaColorStyleGuide,
                            mangaPreserveLineArt = prefs.mangaPreserveLineArt,
                        )
                    }
                    contextLimit = ContextMeter.limitFor(
                        PromptModelSelection.effectiveModelRef(
                            _uiState.value.selectedModelRef,
                            defaultModelRef,
                        ),
                        models,
                    )
                    refreshContextMeter()
                }
            }
            launch {
                settings.preferences.collect { prefs ->
                    customBangCommands = prefs.customBangCommands
                    removedBangKeywords = prefs.removedBangKeywords
                    starCommands = RpgTurnCommands.effectiveCommands(
                        prefs.customStarCommands,
                        prefs.removedStarKeywords,
                    )
                    customSettingTemplates = decodeCampaignSettingTemplates(prefs.customSettingTemplates)
                    customSettingDetailTemplates = decodeCampaignSettingDetailTemplates(
                        prefs.customSettingDetailTemplates,
                    )
                    _uiState.update {
                        it.copy(
                            presetId = prefs.roleplayPresetId,
                            showExtraPromptSurfaces = prefs.extraPromptSurfaces.roleplayButtons,
                            customSettingTemplates = customSettingTemplates,
                            customSettingDetailTemplates = customSettingDetailTemplates,
                            favoriteSettingTemplateIds = prefs.favoriteSettingTemplateIds,
                            favoriteSettingDetailIds = prefs.favoriteSettingDetailIds,
                        )
                    }
                }
            }
            launch {
                db.roleplayDao().observeChats().collect { chats ->
                    chats.find { it.id == chatId }?.let { chat ->
                        boundChat = chat
                        if (chat.displayMode == "dungeonMaster" && loadedRpgCampaignId != chat.id) {
                            restoreRpgStartup(chat)
                        }
                        val selectedRosterId = Regex("Main character IDs:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
                            .find(chat.authorsNote)?.groupValues?.getOrNull(1)
                            ?.split(',')?.map { it.trim() }
                            ?.firstOrNull { it.startsWith("roster:") }
                            ?.substringAfter("roster:")
                        val character = (chat.characterId ?: selectedRosterId)?.let { id ->
                            db.roleplayDao().getCharacter(id)
                        }
                        boundCharacter = character
                        boundPersona = chat.personaId.takeIf { it.isNotBlank() }?.let { id ->
                            db.roleplayDao().getPersona(id)
                        }
                        val preset = chat.presetId?.takeIf { it.isNotBlank() }
                            ?: _uiState.value.presetId
                        var pages = decodePages(chat.pagesJson)
                        if (pages.isEmpty()) {
                            pages = listOf(RpPageMeta(id = "page-1", order = 0))
                            val backfilled = chat.copy(pagesJson = encodePages(pages))
                            boundChat = backfilled
                            db.roleplayDao().upsertChat(backfilled)
                        }
                        val activePage = _uiState.value.activePageId
                            .takeIf { id -> pages.any { it.id == id } }
                            ?: pages.first().id
                        val activeTemplate = pages.firstOrNull { it.id == activePage }?.templateId
                            ?: "classic-6"
                        _uiState.update {
                            it.copy(
                                title = chat.title,
                                displayMode = chat.displayMode.ifBlank { "messenger" },
                                presetId = preset,
                                pages = pages,
                                activePageId = activePage,
                                activeTemplateId = activeTemplate,
                                userIsDungeonMaster = userIsDungeonMaster(chat.authorsNote),
                                activeCampaignPersonaId = chat.personaId,
                            )
                        }
                        migrateLegacyAdventureOpeningIfNeeded(chat)
                        publishMessages()
                    }
                }
            }
            launch {
                _uiState
                    .map { it.displayMode.ifBlank { "messenger" } to it.chatId }
                    .distinctUntilChanged()
                    .flatMapLatest { (mode, id) ->
                        if (id.isBlank()) flowOf(emptyList())
                        else db.roleplayDao().observeMessages(id, mode)
                    }
                    .collect { messages ->
                        rawMessages = messages
                        publishMessages()
                    }
            }
        }
    }

    private fun currentDisplayMode(): String =
        _uiState.value.displayMode.ifBlank { "messenger" }

    private fun activeGridSize(): Int = when (currentDisplayMode()) {
        "dungeonMaster" -> MediaGrid.DM_SIZE
        else -> MediaGrid.SIZE
    }

    private suspend fun publishMessages() {
        val allActive = rawMessages.filter { it.isActiveSwipe }
        val persistedStartupPhase = allActive.asSequence()
            .sortedByDescending { it.createdAt }
            .map { adventureStartupPhase(documentFromJson(it.contentJson).plainText()) }
            .firstOrNull { it != AdventureStartupPhase.None }
            ?: AdventureStartupPhase.None
        val startupPhase = effectiveAdventureStartupPhase(
            persistedPhase = persistedStartupPhase,
            adventurePlanProgress = _uiState.value.adventurePlanProgress,
            isStreaming = _uiState.value.isStreaming,
        )
        val sceneMarkers = allActive
            .filter { it.role == ADVENTURE_SCENE_ROLE }
            .sortedBy { it.createdAt }
        val totalScenes = sceneMarkers.size + 1
        val targetScene = (viewedSceneNumber ?: totalScenes).coerceIn(1, totalScenes)
        if (viewedSceneNumber != null) viewedSceneNumber = targetScene
        val startsAfter = sceneMarkers.getOrNull(targetScene - 2)?.createdAt
        val endsAt = sceneMarkers.getOrNull(targetScene - 1)?.createdAt
        val active = allActive.filter { message ->
            message.role != ADVENTURE_SCENE_ROLE &&
                (startsAfter == null || message.createdAt > startsAfter) &&
                (endsAt == null || message.createdAt < endsAt)
        }
        val panels = mutableListOf<RpMediaRef>()
        val statePages = _uiState.value.pages
        val defaultPageId = statePages.firstOrNull()?.id ?: "page-1"
        val ui = active.map { m ->
            val groupCount = rawMessages.count { it.swipeGroupId == m.swipeGroupId && it.role == m.role }
            val doc = documentFromJson(m.contentJson)
            val paths = mutableListOf<String>()
            val blockIds = mutableListOf<String>()
            val isAudioFlags = mutableListOf<Boolean>()
            val stackPaths = mutableMapOf<String, List<String>>()
            val collapsedMap = mutableMapOf<String, Boolean>()
            val storedCaption = doc.plainText()
            val isAdventureSetup = adventureStartupPhase(storedCaption) != AdventureStartupPhase.None
            val rollResult = adventureRollFrom(storedCaption)
            val actionResult = rollResult?.outcome?.takeIf { it.isNotBlank() }
                ?: adventureOutcomeFrom(storedCaption)
            val caption = stripRpgMetadata(adventureStartupProseFrom(adventureProseFrom(storedCaption)))
            val isUser = m.role == "user"
            // Real names read like a messenger; fall back only when nothing is bound.
            val speaker = if (isUser) {
                boundPersona?.name?.takeIf { it.isNotBlank() } ?: "You"
            } else {
                boundCharacter?.name?.takeIf { it.isNotBlank() }
                    ?: boundChat?.title?.takeIf { it.isNotBlank() }
                    ?: "Character"
            }
            val avatarColorHex = if (isUser) {
                avatarColorHexFor(speaker, null)
            } else {
                avatarColorHexFor(speaker, boundCharacter?.colorHex)
            }
            doc.blocks.forEach { block ->
                when (block) {
                    is MediaBlock -> {
                        if (block.mediaId == DM_TEXT_TILE_MEDIA_ID) {
                            run {
                                panels += RpMediaRef(
                                    pageId = block.pageId ?: defaultPageId,
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = "",
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    mediaId = block.mediaId,
                                    mediaKind = MediaKind.Image,
                                    isTextTile = true,
                                )
                            }
                            return@forEach
                        }
                        val entity = mediaRepository.getById(block.mediaId)
                        val path = entity?.let { mediaRepository.resolveFile(it).absolutePath }
                        if (path != null) {
                            val audio = entity.type == "audio" || block.kind == MediaKind.Audio
                            paths += path
                            blockIds += block.id
                            isAudioFlags += audio
                            collapsedMap[block.id] = block.collapsed
                            run {
                                panels += RpMediaRef(
                                    pageId = block.pageId ?: defaultPageId,
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = path,
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    isAudio = audio,
                                    mediaId = block.mediaId,
                                    mediaKind = block.kind,
                                    mediaScale = block.mediaScale,
                                    mediaOffsetXPercent = block.mediaOffsetXPercent,
                                    mediaOffsetYPercent = block.mediaOffsetYPercent,
                                    overlays = block.overlays,
                                    panelRotationDeg = block.panelRotationDeg,
                                    originalPath = block.originalMediaId
                                        ?.let { mediaRepository.getById(it) }
                                        ?.let { mediaRepository.resolveFile(it).absolutePath }
                                        .orEmpty(),
                                    variantKind = block.variantKind,
                                    mangaVersions = block.mangaVersions,
                                    activeMangaVersionId = block.activeMangaVersionId,
                                )
                            }
                        }
                    }
                    is MediaStackBlock -> {
                        val resolved = block.mediaIds.mapNotNull { id ->
                            mediaRepository.getById(id)?.let { mediaRepository.resolveFile(it).absolutePath }
                        }
                        if (resolved.isNotEmpty()) {
                            val idx = block.currentIndex.coerceIn(0, resolved.lastIndex)
                            paths += resolved[idx]
                            blockIds += block.id
                            isAudioFlags += false
                            stackPaths[block.id] = resolved
                            collapsedMap[block.id] = block.collapsed
                            run {
                                panels += RpMediaRef(
                                    pageId = block.pageId ?: defaultPageId,
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = resolved[idx],
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    stackedPaths = resolved,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    isAudio = false,
                                    mediaId = block.mediaIds.getOrNull(idx).orEmpty(),
                                    mediaKind = MediaKind.Image,
                                    mediaScale = block.mediaScale,
                                    mediaOffsetXPercent = block.mediaOffsetXPercent,
                                    mediaOffsetYPercent = block.mediaOffsetYPercent,
                                    overlays = block.overlays,
                                    panelRotationDeg = block.panelRotationDeg,
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
            RpMessageUi(
                id = m.id,
                swipeGroupId = m.swipeGroupId,
                swipeIndex = m.swipeIndex,
                swipeCount = groupCount.coerceAtLeast(1),
                speaker = speaker,
                text = caption,
                role = m.role,
                createdAt = m.createdAt,
                avatarColorHex = avatarColorHex,
                mediaPaths = paths,
                mediaBlockIds = blockIds,
                mediaIsAudio = isAudioFlags,
                mediaStackPaths = stackPaths,
                mediaCollapsed = collapsedMap,
                usageText = if (m.role != "user" && (m.promptTokens > 0 || m.completionTokens > 0 || m.costUsd > 0.0)) {
                    UsageFormat.formatUsage(m.promptTokens, m.completionTokens, null, m.costUsd.takeIf { it > 0.0 })
                } else {
                    ""
                },
                actionResult = actionResult,
                rollResult = rollResult,
                isAdventureSetup = isAdventureSetup,
            )
        }
        // Choice and art markers are hidden from RpMessageUi text, so parse them from
        // the stored document before stripRpgMetadata removes them for display.
        val latestAssistantText = active.lastOrNull { it.role != "user" }
            ?.let { message -> documentFromJson(message.contentJson).plainText() }
            .orEmpty()
        val actionChoices = parseRpgActionChoices(latestAssistantText)
        val sceneArt = parseRpgSceneArtChoice(latestAssistantText)
        val combatMode = authoritativeRpgMode()
        _uiState.update {
            it.copy(
                messages = ui,
                mediaPanels = panels.filter { panel -> panel.pageId == it.activePageId.ifBlank { defaultPageId } },
                mangaPagePanels = panels.filter { !it.isAudio && !it.isTextTile },
                canPasteMedia = mediaClipboard.hasPayload,
                adventureStartupPhase = startupPhase,
                sceneNumber = targetScene,
                totalScenes = totalScenes,
                canGoToPreviousScene = targetScene > 1,
                viewingCurrentScene = targetScene == totalScenes,
                canUndoSceneAdvance = targetScene == totalScenes && sceneMarkers.isNotEmpty(),
                rpgActionChoices = actionChoices,
                rpgSceneArt = sceneArt,
                rpgCombatMode = combatMode,
            )
        }
    }

    fun onMediaEditAction(messageId: String, blockId: String, action: MediaEditAction) {
        when (action) {
            MediaEditAction.Cut -> {
                copyMedia(messageId, blockId)
                removeMedia(messageId, blockId)
            }
            MediaEditAction.Copy -> copyMedia(messageId, blockId)
            MediaEditAction.Paste -> pasteMedia(messageId)
            MediaEditAction.Delete -> removeMedia(messageId, blockId)
            MediaEditAction.Shrink -> adjustMediaSpan(messageId, blockId, -1)
            MediaEditAction.Expand -> adjustMediaSpan(messageId, blockId, 1)
            MediaEditAction.Collapse -> setMediaCollapsed(messageId, blockId, true)
            MediaEditAction.Uncollapse -> setMediaCollapsed(messageId, blockId, false)
            MediaEditAction.Stack -> stackMedia(messageId, blockId)
            MediaEditAction.Move -> Unit // manga grid handles Move in UI
            MediaEditAction.AdjustImage -> Unit // manga grid handles Adjust image in UI
            MediaEditAction.AddTextOverlay -> addTextOverlay(messageId, blockId)
            MediaEditAction.EditImage -> openImageEditor(messageId, blockId)
            MediaEditAction.SeparatePanels -> separatePanels(messageId, blockId, useAi = true)
            MediaEditAction.SeparatePanelsAuto -> separatePanels(messageId, blockId, useAi = false)
            MediaEditAction.AddMedia -> requestMediaPick()
            MediaEditAction.GenerateMedia -> openImageGen()
        }
    }

    private fun copyMedia(messageId: String, blockId: String) {
        val panel = _uiState.value.mediaPanels.find {
            it.messageId == messageId && it.blockId == blockId
        } ?: return
        mediaClipboard.set(
            MediaClipboardPayload(
                mediaId = panel.mediaId,
                kind = panel.mediaKind,
                gridColSpan = panel.gridColSpan,
                gridRowSpan = panel.gridRowSpan,
                stackedMediaIds = if (panel.stackedPaths.size > 1) {
                    // Re-read ids from document
                    val msg = rawMessages.find { it.id == messageId } ?: return
                    val block = documentFromJson(msg.contentJson).blocks
                        .find { it.id == blockId } as? MediaStackBlock
                    block?.mediaIds.orEmpty()
                } else {
                    emptyList()
                },
            ),
        )
        _uiState.update { it.copy(canPasteMedia = true, errorMessage = "") }
    }

    private fun pasteMedia(messageId: String) {
        val payload = mediaClipboard.payload ?: return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val pageId = _uiState.value.activePageId
            val block = if (payload.stackedMediaIds.size > 1) {
                MediaStackBlock(
                    id = "msb-${UUID.randomUUID()}",
                    mediaIds = payload.stackedMediaIds,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                    pageId = pageId,
                )
            } else {
                MediaBlock(
                    id = "mb-${UUID.randomUUID()}",
                    mediaId = payload.mediaId,
                    kind = payload.kind,
                    widthPercent = payload.widthPercent,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                    pageId = pageId,
                )
            }
            persistMessageBlocks(current, doc.blocks + block)
            _uiState.update { it.copy(canPasteMedia = mediaClipboard.hasPayload) }
        }
    }

    private fun adjustMediaSpan(messageId: String, blockId: String, delta: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            val block = blocks[index]
            val col = block.gridColOrUnset().takeIf { it >= 0 } ?: 0
            val row = block.gridRowOrUnset().takeIf { it >= 0 } ?: 0
            val nextCol = (block.gridColSpanOrOne(gridSize) + delta).coerceIn(1, gridSize - col)
            val nextRow = (block.gridRowSpanOrOne(gridSize) + delta).coerceIn(1, gridSize - row)
            blocks[index] = block.withGridPlacement(col, row, nextCol, nextRow, gridSize)
            persistMessageBlocks(current, blocks)
        }
    }

    private fun setMediaCollapsed(messageId: String, blockId: String, collapsed: Boolean) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(collapsed = collapsed)
                is MediaStackBlock -> block.copy(collapsed = collapsed)
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContextMeter()
    }

    private var lastClearedInput: String = ""

    /** ⌫ tap: delete the draft entry (stashed so hold can undo it). */
    fun clearInput() {
        lastClearedInput = _uiState.value.input
        onInputChange("")
    }

    /** ⌫ press-and-hold: restore the last deleted draft. */
    fun undoClearInput() {
        if (lastClearedInput.isBlank()) return
        val restored = lastClearedInput
        lastClearedInput = ""
        onInputChange(restored)
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { it.copy(outputWords = words.coerceIn(50, 4000)) }
        refreshContextMeter()
    }

    fun updateMinimumOutputWords(words: Int) {
        _uiState.update { it.copy(minimumOutputWords = words.coerceIn(50, 4000)) }
        refreshContextMeter()
    }

    fun selectModel(modelId: String) {
        val ref = PromptModelSelection.modelRef(modelId)
        _uiState.update { it.copy(selectedModelRef = ref) }
        contextLimit = ContextMeter.limitFor(ref, _uiState.value.writingModels)
        refreshContextMeter()
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
        contextLimit = ContextMeter.limitFor(
            _uiState.value.defaultModelRef,
            _uiState.value.writingModels,
        )
        refreshContextMeter()
    }

    fun setGenerationVisible(visible: Boolean) {
        _uiState.update { it.copy(generationVisible = visible) }
    }

    fun setEntryMode(mode: String) {
        val normalized = if (mode == "nai") "nai" else "ai"
        _uiState.update { it.copy(entryMode = normalized, errorMessage = "") }
    }

    fun selectMedia(messageId: String?, blockId: String?) {
        val key = if (messageId != null && blockId != null) "$messageId::$blockId" else null
        _uiState.update { it.copy(selectedMediaKey = key) }
    }

    /** Selects the first image on the current comic page for the reader's edit dock. */
    fun selectFirstMangaPanel() {
        val panel = _uiState.value.mediaPanels.firstOrNull { !it.isAudio } ?: return
        selectMedia(panel.messageId, panel.blockId)
    }

    /** Opens the first image on the current comic page in the existing non-destructive editor. */
    fun openFirstMangaPanelEditor() {
        val panel = _uiState.value.mediaPanels.firstOrNull { !it.isAudio } ?: return
        openImageEditor(panel.messageId, panel.blockId)
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val media = doc.blocks.filter {
                it is MediaBlock || it is MediaStackBlock
            }
            val blocks = buildList {
                if (newText.isNotBlank()) {
                    add(Paragraph("p-${System.currentTimeMillis()}", listOf(Span(newText))))
                }
                addAll(media)
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val existing = rawMessages.find { it.id == messageId } ?: return@launch
            deleteStoredMessage(existing)
            _uiState.update {
                it.copy(
                    selectedMediaKey = if (it.selectedMediaKey?.startsWith("$messageId::") == true) {
                        null
                    } else {
                        it.selectedMediaKey
                    },
                )
            }
        }
    }

    fun removeMedia(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val target = doc.blocks.find { it.id == blockId }
            // Removing a DM text tile removes the whole prose turn.
            if (target is MediaBlock && target.mediaId == DM_TEXT_TILE_MEDIA_ID) {
                deleteStoredMessage(current)
                _uiState.update {
                    it.copy(
                        selectedMediaKey = if (it.selectedMediaKey?.startsWith("$messageId::") == true) {
                            null
                        } else {
                            it.selectedMediaKey
                        },
                    )
                }
                return@launch
            }
            val nextBlocks = doc.blocks.filterNot {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            val hasMedia = nextBlocks.any {
                (it is MediaBlock && it.mediaId != DM_TEXT_TILE_MEDIA_ID) || it is MediaStackBlock
            }
            if (!hasMedia && Document(nextBlocks).plainText().isBlank()) {
                deleteStoredMessage(current)
            } else {
                persistMessageBlocks(current, nextBlocks)
            }
            _uiState.update {
                it.copy(
                    selectedMediaKey = if (it.selectedMediaKey == "$messageId::$blockId") null else it.selectedMediaKey,
                )
            }
        }
    }

    /** Long-press menu: stack with adjacent media when present. */
    fun stackMedia(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val next = blocks.stackMediaWithAdjacent(index) ?: run {
                _uiState.update {
                    it.copy(errorMessage = "Drag this picture onto another to stack them.")
                }
                return@launch
            }
            persistMessageBlocks(current, next)
            _uiState.update { it.copy(errorMessage = "") }
        }
    }

    /** Drag-onto stack within the same message. */
    fun stackMediaOnto(messageId: String, fromBlockId: String, ontoBlockId: String) {
        if (fromBlockId == ontoBlockId) return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks
            val fromIndex = blocks.indexOfFirst {
                (it is MediaBlock && it.id == fromBlockId) || (it is MediaStackBlock && it.id == fromBlockId)
            }
            val ontoIndex = blocks.indexOfFirst {
                (it is MediaBlock && it.id == ontoBlockId) || (it is MediaStackBlock && it.id == ontoBlockId)
            }
            if (fromIndex < 0 || ontoIndex < 0) return@launch
            val next = blocks.stackMediaOnto(fromIndex, ontoIndex) ?: return@launch
            persistMessageBlocks(current, next)
            _uiState.update { it.copy(errorMessage = "", selectedMediaKey = null) }
        }
    }

    /** Persist snap position for a media/stack/text-tile block. */
    fun setMediaGridCell(messageId: String, blockId: String, col: Int, row: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val block = blocks[index]
            blocks[index] = block.withGridPlacement(
                col,
                row,
                block.gridColSpanOrOne(gridSize),
                block.gridRowSpanOrOne(gridSize),
                gridSize,
            )
            persistMessageBlocks(current, blocks)
        }
    }

    fun setMediaGridSpan(messageId: String, blockId: String, colSpan: Int, rowSpan: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val block = blocks[index]
            val col = block.gridColOrUnset().takeIf { it >= 0 } ?: 0
            val row = block.gridRowOrUnset().takeIf { it >= 0 } ?: 0
            blocks[index] = block.withGridPlacement(col, row, colSpan, rowSpan, gridSize)
            persistMessageBlocks(current, blocks)
        }
    }

    /** Auto-place unset panels into free 6×6 cells (row-major), span-aware. */
    fun ensureMangaGridPlacement() {
        viewModelScope.launch { placeUnplacedPanels(MediaGrid.SIZE) }
    }

    /** Auto-place unset panels into free 3×3 cells; ensure text-only messages get tiles. */
    fun ensureDmGridPlacement() {
        viewModelScope.launch {
            ensureTextTilesForDm()
            // Room flow will refresh panels; place whatever is currently known, then again after publish.
            placeUnplacedPanels(MediaGrid.DM_SIZE)
        }
    }

    private suspend fun ensureTextTilesForDm() {
        val active = rawMessages.filter { it.isActiveSwipe }
        for (msg in active) {
            val doc = documentFromJson(msg.contentJson)
            val hasRealMedia = doc.blocks.any {
                (it is MediaBlock && it.mediaId != DM_TEXT_TILE_MEDIA_ID) || it is MediaStackBlock
            }
            val hasTextTile = doc.blocks.any {
                it is MediaBlock && it.mediaId == DM_TEXT_TILE_MEDIA_ID
            }
            val text = doc.plainText()
            if (!hasRealMedia && text.isNotBlank() && !hasTextTile) {
                val tile = MediaBlock(
                    id = "dm-text-${msg.id}",
                    mediaId = DM_TEXT_TILE_MEDIA_ID,
                    kind = MediaKind.Image,
                )
                db.roleplayDao().upsertMessage(
                    msg.copy(contentJson = Document(blocks = doc.blocks + tile).toJson()),
                )
            }
        }
    }

    private suspend fun placeUnplacedPanels(gridSize: Int) {
        val panels = _uiState.value.mediaPanels
        if (panels.isEmpty()) return
        val occupied = mutableSetOf<Pair<Int, Int>>()
        panels.filter { MediaGrid.isPlaced(it.gridCol, it.gridRow, gridSize) }.forEach { panel ->
            occupied += MediaGrid.cellsCovered(
                panel.gridCol,
                panel.gridRow,
                panel.gridColSpan,
                panel.gridRowSpan,
                gridSize,
            )
        }
        // Prefer the page layout's empty slots, so dropped media lands in a panel
        // rather than a bare 1x1 cell somewhere in the corner.
        // Templates are authored against MediaGrid.SIZE, so they only apply to the
        // full-resolution storyboard canvas — never the coarser DM board.
        val slots = if (gridSize == MediaGrid.SIZE) {
            PanelTemplates.byId(_uiState.value.activeTemplateId)?.slots.orEmpty()
        } else {
            emptyList()
        }
        val updates = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
        panels.forEach { panel ->
            if (!MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                val freeSlot = slots.firstOrNull { slot ->
                    MediaGrid.cellsCovered(
                        slot.col, slot.row, slot.colSpan, slot.rowSpan, gridSize,
                    ).none { it in occupied }
                }
                val cell = if (freeSlot != null) {
                    occupied += MediaGrid.cellsCovered(
                        freeSlot.col, freeSlot.row, freeSlot.colSpan, freeSlot.rowSpan, gridSize,
                    )
                    freeSlot.col to freeSlot.row
                } else {
                    val next = MediaGrid.nextFreeCell(occupied, gridSize)
                    occupied += MediaGrid.cellsCovered(next.first, next.second, 1, 1, gridSize)
                    next
                }
                updates += Triple(panel.messageId, panel.blockId, cell)
            }
        }
        updates.forEach { (messageId, blockId, cell) ->
            val current = rawMessages.find { it.id == messageId } ?: return@forEach
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@forEach
            val slot = slots.firstOrNull { it.col == cell.first && it.row == cell.second }
            blocks[index] = if (slot != null) {
                blocks[index].withGridPlacement(
                    slot.col, slot.row, slot.colSpan, slot.rowSpan, gridSize,
                )
            } else {
                blocks[index].withGridCell(cell.first, cell.second, gridSize)
            }
            db.roleplayDao().upsertMessage(
                current.copy(contentJson = Document(blocks = blocks).toJson()),
            )
        }
    }

    fun cycleMediaStack(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it is MediaStackBlock && it.id == blockId }
            if (index < 0) return@launch
            val stack = blocks[index] as MediaStackBlock
            if (stack.mediaIds.isEmpty()) return@launch
            blocks[index] = stack.copy(currentIndex = (stack.currentIndex + 1) % stack.mediaIds.size)
            persistMessageBlocks(current, blocks)
        }
    }

    fun removeSelectedMedia() {
        val key = _uiState.value.selectedMediaKey ?: return
        val parts = key.split("::", limit = 2)
        if (parts.size != 2) return
        removeMedia(parts[0], parts[1])
    }

    fun undoStoryboardEdit() {
        viewModelScope.launch { workspaceHistory.undo() }
    }

    fun redoStoryboardEdit() {
        viewModelScope.launch { workspaceHistory.redo() }
    }

    /** Reorder media within a message by swapping with neighbor. */
    fun moveMedia(messageId: String, blockId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val target = (index + delta).coerceIn(0, blocks.lastIndex)
            if (target == index) return@launch
            val item = blocks.removeAt(index)
            blocks.add(target, item)
            persistMessageBlocks(current, blocks)
        }
    }

    fun setDisplayMode(mode: String) {
        val chat = boundChat ?: return
        val next = when (mode) {
            "dungeonMaster", "roleplay", "messenger" -> mode
            else -> "messenger"
        }
        viewModelScope.launch {
            val updated = chat.copy(displayMode = next)
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update { it.copy(displayMode = next) }
        }
    }

    // --- Storyboard pages ---------------------------------------------------

    fun switchPage(pageId: String) {
        if (_uiState.value.activePageId == pageId) return
        val template = _uiState.value.pages.firstOrNull { it.id == pageId }?.templateId
            ?: "classic-6"
        _uiState.update {
            it.copy(activePageId = pageId, activeTemplateId = template, selectedMediaKey = null)
        }
        viewModelScope.launch { publishMessages() }
    }

    /** Scrolling imported pages selects cached metadata; it must not re-query every image. */
    fun focusImportedMangaPage(pageId: String) {
        _uiState.update { state ->
            if (state.activePageId == pageId || state.pages.none { it.id == pageId }) state else {
                val panels = state.mangaPagePanels.filter { it.pageId == pageId }
                state.copy(activePageId = pageId,
                    activeTemplateId = state.pages.first { it.id == pageId }.templateId,
                    mediaPanels = panels,
                    selectedMediaKey = panels.firstOrNull()?.let { "${it.messageId}::${it.blockId}" })
            }
        }
    }

    fun addPage() {
        val chat = boundChat ?: return
        viewModelScope.launch {
            val pages = _uiState.value.pages
            val next = RpPageMeta(
                id = "page-${UUID.randomUUID()}",
                order = (pages.maxOfOrNull { it.order } ?: -1) + 1,
                title = "Page ${pages.size + 1}",
            )
            val updated = chat.copy(pagesJson = encodePages(pages + next))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update {
                it.copy(
                    pages = pages + next,
                    activePageId = next.id,
                    activeTemplateId = next.templateId,
                    selectedMediaKey = null,
                )
            }
            publishMessages()
        }
    }

    fun renamePage(pageId: String, title: String) {
        val chat = boundChat ?: return
        viewModelScope.launch {
            val pages = _uiState.value.pages.map {
                if (it.id == pageId) it.copy(title = title.ifBlank { null }) else it
            }
            val updated = chat.copy(pagesJson = encodePages(pages))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update { it.copy(pages = pages) }
        }
    }

    /** Deleting a page leaves its media orphaned under the default page (never data loss). */
    fun deletePage(pageId: String) {
        val chat = boundChat ?: return
        val pages = _uiState.value.pages
        if (pages.size <= 1) return
        viewModelScope.launch {
            val remaining = pages.filterNot { it.id == pageId }
            val updated = chat.copy(pagesJson = encodePages(remaining))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            val nextActive = if (_uiState.value.activePageId == pageId) {
                remaining.first().id
            } else {
                _uiState.value.activePageId
            }
            _uiState.update {
                it.copy(pages = remaining, activePageId = nextActive, selectedMediaKey = null)
            }
            publishMessages()
        }
    }

    /**
     * Snap this page's panels into a comic layout. Panels are filled in their
     * existing order, and any beyond the template's slot count keep their current
     * placement — applying a template never drops artwork off the page.
     */
    fun applyPanelTemplate(templateId: String) {
        val template = PanelTemplates.byId(templateId) ?: return
        val gridSize = activeGridSize()
        val pagePanels = _uiState.value.mediaPanels
        viewModelScope.launch {
            // Record the layout first so its frames show even on an empty page.
            val chat = boundChat
            val activeId = _uiState.value.activePageId
            if (chat != null && activeId.isNotBlank()) {
                val pages = _uiState.value.pages.map {
                    if (it.id == activeId) it.copy(templateId = templateId) else it
                }
                val updated = chat.copy(pagesJson = encodePages(pages))
                db.roleplayDao().upsertChat(updated)
                boundChat = updated
                _uiState.update { it.copy(pages = pages, activeTemplateId = templateId) }
            }
            pagePanels.forEachIndexed { index, panel ->
                val slot = template.slots.getOrNull(index) ?: return@forEachIndexed
                val current = rawMessages.find { it.id == panel.messageId } ?: return@forEachIndexed
                val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
                val at = blocks.indexOfFirst { it.id == panel.blockId }
                if (at < 0) return@forEachIndexed
                val placed = blocks[at].withGridPlacement(
                    slot.col,
                    slot.row,
                    slot.colSpan,
                    slot.rowSpan,
                    gridSize,
                )
                blocks[at] = when (placed) {
                    is MediaBlock -> placed.copy(panelRotationDeg = slot.rotationDeg)
                    is MediaStackBlock -> placed.copy(panelRotationDeg = slot.rotationDeg)
                    else -> placed
                }
                persistMessageBlocks(current, blocks)
            }
            _uiState.update { it.copy(errorMessage = "", selectedMediaKey = null) }
        }
    }

    // --- Media transform (pan/zoom within a panel) --------------------------

    fun setMediaTransform(
        messageId: String,
        blockId: String,
        scale: Float,
        offsetXPercent: Float,
        offsetYPercent: Float,
    ) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(
                    mediaScale = scale,
                    mediaOffsetXPercent = offsetXPercent,
                    mediaOffsetYPercent = offsetYPercent,
                )
                is MediaStackBlock -> block.copy(
                    mediaScale = scale,
                    mediaOffsetXPercent = offsetXPercent,
                    mediaOffsetYPercent = offsetYPercent,
                )
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    // --- Text overlays --------------------------------------------------------

    fun addTextOverlay(messageId: String, blockId: String) {
        addOverlay(messageId, blockId, TextOverlayStyle.Plain)
    }

    fun addSpeechBubbleOverlay(messageId: String, blockId: String) {
        addOverlay(messageId, blockId, TextOverlayStyle.SpeechBubble)
    }

    private fun addOverlay(messageId: String, blockId: String, style: TextOverlayStyle) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            val overlay = TextOverlay(
                id = "ov-${UUID.randomUUID()}",
                text = if (style == TextOverlayStyle.SpeechBubble) "Dialogue" else "Caption",
                style = style,
                colorHex = if (style == TextOverlayStyle.SpeechBubble) "#111111" else "#FFFFFF",
                backgroundHex = if (style == TextOverlayStyle.SpeechBubble) "#FFFFFF" else "#000000",
                backgroundAlpha = if (style == TextOverlayStyle.SpeechBubble) 0.96f else 0.65f,
            )
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = block.overlays + overlay)
                is MediaStackBlock -> block.copy(overlays = block.overlays + overlay)
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
            _uiState.update {
                it.copy(editingOverlay = Triple(messageId, blockId, overlay.id))
            }
        }
    }

    fun openOverlayEditor(messageId: String, blockId: String, overlayId: String) {
        _uiState.update { it.copy(editingOverlay = Triple(messageId, blockId, overlayId)) }
    }

    fun closeOverlayEditor() {
        _uiState.update { it.copy(editingOverlay = null) }
    }

    fun moveTextOverlay(messageId: String, blockId: String, overlayId: String, xPercent: Float, yPercent: Float) {
        updateTextOverlay(messageId, blockId, overlayId) { it.copy(xPercent = xPercent, yPercent = yPercent, manuallyAdjusted = true) }
    }

    fun resizeTextOverlay(messageId: String, blockId: String, overlayId: String, x: Float, y: Float, width: Float, height: Float) {
        updateTextOverlay(messageId, blockId, overlayId) {
            it.copy(xPercent = x, yPercent = y, widthPercent = width, heightPercent = height, manuallyAdjusted = true)
        }
    }

    fun saveTextOverlay(messageId: String, blockId: String, overlay: TextOverlay) {
        updateTextOverlay(messageId, blockId, overlay.id) { overlay.copy(manuallyAdjusted = true) }
    }


    /** Re-typeset existing wording only. Never changes the cleaned image or source mask. */
    fun relayoutMangaTranslations(overlayId: String? = null) {
        if (_uiState.value.mangaEditBusy) return
        val targets = _uiState.value.mediaPanels.toList()
        viewModelScope.launch {
            var changed = 0
            val failures = mutableListOf<String>()
            for (panel in targets) {
                val overlays = panel.overlays.filter { it.source == "manga-translation" }
                if (overlays.isEmpty()) continue
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(panel.path, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) continue
                val regions = overlays.map {
                    it.toPanelTextRegion().copy(edited = if (overlayId != null) it.id != overlayId else it.manuallyAdjusted)
                }
                val planned = MangaLetteringPlacement.constrain(regions, options.outWidth.toFloat() / options.outHeight)
                val problems = MangaLetteringValidator.problems(planned, options.outWidth, options.outHeight)
                if (problems.isNotEmpty()) { failures += problems; continue }
                val current = rawMessages.find { it.id == panel.messageId } ?: continue
                val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
                val index = blocks.indexOfFirst { it.id == panel.blockId }
                if (index < 0) continue
                val replacements = planned.filterNot { it.edited }.associateBy { it.id }
                fun replace(existing: List<TextOverlay>) = existing.map { before ->
                    replacements[before.id]?.let { after ->
                        changed++
                        before.copy(xPercent = (after.x + after.w / 2) * 100, yPercent = (after.y + after.h / 2) * 100,
                            widthPercent = after.w * 100, heightPercent = after.h * 100,
                            fontSizeSp = after.fontSizePx * .55f, autoFit = true,
                            manuallyAdjusted = before.manuallyAdjusted)
                    } ?: before
                }
                blocks[index] = when (val block = blocks[index]) {
                    is MediaBlock -> block.copy(overlays = replace(block.overlays))
                    is MediaStackBlock -> block.copy(overlays = replace(block.overlays))
                    else -> continue
                }
                persistMessageBlocks(current, blocks)
            }
            _uiState.update { it.copy(storyboardStatus = if (failures.isNotEmpty())
                "Some placements were kept unchanged: " + failures.distinct().joinToString(" ")
                else "Re-laid out $changed translation(s). Wording, cleanup and unselected manual edits were kept.") }
        }
    }
    fun deleteTextOverlay(messageId: String, blockId: String, overlayId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = block.overlays.filterNot { it.id == overlayId })
                is MediaStackBlock -> block.copy(overlays = block.overlays.filterNot { it.id == overlayId })
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    private fun updateTextOverlay(
        messageId: String,
        blockId: String,
        overlayId: String,
        transform: (TextOverlay) -> TextOverlay,
    ) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            fun applyTo(overlays: List<TextOverlay>) =
                overlays.map { if (it.id == overlayId) transform(it) else it }
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = applyTo(block.overlays))
                is MediaStackBlock -> block.copy(overlays = applyTo(block.overlays))
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun speakText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val status = runCatching { tts.speak(trimmed) }.getOrElse { it.message ?: "TTS failed" }
            _uiState.update { it.copy(ttsStatus = status) }
        }
    }

    override fun onCleared() {
        tts.stop()
        super.onCleared()
    }

    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun requestAudioPick() {
        _uiState.update { it.copy(audioPickRequestId = it.audioPickRequestId + 1) }
    }

    fun clearAudioPickRequest() {
        _uiState.update { it.copy(audioPickRequestId = 0L) }
    }

    // --- AI storyboard page creation ---------------------------------------

    fun openStoryboardGeneration(rightToLeft: Boolean) {
        val state = _uiState.value
        val source = state.input.ifBlank {
            state.messages.asReversed().firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
        }
        _uiState.update {
            it.copy(
                storyboardGenerationOpen = true,
                storyboardGeneration = it.storyboardGeneration.copy(
                    prompt = source,
                    rightToLeft = rightToLeft,
                    status = "",
                ),
            )
        }
    }

    fun closeStoryboardGeneration() {
        if (_uiState.value.storyboardGeneration.phase == StoryboardGenerationPhase.Generating) {
            stopStoryboardGeneration()
        }
        _uiState.update { it.copy(storyboardGenerationOpen = false) }
    }

    fun onStoryboardSourceChanged(source: String) {
        _uiState.update {
            it.copy(
                storyboardGeneration = it.storyboardGeneration.copy(
                    prompt = source,
                    status = if (it.storyboardGeneration.phase == StoryboardGenerationPhase.Failed) "" else it.storyboardGeneration.status,
                ),
            )
        }
    }

    fun setStoryboardGenerateMissingArt(enabled: Boolean) {
        _uiState.update {
            it.copy(storyboardGeneration = it.storyboardGeneration.copy(generateMissingArt = enabled))
        }
    }

    fun startStoryboardGeneration() {
        val state = _uiState.value
        val source = state.storyboardGeneration.prompt.trim()
        if (source.isBlank()) {
            _uiState.update {
                it.copy(
                    storyboardGeneration = it.storyboardGeneration.copy(
                        phase = StoryboardGenerationPhase.Failed,
                        progress = 0,
                        status = "Describe the scene or story beat first.",
                    ),
                )
            }
            return
        }
        storyboardGenerationJob?.cancel()
        val modelRef = state.selectedModelRef.ifBlank { state.defaultModelRef }
        storyboardGenerationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    storyboardGeneration = it.storyboardGeneration.copy(
                        phase = StoryboardGenerationPhase.Generating,
                        progress = 8,
                        status = "Planning panels and dialogue…",
                        draft = null,
                        modelRef = modelRef,
                    ),
                )
            }
            try {
                if (!aiGeneration.hasApiKey(modelRef.ifBlank { null })) {
                    throw AIError.NoApiKey()
                }
                val result = aiGeneration.complete(
                    userMessage = storyboardPagePrompt(
                        source = source,
                        rightToLeft = state.storyboardGeneration.rightToLeft,
                    ),
                    assembled = AssembledPrompt(
                        systemBlocks = listOf(
                            "You are an assistant that creates editable comic and manga page plans. " +
                                "Return only the requested JSON object.",
                        ),
                        messages = emptyList(),
                        usedEntries = emptyList(),
                        tokenBreakdown = emptyList(),
                    ),
                    modelRef = modelRef.ifBlank { null },
                    maxTokens = 1800,
                    temperature = 0.35,
                )
                _uiState.update {
                    it.copy(
                        storyboardGeneration = it.storyboardGeneration.copy(
                            progress = 72,
                            status = "Validating the page plan…",
                        ),
                    )
                }
                val draft = parseStoryboardPageDraft(result.text)
                    ?: error("The model returned an invalid page plan.")
                _uiState.update {
                    it.copy(
                        storyboardGeneration = it.storyboardGeneration.copy(
                            phase = StoryboardGenerationPhase.ReadyToApply,
                            progress = 100,
                            status = "Page plan ready. Review it, then apply it to the storyboard.",
                            draft = draft,
                        ),
                    )
                }
            } catch (_: CancellationException) {
                // Stop owns the visible state; a cancelled request must not resurrect a spinner.
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        storyboardGeneration = it.storyboardGeneration.copy(
                            phase = StoryboardGenerationPhase.Failed,
                            progress = 0,
                            status = error.message?.takeIf(String::isNotBlank)
                                ?: "AI page creation failed. Continue offline to make the page now.",
                        ),
                    )
                }
            }
        }
    }

    fun retryStoryboardGeneration() = startStoryboardGeneration()

    fun stopStoryboardGeneration() {
        storyboardGenerationJob?.cancel()
        storyboardGenerationJob = null
        _uiState.update {
            it.copy(
                storyboardGeneration = it.storyboardGeneration.copy(
                    phase = StoryboardGenerationPhase.Stopped,
                    progress = 0,
                    status = "Stopped. Your scene text is preserved; continue offline or retry when ready.",
                ),
            )
        }
    }

    fun continueStoryboardOffline() {
        val state = _uiState.value
        storyboardGenerationJob?.cancel()
        storyboardGenerationJob = null
        val draft = fallbackStoryboardPageDraft(
            source = state.storyboardGeneration.prompt,
            rightToLeft = state.storyboardGeneration.rightToLeft,
        )
        _uiState.update {
            it.copy(
                storyboardGeneration = it.storyboardGeneration.copy(
                    phase = StoryboardGenerationPhase.OfflineFallback,
                    progress = 100,
                    draft = draft,
                    status = "Offline page plan ready. It uses safe local artwork and remains fully editable.",
                ),
            )
        }
    }

    fun applyStoryboardDraft() {
        val state = _uiState.value
        val draft = state.storyboardGeneration.draft ?: return
        viewModelScope.launch {
            val chat = boundChat ?: return@launch
            ensureBundledAdventureSceneMedia()
            val template = PanelTemplates.byId(draft.templateId) ?: PanelTemplates.byId("classic-6")!!
            val currentPages = decodePages(chat.pagesJson).toMutableList()
            val activePageId = state.activePageId
            val activeHasArtwork = state.mediaPanels.isNotEmpty()
            val targetPage = if (activePageId.isNotBlank() && !activeHasArtwork) {
                currentPages.firstOrNull { it.id == activePageId }
            } else {
                null
            }
            val page = targetPage ?: RpPageMeta(
                id = "page-${UUID.randomUUID()}",
                order = (currentPages.maxOfOrNull { it.order } ?: -1) + 1,
                title = draft.title,
                templateId = template.id,
                readingOrder = draft.readingOrder,
                generationStatus = if (state.storyboardGeneration.phase == StoryboardGenerationPhase.OfflineFallback) "offline" else "ai",
            )
            val pages = if (targetPage == null) {
                currentPages + page
            } else {
                currentPages.map { existing ->
                    if (existing.id == page.id) existing.copy(
                        title = draft.title.ifBlank { existing.title },
                        templateId = template.id,
                        readingOrder = draft.readingOrder,
                        generationStatus = if (state.storyboardGeneration.phase == StoryboardGenerationPhase.OfflineFallback) "offline" else "ai",
                    ) else existing
                }
            }
            val slots = template.slots.sortedWith(
                compareBy<PanelSlot> { it.row }
                    .thenBy { slot -> if (draft.readingOrder == "rtl") -slot.col else slot.col },
            )
            val usedMediaIds = mutableSetOf<String>()
            val blocks = draft.panels.mapIndexedNotNull { index, panel ->
                val slot = slots.getOrNull(index) ?: return@mapIndexedNotNull null
                val media = resolveStoryboardMedia(
                    query = "${panel.mediaQuery} ${panel.description}",
                    source = state.storyboardGeneration.prompt,
                    usedMediaIds = usedMediaIds,
                    allowAiArt = state.storyboardGeneration.generateMissingArt,
                ) ?: return@mapIndexedNotNull null
                usedMediaIds += media.id
                val blockId = "storyboard-${UUID.randomUUID()}"
                val overlays = buildList {
                    if (panel.caption.isNotBlank()) add(
                        TextOverlay(
                            id = "$blockId-caption",
                            text = panel.caption.trim(),
                            style = TextOverlayStyle.Plain,
                            xPercent = 50f,
                            yPercent = 86f,
                            widthPercent = 88f,
                            fontSizeSp = 13f,
                            backgroundHex = "#000000",
                            backgroundAlpha = 0.62f,
                        ),
                    )
                    if (panel.dialogue.isNotBlank()) add(
                        TextOverlay(
                            id = "$blockId-dialogue",
                            text = panel.dialogue.trim(),
                            style = TextOverlayStyle.SpeechBubble,
                            xPercent = 52f,
                            yPercent = 24f,
                            widthPercent = 66f,
                            fontSizeSp = 15f,
                            colorHex = "#111111",
                            backgroundHex = "#FFFFFF",
                            backgroundAlpha = 0.92f,
                        ),
                    )
                }
                MediaBlock(
                    id = blockId,
                    mediaId = media.id,
                    kind = MediaRepository.kindForType(media.type),
                    caption = panel.description.takeIf(String::isNotBlank)?.let { listOf(Span(it)) }.orEmpty(),
                    pageId = page.id,
                    gridCol = slot.col,
                    gridRow = slot.row,
                    gridColSpan = slot.colSpan,
                    gridRowSpan = slot.rowSpan,
                    overlays = overlays,
                    panelRotationDeg = slot.rotationDeg,
                )
            }
            if (blocks.isEmpty()) {
                _uiState.update {
                    it.copy(storyboardGeneration = it.storyboardGeneration.copy(
                        phase = StoryboardGenerationPhase.Failed,
                        status = "No valid artwork was available for this page.",
                    ))
                }
                return@launch
            }
            db.roleplayDao().upsertChat(
                chat.copy(
                    pagesJson = encodePages(pages),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            boundChat = chat.copy(pagesJson = encodePages(pages), updatedAt = System.currentTimeMillis())
            insertStoredMessage(
                RpMessageEntity(
                    id = "rpm-${UUID.randomUUID()}",
                    chatId = chat.id,
                    swipeGroupId = "sw-${UUID.randomUUID()}",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = Document(blocks = blocks).toJson(),
                    createdAt = System.currentTimeMillis(),
                    displayMode = "roleplay",
                ),
            )
            _uiState.update {
                it.copy(
                    pages = pages,
                    activePageId = page.id,
                    activeTemplateId = page.templateId,
                    storyboardGenerationOpen = false,
                    storyboardGeneration = StoryboardGenerationUiState(),
                    storyboardStatus = "Created ${blocks.size}-panel page. Artwork and dialogue are editable.",
                )
            }
            publishMessages()
        }
    }

    fun exportStoryboardPage() {
        val state = _uiState.value
        val page = state.pages.firstOrNull { it.id == state.activePageId }
        if (page == null) {
            _uiState.update { it.copy(storyboardStatus = "There is no storyboard page to export yet.") }
            return
        }
        viewModelScope.launch {
            val panels = state.mediaPanels.mapNotNull { panel ->
                panel.path.takeIf { path -> java.io.File(path).isFile && java.io.File(path).length() > 0L }
                    ?.let { path ->
                        StoryboardExportPanel(
                            path = path,
                            col = panel.gridCol.coerceAtLeast(0),
                            row = panel.gridRow.coerceAtLeast(0),
                            colSpan = panel.gridColSpan.coerceAtLeast(1),
                            rowSpan = panel.gridRowSpan.coerceAtLeast(1),
                            rotationDeg = panel.panelRotationDeg,
                            overlays = panel.overlays,
                        )
                    }
            }
            if (panels.isEmpty()) {
                _uiState.update { it.copy(storyboardStatus = "There is no valid panel artwork to export.") }
                return@launch
            }
            runCatching {
                storyboardPageExporter.export(
                    pageId = page.id,
                    title = page.title ?: "Storyboard page",
                    templateId = page.templateId,
                    panels = panels,
                )
            }.onSuccess { file ->
                _uiState.update { it.copy(storyboardStatus = "Exported page PNG to ${file.absolutePath}") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(storyboardStatus = "Page export failed: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private suspend fun resolveStoryboardMedia(
        query: String,
        source: String,
        usedMediaIds: Set<String>,
        allowAiArt: Boolean,
    ): MediaEntity? {
        val tokens = ("$query $source")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()
        val saved = _uiState.value.mediaPanels
            .mapNotNull { panel -> mediaRepository.getById(panel.mediaId)?.let { panel to it } }
            .filter { (_, media) -> media.type == "image" && mediaRepository.resolveFile(media).let { it.isFile && it.length() > 0L } }
        val all = db.mediaDao().observeAll().first()
            .filter { media -> media.type == "image" && mediaRepository.resolveFile(media).let { it.isFile && it.length() > 0L } }
        val ranked = (saved.map { (panel, media) -> media to 1000 + mediaScore(media, panel.caption, tokens) } +
            all.map { media -> media to mediaScore(media, "", tokens) })
            .distinctBy { it.first.id }
            .sortedWith(compareByDescending<Pair<MediaEntity, Int>> { it.second }.thenBy { it.first.id })
        val preferred = ranked.firstOrNull { it.first.id !in usedMediaIds && it.second > 0 }?.first
        if (preferred != null) return preferred
        if (allowAiArt) {
            generateStoryboardArt(query = query, source = source)?.let { return it }
        }
        return ensureStoryboardFallbackMedia()
    }

    private suspend fun generateStoryboardArt(query: String, source: String): MediaEntity? {
        val modelRef = PromptModelSelection.effectiveModelRef(
            _uiState.value.selectedModelRef,
            _uiState.value.defaultModelRef,
        )
        if (!aiGeneration.hasApiKey(modelRef)) return null
        return runCatching {
            val result = aiGeneration.generateImage(
                prompt = "Editable ${if (_uiState.value.storyboardGeneration.rightToLeft) "manga" else "comic"} panel art. " +
                    "Scene: $source. Panel direction: $query. No text or speech bubbles.",
                modelRef = modelRef,
            )
            val mime = result.second
            mediaRepository.importFromBytes(
                bytes = result.first,
                fileName = "storyboard-${UUID.randomUUID()}.${if (mime == "image/png") "png" else "jpg"}",
                mimeType = mime,
            )
        }.getOrNull()
    }

    private fun mediaScore(media: MediaEntity, extra: String, tokens: Set<String>): Int {
        val metadata = "${media.displayName} ${media.category} ${media.tags} $extra".lowercase()
        return tokens.count { it in metadata } * 10 +
            if (media.category.contains("scene", ignoreCase = true) || media.tags.contains("scene", ignoreCase = true)) 3 else 0
    }

    private suspend fun ensureStoryboardFallbackMedia(): MediaEntity {
        storyboardFallbackMediaId?.let { id -> mediaRepository.getById(id)?.let { return it } }
        val id = "storyboard-fallback-art"
        mediaRepository.getById(id)?.let {
            storyboardFallbackMediaId = id
            return it
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            1200,
            800,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(38, 43, 62))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 52f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("STORYBOARD", 600f, 380f, paint)
        paint.textSize = 28f
        paint.alpha = 190
        canvas.drawText("offline fallback art", 600f, 430f, paint)
        val entity = mediaRepository.importFromBytes(
            bytes = ImageOps.toPngBytes(bitmap),
            id = id,
            fileName = "storyboard-fallback-art.png",
            mimeType = "image/png",
        )
        bitmap.recycle()
        storyboardFallbackMediaId = entity.id
        return entity
    }

    /** ☁️ AI: generate a picture with a cloud image model and attach it. */
    fun openImageGen() {
        viewModelScope.launch {
            val infos = modelCache.toModelInfo(modelCache.models.first())
                .filter { it.generatesImages && it.available }
            _uiState.update {
                it.copy(
                    showImageGen = true,
                    imageGenModels = infos,
                    imageGenModelRef = it.imageGenModelRef.ifBlank {
                        infos.firstOrNull()?.id?.let { id -> PromptModelSelection.modelRef(id) }.orEmpty()
                    },
                    imageGenStatus = "",
                )
            }
        }
    }

    fun closeImageGen() {
        generateJob?.cancel()
        _uiState.update { it.copy(showImageGen = false, imageGenBusy = false) }
    }

    fun onImageGenPrompt(value: String) {
        _uiState.update { it.copy(imageGenPrompt = value, imageGenStatus = "") }
    }

    fun selectImageGenModel(modelId: String) {
        _uiState.update { it.copy(imageGenModelRef = PromptModelSelection.modelRef(modelId)) }
    }

    fun generateImageMedia() {
        val state = _uiState.value
        if (state.imageGenBusy) return
        val prompt = state.imageGenPrompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(imageGenStatus = "Describe the picture first.") }
            return
        }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.update { it.copy(imageGenBusy = true, imageGenStatus = "Generating with the cloud model…") }
            runCatching {
                val modelRef = state.imageGenModelRef.ifBlank { null }
                if (!aiGeneration.hasApiKey(modelRef)) {
                    throw AIError.NoApiKey()
                }
                val (bytes, mime) = aiGeneration.generateImage(prompt, modelRef)
                val media = mediaRepository.importFromBytes(
                    bytes = bytes,
                    fileName = "gen-${System.currentTimeMillis()}.${if (mime == "image/png") "png" else "jpg"}",
                    mimeType = mime,
                )
                val selected = state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }
                val selectedMessage = selected?.let { rawMessages.find { message -> message.id == it[0] } }
                if (selected != null && selectedMessage != null) {
                    val blocks = documentFromJson(selectedMessage.contentJson).blocks.toMutableList()
                    val index = blocks.indexOfFirst { it.id == selected[1] }
                    val current = blocks.getOrNull(index)
                    if (current is MediaBlock) {
                        blocks[index] = current.copy(
                            mediaId = media.id,
                            kind = MediaRepository.kindForType(media.type),
                            originalMediaId = current.originalMediaId ?: current.mediaId,
                            variantKind = "generated",
                        )
                        persistMessageBlocks(selectedMessage, blocks)
                    }
                } else {
                    val doc = Document(
                        blocks = listOf(
                            MediaBlock(
                                id = UUID.randomUUID().toString(),
                                mediaId = media.id,
                                kind = MediaRepository.kindForType(media.type),
                                pageId = _uiState.value.activePageId,
                            ),
                        ),
                    )
                    val now = System.currentTimeMillis()
                    db.roleplayDao().upsertMessage(
                        RpMessageEntity(
                            id = "rpm-$now",
                            chatId = state.chatId,
                            swipeGroupId = "sw-$now",
                            swipeIndex = 0,
                            isActiveSwipe = true,
                            role = "user",
                            contentJson = doc.toJson(),
                            createdAt = now,
                            displayMode = currentDisplayMode(),
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        showImageGen = false,
                        imageGenBusy = false,
                        imageGenPrompt = "",
                        storyboardStatus = if (state.selectedMediaKey != null) {
                            "AI picture replaced the selected panel. Undo is available."
                        } else {
                            "AI picture added to this page."
                        },
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        imageGenBusy = false,
                        imageGenStatus = err.message?.takeIf { m -> m.isNotBlank() }
                            ?: "Generation failed — check the model and key.",
                    )
                }
            }
        }
    }

    fun clearMediaPickRequest() {
        // Consumed as soon as the picker launches, so re-entering the screen
        // never auto-opens the gallery.
        _uiState.update { it.copy(mediaPickRequestId = 0L) }
    }

    fun attachMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val mediaList = mediaRepository.importFromUris(uris)
                val caption = _uiState.value.input.ifBlank { "[media]" }
                val blocks = buildList {
                    add(
                        Paragraph(
                            "p-${System.currentTimeMillis()}",
                            listOf(Span(caption)),
                        ),
                    )
                    val pageId = _uiState.value.activePageId
                    mediaList.forEach { media ->
                        add(
                            MediaBlock(
                                id = UUID.randomUUID().toString(),
                                mediaId = media.id,
                                kind = MediaRepository.kindForType(media.type),
                                pageId = pageId,
                            ),
                        )
                    }
                }
                val doc = Document(blocks = blocks)
                val now = System.currentTimeMillis()
                val entity = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = _uiState.value.chatId,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = doc.toJson(),
                    createdAt = now,
                    displayMode = currentDisplayMode(),
                )
                insertStoredMessage(entity)
                _uiState.update { it.copy(input = "") }
            }
        }
    }

    fun expandComposer() {
        _uiState.update { it.copy(composerMinLines = (it.composerMinLines + 1).coerceAtMost(8)) }
    }

    /**
     * Sets scene art from an image already in the app's Pictures library —
     * no device picker, no re-import.
     */
    fun attachExistingMedia(mediaId: String) {
        if (mediaId.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val caption = _uiState.value.input.ifBlank { "[media]" }
            val doc = Document(
                blocks = listOf(
                    Paragraph("p-$now", listOf(Span(caption))),
                    MediaBlock(
                        id = UUID.randomUUID().toString(),
                        mediaId = mediaId,
                        kind = MediaRepository.kindForType("image"),
                        pageId = _uiState.value.activePageId,
                    ),
                ),
            )
            val entity = RpMessageEntity(
                id = "rpm-$now",
                chatId = _uiState.value.chatId,
                swipeGroupId = "sw-$now",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = doc.toJson(),
                createdAt = now,
                displayMode = currentDisplayMode(),
            )
            insertStoredMessage(entity)
            _uiState.update { it.copy(input = "") }
        }
    }

    fun send() {
        // "!character a scarred caravan guard" writes the entry, files it in the
        // roster, and posts the same words into the adventure.
        com.ihy2ln.weaverse.feature.novel.codex.CodexBang.parse(
            _uiState.value.input,
            customBangCommands,
            removedBangKeywords,
        )?.let { command ->
            runCodexBang(command)
            return
        }
        // "*action swing at the warden" — tabletop player-turn commands.
        RpgTurnCommands.parse(_uiState.value.input, starCommands)?.let { turn ->
            runRpgTurn(turn)
            return
        }
        val startupPending = _uiState.value.adventureStartupPhase in setOf(
            AdventureStartupPhase.Character,
            AdventureStartupPhase.Choose,
            AdventureStartupPhase.Questions,
            AdventureStartupPhase.CuratedQuestions,
            AdventureStartupPhase.Review,
        )
        if (_uiState.value.entryMode == "nai" && !startupPending) addManualEntry() else generate()
    }

    /** Submits generated setup text atomically; avoids racing StateFlow updates from setup buttons. */
    fun submitAdventurePlan(plan: String) {
        val normalized = plan.trim()
        if (normalized.isBlank() || _uiState.value.isStreaming) return
        _uiState.update { it.copy(input = normalized, errorMessage = "", adventurePlanProgress = 1) }
        generate(inputOverride = normalized)
    }

    /** Starts one of the curated Adventure setup openings with a single tap. */
    fun startAdventurePreset(presetId: String) {
        val state = _uiState.value
        if (state.isStreaming || state.adventureStartupPhase != AdventureStartupPhase.Choose) return
        val preset = adventureStartupPresets().firstOrNull { it.id == presetId } ?: return
        _uiState.update { it.copy(input = preset.command, errorMessage = "") }
        send()
    }

    /**
     * Runs a `*` player-turn command: tag the prompt, and resolve a roll for
     * the commands that need one (action / check / attack / cast).
     */
    private fun runRpgTurn(turn: RpgTurnCommands.ParsedTurn) {
        val state = _uiState.value
        if (state.isStreaming) return
        val body = turn.text.ifBlank { turn.command.keyword.replaceFirstChar { it.uppercase() } }
        val tagged = turn.command.promptTag?.let { "$it $body" } ?: body
        _uiState.update { it.copy(input = tagged, errorMessage = "") }
        if (turn.command.requiresRoll) {
            generate(forceAdventureRoll = true)
        } else {
            generate()
        }
    }

    /**
     * Generates a codex entry from a `!kind …` line and posts the prose it
     * rendered into the scene — the same words the entry stores.
     */
    private fun runCodexBang(command: com.ihy2ln.weaverse.feature.novel.codex.CodexBangCommand) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank() || _uiState.value.isStreaming) return
        viewModelScope.launch {
            _uiState.update { it.copy(input = "", isStreaming = true, errorMessage = "") }
            val recent = runCatching {
                db.roleplayDao().getMessages(chatId).takeLast(6).joinToString(separator = System.lineSeparator()) {
                    documentFromJson(it.contentJson).plainText()
                }
            }.getOrDefault("")
            runCatching { codexQuickAdd.run(command, sceneContext = recent) }
                .onSuccess { result ->
                    val now = System.currentTimeMillis()
                    val entity = RpMessageEntity(
                        id = "rpm-$now",
                        chatId = chatId,
                        swipeGroupId = "sw-$now",
                        swipeIndex = 0,
                        isActiveSwipe = true,
                        role = "char",
                        contentJson = Document.fromPlainText(result.text).toJson(),
                        createdAt = now,
                        displayMode = _uiState.value.displayMode,
                    )
                    db.roleplayDao().upsertMessage(entity)
                    workspaceHistory.record(
                        undo = { db.roleplayDao().deleteMessage(entity.id) },
                        redo = { db.roleplayDao().upsertMessage(entity) },
                    )
                    _uiState.update { it.copy(isStreaming = false, composerStatus = result.status) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            errorMessage = err.message ?: "Could not write that entry",
                        )
                    }
                }
        }
    }

    /** Explicit tabletop override: submit the typed action and require a resolved check. */
    fun rollAction() {
        val state = _uiState.value
        if (state.input.isBlank() || state.isStreaming) return
        generate(forceAdventureRoll = true)
    }

    /** ✓ hold-menu ↻: reroll the latest AI reply without retyping anything. */
    fun regenerateLatestReply() {
        if (_uiState.value.isStreaming) return
        val latest = rawMessages.lastOrNull { it.role == "char" && it.isActiveSwipe } ?: return
        regenerate(latest.id)
    }

    /** ✓ hold-menu »: keep the adventure going from where it left off. */
    fun continueAdventure() {
        if (_uiState.value.isStreaming) return
        onInputChange("Continue the scene.")
        generate()
    }

    /** ✓ hold-menu 👤: read the recent scene, then confirm what joins the roster. */
    fun addRosterCharacter() {
        startCapture("roster")
    }

    /** ✓ hold-menu 🛍: read the recent scene, then confirm what files into inventories. */
    fun addInventoryItem() {
        startCapture("inventory")
    }

    /** Long-press on a message: scan that highlighted text instead of the whole scene. */
    fun captureFromText(text: String, kind: String) {
        viewModelScope.launch { openCapture(kind, text) }
    }

    private fun startCapture(kind: String) {
        viewModelScope.launch {
            openCapture(kind, recentSceneText())
        }
    }

    private suspend fun openCapture(kind: String, source: String) {
        val chatId = _uiState.value.chatId.ifBlank { boundChat?.id.orEmpty() }
        if (source.isBlank()) {
            setComposerStatus("Nothing to read yet — play a scene first.")
            return
        }
        if (kind == "ai") {
            openAiCapture(source, chatId)
            return
        }
        val extraction = runCatching { adventureCapture.extract(source) }.getOrNull()
        if (kind == "roster") {
            val chars = extraction?.characters.orEmpty().filter { it.name.isNotBlank() }
            if (chars.isEmpty()) {
                adventureCapture.addBlankCharacter()
                setComposerStatus("No characters detected — added a blank roster character to edit.")
                return
            }
            _uiState.update {
                it.copy(
                    captureDialog = CaptureDialogState(
                        kind = kind,
                        sourceText = source,
                        extraction = extraction!!,
                        candidates = chars.map { char ->
                            CaptureCandidate(
                                name = char.name,
                                summary = listOf(
                                    char.characterClass,
                                    char.species,
                                    if (char.level > 0) "Lv ${char.level}" else "",
                                    if (char.maxHp > 0) "HP ${char.currentHp}/${char.maxHp}" else "",
                                    if (char.inParty) "party" else "",
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                    .ifBlank { char.notes },
                            )
                        },
                    ),
                )
            }
        } else {
            val items = extraction?.items.orEmpty().filter { it.name.isNotBlank() }
            if (items.isEmpty()) {
                val carrier = adventureCapture.addBlankItem(chatId)
                setComposerStatus(
                    if (carrier == null) {
                        "No inventory carrier found — add or mark a character first."
                    } else {
                        "No items detected — added a blank item to $carrier's inventory."
                    },
                )
                return
            }
            _uiState.update {
                it.copy(
                    captureDialog = CaptureDialogState(
                        kind = kind,
                        sourceText = source,
                        extraction = extraction!!,
                        candidates = items.map { item ->
                            CaptureCandidate(
                                name = item.name,
                                summary = listOf(
                                    "×${item.quantity.coerceAtLeast(1)}",
                                    item.carrier.ifBlank { "party" },
                                    item.notes,
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            )
                        },
                    ),
                )
            }
        }
    }

    /**
     * AI-sorted capture: asks the model to split the selected text into
     * character-sheet facts, inventory items, and codex lore, then shows a
     * review dialog where the user confirms or re-routes each section.
     */
    private suspend fun openAiCapture(source: String, chatId: String) {
        setComposerStatus("AI is sorting the text…")
        val plan = runCatching { adventureCapture.plan(source) }.getOrNull()
        if (plan == null) {
            setComposerStatus(
                "The AI could not sort that text. Add an OpenRouter key, or use " +
                    "Add to roster / Add to inventory directly.",
            )
            return
        }
        val candidates = buildList {
            plan.characters.filter { it.name.isNotBlank() }.forEach { char ->
                add(
                    CaptureCandidate(
                        name = "[C] ${char.name}",
                        summary = listOf(
                            char.characterClass,
                            char.species,
                            if (char.level > 0) "Lv ${char.level}" else "",
                            if (char.maxHp > 0) "HP ${char.currentHp}/${char.maxHp}" else "",
                            if (char.inParty) "party" else "",
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { char.notes },
                    ),
                )
            }
            plan.items.filter { it.name.isNotBlank() }.forEach { item ->
                add(
                    CaptureCandidate(
                        name = "[I] ${item.name}",
                        summary = listOf(
                            "×${item.quantity.coerceAtLeast(1)}",
                            item.carrier.ifBlank { "party" },
                            item.notes,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                    ),
                )
            }
            plan.lore.filter { it.text.isNotBlank() || it.title.isNotBlank() }.forEach { blob ->
                add(
                    CaptureCandidate(
                        name = "[L] ${blob.title.ifBlank { blob.category.ifBlank { "Lore" } }}",
                        summary = blob.text.take(120),
                    ),
                )
            }
        }
        if (candidates.isEmpty()) {
            setComposerStatus("Nothing worth sorting was found in that text.")
            return
        }
        _uiState.update {
            it.copy(
                captureDialog = CaptureDialogState(
                    kind = "ai",
                    sourceText = source,
                    extraction = AdventureCapture.Extraction(
                        characters = plan.characters,
                        items = plan.items,
                        lore = plan.lore,
                    ),
                    candidates = candidates,
                ),
            )
        }
    }

    fun toggleCaptureCandidate(name: String) {
        _uiState.update { state ->
            val dialog = state.captureDialog ?: return@update state
            state.copy(
                captureDialog = dialog.copy(
                    candidates = dialog.candidates.map {
                        if (it.name == name) it.copy(selected = !it.selected) else it
                    },
                ),
            )
        }
    }

    /** Applies only the checked candidates. */
    fun confirmCapture() {
        val dialog = _uiState.value.captureDialog ?: return
        val chatId = _uiState.value.chatId.ifBlank { boundChat?.id.orEmpty() }
        viewModelScope.launch {
            val selected = dialog.candidates.filter { it.selected }.map { it.name }.toSet()
            val message = when (dialog.kind) {
                "roster" -> {
                    val chosen = dialog.extraction.characters.filter { "[C] ${it.name}" in selected || it.name in selected }
                    if (chosen.isEmpty()) {
                        "Nothing selected."
                    } else {
                        "Added to roster: ${adventureCapture.applyCharacters(chosen, chatId).joinToString()}"
                    }
                }
                "ai" -> {
                    val chosenChars = dialog.extraction.characters.filter { "[C] ${it.name}" in selected }
                    val chosenItems = dialog.extraction.items.filter { "[I] ${it.name}" in selected }
                    val chosenLore = dialog.extraction.lore.filter {
                        "[L] ${it.title.ifBlank { it.category.ifBlank { "Lore" } }}" in selected
                    }
                    if (chosenChars.isEmpty() && chosenItems.isEmpty() && chosenLore.isEmpty()) {
                        "Nothing selected."
                    } else {
                        adventureCapture.applyPlan(
                            AdventureCapture.CapturePlan(
                                characters = chosenChars,
                                items = chosenItems,
                                lore = chosenLore,
                            ),
                            chatId,
                        ).ifBlank { "Nothing was placed." }
                    }
                }
                else -> {
                    val chosen = dialog.extraction.items.filter { it.name in selected }
                    if (chosen.isEmpty()) {
                        "Nothing selected."
                    } else {
                        "Items filed: ${adventureCapture.applyItems(chosen, chatId)}"
                    }
                }
            }
            _uiState.update { it.copy(captureDialog = null, composerStatus = message) }
            startComposerStatusTimer()
        }
    }

    fun dismissCapture() {
        _uiState.update { it.copy(captureDialog = null) }
    }

    private fun setComposerStatus(message: String) {
        _uiState.update { it.copy(composerStatus = message) }
        startComposerStatusTimer()
    }

    private fun recentSceneText(): String = rawMessages
        .filter { it.isActiveSwipe && it.role != ADVENTURE_SCENE_ROLE }
        .takeLast(6)
        .joinToString("\n") { documentFromJson(it.contentJson).plainText() }

    private fun startComposerStatusTimer() {
        composerStatusJob?.cancel()
        composerStatusJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(composerStatus = "") }
        }
    }

    /** Non-AI (NAI): insert the typed text as a user message without calling a model. */
    fun addManualEntry() {
        viewedSceneNumber = null
        insertUserText(_uiState.value.input)
    }

    /** Insert dictated / pasted plain text as a user message in the active display mode. */
    fun insertUserText(text: String) {
        val state = _uiState.value
        if (text.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (currentDisplayMode() == "dungeonMaster" && ExplicitStayInScene.containsMatchIn(text)) {
                removeLatestSceneMarker()
            }
            if (currentDisplayMode() == "dungeonMaster" && ExplicitSceneAdvance.containsMatchIn(text)) {
                upsertCurrentSceneLore(reason = "The player explicitly advanced the scene.")
                insertSceneMarker(state.chatId, "The player explicitly advanced the scene.", now - 1)
            }
            val entity = RpMessageEntity(
                id = "rpm-$now",
                chatId = state.chatId,
                swipeGroupId = "sw-$now",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(text.trim()).toJson(),
                createdAt = now,
                displayMode = currentDisplayMode(),
            )
            insertStoredMessage(entity)
            _uiState.update { it.copy(input = "", errorMessage = "") }
        }
    }

    fun generate(forceAdventureRoll: Boolean = false, inputOverride: String? = null) {
        val state = _uiState.value
        val submittedInput = inputOverride ?: state.input
        if (submittedInput.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        val startupPending = state.adventureStartupPhase in setOf(
            AdventureStartupPhase.Character,
            AdventureStartupPhase.Choose,
            AdventureStartupPhase.Questions,
            AdventureStartupPhase.CuratedQuestions,
            AdventureStartupPhase.Review,
        )
        if (state.entryMode == "nai" && !startupPending && !forceAdventureRoll) {
            addManualEntry()
            return
        }
        generateJob?.cancel()
        viewedSceneNumber = null
        generateJob = viewModelScope.launch {
            val activeModelRef = PromptModelSelection.effectiveModelRef(
                state.selectedModelRef,
                state.defaultModelRef,
            )
            if (!aiGeneration.hasApiKey(activeModelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty(), adventurePlanProgress = 0) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val groupId = "sw-$now"
            val userText = submittedInput
            val mode = currentDisplayMode()
            val topicMedia = currentTopicMediaSnapshot()
            val startupPhase = currentAdventureStartupPhase()
            val startupActive = mode == "dungeonMaster" &&
                startupPhase in setOf(
                    AdventureStartupPhase.Character,
                    AdventureStartupPhase.Choose,
                    AdventureStartupPhase.Questions,
                    AdventureStartupPhase.CuratedQuestions,
                    AdventureStartupPhase.Review,
                )
            val startupDirective = if (startupActive) {
                adventureStartupDirective(startupPhase, userText)
            } else {
                ""
            }
            val nextStartupPhase = if (startupActive) {
                nextAdventureStartupPhase(startupPhase, userText)
            } else {
                AdventureStartupPhase.None
            }
            if (startupActive) {
                _uiState.update { it.copy(adventurePlanProgress = 10) }
            }
            val playerAdvancedScene = mode == "dungeonMaster" && ExplicitSceneAdvance.containsMatchIn(userText)
            val playerStayedInScene = mode == "dungeonMaster" && ExplicitStayInScene.containsMatchIn(userText)
            val difficulty = defaultPresets.find { it.id == state.presetId }
            val checkDecision = if (startupActive) {
                AdventureCheckDecision(false, "Adventure setup")
            } else if (mode == "dungeonMaster" && forceAdventureRoll) {
                AdventureCheckDecision(true, "Player-forced action roll")
            } else {
                decideAdventureCheck(userText, state.userIsDungeonMaster)
            }
            val backgroundRoll = checkDecision.takeIf { it.requiresRoll }?.let { decision ->
                simulateAdventureRoll(
                    campaignRules = boundChat?.authorsNote.orEmpty(),
                    modifier = adventureSheetModifier(decision),
                    checkLabel = decision.checkLabel,
                    targetDc = difficulty?.targetDc ?: 12,
                )
            }
            if (mode == "dungeonMaster" && backgroundRoll != null) {
                _uiState.update {
                    it.copy(
                        input = "",
                        isStreaming = true,
                        streamingText = "",
                        errorMessage = "",
                        activeRoll = backgroundRoll,
                        rollAnimationId = System.nanoTime(),
                    )
                }
                // Let the player see the physical roll before the DM begins narrating its consequence.
                delay(850)
            } else if (backgroundRoll == null) {
                _uiState.update { it.copy(activeRoll = null) }
            }
            // Reasoning-capable models and private RPG markers share the completion budget.
            // Reserve headroom, then enforce the user's visible word cap before storage.
            val maxTokens = (state.outputWords * 1.7 + 192).toInt().coerceIn(192, 8192)
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            val userMessage = RpMessageEntity(
                id = "rpm-$now",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(
                    if (startupActive) {
                        withAdventureStartupMarker(userText, startupPhase)
                    } else {
                        userText
                    },
                ).toJson(),
                createdAt = now,
                displayMode = mode,
            )
            if (playerStayedInScene) {
                removeLatestSceneMarker()
            } else if (playerAdvancedScene) {
                upsertCurrentSceneLore(reason = "The player explicitly advanced the scene.")
                insertSceneMarker(state.chatId, "The player explicitly advanced the scene.", now - 1)
            }
            db.roleplayDao().upsertMessage(userMessage)
            boundChat?.let { chat ->
                if (chat.presetId != state.presetId) {
                    val updated = chat.copy(presetId = state.presetId, updatedAt = now)
                    db.roleplayDao().upsertChat(updated)
                    boundChat = updated
                }
            }
            _uiState.update {
                it.copy(
                    input = "",
                    isStreaming = true,
                    streamingText = "",
                    errorMessage = "",
                    adventurePlanProgress = if (startupActive) 25 else it.adventurePlanProgress,
                )
            }
            // History is already mode-filtered via observeMessages(chatId, displayMode).
            val history = rawMessages
                .filter { it.isActiveSwipe && it.displayMode == mode && it.role != ADVENTURE_SCENE_ROLE }
                .map { msg ->
                    val role = if (msg.role == "user") "user" else "assistant"
                    role to documentFromJson(msg.contentJson).plainText()
                }
            // Lorebook-style codex activation: entries whose names/aliases appear in
            // the recent story or the new action are injected into the prompt.
            val codexBlock = runCatching {
                val entries = db.codexDao().getAllEntries()
                val scan = history.takeLast(6).joinToString("\n") { it.second }
                contextBuilder.build(
                    entries,
                    ContextBuildRequest(
                        scanText = scan + "\n" + userText,
                        userMessage = userText,
                        maxContextTokens = 6000,
                        reserveResponseTokens = maxTokens,
                    ),
                ).codexBlock
            }.getOrDefault("")
            val builder = StringBuilder()
            var usageText = ""
            var promptTokens = 0
            var completionTokens = 0
            var costUsd = 0.0
            runCatching {
                aiGeneration.stream(
                    userMessage = userText,
                    assembled = generation.assemble(
                        character = boundCharacter,
                        persona = boundPersona,
                        history = history,
                        outputWords = state.outputWords,
                        difficultyDirective = difficulty?.directive,
                        extraSystem = sessionSystemBlocks(mode) +
                            listOfNotNull(codexBlock.takeIf { it.isNotBlank() }) +
                            listOfNotNull(topicMedia.promptDirective()) +
                            PromptWordLimit.instruction(state.minimumOutputWords, state.outputWords) +
                            listOfNotNull(startupDirective.takeIf { it.isNotBlank() }) +
                            if (mode == "dungeonMaster") {
                            listOf(
                                backgroundRoll?.asHiddenDmInstruction(
                                    difficultyName = difficulty?.name ?: "Medium",
                                    targetDc = difficulty?.targetDc ?: 12,
                                )
                                    ?: noAdventureRollInstruction(),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                    modelRef = activeModelRef,
                    maxTokens = maxTokens,
                    temperature = temperature,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update {
                                it.copy(
                                    streamingText = adventureStartupProseFrom(adventureWorldProseFrom(
                                        adventureProseFrom(
                                            AiSceneAdvanceMarker.replace(
                                                topicMediaVisibleText(builder.toString()),
                                                "",
                                            ).trimStart(),
                                        ),
                                    )),
                                    adventurePlanProgress = if (startupActive) {
                                        (35 + (builder.length * 55 / (state.outputWords * 6).coerceAtLeast(1))).coerceAtMost(90)
                                    } else it.adventurePlanProgress,
                                )
                            }
                        }
                        is AIChunk.Usage -> {
                            promptTokens = chunk.promptTokens
                            completionTokens = chunk.completionTokens
                            costUsd = chunk.cost ?: 0.0
                            usageText = UsageFormat.formatUsage(
                                promptTokens = chunk.promptTokens,
                                completionTokens = chunk.completionTokens,
                                totalTokens = chunk.totalTokens,
                                cost = chunk.cost,
                            )
                        }
                        is AIChunk.RetryWait -> {
                            _uiState.update {
                                it.copy(errorMessage = "Rate limited — retry in ${chunk.secondsLeft}s")
                            }
                        }
                        AIChunk.Done -> Unit
                    }
                }
            }.onFailure { err ->
                workspaceHistory.record(
                    undo = { db.roleplayDao().deleteMessage(userMessage.id) },
                    redo = { db.roleplayDao().upsertMessage(userMessage) },
                )
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err), adventurePlanProgress = 0)
                }
                return@launch
            }
            val rawReply = builder.toString()
            val topicMediaReply = parseTopicMediaReply(rawReply)
            val worldUpdates = adventureWorldUpdatesFrom(topicMediaReply.visibleText)
            val visibleReply = adventureStartupProseFrom(
                adventureProseFrom(AiSceneAdvanceMarker.replace(worldUpdates.prose, "").trim()),
            )
            if (visibleReply.isBlank()) {
                db.roleplayDao().deleteMessage(userMessage.id)
                _uiState.update {
                    it.copy(
                        input = userText,
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = "The selected model returned no visible DM response. Your action was restored; tap ✓ to retry or choose another model.",
                        adventurePlanProgress = 0,
                    )
                }
                return@launch
            }
            persistAdventureWorldUpdates(worldUpdates)
            // Auto-bookkeeping: pull character/item facts into roster + inventory.
            if (mode == "dungeonMaster") {
                val captureChatId = state.chatId
                viewModelScope.launch { adventureCapture.captureAndApply(rawReply, captureChatId) }
            }
            val aiAdvanceMatch = AiSceneAdvanceMarker.find(worldUpdates.prose)
            val aiAdvancedScene = mode == "dungeonMaster" && aiAdvanceMatch != null &&
                !playerAdvancedScene && !ExplicitStayInScene.containsMatchIn(userText)
            if (aiAdvancedScene) {
                upsertCurrentSceneLore(
                    reason = aiAdvanceMatch?.groupValues?.getOrNull(1).orEmpty(),
                )
                insertSceneMarker(
                    state.chatId,
                    aiAdvanceMatch?.groupValues?.getOrNull(1)?.ifBlank { "The game master advanced the scene." }
                        ?: "The game master advanced the scene.",
                    now + 1,
                )
            }
            val cleanedReply = PromptWordLimit.trim(
                AiSceneAdvanceMarker.replace(worldUpdates.prose, "").trim(),
                state.outputWords,
            )
            val replyWithRoll = backgroundRoll?.let { withAdventureRollMarker(cleanedReply, it) }
                ?: cleanedReply
            val storedReply = if (startupActive) {
                withAdventureStartupMarker(replyWithRoll, nextStartupPhase)
            } else {
                replyWithRoll
            }
            val mediaRequests = topicMediaRequestsFor(
                topicMediaReply.copy(visibleText = visibleReply),
                topicMedia.topics,
            )
            val topicDocument = documentWithTopicMedia(storedReply, topicMedia, mediaRequests)
            val artChoice = parseRpgSceneArtChoice(rawReply)
            val finalDocument = if (mode == "dungeonMaster") {
                documentWithBestSceneMedia(
                    document = topicDocument,
                    sceneText = visibleReply,
                    sceneTags = listOfNotNull(artChoice?.category, artChoice?.mood)
                        .filter { it.isNotBlank() }
                        .joinToString(", "),
                )
            } else {
                topicDocument
            }
            val reply = RpMessageEntity(
                id = "rpm-${now + 1}",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = finalDocument.toJson(),
                createdAt = now + if (aiAdvancedScene) 2 else 1,
                displayMode = mode,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                costUsd = costUsd,
            )
            db.roleplayDao().upsertMessage(reply)
            // Do not wait for the Room observer to infer the phase from message ordering.
            // The opening scene is durably stored above, so leave setup immediately.
            if (startupActive) {
                _uiState.update {
                    it.copy(
                        adventureStartupPhase = nextStartupPhase,
                        adventurePlanProgress = 100,
                    )
                }
            }
            if (mode == "dungeonMaster") {
                upsertCurrentSceneLore(
                    summaryOverride = worldUpdates.sceneSynopsis,
                    extraMessages = listOf(userMessage, reply),
                )
            }
            val added = listOf(userMessage, reply)
            workspaceHistory.record(
                undo = { added.forEach { db.roleplayDao().deleteMessage(it.id) } },
                redo = { added.forEach { db.roleplayDao().upsertMessage(it) } },
            )
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingText = "",
                    lastUsage = usageText,
                    adventureStartupPhase = if (startupActive) nextStartupPhase else it.adventureStartupPhase,
                    adventurePlanProgress = if (startupActive) 100 else it.adventurePlanProgress,
                )
            }
            if (mode == "dungeonMaster") {
                worldUpdates.combat?.let { encounter -> beginRpgCombat(encounter) }
            }
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "Cancelled", adventurePlanProgress = 0) }
    }

    fun swipe(messageId: String, direction: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val siblings = rawMessages
                .filter { it.swipeGroupId == current.swipeGroupId && it.role == current.role }
                .sortedBy { it.swipeIndex }
            val idx = siblings.indexOfFirst { it.id == messageId }.coerceAtLeast(0)
            val nextIdx = (idx + direction).coerceIn(0, siblings.lastIndex)
            if (nextIdx == idx) return@launch
            val after = siblings.mapIndexed { i, msg -> msg.copy(isActiveSwipe = i == nextIdx) }
            after.forEach { db.roleplayDao().upsertMessage(it) }
            workspaceHistory.record(
                undo = { siblings.forEach { db.roleplayDao().upsertMessage(it) } },
                redo = { after.forEach { db.roleplayDao().upsertMessage(it) } },
            )
        }
    }

    fun regenerate(messageId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId && it.role == "char" } ?: return@launch
            val state = _uiState.value
            val activeModelRef = PromptModelSelection.effectiveModelRef(
                state.selectedModelRef,
                state.defaultModelRef,
            )
            if (!aiGeneration.hasApiKey(activeModelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val siblings = rawMessages.filter { it.swipeGroupId == current.swipeGroupId && it.role == "char" }
            val words = state.outputWords
            val difficulty = defaultPresets.find { it.id == _uiState.value.presetId }
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            val topicMedia = currentTopicMediaSnapshot()
            _uiState.update { it.copy(isStreaming = true, errorMessage = "") }
            val history = rawMessages
                .filter {
                    it.isActiveSwipe && it.displayMode == current.displayMode &&
                        it.id != messageId && it.role != ADVENTURE_SCENE_ROLE
                }
                .map { msg ->
                    val role = if (msg.role == "user") "user" else "assistant"
                    role to documentFromJson(msg.contentJson).plainText()
                }
            runCatching {
                aiGeneration.complete(
                    userMessage = "Continue the roleplay from here. Write the character's next beat.",
                    assembled = generation.assemble(
                        character = boundCharacter,
                        persona = boundPersona,
                        history = history,
                        outputWords = words,
                        difficultyDirective = difficulty?.directive,
                        extraSystem = sessionSystemBlocks(current.displayMode) +
                            listOfNotNull(topicMedia.promptDirective()) +
                            PromptWordLimit.instruction(state.minimumOutputWords, words),
                    ),
                    modelRef = activeModelRef,
                    maxTokens = (words * 1.7 + 192).toInt().coerceIn(192, 8192),
                    temperature = temperature,
                )
            }.onSuccess { reply ->
                val now = System.currentTimeMillis()
                val topicMediaReply = parseTopicMediaReply(reply.text)
                val trimmedReply = PromptWordLimit.trim(topicMediaReply.visibleText, words)
                val mediaRequests = topicMediaRequestsFor(
                    topicMediaReply.copy(visibleText = trimmedReply),
                    topicMedia.topics,
                )
                val topicDocument = documentWithTopicMedia(trimmedReply, topicMedia, mediaRequests)
                val artChoice = parseRpgSceneArtChoice(reply.text)
                val finalDocument = if (current.displayMode.ifBlank { currentDisplayMode() } == "dungeonMaster") {
                    documentWithBestSceneMedia(
                        document = topicDocument,
                        sceneText = trimmedReply,
                        sceneTags = listOfNotNull(artChoice?.category, artChoice?.mood)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                    )
                } else {
                    topicDocument
                }
                val deactivated = siblings.map { it.copy(isActiveSwipe = false) }
                val generated = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = current.chatId,
                    swipeGroupId = current.swipeGroupId,
                    swipeIndex = siblings.size,
                    isActiveSwipe = true,
                    role = "char",
                    contentJson = finalDocument.toJson(),
                    createdAt = now,
                    displayMode = current.displayMode.ifBlank { currentDisplayMode() },
                )
                deactivated.forEach { db.roleplayDao().upsertMessage(it) }
                db.roleplayDao().upsertMessage(generated)
                if (current.displayMode.ifBlank { currentDisplayMode() } == "dungeonMaster") {
                    val captureChatId = current.chatId
                    viewModelScope.launch {
                        adventureCapture.captureAndApply(reply.text, captureChatId)
                    }
                }
                workspaceHistory.record(
                    undo = {
                        db.roleplayDao().deleteMessage(generated.id)
                        siblings.forEach { db.roleplayDao().upsertMessage(it) }
                    },
                    redo = {
                        deactivated.forEach { db.roleplayDao().upsertMessage(it) }
                        db.roleplayDao().upsertMessage(generated)
                    },
                )
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        lastUsage = UsageFormat.formatUsage(
                            promptTokens = reply.promptTokens,
                            completionTokens = reply.completionTokens,
                            cost = reply.cost,
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isStreaming = false, errorMessage = formatError(err))
                }
            }
        }
    }

    private fun formatError(err: Throwable): String = when (err) {
        is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
        is AIError -> err.message.orEmpty()
        else -> err.message ?: err.toString()
    }

    private fun refreshContextMeter() {
        val state = _uiState.value
        val mode = currentDisplayMode()
        val history = rawMessages
            .filter { it.isActiveSwipe && it.displayMode == mode && it.role != ADVENTURE_SCENE_ROLE }
            .map { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                role to documentFromJson(msg.contentJson).plainText()
            }
        val difficulty = defaultPresets.find { it.id == state.presetId }
        val assembled = generation.assemble(
            character = boundCharacter,
            persona = boundPersona,
            history = history,
            outputWords = state.outputWords,
            difficultyDirective = difficulty?.directive,
            extraSystem = sessionSystemBlocks(mode) +
                PromptWordLimit.instruction(state.minimumOutputWords, state.outputWords),
        )
        val reading = generation.meter(assembled, state.input, contextLimit)
        _uiState.update { it.copy(contextMeter = reading) }
    }

    private fun adventureSheetModifier(decision: AdventureCheckDecision): Int {
        val character = boundCharacter ?: return 0
        val sheet = decodeRpgSheet(character.extensionsJson)
        val score = when (decision.ability) {
            AdventureAbility.Strength -> sheet.strength
            AdventureAbility.Dexterity -> sheet.dexterity
            AdventureAbility.Constitution -> sheet.constitution
            AdventureAbility.Intelligence -> sheet.intelligence
            AdventureAbility.Wisdom -> sheet.wisdom
            AdventureAbility.Charisma -> sheet.charisma
            null -> 10
        }
        return abilityModifier(score) + if (decision.addProficiency) sheet.proficiencyBonus else 0
    }

    private fun currentAdventureStartupPhase(): AdventureStartupPhase = rawMessages
        .asSequence()
        .filter { it.isActiveSwipe && it.displayMode == "dungeonMaster" }
        .sortedByDescending { it.createdAt }
        .map { adventureStartupPhase(documentFromJson(it.contentJson).plainText()) }
        .firstOrNull { it != AdventureStartupPhase.None }
        ?: AdventureStartupPhase.None

    private suspend fun migrateLegacyAdventureOpeningIfNeeded(chat: RpChatEntity) {
        if (chat.displayMode != "dungeonMaster") return
        val messages = db.roleplayDao().getMessagesForMode(chat.id, "dungeonMaster")
            .filter { it.isActiveSwipe && it.role != ADVENTURE_SCENE_ROLE }
        if (messages.size != 1) return
        val opening = messages.single()
        val text = documentFromJson(opening.contentJson).plainText()
        if (!isLegacyPassiveAdventureOpening(text)) return
        db.roleplayDao().upsertMessage(
            opening.copy(
                contentJson = Document.fromPlainText(
                    adventureStartupPrompt(
                        userIsDungeonMaster = userIsDungeonMaster(chat.authorsNote),
                        needsCharacter = chat.authorsNote.contains(
                            "Main character IDs: none",
                            ignoreCase = true,
                        ),
                    ),
                ).toJson(),
            ),
        )
    }

    /** Player-owned scene controls always override the AI game master's pacing. */
    fun advanceScene(reason: String = "The player moved the adventure to the next scene.") {
        val state = _uiState.value
        if (state.chatId.isBlank() || currentDisplayMode() != "dungeonMaster") return
        viewModelScope.launch {
            if (!state.viewingCurrentScene) {
                viewedSceneNumber = (state.sceneNumber + 1).coerceAtMost(state.totalScenes)
                publishMessages()
            } else {
                viewedSceneNumber = null
                upsertCurrentSceneLore(reason = reason)
                insertSceneMarker(state.chatId, reason, System.currentTimeMillis())
            }
        }
    }

    fun previousScene() {
        val state = _uiState.value
        if (state.sceneNumber <= 1 || currentDisplayMode() != "dungeonMaster") return
        viewModelScope.launch {
            viewedSceneNumber = state.sceneNumber - 1
            publishMessages()
        }
    }

    fun undoLastSceneAdvance() {
        viewedSceneNumber = null
        viewModelScope.launch { removeLatestSceneMarker() }
    }

    private suspend fun removeLatestSceneMarker() {
        val marker = rawMessages
            .filter { it.role == ADVENTURE_SCENE_ROLE && it.isActiveSwipe }
            .maxByOrNull { it.createdAt }
            ?: return
        db.roleplayDao().deleteMessage(marker.id)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(marker) },
            redo = { db.roleplayDao().deleteMessage(marker.id) },
        )
    }

    private suspend fun insertSceneMarker(chatId: String, reason: String, createdAt: Long): RpMessageEntity {
        val marker = RpMessageEntity(
            id = "rpscene-${UUID.randomUUID()}",
            chatId = chatId,
            swipeGroupId = "rpscene-${UUID.randomUUID()}",
            swipeIndex = 0,
            isActiveSwipe = true,
            role = ADVENTURE_SCENE_ROLE,
            contentJson = Document.fromPlainText(reason.ifBlank { "The scene changed." }).toJson(),
            createdAt = createdAt,
            displayMode = "dungeonMaster",
        )
        db.roleplayDao().upsertMessage(marker)
        workspaceHistory.record(
            undo = { db.roleplayDao().deleteMessage(marker.id) },
            redo = { db.roleplayDao().upsertMessage(marker) },
        )
        return marker
    }

    private suspend fun persistAdventureWorldUpdates(updates: AdventureWorldUpdates) {
        val chat = boundChat ?: return
        val scopeId = chat.bookId ?: return
        val now = System.currentTimeMillis()
        updates.characters.forEach { update ->
            val existing = db.roleplayDao().getCharacters()
                .firstOrNull { it.name.equals(update.name, ignoreCase = true) }
            val categoryName = when (update.role.lowercase()) {
                "team", "party", "player" -> "Team"
                "enemy", "enemies", "hostile" -> "Enemies"
                "npc", "npcs" -> "NPCs"
                else -> "Other"
            }
            val codexEntry = upsertAdventureCodexEntry(
                scopeId = scopeId,
                category = "Characters",
                name = update.name,
                summary = buildString {
                    if (update.description.isNotBlank()) appendLine(update.description)
                    append("${update.species.ifBlank { "Unknown species" }} · ${update.characterClass} level ${update.level}")
                    if (update.portraitBrief.isNotBlank()) append("\nPortrait brief: ${update.portraitBrief}")
                },
                now = now,
            )
            val generatedSheet = createRpgCharacterSheet(
                name = update.name,
                description = update.description,
                species = update.species,
                characterClass = update.characterClass,
                level = update.level,
                background = update.background,
                strength = update.strength,
                dexterity = update.dexterity,
                constitution = update.constitution,
                intelligence = update.intelligence,
                wisdom = update.wisdom,
                charisma = update.charisma,
            )
            val sheet = existing?.let { decodeRpgSheet(it.extensionsJson) } ?: generatedSheet
            val character = (existing ?: RpCharacterEntity(
                id = "rpc-${UUID.randomUUID()}",
                name = update.name,
                createdAt = now,
            )).copy(
                name = update.name,
                description = existing?.description?.takeIf { it.isNotBlank() } ?: update.description,
                creatorNotes = listOfNotNull(
                    existing?.creatorNotes?.takeIf { it.isNotBlank() },
                    update.portraitBrief.takeIf { it.isNotBlank() }?.let { "Portrait brief: $it" },
                ).distinct().joinToString("\n"),
                tagsJson = "[\"$categoryName\"]",
                extensionsJson = encodeRpgSheet(existing?.extensionsJson ?: "{}", sheet),
                defaultCodexId = codexEntry.id,
                colorHex = existing?.colorHex ?: avatarColorHexFor(update.name, null),
                inParty = categoryName == "Team" || existing?.inParty == true,
            )
            db.roleplayDao().upsertCharacter(character)
            if (character.inParty && boundCharacter == null) {
                boundCharacter = character
                val revisedNote = chat.authorsNote
                    .replace("Main character(s): None selected — guided character creation required", "Main character(s): ${character.name}")
                    .replace("Main character IDs: none", "Main character IDs: roster:${character.id}")
                val revisedChat = chat.copy(authorsNote = revisedNote, updatedAt = now)
                db.roleplayDao().upsertChat(revisedChat)
                boundChat = revisedChat
            }
        }
        updates.lore.forEach { update ->
            upsertAdventureCodexEntry(scopeId, update.category, update.name, update.summary, now)
        }
    }

    private suspend fun upsertAdventureCodexEntry(
        scopeId: String,
        category: String,
        name: String,
        summary: String,
        now: Long,
    ): CodexEntryEntity {
        val categories = db.codexDao().getCategories(scopeId)
        val categoryEntity = categories.firstOrNull { it.name.equals(category, ignoreCase = true) }
            ?: CodexCategoryEntity(
                id = "rpg-cat-${UUID.randomUUID()}",
                scopeType = "book",
                scopeId = scopeId,
                name = category,
                colorHex = if (category.equals("Characters", true)) "#3F7A5A" else "#6B5B95",
                sortOrder = categories.size,
            ).also { db.codexDao().upsertCategory(it) }
        val existing = db.codexDao().getEntries(scopeId)
            .firstOrNull { it.categoryId == categoryEntity.id && it.name.equals(name, ignoreCase = true) }
        val text = summary.trim().ifBlank { name }
        val entry = existing?.copy(
            docJson = Document.fromPlainText(text).toJson(),
            plainText = text,
            isAiGenerated = true,
            updatedAt = now,
        ) ?: CodexEntryEntity(
            id = "rpg-codex-${UUID.randomUUID()}",
            categoryId = categoryEntity.id,
            scopeType = "book",
            scopeId = scopeId,
            name = name,
            docJson = Document.fromPlainText(text).toJson(),
            plainText = text,
            isAiGenerated = true,
            createdAt = now,
            updatedAt = now,
        )
        db.codexDao().upsertEntry(entry)
        return entry
    }

    /** Keeps an adventure-journal synopsis current after every resolved DM turn and scene change. */
    private suspend fun upsertCurrentSceneLore(
        reason: String = "",
        summaryOverride: String = "",
        extraMessages: List<RpMessageEntity> = emptyList(),
    ) {
        val chat = boundChat ?: return
        val scopeId = chat.bookId ?: return
        val startup = currentAdventureStartupPhase()
        if (startup !in setOf(AdventureStartupPhase.None, AdventureStartupPhase.Complete)) return
        val lastMarkerAt = rawMessages
            .filter { it.role == ADVENTURE_SCENE_ROLE && it.isActiveSwipe }
            .maxOfOrNull { it.createdAt } ?: Long.MIN_VALUE
        val sceneMessages = (rawMessages + extraMessages)
            .distinctBy { it.id }
            .filter {
                it.isActiveSwipe && it.displayMode == "dungeonMaster" &&
                    it.role != ADVENTURE_SCENE_ROLE && it.createdAt > lastMarkerAt
            }
        val synopsis = summaryOverride.trim().ifBlank { sceneMessages
            .filter { it.role == "char" }
            .joinToString(" ") { message ->
                adventureStartupProseFrom(adventureWorldProseFrom(adventureProseFrom(
                    documentFromJson(message.contentJson).plainText(),
                )))
            }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1_200)
            .ifBlank { reason.trim().ifBlank { "The scene is in progress." } } }
        upsertAdventureCodexEntry(
            scopeId = scopeId,
            category = "Adventure Journal",
            name = "Scene ${_uiState.value.sceneNumber} · ${chat.title}",
            summary = synopsis,
            now = System.currentTimeMillis(),
        )
    }

    // ------------------------------------------------- structured RPG startup wizard

    private fun setupSnapshot(chat: RpChatEntity): RpgCampaignSetupSnapshot {
        val note = chat.authorsNote
        fun line(label: String): String = Regex("(?im)^" + Regex.escape(label) + ":\\s*(.*)$")
            .find(note)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        // Setting Details and House Rules are saved as bare preset ids ("frontier",
        // "cinematic", "custom"), so the CYOA/Chapter Plan prompts need the actual
        // guidance text resolved here — the id alone tells the model nothing.
        val settingDetailId = line("Setting details preset")
        val settingDetails = effectiveSettingDetailTemplates()
            .firstOrNull { it.id.equals(settingDetailId, ignoreCase = true) }?.details.orEmpty()
        val houseRuleId = line("House rules preset")
        val houseRulePreset = CampaignHouseRuleTemplates
            .firstOrNull { it.id.equals(houseRuleId, ignoreCase = true) }?.directive.orEmpty()
        return RpgCampaignSetupSnapshot(
            title = chat.title,
            setting = line("Setting").ifBlank { "Open fantasy setting" },
            modeId = rpgCombatRulesetFromSetup(note).id,
            ruleSystem = line("Rules system").ifBlank { "D&D d20" },
            houseRules = Regex("(?im)^House rules:\\s*([\\s\\S]*?)(?=\\n\\n|\\z)")
                .find(note)?.groupValues?.getOrNull(1)?.trim().orEmpty(),
            characters = line("Main character(s)"),
            pointOfView = line("Narrative point of view").ifBlank { "Third-person multiple" },
            tense = line("Narrative tense").ifBlank { "Past tense" },
            playerRole = line("Player role").ifBlank { "Adventurer" },
            settingDetails = settingDetails,
            houseRulePreset = houseRulePreset,
        )
    }

    private suspend fun restoreRpgStartup(chat: RpChatEntity) {
        val existed = db.roleplayDao().getRpgCampaignSave(chat.id) != null
        var restored = rpgCampaignRepository.restoreRpgCampaign(
            chat.id,
            rpgCombatRulesetFromSetup(chat.authorsNote).id,
            setupSnapshot(chat).ruleSystem,
        )
        if (!existed) {
            val legacyMessages = db.roleplayDao().getMessagesForMode(chat.id, "dungeonMaster")
            val legacyPhase = legacyMessages.asReversed().asSequence()
                .map { adventureStartupPhase(documentFromJson(it.contentJson).plainText()) }
                .firstOrNull { it != AdventureStartupPhase.None } ?: AdventureStartupPhase.None
            val step = when (legacyPhase) {
                AdventureStartupPhase.Complete -> RpgStartupStep.Started
                AdventureStartupPhase.Review -> RpgStartupStep.Verification
                AdventureStartupPhase.None -> if (legacyMessages.any { message ->
                    documentFromJson(message.contentJson).plainText().isNotBlank()
                }) RpgStartupStep.Started else RpgStartupStep.Cyoa
                else -> RpgStartupStep.Cyoa
            }
            restored = restored.copy(startup = restored.startup.copy(step = step))
        }
        restored = restored.copy(
            modeId = rpgCombatRulesetFromSetup(chat.authorsNote).id,
            startup = restored.startup.copy(
                setup = setupSnapshot(chat),
                generationStatus = if (restored.startup.generationStatus == RpgGenerationStatus.Generating) {
                    RpgGenerationStatus.Failed
                } else restored.startup.generationStatus,
                generationError = if (restored.startup.generationStatus == RpgGenerationStatus.Generating) {
                    "Generation was interrupted. Tap Retry to continue."
                } else restored.startup.generationError,
            ),
        )
        loadedRpgCampaignId = chat.id
        persistRpgCampaign(restored)
    }

    private fun authoritativeRpgMode(): RpgCombatRuleset {
        val campaign = rpgCampaignState
        val savedModeId = campaign?.startup?.setup?.modeId?.takeIf { it.isNotBlank() }
            ?: campaign?.modeId?.takeIf { it.isNotBlank() }
        return savedModeId?.let(RpgCombatRuleset::fromId)
            ?: rpgCombatRulesetFromSetup(boundChat?.authorsNote.orEmpty())
    }

    /** Opens the RPG-native encounter screen in the campaign's authoritative mode. */
    fun beginRpgCombat(start: AdventureCombatStart? = null) {
        if (_uiState.value.activeRpgCombat != null || _uiState.value.isStreaming) return
        viewModelScope.launch {
            val chat = boundChat ?: return@launch
            val mode = authoritativeRpgMode()
            val campaign = rpgCampaignState ?: createRpgCampaign(chat.id, mode.id)
            val currentNode = campaign.map.nodes.firstOrNull { it.id == campaign.map.currentNodeId }
            val roster = db.roleplayDao().getCharacters()
            val partyEntities = buildList {
                boundCharacter?.let(::add)
                addAll(roster.filter { it.inParty })
            }.distinctBy { it.id }.take(4)
            val party = partyEntities.map { character ->
                val sheet = decodeRpgSheet(character.extensionsJson)
                val portraitPath = character.avatarMediaId
                    ?.let { mediaRepository.getById(it) }
                    ?.let { media -> mediaRepository.resolveFile(media) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                    ?.absolutePath
                    .orEmpty()
                val attack = when (mode) {
                    RpgCombatRuleset.CardBattle -> sheet.tacticalAttack
                    else -> maxOf(
                        abilityModifier(sheet.strength),
                        abilityModifier(sheet.dexterity),
                        abilityModifier(sheet.intelligence),
                        abilityModifier(sheet.wisdom),
                        abilityModifier(sheet.charisma),
                    ) + sheet.proficiencyBonus
                }
                RpgCombatant(
                    id = character.id,
                    name = character.name,
                    maxHp = sheet.maxHp.coerceAtLeast(1),
                    hp = (campaign.party.hpByMember[character.id] ?: sheet.currentHp).coerceIn(0, sheet.maxHp.coerceAtLeast(1)),
                    armorClass = sheet.armorClass.coerceAtLeast(1),
                    attackModifier = attack,
                    statuses = campaign.party.conditionsByMember[character.id].orEmpty().mapNotNull { value ->
                        runCatching { RpgStatusEffect.valueOf(value) }.getOrNull()
                    }.toSet(),
                    artPath = portraitPath,
                )
            }.ifEmpty {
                val portraitPath = boundPersona?.avatarMediaId
                    ?.let { mediaRepository.getById(it) }
                    ?.let { media -> mediaRepository.resolveFile(media) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                    ?.absolutePath
                    .orEmpty()
                listOf(
                    RpgCombatant(
                        id = "party-hero",
                        name = boundPersona?.name?.ifBlank { "Hero" } ?: "Hero",
                        maxHp = 12,
                        armorClass = 12,
                        attackModifier = 3,
                        artPath = portraitPath,
                    ),
                )
            }
            val enemyNames = start?.enemies?.filter(String::isNotBlank).orEmpty().ifEmpty { listOf("Hostile Threat") }
            val enemies = enemyNames.mapIndexed { index, name ->
                val monsterArt = pickGkomVariant(
                    enemyId = name,
                    seed = chat.id.hashCode().toLong() + index,
                )?.artAssetPath.orEmpty()
                RpgCombatant(
                    id = "enemy-${UUID.randomUUID()}",
                    name = name,
                    maxHp = 10 + index * 4,
                    armorClass = 12 + index.coerceAtMost(2),
                    attackModifier = 2 + index.coerceAtMost(2),
                    isEnemy = true,
                    artPath = monsterArt.takeIf(String::isNotBlank)
                        ?.let { "file:///android_asset/$it" }
                        .orEmpty(),
                )
            }
            val latestScene = rawMessages.asReversed().firstOrNull { it.role != "user" && it.role != ADVENTURE_SCENE_ROLE }
                ?.let { documentFromJson(it.contentJson).plainText().lineSequence().firstOrNull()?.trim() }
                .orEmpty()
            val setup = RpgEncounterSetup(
                id = currentNode?.encounterId
                    ?: "enc-${UUID.randomUUID()}",
                title = start?.title?.takeIf { it.isNotBlank() } ?: "Scene ${_uiState.value.sceneNumber} Encounter",
                stakes = start?.stakes?.takeIf { it.isNotBlank() }
                    ?: latestScene.ifBlank { "The party must overcome the immediate threat." },
                campaignRuleset = mode,
                enemies = enemies,
                party = party,
            )
            val combat = createRpgEncounter(setup)
            val partyState = campaign.party.copy(
                memberIds = party.map { it.id },
                hpByMember = campaign.party.hpByMember + party.associate { it.id to it.hp },
                conditionsByMember = campaign.party.conditionsByMember + party.associate { it.id to it.statuses.map { status -> status.name }.toSet() },
            )
            persistRpgCampaign(campaign.copy(party = partyState, activeCombatJson = rpgCombatJson.encodeToString(combat)))
            _uiState.update {
                it.copy(
                    activeRpgCombat = combat,
                    selectedCombatCardId = null,
                    selectedCombatTargetId = null,
                    combatActionPreview = null,
                    combatTextAction = "",
                    composerStatus = "Entered ${mode.label} combat.",
                )
            }
        }
    }

    /** One-encounter override. The campaign mode itself is deliberately unchanged. */
    fun selectEncounterRuleset(ruleset: RpgCombatRuleset) {
        val combat = _uiState.value.activeRpgCombat ?: return
        val updated = combat.copy(ruleset = ruleset)
        viewModelScope.launch {
            persistActiveCombat(updated)
            _uiState.update {
                it.copy(
                    selectedCombatCardId = null,
                    selectedCombatTargetId = null,
                    combatActionPreview = null,
                    combatTextAction = "",
                )
            }
        }
    }

    fun selectCombatCard(card: com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatCard) {
        _uiState.update { current ->
            val combat = current.activeRpgCombat ?: return@update current
            val cardId = card.id
            val action = RpgCombatAction(
                actorId = combat.activeCombatantId,
                cardId = cardId,
                targetId = current.selectedCombatTargetId,
            )
            current.copy(
                selectedCombatCardId = cardId,
                combatActionPreview = previewRpgCombatAction(combat, action),
            )
        }
    }

    fun selectCombatTarget(target: RpgCombatant) {
        _uiState.update { current ->
            val combat = current.activeRpgCombat ?: return@update current
            val action = RpgCombatAction(
                actorId = combat.activeCombatantId,
                cardId = current.selectedCombatCardId,
                targetId = target.id,
                text = current.combatTextAction,
                requestedCheck = riskyCombatText.containsMatchIn(current.combatTextAction),
            )
            current.copy(
                selectedCombatTargetId = target.id,
                combatActionPreview = previewRpgCombatAction(combat, action),
            )
        }
    }

    fun onCombatTextAction(value: String) {
        _uiState.update { current ->
            val combat = current.activeRpgCombat ?: return@update current
            val targetId = current.selectedCombatTargetId
                ?: combat.combatants.firstOrNull { it.isEnemy && it.hp > 0 }?.id
            val action = RpgCombatAction(
                actorId = combat.activeCombatantId,
                targetId = targetId,
                text = value,
                requestedCheck = riskyCombatText.containsMatchIn(value),
            )
            current.copy(
                combatTextAction = value,
                selectedCombatTargetId = targetId,
                combatActionPreview = previewRpgCombatAction(combat, action),
            )
        }
    }

    fun confirmCombatAction() {
        val current = _uiState.value
        val combat = current.activeRpgCombat ?: return
        val targetId = current.selectedCombatTargetId
            ?: combat.combatants.firstOrNull { it.isEnemy && it.hp > 0 }?.id
        val action = RpgCombatAction(
            actorId = combat.activeCombatantId,
            cardId = current.selectedCombatCardId,
            targetId = targetId,
            text = current.combatTextAction,
            requestedCheck = riskyCombatText.containsMatchIn(current.combatTextAction),
        )
        val preview = previewRpgCombatAction(combat, action)
        if (!preview.legal) {
            _uiState.update { it.copy(combatActionPreview = preview) }
            return
        }
        val resolved = resolveRpgCombatAction(
            combat,
            action,
            seed = combat.encounter.id.hashCode().toLong() + combat.turn,
        )
        viewModelScope.launch {
            persistActiveCombat(resolved)
            _uiState.update {
                it.copy(
                    selectedCombatCardId = null,
                    selectedCombatTargetId = null,
                    combatActionPreview = null,
                    combatTextAction = "",
                )
            }
        }
    }

    fun retreatRpgCombat() {
        val combat = _uiState.value.activeRpgCombat ?: return
        if (combat.finished) return
        val outcome = RpgCombatOutcome(
            encounterId = combat.encounter.id,
            result = RpgCombatOutcome.Result.Retreat,
            ruleset = combat.ruleset,
            survivingPartyIds = combat.combatants.filter { !it.isEnemy && it.hp > 0 }.map { it.id },
            recap = "The party retreated from ${combat.encounter.title}.",
        )
        viewModelScope.launch { persistActiveCombat(combat.copy(finished = true, outcome = outcome)) }
    }

    fun finishRpgCombat() {
        val combat = _uiState.value.activeRpgCombat ?: return
        val outcome = combat.outcome ?: return
        viewModelScope.launch {
            combat.combatants.filterNot { it.isEnemy }.forEach { member ->
                db.roleplayDao().getCharacter(member.id)?.let { character ->
                    val sheet = decodeRpgSheet(character.extensionsJson)
                    db.roleplayDao().upsertCharacter(
                        character.copy(extensionsJson = encodeRpgSheet(character.extensionsJson, sheet.copy(currentHp = member.hp.coerceIn(0, sheet.maxHp)))),
                    )
                }
            }
            val campaign = rpgCampaignState ?: return@launch
            val withParty = updateRpgPartyFromCombat(campaign, combat.combatants)
            persistRpgCampaign(applyCombatOutcome(withParty, outcome))
            val now = System.currentTimeMillis()
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-combat-$now",
                    chatId = campaign.campaignId,
                    swipeGroupId = "combat-${outcome.encounterId}",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "char",
                    contentJson = Document.fromPlainText(
                        "COMBAT OUTCOME · ${outcome.result.name.uppercase()}\n${outcome.recap}\nThe campaign returns to ${authoritativeRpgMode().label}.",
                    ).toJson(),
                    createdAt = now,
                    displayMode = "dungeonMaster",
                ),
            )
            _uiState.update {
                it.copy(
                    activeRpgCombat = null,
                    selectedCombatCardId = null,
                    selectedCombatTargetId = null,
                    combatActionPreview = null,
                    combatTextAction = "",
                    rpgCombatMode = authoritativeRpgMode(),
                    composerStatus = "Combat resolved: ${outcome.result.name}.",
                )
            }
        }
    }

    fun enterAdventureMapNode(nodeId: String) {
        viewModelScope.launch {
            val campaign = rpgCampaignState ?: return@launch
            val updated = enterRpgSceneNode(campaign, nodeId)
            if (updated == campaign) return@launch
            persistRpgCampaign(updated)
            updated.map.nodes.firstOrNull { it.id == nodeId }?.let { node ->
                publishRpgMapScene(node)
            }
        }
    }

    fun exploreRpgFreely() {
        viewModelScope.launch {
            rpgCampaignState?.let { campaign ->
                persistRpgCampaign(enterFreeformExploration(campaign, campaign.map.nodes.firstOrNull { it.id == campaign.map.currentNodeId }?.location ?: "the surrounding wilds"))
            }
        }
    }

    fun returnToRpgChapter() {
        viewModelScope.launch {
            rpgCampaignState?.let { campaign -> persistRpgCampaign(returnToChapterNode(campaign)) }
        }
    }

    private suspend fun publishRpgMapScene(node: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgSceneNode) {
        val chat = boundChat ?: return
        val text = buildString {
            appendLine(node.title)
            appendLine()
            appendLine(node.summary)
            appendLine()
            appendLine("Objective: ${node.objective}")
            appendLine("Location: ${node.location}")
            appendLine("[[RPG_CHOICE|id=1|title=Investigate the objective|description=Follow the current lead and look for a useful opening.]]")
            appendLine("[[RPG_CHOICE|id=2|title=Study the surroundings|description=Search for a safer route or hidden clue.]]")
            appendLine("[[RPG_CHOICE|id=3|title=Prepare the party|description=Check the party before committing to the next danger.]]")
            append("[[SCENE_ART:${node.sceneArtAssetId.ifBlank { "scene-auto" }}|category=scene|mood=adventure]]")
        }
        val document = documentWithBestSceneMedia(Document.fromPlainText(text), "${node.title} ${node.summary}", node.location)
        val now = System.currentTimeMillis()
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-node-${node.id}-$now",
                chatId = chat.id,
                swipeGroupId = "sw-node-${node.id}-$now",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = document.toJson(),
                createdAt = now,
                displayMode = "dungeonMaster",
            ),
        )
    }

    private suspend fun persistActiveCombat(combat: RpgCombatState) {
        val campaign = rpgCampaignState ?: return
        persistRpgCampaign(campaign.copy(activeCombatJson = rpgCombatJson.encodeToString(combat)))
        _uiState.update { it.copy(activeRpgCombat = combat) }
    }

    private suspend fun persistRpgCampaign(state: RpgCampaignState) {
        rpgCampaignState = state
        rpgCampaignRepository.saveRpgCampaign(state)
        val restoredCombat = state.activeCombatJson?.let { encoded ->
            runCatching { rpgCombatJson.decodeFromString<RpgCombatState>(encoded) }.getOrNull()
        }
        _uiState.update {
            it.copy(
                rpgStartup = state.startup,
                adventurePlanProgress = state.startup.generationProgress,
                rpgCampaign = state,
                activeRpgCombat = restoredCombat,
                selectedCombatCardId = it.selectedCombatCardId.takeIf { restoredCombat != null },
                selectedCombatTargetId = it.selectedCombatTargetId.takeIf { restoredCombat != null },
                combatActionPreview = it.combatActionPreview.takeIf { restoredCombat != null },
                combatTextAction = it.combatTextAction.takeIf { restoredCombat != null }.orEmpty(),
            )
        }
    }

    private suspend fun updateRpgStartup(transform: (RpgStartupState) -> RpgStartupState) {
        val campaign = rpgCampaignState ?: return
        persistRpgCampaign(campaign.copy(startup = transform(campaign.startup)))
    }

    private fun updateRpgStartupDraft(transform: (RpgStartupState) -> RpgStartupState) {
        val campaign = rpgCampaignState ?: return
        val updated = campaign.copy(startup = transform(campaign.startup))
        rpgCampaignState = updated
        _uiState.update { it.copy(rpgStartup = updated.startup) }
        rpgDraftSaveJob?.cancel()
        rpgDraftSaveJob = viewModelScope.launch {
            delay(200)
            rpgCampaignState?.let { latest -> rpgCampaignRepository.saveRpgCampaign(latest) }
        }
    }

    fun saveCyoaAnswer(questionId: String, value: String, presetId: String? = null) {
        updateRpgStartupDraft { startup ->
            val answer = RpgPlanAnswer(questionId, value, presetId, skipped = false)
            startup.copy(
                plan = startup.plan.copy(answers = startup.plan.answers.filterNot { it.questionId == questionId } + answer),
                generationStatus = RpgGenerationStatus.Idle,
                generationError = "",
            )
        }
    }

    fun selectCyoaPreset(questionId: String, value: String) = saveCyoaAnswer(questionId, value, value)

    fun skipCyoaQuestion(questionId: String) {
        viewModelScope.launch {
            updateRpgStartup { startup ->
                val answer = RpgPlanAnswer(questionId, value = "", skipped = true)
                startup.copy(plan = startup.plan.copy(answers = startup.plan.answers.filterNot { it.questionId == questionId } + answer))
            }
        }
    }

    fun randomizeUnansweredCyoa() {
        viewModelScope.launch {
            updateRpgStartup { startup ->
                val answers = adventurePlanQuestions().map { question ->
                    startup.plan.answers.firstOrNull { it.questionId == question.id }
                        ?.takeIf { it.value.isNotBlank() || it.skipped }
                        ?: RpgPlanAnswer(question.id, question.presets.random(), presetId = "random")
                }
                startup.copy(plan = RpgAdventurePlan(answers))
            }
        }
    }

    fun generateCyoaSuggestions() {
        val campaign = rpgCampaignState ?: return
        if (campaign.startup.cyoaSuggestionStatus == RpgGenerationStatus.Generating) return
        cyoaSuggestionJob?.cancel()
        cyoaSuggestionJob = viewModelScope.launch {
            updateRpgStartup { it.copy(
                cyoaSuggestionStatus = RpgGenerationStatus.Generating,
                cyoaSuggestionProgress = 1,
                cyoaSuggestionError = "",
            ) }
            val activeModel = PromptModelSelection.effectiveModelRef(_uiState.value.selectedModelRef, _uiState.value.defaultModelRef)
            if (!aiGeneration.hasApiKey(activeModel)) {
                updateRpgStartup { it.copy(
                    cyoaSuggestions = fallbackCyoaSuggestions(it.setup),
                    cyoaSuggestionStatus = RpgGenerationStatus.Failed,
                    cyoaSuggestionProgress = 100,
                    cyoaSuggestionError = "AI suggestions are unavailable, so campaign-aware local suggestions are shown.",
                ) }
                return@launch
            }
            val builder = StringBuilder()
            runCatching {
                aiGeneration.stream(
                    userMessage = cyoaSuggestionPrompt(campaign.startup.setup),
                    modelRef = activeModel,
                    maxTokens = 700,
                    temperature = 0.8,
                ).collect { chunk ->
                    if (chunk is AIChunk.Delta) {
                        builder.append(chunk.text)
                        updateRpgStartup { it.copy(cyoaSuggestionProgress = (10 + builder.length / 8).coerceAtMost(90)) }
                    }
                }
                parseCyoaSuggestions(builder.toString()) ?: error("AI suggestions were not valid.")
            }.onSuccess { suggestions ->
                updateRpgStartup { it.copy(
                    cyoaSuggestions = suggestions,
                    cyoaSuggestionStatus = RpgGenerationStatus.Complete,
                    cyoaSuggestionProgress = 100,
                    cyoaSuggestionError = "",
                ) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                updateRpgStartup { current -> current.copy(
                    cyoaSuggestions = fallbackCyoaSuggestions(current.setup),
                    cyoaSuggestionStatus = RpgGenerationStatus.Failed,
                    cyoaSuggestionProgress = 100,
                    cyoaSuggestionError = "AI suggestions could not be loaded, so campaign-aware local suggestions are shown.",
                ) }
            }
        }
    }

    fun useLocalCyoaSuggestions() {
        if (rpgCampaignState == null) return
        viewModelScope.launch {
            updateRpgStartup {
                it.copy(
                    cyoaSuggestions = fallbackCyoaSuggestions(it.setup),
                    cyoaSuggestionStatus = RpgGenerationStatus.Failed,
                    cyoaSuggestionProgress = 100,
                    cyoaSuggestionError = "Using campaign-aware local suggestions.",
                )
            }
        }
    }

    fun saveAdventurePlan() {
        val state = rpgCampaignState ?: return
        rpgDraftSaveJob?.cancel()
        viewModelScope.launch { persistRpgCampaign(state) }
    }

    fun generateChapterPlan() {
        val campaign = rpgCampaignState ?: return
        if (_uiState.value.isStreaming) return
        val requestId = campaign.startup.generationRequestId.takeIf {
            campaign.startup.step == RpgStartupStep.GeneratingChapterPlan && it.isNotBlank()
        } ?: UUID.randomUUID().toString()
        generateJob = viewModelScope.launch {
            updateRpgStartup {
                it.copy(
                    step = RpgStartupStep.GeneratingChapterPlan,
                    generationStatus = RpgGenerationStatus.Generating,
                    generationProgress = 1,
                    generationError = "",
                    generationRequestId = requestId,
                )
            }
            _uiState.update { it.copy(isStreaming = true, errorMessage = "") }
            val builder = StringBuilder()
            val activeModel = PromptModelSelection.effectiveModelRef(_uiState.value.selectedModelRef, _uiState.value.defaultModelRef)
            if (!aiGeneration.hasApiKey(activeModel)) {
                updateRpgStartup { it.copy(generationStatus = RpgGenerationStatus.Failed, generationProgress = 1, generationError = AIError.NoApiKey().message.orEmpty()) }
                _uiState.update { it.copy(isStreaming = false) }
                return@launch
            }
            runCatching {
                aiGeneration.stream(
                    userMessage = chapterPlanPrompt(campaign.startup.setup, campaign.startup.plan),
                    modelRef = activeModel,
                    maxTokens = 1800,
                    temperature = 0.7,
                ).collect { chunk ->
                    if (chunk is AIChunk.Delta) {
                        builder.append(chunk.text)
                        updateRpgStartup { current ->
                            if (current.generationRequestId != requestId) current else current.copy(
                                generationProgress = (10 + builder.length / 25).coerceAtMost(90),
                            )
                        }
                    }
                }
                val generated = parseChapterPlan(builder.toString())
                completeChapterPlan(generated, fallbackChapterPlan(campaign.startup.setup, campaign.startup.plan)) to (generated == null)
            }.onSuccess { (payload, usedFallback) ->
                updateRpgStartup { current ->
                    if (current.generationRequestId != requestId) current else current.copy(
                        step = RpgStartupStep.ChapterPlan,
                        chapterOutline = payload.outline,
                        openingScene = payload.openingScene,
                        generationStatus = RpgGenerationStatus.Complete,
                        generationProgress = 100,
                        generationError = if (usedFallback) {
                            "The AI returned an unusual format. Missing plan fields were completed locally so you can continue."
                        } else "",
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                updateRpgStartup { current -> current.copy(
                    generationStatus = RpgGenerationStatus.Failed,
                    generationError = formatError(error),
                ) }
            }
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    fun useAuthoredChapterPlan() {
        viewModelScope.launch {
            updateRpgStartup { startup ->
                val payload = fallbackChapterPlan(startup.setup, startup.plan)
                startup.copy(
                    step = RpgStartupStep.ChapterPlan,
                    chapterOutline = payload.outline,
                    openingScene = payload.openingScene,
                    generationStatus = RpgGenerationStatus.Complete,
                    generationProgress = 100,
                    generationError = "",
                )
            }
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    fun updateChapterOutline(outline: RpgChapterOutline) {
        updateRpgStartupDraft { it.copy(chapterOutline = outline) }
    }

    fun updateOpeningSceneGuideline(scene: RpgOpeningSceneGuideline) {
        updateRpgStartupDraft { it.copy(openingScene = scene) }
    }

    fun openAdventureVerification() {
        viewModelScope.launch { updateRpgStartup { it.copy(step = RpgStartupStep.Verification, generationStatus = RpgGenerationStatus.Idle, generationProgress = 0) } }
    }

    fun editCyoaPlan() {
        viewModelScope.launch { updateRpgStartup { it.copy(step = RpgStartupStep.Cyoa, generationStatus = RpgGenerationStatus.Idle, generationProgress = 0) } }
    }

    fun editChapterPlan() {
        viewModelScope.launch { updateRpgStartup { it.copy(step = RpgStartupStep.ChapterPlan, generationStatus = RpgGenerationStatus.Idle, generationProgress = 100) } }
    }

    fun reviseRemainingChapterOutline(revisedBeats: List<com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterBeat>) {
        viewModelScope.launch {
            updateRpgStartup { startup ->
                val completedById = startup.chapterOutline.beats.filter { it.completed }.associateBy { it.id }
                val upcoming = revisedBeats.filterNot { it.id in completedById }.map { it.copy(completed = false) }
                startup.copy(
                    chapterOutline = startup.chapterOutline.copy(
                        beats = completedById.values.toList() + upcoming,
                    ),
                )
            }
        }
    }

    fun verifyAndGenerateOpeningScene() {
        val campaign = rpgCampaignState ?: return
        if (_uiState.value.isStreaming) return
        val requestId = campaign.startup.generationRequestId.takeIf {
            campaign.startup.step == RpgStartupStep.GeneratingScene && it.isNotBlank()
        } ?: UUID.randomUUID().toString()
        generateJob = viewModelScope.launch {
            updateRpgStartup { it.copy(
                step = RpgStartupStep.GeneratingScene,
                generationStatus = RpgGenerationStatus.Generating,
                generationProgress = 1,
                generationError = "",
                generationRequestId = requestId,
            ) }
            _uiState.update { it.copy(isStreaming = true, errorMessage = "") }
            val builder = StringBuilder()
            val activeModel = PromptModelSelection.effectiveModelRef(_uiState.value.selectedModelRef, _uiState.value.defaultModelRef)
            if (!aiGeneration.hasApiKey(activeModel)) {
                updateRpgStartup { it.copy(generationStatus = RpgGenerationStatus.Failed, generationProgress = 1, generationError = AIError.NoApiKey().message.orEmpty()) }
                _uiState.update { it.copy(isStreaming = false) }
                return@launch
            }
            runCatching {
                aiGeneration.stream(
                    userMessage = openingScenePrompt(campaign.startup.setup, campaign.startup.plan, campaign.startup.chapterOutline, campaign.startup.openingScene),
                    modelRef = activeModel,
                    maxTokens = 1800,
                    temperature = 0.85,
                ).collect { chunk ->
                    if (chunk is AIChunk.Delta) {
                        builder.append(chunk.text)
                        updateRpgStartup { current -> if (current.generationRequestId != requestId) current else current.copy(
                            generationProgress = (10 + builder.length / 25).coerceAtMost(90),
                        ) }
                    }
                }
                completeSceneDraft(
                    parseSceneDraft(builder.toString()),
                    fallbackSceneDraft(campaign.startup.plan, campaign.startup.openingScene),
                )
            }.onSuccess { draft -> finishOpeningScene(requestId, draft) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    updateRpgStartup { it.copy(
                        generationStatus = RpgGenerationStatus.Failed,
                        generationError = formatError(error),
                    ) }
                }
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    fun useAuthoredOpeningScene() {
        val startup = rpgCampaignState?.startup ?: return
        val requestId = startup.generationRequestId.ifBlank { UUID.randomUUID().toString() }
        viewModelScope.launch { finishOpeningScene(requestId, fallbackSceneDraft(startup.plan, startup.openingScene)) }
    }

    private suspend fun finishOpeningScene(requestId: String, draft: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgSceneDraft) {
        val chat = boundChat ?: return
        rpgCampaignState?.let { campaign ->
            val selectedMode = RpgCombatRuleset.fromId(campaign.startup.setup.modeId)
            if (campaign.modeId != selectedMode.id) {
                persistRpgCampaign(campaign.copy(modeId = selectedMode.id))
            }
            _uiState.update { it.copy(rpgCombatMode = selectedMode) }
        }
        val choiceMarkers = draft.choices.take(3).mapIndexed { index, choice ->
            "[[RPG_CHOICE|id=${index + 1}|title=${choice.replace("|", "/").replace("]", ")")}|description=]]"
        }.joinToString("\n")
        val text = buildString {
            appendLine(draft.prose.trim())
            appendLine(choiceMarkers)
            append("[[SCENE_ART:scene-auto|category=scene|mood=${draft.sceneArtTags.replace("|", ",")}]]")
        }
        val topicMedia = currentTopicMediaSnapshot()
        val mediaRequests = topicMediaRequestsFor(
            parseTopicMediaReply("${draft.prose}\n${draft.sceneArtTags}"),
            topicMedia.topics,
        )
        val topicDocument = documentWithTopicMedia(text, topicMedia, mediaRequests)
        val sceneDocument = documentWithBestSceneMedia(topicDocument, draft.prose, draft.sceneArtTags)
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-opening-$requestId",
                chatId = chat.id,
                swipeGroupId = "sw-opening-$requestId",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = sceneDocument.toJson(),
                createdAt = System.currentTimeMillis(),
                displayMode = "dungeonMaster",
            ),
        )
        updateRpgStartup { it.copy(
            step = RpgStartupStep.Started,
            sceneDraft = draft,
            generationStatus = RpgGenerationStatus.Complete,
            generationProgress = 100,
            generationError = "",
            generationRequestId = requestId,
        ) }
    }

    fun retryStartupGeneration() {
        when (rpgCampaignState?.startup?.step) {
            RpgStartupStep.GeneratingChapterPlan -> generateChapterPlan()
            RpgStartupStep.GeneratingScene -> verifyAndGenerateOpeningScene()
            else -> Unit
        }
    }

    /** Stops any AI work started by the RPG setup wizard without discarding the player's answers. */
    fun cancelRpgSetupGeneration() {
        val startup = rpgCampaignState?.startup ?: return
        val cancelSuggestions = startup.cyoaSuggestionStatus == RpgGenerationStatus.Generating
        val cancelMainGeneration = startup.generationStatus == RpgGenerationStatus.Generating &&
            startup.step in setOf(RpgStartupStep.GeneratingChapterPlan, RpgStartupStep.GeneratingScene)
        if (!cancelSuggestions && !cancelMainGeneration) return

        cyoaSuggestionJob?.cancel()
        cyoaSuggestionJob = null
        generateJob?.cancel()
        generateJob = null
        viewModelScope.launch {
            rpgCampaignState?.let { campaign -> persistRpgCampaign(stopRpgSetupGeneration(campaign)) }
            _uiState.update { it.copy(isStreaming = false, errorMessage = "") }
        }
    }

    // ------------------------------------------------- campaign options sheet

    /** Opens the campaign options dialog, pre-filled from the stored setup note. */
    fun beginCampaignSetup() {
        val chat = boundChat ?: return
        val note = chat.authorsNote
        fun line(label: String): String =
            Regex("(?im)^" + Regex.escape(label) + ":\\s*(.*)$").find(note)
                ?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val settingFull = line("Setting")
        val settingTemplate = effectiveSettingTemplates().firstOrNull {
            settingFull.equals(it.label, ignoreCase = true) ||
                settingFull.startsWith(it.label + " —", ignoreCase = true)
        }
        val settingDetails = settingTemplate?.let { template ->
            settingFull.removePrefix(template.label).trim().removePrefix("—").trim()
        } ?: settingFull
        val idsLine = line("Main character IDs")
        val idList = if (idsLine.equals("none", ignoreCase = true)) {
            emptyList()
        } else {
            idsLine.split(',').map { it.trim() }.filter { it.contains(':') }
        }
        val options = _uiState.value.campaignCharacterOptions
        val selectedIds = options.filter { option -> idList.any { it.equals(option.id, ignoreCase = true) } }
            .map { it.id }.toSet()
        val povLabel = line("Narrative point of view")
        val povId = CampaignPerspectiveTemplates.firstOrNull { it.label.equals(povLabel, ignoreCase = true) }
            ?.id ?: "third-multiple"
        val rulesLabel = line("Rules system")
        val rulesId = CampaignRulesetTemplates.firstOrNull { it.label.equals(rulesLabel, ignoreCase = true) }
            ?.id ?: "dnd-5e"
        val gameModeId = rpgCombatRulesetFromSetup(note).id
        val settingDetailId = line("Setting details preset")
            .takeIf { id -> effectiveSettingDetailTemplates().any { it.id.equals(id, ignoreCase = true) } }
            ?: "custom"
        val houseRuleId = line("House rules preset")
            .takeIf { id -> CampaignHouseRuleTemplates.any { it.id.equals(id, ignoreCase = true) } }
            ?: "custom"
        val campaignRoleId = if (line("Player role").contains("Dungeon Master", ignoreCase = true)) "dm" else "player"
        val houseRules = Regex("(?im)^House rules:\\s*([\\s\\S]*?)(?=\\n\\n|\\z)").find(note)
            ?.groupValues?.getOrNull(1)?.trim().orEmpty()
        _uiState.update {
            it.copy(
                showCampaignOptions = true,
                campaignSetupInitial = NewWorkDetails(
                    title = chat.title,
                    genre = settingDetails,
                    tense = line("Narrative tense").ifBlank { "Past tense" },
                    styleGuide = houseRules,
                    mainCharacters = options.filter { option -> option.id in selectedIds },
                    rulesetId = rulesId,
                    gameModeId = gameModeId,
                    settingId = settingTemplate?.id ?: "high-fantasy",
                    settingDetailId = settingDetailId,
                    houseRuleId = houseRuleId,
                    narrativePov = povLabel,
                    campaignRoleId = campaignRoleId,
                ),
            )
        }
    }

    fun dismissCampaignOptions() {
        _uiState.update { it.copy(showCampaignOptions = false, campaignSetupInitial = null) }
    }

    /** Setup dialog: save a user-defined setting template. */
    fun addSettingTemplate(name: String, section: String, theme: String, guidance: String) {
        viewModelScope.launch { settings.addSettingTemplate(name, guidance, section, theme) }
    }

    /** Setup dialog: delete a user-defined setting template. */
    fun removeSettingTemplate(id: String) {
        viewModelScope.launch { settings.removeSettingTemplate(id) }
    }

    fun addSettingDetailTemplate(name: String, section: String, theme: String, guidance: String) {
        viewModelScope.launch { settings.addSettingDetailTemplate(name, guidance, section, theme) }
    }

    fun removeSettingDetailTemplate(id: String) {
        viewModelScope.launch { settings.removeSettingDetailTemplate(id) }
    }

    fun toggleFavoriteSettingTemplate(id: String) {
        viewModelScope.launch { settings.toggleFavoriteSettingTemplate(id) }
    }

    fun toggleFavoriteSettingDetail(id: String) {
        viewModelScope.launch { settings.toggleFavoriteSettingDetail(id) }
    }

    /**
     * Setup-screen "Restart": wipes every message in this adventure and re-seeds
     * the guided setup opening, so the campaign starts over from phase one with
     * the same campaign rules.
     */
    fun restartAdventure() {
        val chat = boundChat ?: return
        viewModelScope.launch {
            db.roleplayDao().deleteMessagesForChat(chat.id)
            viewedSceneNumber = null
            generateJob?.cancel()
            val base = rpgCampaignState ?: createRpgCampaign(chat.id, rpgCombatRulesetFromSetup(chat.authorsNote).id)
            persistRpgCampaign(base.copy(startup = RpgStartupState(setup = setupSnapshot(chat))))
            _uiState.update {
                it.copy(
                    showCampaignOptions = false,
                    campaignSetupInitial = null,
                    isStreaming = false,
                    streamingText = "",
                    activeRoll = null,
                    input = "",
                )
            }
        }
    }

    /** Rewrites the campaign setup note, roster flags, persona, and the work's own fields. */
    fun applyCampaignSetup(details: NewWorkDetails) {
        val chat = boundChat ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val effective = details.mainCharacters.map { option ->
                if (!option.id.startsWith("persona:")) {
                    option
                } else {
                    ensurePlayerSheet(option.id.substringAfter(':'), now) ?: option
                }
            }
            effective.filter { it.id.startsWith("roster:") }.forEach { option ->
                db.roleplayDao().getCharacter(option.id.substringAfter(':'))?.let { character ->
                    if (!character.inParty) db.roleplayDao().upsertCharacter(character.copy(inParty = true))
                }
            }
            val personaId = details.mainCharacters.firstOrNull { it.id.startsWith("persona:") }
                ?.id?.substringAfter(':')
                ?: chat.personaId.ifBlank { "persona-default" }
            val mainCharacters = effective.joinToString(", ") { it.name }
                .ifBlank { "None selected — guided character creation required" }
            val userIsDungeonMaster = details.campaignRoleId == "dm"
            val ruleset = CampaignRulesetTemplates.firstOrNull { it.id == details.rulesetId }
                ?: CampaignRulesetTemplates.first()
            val guidance = listOf(
                "Setting guidance: " +
                    (effectiveSettingTemplates().firstOrNull { it.id == details.settingId }?.directive ?: ""),
                "Rules guidance: ${ruleset.directive}",
                "Perspective guidance: " +
                    (CampaignPerspectiveTemplates.firstOrNull { it.label == details.narrativePov }?.directive
                        ?: CampaignPerspectiveTemplates.first().directive),
                if (userIsDungeonMaster) {
                    "User role guidance: The user is the Dungeon Master and has authority over the world, scenes, NPCs, and rulings. The AI controls the selected player-character party and must respond with their decisions, actions, and dialogue without overriding the user's world narration."
                } else {
                    "User role guidance: The user controls the selected player character(s). The AI is the Dungeon Master and controls the world, NPCs, opposition, and consequences without choosing the player's actions."
                },
                details.styleGuide.trim().takeIf { it.isNotBlank() }?.let { "House rules: $it" }.orEmpty(),
            ).filter { it.isNotBlank() }.joinToString("\n\n")
            val setup = buildString {
                appendLine("Campaign: ${details.title}")
                appendLine("Setting: ${details.genre.ifBlank { "Open fantasy setting" }}")
                appendLine("Main character(s): $mainCharacters")
                appendLine(
                    "Main character IDs: " + effective.joinToString(", ") { it.id }.ifBlank { "none" },
                )
                appendLine("Narrative tense: ${details.tense.ifBlank { "Past tense" }}")
                appendLine("Narrative point of view: ${details.narrativePov.ifBlank { "Third-person multiple" }}")
                appendLine("Player role: ${if (userIsDungeonMaster) "Dungeon Master" else "Adventurer"}")
                appendLine("Rules system: ${ruleset.label}")
                appendLine("Game mode: ${RpgCombatRuleset.fromId(details.gameModeId.ifBlank { details.rulesetId }).id}")
                // Kept as a compatibility alias for older saves and prompt parsers.
                appendLine("Combat style: ${RpgCombatRuleset.fromId(details.gameModeId.ifBlank { details.rulesetId }).id}")
                appendLine("Setting details preset: ${details.settingDetailId.ifBlank { "custom" }}")
                appendLine("House rules preset: ${details.houseRuleId.ifBlank { "custom" }}")
                if (guidance.isNotBlank()) append(guidance)
            }.trim()
            val updated = chat.copy(
                title = details.title,
                personaId = personaId,
                authorsNote = setup,
                updatedAt = now,
            )
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            rpgCampaignState?.let { campaign ->
                persistRpgCampaign(campaign.copy(
                    modeId = RpgCombatRuleset.fromId(details.gameModeId.ifBlank { details.rulesetId }).id,
                    ruleSystemId = details.rulesetId,
                    startup = campaign.startup.copy(
                        setup = setupSnapshot(updated),
                        cyoaSuggestions = emptyMap(),
                        cyoaSuggestionStatus = RpgGenerationStatus.Idle,
                        cyoaSuggestionProgress = 0,
                        cyoaSuggestionError = "",
                    ),
                ))
            }
            chat.bookId?.let { bookId ->
                db.bookDao().getById(bookId)?.let { book ->
                    db.bookDao().upsert(
                        book.copy(
                            title = details.title,
                            genre = details.genre,
                            tense = details.tense,
                            styleGuide = details.styleGuide,
                            updatedAt = now,
                        ),
                    )
                }
            }
            _uiState.update {
                it.copy(
                    title = details.title,
                    userIsDungeonMaster = userIsDungeonMaster,
                    showCampaignOptions = false,
                    campaignSetupInitial = null,
                    composerStatus = "Campaign setup updated.",
                )
            }
            startComposerStatusTimer()
        }
    }

    /** Mirrors AppShellViewModel's persona→sheet migration when editing the party. */
    private suspend fun ensurePlayerSheet(personaId: String, now: Long): WorkCharacterOption? {
        val persona = db.roleplayDao().getPersona(personaId) ?: return null
        val sheetId = "rpc-player-$personaId"
        val sheet = db.roleplayDao().getCharacter(sheetId) ?: RpCharacterEntity(
            id = sheetId,
            name = persona.name,
            avatarMediaId = persona.avatarMediaId,
            description = persona.description,
            tagsJson = "[\"Player\"]",
            extensionsJson = encodeRpgSheet(
                "{}",
                createRpgCharacterSheet(name = persona.name, description = persona.description),
            ),
            defaultCodexId = "persona:$personaId",
            inParty = true,
            createdAt = now,
        )
        db.roleplayDao().upsertCharacter(sheet.copy(inParty = true))
        return WorkCharacterOption("roster:$sheetId", persona.name, "Player roster")
    }

    // --------------------------------------------------- picture editor

    /** Opens the full-screen picture editor for a panel (erase text, translate). */
    fun openImageEditor(messageId: String, blockId: String) {
        val panel = _uiState.value.mediaPanels.find {
            it.messageId == messageId && it.blockId == blockId
        } ?: return
        if (panel.isAudio) return
        val persistedRegions = rawMessages.find { it.id == messageId }
            ?.let { documentFromJson(it.contentJson).blocks }
            ?.filterIsInstance<MediaBlock>()
            ?.find { it.id == blockId }
            ?.overlays
            ?.map(TextOverlay::toPanelTextRegion)
            .orEmpty()
        val reviewRegions = _uiState.value.mangaTranslationReviewRegions[blockId].orEmpty()
        _uiState.update {
            it.copy(
                imageEditor = PanelEditorUi(
                    messageId = messageId,
                    blockId = blockId,
                    mediaId = panel.mediaId,
                    path = panel.path,
                    originalPath = panel.originalPath,
                    regions = if (reviewRegions.isNotEmpty()) reviewRegions + persistedRegions.filter {
                        it.backingOverlay?.source != "manga-translation" && reviewRegions.none { draft -> draft.id == it.id }
                    } else persistedRegions,
                    selectedRegionId = (reviewRegions.ifEmpty { persistedRegions }).firstOrNull()?.id,
                    status = if (reviewRegions.isNotEmpty()) {
                        "Automatic translation was not applied: " +
                            it.mangaTranslationReviewReasons[blockId].orEmpty().ifBlank { "The page could not be verified." } +
                            " Inspect the draft text and source lettering. Double-tap a text box to correct its translation; " +
                            "use the cleanup tools for remaining source letters, then Save when satisfied."
                    } else if (persistedRegions.isNotEmpty()) {
                        "Drag text to move it; use Whiteout for missed source text."
                    } else "",
                ),
            )
        }
    }

    fun closeImageEditor() {
        _uiState.update { it.copy(imageEditor = null) }
    }

    fun openActiveMangaReview() {
        if (_uiState.value.mangaEditBusy) return
        val state = _uiState.value
        val panel = state.mediaPanels.firstOrNull { it.blockId in state.mangaTranslationReviewRegions }
        if (panel == null) {
            _uiState.update { it.copy(storyboardStatus = "No review draft is available on this page. Run Translate again to create a new draft.") }
            return
        }
        openImageEditor(panel.messageId, panel.blockId)
    }

    fun editorSetStatus(status: String) {
        _uiState.update { ed -> ed.imageEditor?.let { ed.copy(imageEditor = it.copy(status = status)) } ?: ed }
    }

    fun editorSetBusy(busy: Boolean) {
        _uiState.update { ed -> ed.imageEditor?.let { ed.copy(imageEditor = it.copy(busy = busy)) } ?: ed }
    }

    fun editorSetLanguage(language: String) {
        _uiState.update { ed -> ed.imageEditor?.let { ed.copy(imageEditor = it.copy(targetLanguage = language)) } ?: ed }
    }

    fun selectEditorVisionModel(modelId: String) {
        val modelRef = PromptModelSelection.modelRef(modelId)
        _uiState.update {
            it.copy(editorVisionModelRef = modelRef, editorModelsStatus = "Vision/OCR model selected.")
        }
        viewModelScope.launch { settings.setMangaVisionModel(modelRef) }
    }

    fun selectEditorTextModel(modelId: String) {
        val modelRef = PromptModelSelection.modelRef(modelId)
        _uiState.update {
            it.copy(editorTextModelRef = modelRef, editorModelsStatus = "Translation model selected.")
        }
        viewModelScope.launch { settings.setMangaTextModel(modelRef) }
    }

    fun selectEditorImageModel(modelId: String) {
        val modelRef = PromptModelSelection.modelRef(modelId)
        _uiState.update {
            it.copy(editorImageModelRef = modelRef, editorModelsStatus = "Image editing model selected.")
        }
        viewModelScope.launch { settings.setMangaImageModel(modelRef) }
    }

    fun saveMangaColorStyle(guide: String, preserve: Boolean) {
        _uiState.update { it.copy(mangaColorStyleGuide = guide.take(2000), mangaPreserveLineArt = preserve) }
        viewModelScope.launch { settings.setMangaColorStyle(guide, preserve) }
    }

    private suspend fun colorizeMangaImage(path: String, modelRef: String): Pair<ByteArray, String> {
        val prefs = settings.preferences.first()
        if (prefs.mangaPreserveLineArt) com.ihy2ln.weaverse.core.media.MangaColorTransfer.validateSource(path)
        val attachment = PanelAi.imageAttachmentFor(path, maxDim = 1536)
            ?: error("Could not read the source image.")
        val generated = aiGeneration.generateImage(
            prompt = com.ihy2ln.weaverse.core.media.MangaColorPolicy.prompt(prefs.mangaColorStyleGuide),
            modelRef = modelRef,
            imageAttachments = listOf(attachment),
        )
        return if (prefs.mangaPreserveLineArt) {
            com.ihy2ln.weaverse.core.media.MangaColorTransfer.preserveDrawing(path, generated.first) to "image/png"
        } else generated
    }

    fun refreshEditorModels() {
        if (_uiState.value.editorModelsRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(editorModelsRefreshing = true, editorModelsStatus = "Refreshing OpenRouter models…")
            }
            runCatching {
                val models = openRouterRepository.fetchModels(forceRefresh = true)
                openRouterRepository.fetchImageEditingModels()
                models
            }
                .onSuccess { models ->
                    val visionCount = com.ihy2ln.weaverse.ai.MangaEditorModels.vision(models).size
                    val textCount = com.ihy2ln.weaverse.ai.MangaEditorModels.text(models).size
                    val imageCount = openRouterRepository.imageEditingModels.value.size
                    _uiState.update {
                        it.copy(
                            editorModelsStatus =
                                "Compatible catalog: $visionCount OCR, $textCount text, $imageCount reference-image models. Not live-tested on your account.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(editorModelsStatus = error.message ?: "Could not refresh editor models.")
                    }
                }
            _uiState.update { it.copy(editorModelsRefreshing = false) }
        }
    }

    /**
     * Runs the same vision OCR/translation used by the panel editor over every
     * image panel on the active imported manga page. Originals stay in the
     * media library; English is stored as editable overlays on the page.
     */
    fun translateActiveMangaPageToEnglish() {
        val pageId = _uiState.value.activePageId
        if (pageId.isBlank()) {
            _uiState.update { it.copy(storyboardStatus = "Select an imported manga page first.") }
            return
        }
        startMangaTranslation(setOf(pageId), "page")
    }

    /** Translates every editable page produced from one downloaded chapter. */
    fun translateDownloadedChapter(chapterId: String) {
        val pageIds = _uiState.value.pages
            .filter { it.sourceChapterId == chapterId }
            .mapTo(linkedSetOf()) { it.id }
        if (pageIds.isEmpty()) {
            _uiState.update { it.copy(storyboardStatus = "This downloaded chapter is not loaded in the manga editor.") }
            return
        }
        startMangaTranslation(pageIds, "chapter")
    }

    private data class MangaEditTarget(
        val message: RpMessageEntity,
        val block: MediaBlock,
        val path: String,
    )

    private suspend fun mangaEditTargets(pageIds: Set<String>): List<MangaEditTarget> =
        rawMessages.flatMap { message ->
            documentFromJson(message.contentJson).blocks.mapNotNull { candidate ->
                val block = candidate as? MediaBlock ?: return@mapNotNull null
                if (block.pageId !in pageIds || block.kind != MediaKind.Image) return@mapNotNull null
                val entity = mediaRepository.getById(block.mediaId) ?: return@mapNotNull null
                MangaEditTarget(message, block, mediaRepository.resolveFile(entity).absolutePath)
            }
        }

    private fun MangaCleanupBounds.toRectF(): RectF = RectF(x, y, right, bottom)

    /**
     * Result of rendering one page. [cleaned] is the cleaned plate only: the English lives on
     * as editable overlays, so it is never burned into the stored picture.
     */
    private class MangaPageRender(
        val outcome: MangaPageOutcome,
        val regions: List<PanelTextRegion>,
        val cleaned: android.graphics.Bitmap?,
        val reason: String,
    )

    /**
     * Resolves where each English replacement sits and what colour it must be, measured on the
     * already-cleaned plate. The container walk finds the white balloon or the black caption
     * box that held the source glyphs, which keeps the replacement off the artwork and lets a
     * dark caption take white lettering instead of unreadable black.
     */
    private fun placedEnglishRegions(
        bitmap: android.graphics.Bitmap,
        regions: List<PanelTextRegion>,
    ): List<PanelTextRegion> {
        if (regions.isEmpty()) return regions
        val containers = ImageOps.textContainers(bitmap, regions.map { it.cleanupRect() })
        return regions.mapIndexed { index, region ->
            val frame = containers.getOrNull(index) ?: return@mapIndexed region
            val box = frame.box
            // The container is the balloon's bounding rectangle, but a balloon is an ellipse:
            // text filling the rectangle spills out at the curved top and bottom. Inset to the
            // area a letterer would actually set inside the shape.
            val centerX = (box.left + box.right) / 2f
            val centerY = (box.top + box.bottom) / 2f
            val safeW = ((box.right - box.left) * TextSafeWidthFraction).coerceIn(0.01f, 1f)
            val safeH = ((box.bottom - box.top) * TextSafeHeightFraction).coerceIn(0.01f, 1f)
            region.copy(
                x = (centerX - safeW / 2f).coerceIn(0f, 1f - safeW),
                y = (centerY - safeH / 2f).coerceIn(0f, 1f - safeH),
                w = safeW,
                h = safeH,
                cleanupX = if (region.cleanupX >= 0f) region.cleanupX else region.x,
                cleanupY = if (region.cleanupY >= 0f) region.cleanupY else region.y,
                cleanupW = if (region.cleanupW > 0f) region.cleanupW else region.w,
                cleanupH = if (region.cleanupH > 0f) region.cleanupH else region.h,
                fontSizePx = MangaLetteringPlacement.preferredSize(region, bitmap.width.toFloat() / bitmap.height),
                fillHex = ImageOps.colorIntToHex(ImageOps.contrastingFillColor(frame.backgroundLuminance)),
                strokeHex = ImageOps.colorIntToHex(ImageOps.contrastingStrokeColor(frame.backgroundLuminance)),
            )
        }
    }

    /**
     * The page as the reader would see it — cleaned plate plus the English layers — built only
     * to be shown to the verifier. Nothing here is written to disk.
     */
    private fun compositeWithEnglish(
        cleaned: android.graphics.Bitmap,
        regions: List<PanelTextRegion>,
    ): android.graphics.Bitmap {
        val composite = cleaned.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        ImageOps.typesetLayers(
            composite,
            regions.filter { it.visible && it.translation.isNotBlank() }.map { it.toTypesetLayer() },
        )
        return composite
    }

    /**
     * Cleans, letters and verifies one page, retrying at most once with the boxes Vision still
     * found source lettering in. Every attempt reloads the original file, so a retry cleans
     * fresh pixels instead of compounding damage from the attempt before it.
     */
    private suspend fun renderTranslatedPage(
        path: String,
        visionModelRef: String,
        planned: List<PanelTextRegion>,
        onStage: (String) -> Unit,
    ): MangaPageRender {
        var cleanupBounds = MangaTranslationPlan.cleanupBounds(planned)
        var residual: List<PanelTextRegion> = emptyList()
        var reason = ""
        for (attempt in 0..1) {
            val cleaned = ImageOps.loadBitmap(path, maxDim = 3200)
                ?: return MangaPageRender(
                    MangaPageOutcome.Rejected,
                    planned,
                    null,
                    "the original picture could not be opened",
                )
            var retained = false
            try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            onStage(if (attempt == 0) "Cleaning" else "Retrying")
            ImageOps.replaceTextRegions(cleaned, cleanupBounds.map { it.toRectF() })
            onStage("Typesetting")
            val safePlacement = MangaLetteringPlacement.constrain(placedEnglishRegions(cleaned, planned), cleaned.width.toFloat() / cleaned.height)
            val placed = try {
                PanelAi.refineLettering(aiGeneration, visionModelRef, cleaned, safePlacement)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                safePlacement
            }
            val placementProblems = MangaLetteringValidator.problems(placed, cleaned.width, cleaned.height)
            if (placementProblems.isNotEmpty()) {
                retained = true
                return MangaPageRender(MangaPageOutcome.Translated,
                    placed, cleaned, placementProblems.joinToString("\n"))
            }
            onStage("Verifying")
            val composite = compositeWithEnglish(cleaned, placed)
            val verification = try {
                PanelAi.verifyNoForeignText(aiGeneration, visionModelRef, composite)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                retained = true
                return MangaPageRender(MangaPageOutcome.Translated, placed, cleaned,
                    "Automatic verification was unavailable. The generated version was kept.")
            } finally {
                composite.recycle()
            }
            if (verification.passed) {
                retained = true
                return MangaPageRender(
                    if (attempt == 0) MangaPageOutcome.Translated else MangaPageOutcome.Retried,
                    placed,
                    cleaned,
                    "",
                )
            }
            residual = verification.residualRegions
            reason = verification.error
                ?: "source lettering was still visible in ${residual.size} region(s)"
            // A verifier that could not run gives no boxes to widen, so a second pass would do
            // exactly what the first did. Keep the candidate as a selectable version with a warning.
            if (attempt == 1 || residual.isEmpty()) {
                retained = true
                return MangaPageRender(MangaPageOutcome.Translated, placed, cleaned, reason)
            }
            cleanupBounds = MangaTranslationPlan.mergeCleanupBounds(
                cleanupBounds,
                MangaTranslationPlan.cleanupBounds(residual),
            )
            } finally {
                if (!retained && !cleaned.isRecycled) cleaned.recycle()
            }
        }
        return MangaPageRender(
            MangaPageOutcome.Rejected,
            MangaTranslationPlan.reviewRegions(planned, residual),
            null,
            reason.ifBlank { "the page could not be verified" },
        )
    }

    fun colorizeAndTranslateActivePage() {
        val id = _uiState.value.activePageId
        if (id.isNotBlank()) startMangaTranslation(setOf(id), "page", colorizeFirst = true)
    }

    fun colorizeAndTranslateChapter(chapterId: String) {
        val ids = _uiState.value.pages.filter { it.sourceChapterId == chapterId }.mapTo(linkedSetOf()) { it.id }
        if (ids.isNotEmpty()) startMangaTranslation(ids, "chapter", colorizeFirst = true)
    }

    private fun startMangaTranslation(pageIds: Set<String>, scopeLabel: String, colorizeFirst: Boolean = false) {
        if (_uiState.value.mangaEditBusy || mangaEditJob?.isActive == true) return
        // Claim the job before suspending so repeated taps cannot start parallel bitmap jobs.
        _uiState.update { it.copy(mangaEditBusy = true, mangaEditAction = "Translating to English") }
        mangaEditJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var completed = 0
            var translatedPanels = 0
            var retriedPanels = 0
            var translatedRegions = 0
            var rejectedPages = 0
            var alreadyEnglish = 0
            try {
                val targets = mangaEditTargets(pageIds)
                if (targets.isEmpty()) {
                    _uiState.update { it.copy(storyboardStatus = "No imported manga pictures were found in this $scopeLabel.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        mangaEditBusy = true,
                        mangaEditAction = "Translating to English",
                        mangaEditCurrent = 0,
                        mangaEditTotal = targets.size,
                    )
                }
                val modelRef = visionModelRef()
                if (modelRef == null) {
                    _uiState.update {
                        it.copy(
                            storyboardStatus = if (aiGeneration.hasApiKey()) {
                                "English translation found no Vision-capable model. Open Settings → " +
                                    "AI Connections and refresh the model list, then try again."
                            } else {
                                "English translation needs an AI connection. Add your API key in " +
                                    "Settings → AI Connections."
                            },
                        )
                    }
                    return@launch
                }
                val modelLabel = if (modelRef == MANGA_TRANSLATION_LUNA_REF) {
                    "GPT-5.6 Luna"
                } else {
                    modelRef.removePrefix("openrouter/")
                }
                val textModelRef = editorTextModelRef()
                val textModelLabel = textModelRef.removePrefix("openrouter/")
                val workingBlocks = targets.map { it.message }.distinctBy { it.id }.associate { message ->
                    message.id to documentFromJson(message.contentJson).blocks.toMutableList()
                }
                val combinedImageModel = if (colorizeFirst) imageEditModelRef()
                    ?: error("Choose an image-editing model before colorizing and translating.") else null
                targets.forEachIndexed { index, sourceTarget ->
                    var target = sourceTarget
                    fun stage(step: String) {
                        _uiState.update {
                            it.copy(
                                storyboardStatus = "$step ${index + 1}/${targets.size} · " +
                                    "Vision: $modelLabel · Text: $textModelLabel…",
                                mangaEditCurrent = index,
                            )
                        }
                    }
                    if (combinedImageModel != null) {
                        stage("Colorizing before translation")
                        val (bytes, mime) = colorizeMangaImage(target.path, combinedImageModel)
                        val media = mediaRepository.importFromBytes(bytes,
                            "manga-color-translate-${UUID.randomUUID()}.${if (mime == "image/png") "png" else "jpg"}", mime)
                        val blocks = workingBlocks.getValue(target.message.id)
                        val blockIndex = blocks.indexOfFirst { it.id == target.block.id }
                        val colored = target.block.copy(mediaId = media.id,
                            originalMediaId = target.block.originalMediaId ?: target.block.mediaId,
                            variantKind = "colorized")
                        blocks[blockIndex] = colored
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            persistMessageBlocks(target.message, blocks)
                        }
                        target = target.copy(block = colored, path = mediaRepository.resolveFile(media).absolutePath)
                    }
                    stage("Reading")
                    val detected = PanelAi.readText(aiGeneration, modelRef, target.path, "English")
                        .orEmpty()
                        .filter { it.translation.isNotBlank() || it.original.isNotBlank() }
                    // Lettering already printed in English is left exactly as the artist drew it.
                    val planned = MangaTranslationPlan.translatable(detected)
                    if (planned.isEmpty()) {
                        // A page with no text at all is not a page that was already English.
                        if (MangaTranslationPlan.preservedEnglish(detected).isNotEmpty()) alreadyEnglish++
                        completed = index + 1
                        _uiState.update { it.copy(mangaEditCurrent = completed) }
                        return@forEachIndexed
                    }
                    stage("Translating")
                    val translatedText = PanelAi.translateTexts(
                        aiGeneration,
                        textModelRef,
                        planned.map { region -> region.original.ifBlank { region.translation } },
                        "English",
                    )
                    var finalRegions = if (translatedText != null && translatedText.size == planned.size) {
                        planned.zip(translatedText) { region, translation -> region.copy(translation = translation) }
                    } else planned
                    PanelAi.proofreadTexts(
                        aiGeneration,
                        textModelRef,
                        finalRegions.map { region -> region.translation.ifBlank { region.original } },
                        "English",
                    )?.takeIf { it.size == finalRegions.size }?.let { polished ->
                        finalRegions = finalRegions.zip(polished) { region, text -> region.copy(translation = text) }
                    }
                    // Script validation happens before any pixel is touched: a page that failed
                    // to translate is safer unchanged than cleaned and left blank.
                    val safeRegions = validatedEnglishRegions(finalRegions)
                    completed = index + 1
                    if (safeRegions == null) {
                        rejectedPages++
                        markPageNeedsReview(
                            target = target,
                            regions = finalRegions,
                            reason = "the translation was incomplete or was not English",
                        )
                        _uiState.update { it.copy(mangaEditCurrent = completed) }
                        return@forEachIndexed
                    }
                    val render = renderTranslatedPage(target.path, modelRef, safeRegions) { step -> stage(step) }
                    if (render.cleaned == null) {
                        rejectedPages++
                        markPageNeedsReview(target, render.regions, render.reason)
                        _uiState.update { it.copy(mangaEditCurrent = completed) }
                        return@forEachIndexed
                    }
                    val blocks = workingBlocks[target.message.id]
                    val blockIndex = blocks?.indexOfFirst { it.id == target.block.id } ?: -1
                    val base = blocks?.getOrNull(blockIndex) as? MediaBlock
                    if (base == null) {
                        render.cleaned.recycle()
                        _uiState.update { it.copy(mangaEditCurrent = completed) }
                        return@forEachIndexed
                    }
                    val entity = try {
                        mediaRepository.importFromBytes(
                            bytes = ImageOps.toPngBytes(render.cleaned),
                            fileName = "manga-english-${UUID.randomUUID()}.png",
                            mimeType = "image/png",
                        )
                    } finally {
                        render.cleaned.recycle()
                    }
                    val versionRun = UUID.randomUUID().toString()
                    val savedVersions = base.savedMangaVersions()
                    val runNumber = savedVersions.count { it.id.endsWith("-edited") } + 1
                    val translationOnly = com.ihy2ln.weaverse.core.text.MangaPageVersion(
                        "$versionRun-text", "Translation without cleanup · $runNumber",
                        base.originalMediaId ?: base.mediaId,
                        safeRegions.mapIndexed { i, region -> region.toEditableOverlay(i) },
                    )
                    val editedVersion = com.ihy2ln.weaverse.core.text.MangaPageVersion(
                        "$versionRun-edited", "Translation with edits · $runNumber", entity.id,
                        base.overlays.filterNot { it.source == "manga-translation" } +
                            render.regions.filter { it.visible && it.translation.isNotBlank() }
                                .mapIndexed { i, region -> region.toEditableOverlay(i) },
                        render.reason,
                    )
                    blocks[blockIndex] = base.copy(
                        mediaId = entity.id,
                        mangaVersions = savedVersions + translationOnly + editedVersion,
                        activeMangaVersionId = editedVersion.id,
                        originalMediaId = base.originalMediaId ?: base.mediaId,
                        variantKind = "translated",
                        // The stored picture is the cleaned plate. English stays a durable layer
                        // so it can be dragged, resized and re-worded without another render.
                        overlays = base.overlays.filterNot { it.source == "manga-translation" } +
                            render.regions
                                .filter { it.visible && it.translation.isNotBlank() }
                                .mapIndexed { layerIndex, region -> region.toEditableOverlay(layerIndex) },
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        persistMessageBlocks(target.message, blocks)
                    }
                    clearPageReview(
                        target,
                        targets.filter { it.block.pageId == target.block.pageId }
                            .mapTo(mutableSetOf()) { it.block.id },
                    )
                    translatedPanels++
                    if (render.outcome == MangaPageOutcome.Retried) retriedPanels++
                    translatedRegions += render.regions.count { it.translation.isNotBlank() }
                    _uiState.update { it.copy(mangaEditCurrent = completed) }
                }
                _uiState.update {
                    it.copy(
                        storyboardStatus = MangaTranslationPlan.summary(
                            scopeLabel = scopeLabel,
                            translatedPanels = translatedPanels,
                            retriedPanels = retriedPanels,
                            translatedRegions = translatedRegions,
                            rejectedPages = rejectedPages,
                            alreadyEnglish = alreadyEnglish,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update {
                    it.copy(storyboardStatus = "Translation stopped after $completed item(s). Saved edits were kept.")
                }
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.e("MangaTranslation", "Translation failed after $completed item(s)", failure)
                _uiState.update {
                    it.copy(storyboardStatus = "Translation stopped: ${failure.message?.take(300) ?: "Unknown error"}. Saved pages were kept.")
                }
            } catch (failure: OutOfMemoryError) {
                android.util.Log.e("MangaTranslation", "Insufficient memory while translating", failure)
                _uiState.update {
                    it.copy(storyboardStatus = "Not enough memory to translate this page. Saved pages were kept. Try a smaller page.")
                }
            } finally {
                _uiState.update {
                    it.copy(mangaEditBusy = false, mangaEditAction = "", mangaEditCurrent = 0, mangaEditTotal = 0)
                }
            }
        }
    }

    /**
     * A page that could not be verified keeps its original media and mediaId untouched. The
     * proposed English is kept as a review draft, so reopening the editor shows the work so far
     * with the unresolved source regions highlighted.
     */
    private fun markPageNeedsReview(
        target: MangaEditTarget,
        regions: List<PanelTextRegion>,
        reason: String,
    ) {
        val draft = MangaReviewDraft(
            pageId = target.block.pageId.orEmpty(),
            blockId = target.block.id,
            regions = regions.map { it.copy(reviewRequired = it.reviewRequired || it.translation.isBlank()) },
            reason = reason,
        )
        _uiState.update { state ->
            state.copy(
                mangaTranslationReviewPageIds = state.mangaTranslationReviewPageIds + draft.pageId,
                mangaTranslationReviewRegions =
                    state.mangaTranslationReviewRegions + (draft.blockId to draft.regions),
                mangaTranslationReviewReasons = state.mangaTranslationReviewReasons + (draft.blockId to draft.reason),
            )
        }
    }

    /**
     * A page can hold several panels. Clearing one panel's review only clears the page when no
     * sibling panel on it is still waiting to be looked at.
     */
    private fun clearPageReview(target: MangaEditTarget, pageBlockIds: Set<String>) {
        _uiState.update { state ->
            val remaining = state.mangaTranslationReviewRegions - target.block.id
            val pageStillNeedsReview = pageBlockIds.any { it != target.block.id && it in remaining }
            state.copy(
                mangaTranslationReviewRegions = remaining,
                mangaTranslationReviewReasons = state.mangaTranslationReviewReasons - target.block.id,
                mangaTranslationReviewPageIds = if (pageStillNeedsReview) {
                    state.mangaTranslationReviewPageIds
                } else {
                    state.mangaTranslationReviewPageIds - target.block.pageId.orEmpty()
                },
            )
        }
    }

    fun colorizeActiveMangaPage() {
        val pageId = _uiState.value.activePageId
        if (pageId.isBlank()) {
            _uiState.update { it.copy(storyboardStatus = "Select an imported manga page first.") }
            return
        }
        startMangaColorization(setOf(pageId), "page")
    }

    fun colorizeDownloadedChapter(chapterId: String) {
        val pageIds = _uiState.value.pages
            .filter { it.sourceChapterId == chapterId }
            .mapTo(linkedSetOf()) { it.id }
        if (pageIds.isEmpty()) {
            _uiState.update { it.copy(storyboardStatus = "This downloaded chapter is not loaded in the manga editor.") }
            return
        }
        startMangaColorization(pageIds, "chapter")
    }

    private fun startMangaColorization(pageIds: Set<String>, scopeLabel: String) {
        if (_uiState.value.mangaEditBusy || mangaEditJob?.isActive == true) return
        _uiState.update { it.copy(mangaEditBusy = true, mangaEditAction = "Colorizing black-and-white art") }
        mangaEditJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var completed = 0
            var colorized = 0
            var alreadyColor = 0
            try {
                val targets = mangaEditTargets(pageIds)
                if (targets.isEmpty()) {
                    _uiState.update { it.copy(storyboardStatus = "No imported manga pictures were found in this $scopeLabel.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        mangaEditBusy = true,
                        mangaEditAction = "Colorizing black-and-white art",
                        mangaEditCurrent = 0,
                        mangaEditTotal = targets.size,
                    )
                }
                val imageModelRef = imageEditModelRef()
                if (imageModelRef == null) {
                    _uiState.update {
                        it.copy(storyboardStatus = "High-quality colorization needs an Image generation model that also accepts image input. Choose one in Settings → Models.")
                    }
                    return@launch
                }
                val imageModelLabel = imageModelRef.removePrefix("openrouter/")
                val workingBlocks = targets.map { it.message }.distinctBy { it.id }.associate { message ->
                    message.id to documentFromJson(message.contentJson).blocks.toMutableList()
                }
                targets.forEachIndexed { index, target ->
                    _uiState.update {
                        it.copy(
                            storyboardStatus =
                                "Checking and colorizing ${index + 1}/${targets.size} · Image: $imageModelLabel…",
                            mangaEditCurrent = index,
                        )
                    }
                    val bitmap = ImageOps.loadBitmap(target.path, maxDim = 480)
                    if (bitmap != null) {
                        try {
                            if (ImageOps.isMostlyGrayscale(bitmap)) {
                                // A full-page edit needs more than the 1100px used for a
                                // read-the-lettering vision call, or fine line art and small
                                // text turn to mush once the model repaints the page.
                                val (bytes, mime) = colorizeMangaImage(target.path, imageModelRef)
                                val entity = mediaRepository.importFromBytes(
                                    bytes = bytes,
                                    fileName = "manga-ai-color-${UUID.randomUUID()}.${if (mime == "image/png") "png" else "jpg"}",
                                    mimeType = mime,
                                )
                                val blocks = workingBlocks[target.message.id] ?: return@forEachIndexed
                                val blockIndex = blocks.indexOfFirst { it.id == target.block.id }
                                val current = blocks.getOrNull(blockIndex) as? MediaBlock ?: return@forEachIndexed
                                blocks[blockIndex] = current.copy(
                                    mediaId = entity.id,
                                    originalMediaId = current.originalMediaId ?: current.mediaId,
                                    variantKind = "colorized",
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                    persistMessageBlocks(target.message, blocks)
                                }
                                colorized++
                            } else {
                                alreadyColor++
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    completed = index + 1
                    _uiState.update { it.copy(mangaEditCurrent = completed) }
                }
                _uiState.update {
                    it.copy(
                        storyboardStatus = "AI-colorized $colorized black-and-white picture(s); $alreadyColor already contained color. Original files remain unchanged.",
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update {
                    it.copy(storyboardStatus = "Colorization stopped after $completed item(s). Saved colorized copies were kept.")
                }
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.e("MangaColorization", "Colorization failed after $completed item(s)", failure)
                _uiState.update {
                    it.copy(storyboardStatus = "Colorization stopped: ${failure.message?.take(300) ?: "Unknown error"}. Saved pages were kept.")
                }
            } catch (failure: OutOfMemoryError) {
                android.util.Log.e("MangaColorization", "Insufficient memory while colorizing", failure)
                _uiState.update {
                    it.copy(storyboardStatus = "Not enough memory to colorize this page. Saved pages were kept. Try a smaller page.")
                }
            } finally {
                _uiState.update {
                    it.copy(mangaEditBusy = false, mangaEditAction = "", mangaEditCurrent = 0, mangaEditTotal = 0)
                }
            }
        }
    }

    fun stopMangaEditProcessing() {
        mangaEditJob?.cancel()
    }

    /**
     * Vision model choice for storyboard work.
     *
     * Prefer the configured GPT-5.6 Luna roll for the manga workflow, then the
     * user's selected/default model, and only then another cached Vision model.
     * This makes the model used for page translation deterministic instead of
     * depending on cache ordering.
     */
    private suspend fun visionModelRef(): String? {
        // A cold start has an empty model cache, so every "supports images" check below would
        // say no and the caller would blame the user's settings for a list we simply had not
        // fetched yet. Warm it once, here, so every caller gets the same behaviour.
        var cached = modelCache.toModelInfo(modelCache.models.first())
        if (cached.none { it.supportsImages && it.available }) {
            runCatching { openRouterRepository.fetchModels(forceRefresh = false) }
            cached = modelCache.toModelInfo(modelCache.models.first())
        }
        val infos = com.ihy2ln.weaverse.ai.MangaEditorModels.vision(cached)
        _uiState.value.editorVisionModelRef.takeIf { it.isNotBlank() }?.let { selected ->
            if (aiGeneration.hasApiKey(selected) && infos.any { PromptModelSelection.modelRef(it.id) == selected }) return selected
        }
        if (aiGeneration.hasApiKey(MANGA_TRANSLATION_LUNA_REF) &&
            infos.any { PromptModelSelection.modelRef(it.id) == MANGA_TRANSLATION_LUNA_REF }
        ) {
            return MANGA_TRANSLATION_LUNA_REF
        }
        val selected = PromptModelSelection.effectiveModelRef(
            _uiState.value.selectedModelRef,
            _uiState.value.defaultModelRef,
        )
        if (selected.isNotBlank() &&
            aiGeneration.hasApiKey(selected) &&
            infos.any { PromptModelSelection.modelRef(it.id) == selected }
        ) {
            return selected
        }
        infos.firstOrNull { it.supportsImages && it.available }?.let {
            return PromptModelSelection.modelRef(it.id)
        }
        return null
    }

    private fun editorTextModelRef(): String =
        _uiState.value.editorTextModelRef.ifBlank {
            PromptModelSelection.effectiveModelRef(
                _uiState.value.selectedModelRef,
                _uiState.value.defaultModelRef,
            )
        }

    /**
     * Only a true image-to-image model (accepts a picture, returns a picture) can deliver a
     * faithful colorized or retyped page — a text-to-image-only model would ignore the
     * source art and invent something unrelated. Candidates whose id/name names a known
     * strong image-editing family sort first; OpenRouter otherwise returns models in no
     * particular quality order.
     */
    private suspend fun imageEditModelRef(): String? {
        val knownGoodHints = listOf("gemini" to "image", "nano-banana" to null, "flux-kontext" to null, "gpt-image" to null)
        fun rank(model: ModelInfo): Int {
            val id = model.id.lowercase()
            val name = model.displayName.lowercase()
            return knownGoodHints.indexOfFirst { (primary, secondary) ->
                (id.contains(primary) || name.contains(primary)) &&
                    (secondary == null || id.contains(secondary) || name.contains(secondary))
            }.let { if (it < 0) knownGoodHints.size else it }
        }
        val candidates = openRouterRepository.fetchImageEditingModels()
        _uiState.value.editorImageModelRef.takeIf { selected ->
            selected.isNotBlank() && candidates.any { PromptModelSelection.modelRef(it.id) == selected }
        }?.let { return it }
        return candidates.minByOrNull(::rank)
            ?.let { PromptModelSelection.modelRef(it.id) }
    }

    /** 🎤 AI: read every text region on the open picture, with a translation. */
    fun editorFindText() {
        editorRunPipeline(
            setOf(
                MangaProcessStage.Detection,
                MangaProcessStage.Ocr,
                MangaProcessStage.Translation,
            ),
        )
    }

    fun editorUpdateRegions(regions: List<PanelTextRegion>) {
        _uiState.update { state ->
            state.imageEditor?.let { editor ->
                state.copy(imageEditor = editor.copy(regions = regions))
            } ?: state
        }
    }

    fun editorSelectRegion(id: String?) {
        _uiState.update { state ->
            state.imageEditor?.let { editor ->
                state.copy(imageEditor = editor.copy(selectedRegionId = id))
            } ?: state
        }
    }

    fun editorConsumeCleanup() {
        _uiState.update { state ->
            state.imageEditor?.let { editor ->
                state.copy(
                    imageEditor = editor.copy(
                        pendingCleanup = false,
                        status = if (editor.status.isBlank()) "Cleaned source lettering." else editor.status,
                    ),
                )
            } ?: state
        }
    }

    fun editorRunPipeline(stages: Set<MangaProcessStage>) {
        val editor = _uiState.value.imageEditor ?: return
        if (stages.isEmpty() || editor.busy) return
        val wantDetect = MangaProcessStage.Detection in stages
        val wantOcr = MangaProcessStage.Ocr in stages
        val wantTranslate = MangaProcessStage.Translation in stages
        val wantProofread = MangaProcessStage.Proofreading in stages
        val wantCleanup = MangaProcessStage.Cleanup in stages
        val existing = editor.regions
        val canTranslateExisting = existing.any { it.original.isNotBlank() }

        if (!wantDetect && !wantOcr && !wantTranslate && !wantProofread && wantCleanup) {
            _uiState.update { state ->
                state.imageEditor?.let {
                    state.copy(
                        imageEditor = it.copy(
                            pendingCleanup = true,
                            status = "Cleaning source lettering…",
                        ),
                    )
                } ?: state
            }
            return
        }

        viewModelScope.launch {
            editorSetBusy(true)
            val modelRef = visionModelRef()
            if (modelRef == null) {
                editorSetBusy(false)
                editorSetStatus("No Vision-capable model available — pick one in Settings → Writing.")
                return@launch
            }
            var nextRegions = existing
            var status = ""
            when {
                wantDetect && existing.isNotEmpty() -> {
                    status = "Detection skipped — delete text layers to detect this page again."
                    if (wantTranslate && canTranslateExisting) {
                        editorSetStatus("Translating existing layers…")
                        nextRegions = translateExistingLayers(editorTextModelRef(), existing, editor.targetLanguage)
                            ?: existing
                        status = "Updated translation. $status"
                    }
                    if (wantProofread && nextRegions.any { it.translation.isNotBlank() }) {
                        nextRegions = proofreadExistingLayers(editorTextModelRef(), nextRegions, editor.targetLanguage)
                            ?: nextRegions
                        status = "Proofread translated text. $status"
                    }
                }
                wantDetect && !wantOcr && !wantTranslate -> {
                    editorSetStatus("Detecting text regions…")
                    nextRegions = PanelAi.detectText(aiGeneration, modelRef, editor.path).orEmpty()
                    status = if (nextRegions.isEmpty()) {
                        "No text regions were detected."
                    } else {
                        "Detected ${nextRegions.size} text region(s)."
                    }
                }
                wantTranslate && !wantDetect && !wantOcr && canTranslateExisting -> {
                    editorSetStatus("Translating existing layers…")
                    nextRegions = translateExistingLayers(editorTextModelRef(), existing, editor.targetLanguage)
                        ?: existing
                    status = "Translated existing layers."
                }
                wantProofread && !wantDetect && !wantOcr && !wantTranslate &&
                    existing.any { it.translation.isNotBlank() } -> {
                    editorSetStatus("Proofreading translated text…")
                    nextRegions = proofreadExistingLayers(editorTextModelRef(), existing, editor.targetLanguage)
                        ?: existing
                    status = "Proofread translated layers."
                }
                wantDetect || wantOcr || wantTranslate || wantProofread -> {
                    editorSetStatus("Reading text with the AI…")
                    val found = PanelAi.readText(aiGeneration, modelRef, editor.path, editor.targetLanguage)
                    if (found == null) {
                        status = "The AI could not read this picture — try another Vision model."
                    } else {
                        nextRegions = if (wantTranslate) {
                            translateExistingLayers(editorTextModelRef(), found, editor.targetLanguage) ?: found
                        } else found.map { it.copy(translation = "") }
                        if (wantProofread && nextRegions.any { it.translation.isNotBlank() }) {
                            editorSetStatus("Proofreading translated English…")
                            nextRegions = proofreadExistingLayers(
                                editorTextModelRef(),
                                nextRegions,
                                editor.targetLanguage,
                            ) ?: nextRegions
                        }
                        status = "Found ${nextRegions.size} text region(s)."
                    }
                }
            }
            _uiState.update { state ->
                state.imageEditor?.let { current ->
                    state.copy(
                        imageEditor = current.copy(
                            regions = nextRegions,
                            busy = false,
                            status = status,
                            selectedRegionId = current.selectedRegionId ?: nextRegions.firstOrNull()?.id,
                            pendingCleanup = wantCleanup && nextRegions.isNotEmpty(),
                        ),
                    )
                } ?: state
            }
        }
    }

    private suspend fun translateExistingLayers(
        modelRef: String,
        regions: List<PanelTextRegion>,
        language: String,
    ): List<PanelTextRegion>? {
        val editable = regions.mapIndexed { index, region -> index to region }
            .filter { (_, region) -> !region.edited && region.original.isNotBlank() }
        if (editable.isEmpty()) return regions
        val translated = PanelAi.translateTexts(
            aiGeneration,
            modelRef,
            editable.map { it.second.original },
            language,
        ) ?: return null
        val byIndex = editable.map { it.first }.zip(translated).toMap()
        return regions.mapIndexed { index, region ->
            val next = byIndex[index] ?: return@mapIndexed region
            region.copy(translation = next)
        }
    }

    private suspend fun proofreadExistingLayers(
        modelRef: String,
        regions: List<PanelTextRegion>,
        language: String,
    ): List<PanelTextRegion>? {
        val candidates = regions.mapIndexed { index, region -> index to region }
            .filter { (_, region) -> !region.edited && region.translation.isNotBlank() }
        if (candidates.isEmpty()) return regions
        val polished = PanelAi.proofreadTexts(
            aiGeneration,
            modelRef,
            candidates.map { it.second.translation },
            language,
        ) ?: return null
        val byIndex = candidates.map { it.first }.zip(polished).toMap()
        return regions.mapIndexed { index, region ->
            byIndex[index]?.let { region.copy(translation = it) } ?: region
        }
    }

    /**
     * Applies translated regions by inpainting source lettering, then typesetting
     * the translation into the cleaned page.
     */
    fun applyTranslatedRegions(regions: List<PanelTextRegion>) {
        val editor = _uiState.value.imageEditor ?: return
        if (regions.isEmpty()) return
        val safeRegions = validatedEnglishRegions(regions)
        if (safeRegions == null) {
            editorSetStatus(
                "Not applied: every visible source region needs a complete English translation. " +
                    "Foreign-script or copied source text was rejected.",
            )
            return
        }
        viewModelScope.launch {
            editorSetBusy(true)
            editorSetStatus("Cleaning source lettering…")
            val current = rawMessages.find { it.id == editor.messageId }
            val blocks = current?.let { documentFromJson(it.contentJson).blocks.toMutableList() }
            val index = blocks?.indexOfFirst { it.id == editor.blockId } ?: -1
            val base = blocks?.getOrNull(index) as? MediaBlock
            if (current == null || base == null) {
                editorSetBusy(false)
                editorSetStatus("Could not find this picture in the chat anymore.")
                return@launch
            }
            val bitmap = ImageOps.loadBitmap(editor.path, maxDim = 3200)
            if (bitmap == null) {
                editorSetBusy(false)
                editorSetStatus("Could not open the picture to typeset the translation.")
                return@launch
            }
            var placed: List<PanelTextRegion> = emptyList()
            val entity = try {
                // Only the fixed source bounds are cleaned; where the English ends up is a
                // separate, movable decision that never feeds back into the cleanup.
                ImageOps.replaceTextRegions(
                    bitmap,
                    MangaTranslationPlan.cleanupBounds(safeRegions).map { it.toRectF() },
                )
                placed = placedEnglishRegions(bitmap, safeRegions)
                mediaRepository.importFromBytes(
                    bytes = ImageOps.toPngBytes(bitmap),
                    fileName = "manga-english-${UUID.randomUUID()}.png",
                    mimeType = "image/png",
                )
            } finally {
                bitmap.recycle()
            }
            val lettered = placed.filter { it.visible && it.translation.isNotBlank() }
            blocks[index] = base.copy(
                mediaId = entity.id,
                originalMediaId = base.originalMediaId ?: base.mediaId,
                variantKind = "translated",
                overlays = base.overlays.filterNot { it.source == "manga-translation" } +
                    lettered.mapIndexed { layerIndex, region -> region.toEditableOverlay(layerIndex) },
            )
            persistMessageBlocks(current, blocks)
            _uiState.update { ed ->
                ed.imageEditor?.let {
                    ed.copy(
                        imageEditor = it.copy(
                            path = mediaRepository.resolveFile(entity).absolutePath,
                            regions = placed,
                            selectedRegionId = placed.firstOrNull()?.id,
                        ),
                    )
                } ?: ed
            }
            editorSetBusy(false)
            editorSetStatus(
                "Cleaned and lettered ${lettered.size} region(s). " +
                    "Drag text to move it; use Whiteout for missed source text.",
            )
        }
    }

    /**
     * Validates the exact set that is about to be burned into a bitmap. A failed
     * translation is safer as an unchanged original than as an erased bubble or
     * a page containing source-script leakage.
     */
    private fun validatedEnglishRegions(regions: List<PanelTextRegion>): List<PanelTextRegion>? {
        val normalized = regions.map { region ->
            region.copy(translation = PanelAi.normalizeEnglishText(region.translation))
        }
        val badText = PanelAi.invalidEnglishRegionIndexes(normalized)
        val badGeometry = normalized.indices.filter { index ->
            val region = normalized[index]
            !region.x.isFinite() || !region.y.isFinite() || !region.w.isFinite() || !region.h.isFinite() ||
                region.x < 0f || region.y < 0f || region.w < 0.01f || region.h < 0.01f ||
                region.x + region.w > 1.001f || region.y + region.h > 1.001f
        }
        return if (badText.isEmpty() && badGeometry.isEmpty()) normalized else null
    }

    private fun baseFontSize(block: MediaBlock): Float = when (block.gridRowSpan) {
        1 -> 16f
        2 -> 24f
        else -> 34f
    }

    /**
     * Writes the edited page as a new media file and points the panel at that
     * copy. The source file on disk is never overwritten; Original/Edited still
     * shows the first scan.
     */
    fun saveEditedPanel(bitmap: android.graphics.Bitmap) {
        val editor = _uiState.value.imageEditor ?: return
        val sourcePath = editor.path
        // Brush and whiteout edits are pixels and belong in the saved picture; the English
        // is saved alongside them as layers, so wording and position stay editable.
        val pending = editor.regions.filter { it.visible && it.translation.isNotBlank() }
        val translated = pending.filter { it.backingOverlay?.source?.let { source -> source == "manga-translation" } ?: true }
        val validated = validatedEnglishRegions(translated)
        val safePending = if (validated == null) null else pending.map { region -> validated.find { it.id == region.id } ?: region }
        if (translated.isNotEmpty() && safePending == null) {
            _uiState.update {
                it.copy(
                    storyboardStatus =
                        "Save stopped: replace incomplete or non-English lettering before saving. " +
                            "The original page is unchanged.",
                )
            }
            return
        }
        viewModelScope.launch {
            val entity = mediaRepository.importFromBytes(
                bytes = ImageOps.toPngBytes(bitmap),
                fileName = "manga-edit-${java.util.UUID.randomUUID()}.png",
                mimeType = "image/png",
            )
            val savedPath = mediaRepository.resolveFile(entity).absolutePath
            if (savedPath == sourcePath) {
                _uiState.update {
                    it.copy(storyboardStatus = "Save stopped: the new version would have replaced the original file.")
                }
                return@launch
            }
            val current = rawMessages.find { it.id == editor.messageId }
            if (current != null) {
                val blocks = documentFromJson(current.contentJson).blocks.map { block ->
                    if (block.id == editor.blockId && block is MediaBlock) {
                        block.copy(
                            mediaId = entity.id,
                            originalMediaId = block.originalMediaId ?: block.mediaId,
                            variantKind = "edited",
                            overlays = safePending.orEmpty()
                                    .mapIndexed { layerIndex, region -> region.toEditableOverlay(layerIndex) },
                        )
                    } else {
                        block
                    }
                }
                persistMessageBlocks(current, blocks)
            }
            if (current != null) {
                val savedBlock = documentFromJson(current.contentJson).blocks
                    .filterIsInstance<MediaBlock>().firstOrNull { it.id == editor.blockId }
                if (savedBlock != null) clearPageReview(
                    MangaEditTarget(current, savedBlock, sourcePath),
                    documentFromJson(current.contentJson).blocks.filterIsInstance<MediaBlock>()
                        .filter { it.pageId == savedBlock.pageId }.mapTo(mutableSetOf()) { it.id },
                )
            }
            _uiState.update {
                it.copy(
                    imageEditor = null,
                    storyboardStatus = "Saved a new version with ${safePending.orEmpty().size} text layer(s). " +
                        "The original page is unchanged — use Original / Edited to compare.",
                )
            }
        }
    }

    // --------------------------------------------- AI panel separation

    /**
     * Separates an imported comic page into individual panel pictures.
     * useAi = true asks a Vision model for the boxes and falls back to the
     * gutter heuristic; useAi = false is offline and free.
     */
    fun separatePanels(messageId: String, blockId: String, useAi: Boolean) {
        val panel = _uiState.value.mediaPanels.find {
            it.messageId == messageId && it.blockId == blockId
        } ?: return
        viewModelScope.launch {
            val detectorName = if (useAi) "AI panel detection" else "Offline white-gutter detection"
            _uiState.update { it.copy(storyboardStatus = "$detectorName is reading the source page…") }
            val originalMessage = rawMessages.find { it.id == messageId }
            val originalBlock = originalMessage
                ?.let { documentFromJson(it.contentJson).blocks }
                ?.find { block ->
                    (block is MediaBlock && block.id == blockId) ||
                        (block is MediaStackBlock && block.id == blockId)
                }
            if (originalBlock == null) {
                _uiState.update {
                    it.copy(storyboardStatus = "$detectorName failed: the original panel record is missing. No pages changed.")
                }
                return@launch
            }
            val source = ImageOps.loadBitmap(panel.path) ?: run {
                _uiState.update {
                    it.copy(storyboardStatus = "$detectorName failed: the source image could not be read. No pages changed.")
                }
                return@launch
            }

            var boxes = emptyList<NormalizedPanelBox>()
            var resultLabel = ""
            var aiFallbackNote = ""
            if (useAi) {
                val modelRef = visionModelRef()
                if (modelRef == null) {
                    aiFallbackNote = "AI detection was unavailable because no Vision-capable model is configured. "
                } else {
                    val aiResult = PanelAi.detectPanelsDetailed(aiGeneration, modelRef, panel.path)
                    when {
                        aiResult.error != null -> {
                            aiFallbackNote = "AI detection failed: ${aiResult.error} "
                        }
                        aiResult.boxes.size > 1 -> {
                            boxes = aiResult.boxes.map { rect ->
                                NormalizedPanelBox(rect.left, rect.top, rect.right, rect.bottom)
                            }
                            resultLabel = "AI detection found ${boxes.size} panels."
                        }
                        else -> {
                            aiFallbackNote = "AI detection found ${aiResult.boxes.size} panel(s). "
                        }
                    }
                }
            }

            if (boxes.size <= 1) {
                _uiState.update {
                    it.copy(
                        storyboardStatus = aiFallbackNote +
                            "Running offline white-gutter detection (near-white, page-spanning gutters only)…",
                    )
                }
                val offline = ImageOps.detectPanelsByGuttersDetailed(source)
                when (offline.kind) {
                    OfflinePanelDetectionKind.Multiple -> {
                        boxes = offline.boxes
                        resultLabel = offline.message
                    }
                    OfflinePanelDetectionKind.Single -> {
                        source.recycle()
                        _uiState.update {
                            it.copy(
                                storyboardStatus = aiFallbackNote + offline.message +
                                    " The original page remains unchanged. Try AI for dark or irregular gutters.",
                            )
                        }
                        return@launch
                    }
                    OfflinePanelDetectionKind.Failed -> {
                        source.recycle()
                        _uiState.update {
                            it.copy(
                                storyboardStatus = aiFallbackNote + offline.message +
                                    " The original page remains unchanged.",
                            )
                        }
                        return@launch
                    }
                }
            }

            val newPageId = "page-${java.util.UUID.randomUUID()}"
            val gridSize = activeGridSize()
            val cropBoxes = boxes.sortedWith(
                compareBy<NormalizedPanelBox> { it.top }.thenBy { it.left },
            ).map { box ->
                val inset = 0.006f
                NormalizedPanelBox(
                    (box.left + inset).coerceAtMost(1f),
                    (box.top + inset).coerceAtMost(1f),
                    (box.right - inset).coerceAtLeast(0f),
                    (box.bottom - inset).coerceAtLeast(0f),
                )
            }
            val croppedMediaIds = runCatching {
                cropBoxes.map { cropBox ->
                    val cropped = ImageOps.crop(source, cropBox.toRectF())
                    try {
                        mediaRepository.importFromBytes(
                            bytes = ImageOps.toJpegBytes(cropped),
                            fileName = "${java.util.UUID.randomUUID()}.jpg",
                            mimeType = "image/jpeg",
                        ).id
                    } finally {
                        if (cropped !== source) cropped.recycle()
                    }
                }
            }.getOrElse { error ->
                source.recycle()
                _uiState.update {
                    it.copy(
                        storyboardStatus = "$resultLabel Cropping failed: " +
                            (error.message ?: "unknown image error") +
                            " The original page remains unchanged.",
                    )
                }
                return@launch
            }
            source.recycle()
            val separation = buildPanelSeparationOutput(
                originalBlock = originalBlock,
                croppedMediaIds = croppedMediaIds,
                boxes = cropBoxes,
                newPageId = newPageId,
                gridSize = gridSize,
            )

            // Only the new blocks are persisted; the source message/page is untouched.
            val chat = boundChat ?: return@launch
            val pages = decodePages(chat.pagesJson).toMutableList()
            pages.add(RpPageMeta(id = newPageId, order = (pages.maxOfOrNull { it.order } ?: -1) + 1, title = "Panels ×${boxes.size}"))
            val updated = chat.copy(pagesJson = encodePages(pages), updatedAt = System.currentTimeMillis())
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            val now = System.currentTimeMillis()
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-$now",
                    chatId = chat.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = Document(blocks = separation.newBlocks).toJson(),
                    createdAt = now,
                    displayMode = "roleplay",
                ),
            )
            _uiState.update {
                it.copy(
                    pages = pages,
                    activePageId = newPageId,
                    storyboardStatus = "$resultLabel Created a new separated-panel page; the original page remains unchanged.",
                )
            }
        }
    }

    /**
     * Imports externally-generated images into durable media storage, then
     * places them in the selected empty layout slot or the next free slot.
     * [replaceTargetKey] is supplied only after the user explicitly confirms
     * replacement of an occupied panel.
     */
    fun importGeneratedPanels(
        uris: List<Uri>,
        selectedSlotIndex: Int?,
        replaceTargetKey: String? = null,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(storyboardStatus = "Importing generated panel artwork…") }
            val imported = runCatching { mediaRepository.importFromUris(uris) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            storyboardStatus = "Generated-panel import failed: " +
                                (error.message ?: "the selected image could not be copied"),
                        )
                    }
                    return@launch
                }
            if (imported.isEmpty()) {
                _uiState.update { it.copy(storyboardStatus = "No generated panel images were selected.") }
                return@launch
            }

            var nextMediaIndex = 0
            val replaced = replaceTargetKey?.let { key ->
                replaceSelectedPanelMedia(key, imported.first().id)
            } == true
            if (replaced) nextMediaIndex = 1

            val state = _uiState.value
            val activePageId = state.activePageId
            val gridSize = MediaGrid.SIZE
            val templateId = state.activeTemplateId
            val slots = PanelTemplates.byId(templateId)?.slots.orEmpty()
            val blocks = mutableListOf<Block>()
            val pages = decodePages(boundChat?.pagesJson.orEmpty()).toMutableList()
            var targetPageId = activePageId
            var targetPanels = state.mediaPanels.map { panel ->
                StoryboardGridItem(
                    col = panel.gridCol,
                    row = panel.gridRow,
                    colSpan = panel.gridColSpan,
                    rowSpan = panel.gridRowSpan,
                )
            }
            var preferredSlot = selectedSlotIndex
            var overflowPages = 0

            while (nextMediaIndex < imported.size) {
                val remaining = imported.size - nextMediaIndex
                val plan = planStoryboardAssetPlacements(
                    importCount = remaining,
                    panels = targetPanels,
                    slots = slots,
                    selectedSlotIndex = preferredSlot,
                    gridSize = gridSize,
                )
                plan.placements.forEach { placement ->
                    val media = imported[nextMediaIndex++]
                    blocks += MediaBlock(
                        id = "mb-${UUID.randomUUID()}",
                        mediaId = media.id,
                        kind = MediaKind.Image,
                        pageId = targetPageId,
                    ).withGridPlacement(
                        placement.col,
                        placement.row,
                        placement.colSpan,
                        placement.rowSpan,
                        gridSize,
                    ).let { placed ->
                        (placed as MediaBlock).copy(panelRotationDeg = placement.rotationDeg)
                    }
                    targetPanels = targetPanels + StoryboardGridItem(
                        placement.col,
                        placement.row,
                        placement.colSpan,
                        placement.rowSpan,
                    )
                }
                if (nextMediaIndex >= imported.size) break

                // The active page is genuinely full. Keep every durable import
                // visible by continuing on a new page with the same layout.
                overflowPages++
                targetPageId = "page-${UUID.randomUUID()}"
                pages += RpPageMeta(
                    id = targetPageId,
                    order = (pages.maxOfOrNull { it.order } ?: -1) + 1,
                    title = "Generated ${pages.size + 1}",
                    templateId = templateId,
                )
                targetPanels = emptyList()
                preferredSlot = null
            }

            if (overflowPages > 0) {
                val chat = boundChat
                if (chat != null) {
                    val updated = chat.copy(
                        pagesJson = encodePages(pages),
                        updatedAt = System.currentTimeMillis(),
                    )
                    db.roleplayDao().upsertChat(updated)
                    boundChat = updated
                }
            }
            if (blocks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                insertStoredMessage(
                    RpMessageEntity(
                        id = "rpm-${UUID.randomUUID()}",
                        chatId = state.chatId,
                        swipeGroupId = "sw-${UUID.randomUUID()}",
                        swipeIndex = 0,
                        isActiveSwipe = true,
                        role = "user",
                        contentJson = Document(blocks = blocks).toJson(),
                        createdAt = now,
                        displayMode = "roleplay",
                    ),
                )
            }
            _uiState.update {
                it.copy(
                    storyboardStatus = buildString {
                        append("Imported ${imported.size} generated panel image(s).")
                        if (replaced) append(" Replaced the selected panel explicitly.")
                        if (overflowPages > 0) append(" Continued onto $overflowPages new page(s) because the active page was full.")
                        append(" Tap a panel to adjust, drag, resize, or stack it.")
                    },
                    selectedMediaKey = null,
                )
            }
        }
    }

    private suspend fun replaceSelectedPanelMedia(targetKey: String, mediaId: String): Boolean {
        val parts = targetKey.split("::", limit = 2)
        if (parts.size != 2) return false
        val current = rawMessages.find { it.id == parts[0] } ?: return false
        val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
        val index = blocks.indexOfFirst { block ->
            (block is MediaBlock && block.id == parts[1]) ||
                (block is MediaStackBlock && block.id == parts[1])
        }
        if (index < 0) return false
        blocks[index] = when (val block = blocks[index]) {
            is MediaBlock -> block.copy(mediaId = mediaId, kind = MediaKind.Image)
            is MediaStackBlock -> {
                if (block.mediaIds.isEmpty()) return false
                val ids = block.mediaIds.toMutableList()
                ids[block.currentIndex.coerceIn(0, ids.lastIndex)] = mediaId
                block.copy(mediaIds = ids)
            }
            else -> return false
        }
        persistMessageBlocks(current, blocks)
        return true
    }

    /**
     * Add-pages button: imports individual pictures or whole PDF/CBZ/webtoon
     * files and appends every discovered page as one full-page panel.
     */
    fun importPages(uris: List<Uri>, rightToLeft: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val importedPages = mutableListOf<MangaPageProcessingResult>()
            var failedFiles = 0
            uris.forEach { uri ->
                runCatching {
                    mangaFileImporter.importPages(
                        uri = uri,
                        onProgress = { progress ->
                            _uiState.update { it.copy(storyboardStatus = progress) }
                        },
                    ) { media, label ->
                        val aiBoxes = runCatching {
                            val modelRef = visionModelRef()
                            if (modelRef == null || !aiGeneration.hasApiKey(modelRef)) {
                                null
                            } else {
                                _uiState.update {
                                    it.copy(storyboardStatus = "AI is separating panels in $label…")
                                }
                                PanelAi.detectPanelsDetailed(
                                    aiGeneration,
                                    modelRef,
                                    mediaRepository.resolveFile(media).absolutePath,
                                ).boxes.takeIf { boxes -> boxes.size > 1 }
                            }
                        }.getOrNull()
                        if (aiBoxes == null) {
                            _uiState.update {
                                it.copy(storyboardStatus = "Using offline panel fallback for $label…")
                            }
                        }
                        importedPages += MangaPageProcessor.splitPage(
                            source = media,
                            mediaRepository = mediaRepository,
                            rightToLeft = rightToLeft,
                            preferredBoxes = aiBoxes?.map { box ->
                                NormalizedPanelBox(box.left, box.top, box.right, box.bottom)
                            },
                        )
                    }
                }.onFailure {
                    failedFiles++
                }
            }
            if (importedPages.isEmpty()) {
                _uiState.update {
                    it.copy(storyboardStatus = "No pages were imported. Choose a PDF, CBZ/ZIP, or image file.")
                }
                return@launch
            }

            val chat = boundChat ?: return@launch
            val gridSize = activeGridSize()
            val pageMetas = decodePages(chat.pagesJson).toMutableList()
            val now = System.currentTimeMillis()
            val blocks = mutableListOf<Block>()
            var lastImportedPageId = _uiState.value.activePageId
            importedPages.forEachIndexed { sourceIndex, imported ->
                val templateId = if (imported.panels.size <= 6) "classic-6" else "vertical-strip"
                val template = PanelTemplates.byId(templateId) ?: PanelTemplates.byId("classic-6")!!
                var panelIndex = 0
                var continuation = 0
                while (panelIndex < imported.panels.size) {
                    val pageId = "page-${java.util.UUID.randomUUID()}"
                    lastImportedPageId = pageId
                    pageMetas.add(
                        RpPageMeta(
                            id = pageId,
                            order = (pageMetas.maxOfOrNull { it.order } ?: -1) + 1,
                            title = buildString {
                                append("Page ${pageMetas.size + 1}")
                                append(" · ${imported.panels.size} panel(s)")
                                if (continuation > 0) append(" · continued")
                            },
                            templateId = template.id,
                            readingOrder = if (rightToLeft) "rtl" else "ltr",
                            generationStatus = if (imported.usedFallback) "offline-fallback" else "offline-panels",
                        ),
                    )
                    val remaining = imported.panels.size - panelIndex
                    val placementPlan = planStoryboardAssetPlacements(
                        importCount = remaining,
                        panels = emptyList(),
                        slots = template.slots,
                        gridSize = gridSize,
                    )
                    if (placementPlan.placements.isEmpty()) break
                    placementPlan.placements.forEach { placement ->
                        val media = imported.panels[panelIndex++]
                        blocks.add(
                            MediaBlock(
                                id = "mb-${java.util.UUID.randomUUID()}",
                                mediaId = media.id,
                                kind = MediaKind.Image,
                                pageId = pageId,
                            ).withGridPlacement(
                                placement.col,
                                placement.row,
                                placement.colSpan,
                                placement.rowSpan,
                                gridSize,
                            ).let { placed ->
                                (placed as MediaBlock).copy(panelRotationDeg = placement.rotationDeg)
                            },
                        )
                    }
                    continuation++
                    if (placementPlan.remainingCount <= 0) break
                }
                _uiState.update {
                    it.copy(storyboardStatus = "Imported page ${sourceIndex + 1}/${importedPages.size} with ${imported.panels.size} editable panel(s).")
                }
            }
            val updated = chat.copy(pagesJson = encodePages(pageMetas), updatedAt = System.currentTimeMillis())
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-$now",
                    chatId = chat.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = Document(blocks = blocks).toJson(),
                    createdAt = now,
                    displayMode = "roleplay",
                ),
            )
            _uiState.update {
                it.copy(
                    pages = pageMetas,
                    activePageId = lastImportedPageId.ifBlank { pageMetas.last().id },
                    storyboardStatus = buildString {
                        val panelCount = importedPages.sumOf { it.panels.size }
                        append("Imported ${importedPages.size} manga page(s) as $panelCount editable panel(s). Original page media was preserved.")
                        if (failedFiles > 0) append(" $failedFiles file(s) could not be read.")
                    },
                )
            }
        }
    }

    /** Adds a completed library chapter as editable Storyboard pages and panels. */
    fun importDownloadedChapter(chapterId: String) {
        viewModelScope.launch {
            val chat = boundChat ?: return@launch
            val chapter = db.mangaDao().getChapter(chapterId)
            if (chapter == null) {
                _uiState.update { it.copy(storyboardStatus = "Downloaded chapter not found.") }
                return@launch
            }
            if (chapter.status != "completed") {
                _uiState.update { it.copy(storyboardStatus = "Download the chapter completely before adding it to Storyboard.") }
                return@launch
            }
            val originals = mangaDownloadRepository.importChapterPages(chapterId)
            if (originals.isEmpty()) {
                _uiState.update { it.copy(storyboardStatus = "The chapter has no readable local pages. Retry the download.") }
                return@launch
            }
            val rightToLeft = chapter.readingOrder.equals("rtl", ignoreCase = true)
            val processed = originals.map { original ->
                MangaPageProcessor.splitPage(
                    source = original,
                    mediaRepository = mediaRepository,
                    rightToLeft = rightToLeft,
                )
            }
            val pages = decodePages(chat.pagesJson).toMutableList()
            val blocks = mutableListOf<Block>()
            var lastPageId = _uiState.value.activePageId
            processed.forEachIndexed { sourceIndex, result ->
                val template = PanelTemplates.byId(if (result.panels.size <= 6) "classic-6" else "vertical-strip")
                    ?: PanelTemplates.byId("classic-6")!!
                var panelIndex = 0
                while (panelIndex < result.panels.size) {
                    val pageId = "page-${UUID.randomUUID()}"
                    lastPageId = pageId
                    pages += RpPageMeta(
                        id = pageId,
                        order = (pages.maxOfOrNull { it.order } ?: -1) + 1,
                        title = "${chapter.mangaTitle} · ${chapter.title} · Page ${sourceIndex + 1}",
                        templateId = template.id,
                        readingOrder = if (rightToLeft) "rtl" else "ltr",
                        generationStatus = "downloaded-offline-panels",
                        sourceChapterId = chapterId,
                    )
                    val placement = planStoryboardAssetPlacements(
                        importCount = result.panels.size - panelIndex,
                        panels = emptyList(),
                        slots = template.slots,
                        gridSize = activeGridSize(),
                    )
                    if (placement.placements.isEmpty()) break
                    placement.placements.forEach { slot ->
                        val media = result.panels[panelIndex++]
                        blocks += MediaBlock(
                            id = "mb-${UUID.randomUUID()}",
                            mediaId = media.id,
                            kind = MediaKind.Image,
                            pageId = pageId,
                        ).withGridPlacement(
                            slot.col,
                            slot.row,
                            slot.colSpan,
                            slot.rowSpan,
                            activeGridSize(),
                        )
                    }
                }
            }
            val now = System.currentTimeMillis()
            db.roleplayDao().upsertChat(chat.copy(pagesJson = encodePages(pages), updatedAt = now))
            boundChat = chat.copy(pagesJson = encodePages(pages), updatedAt = now)
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-$now",
                    chatId = chat.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = Document(blocks = blocks).toJson(),
                    createdAt = now,
                    displayMode = "roleplay",
                ),
            )
            _uiState.update {
                it.copy(
                    pages = pages,
                    activePageId = lastPageId.ifBlank { pages.lastOrNull()?.id.orEmpty() },
                    storyboardStatus = "Added ${originals.size} downloaded page(s) as ${processed.sumOf { it.panels.size }} editable panel(s). Original pages remain in the local library.",
                )
            }
            publishMessages()
        }
    }

    private fun sessionSystemBlocks(mode: String): List<String> = listOfNotNull(
        boundChat?.authorsNote?.takeIf { it.isNotBlank() }?.let {
            "Campaign setup and house rules:\n$it"
        },
        if (mode == "dungeonMaster") {
            adventureRoleDirective(boundChat?.authorsNote.orEmpty(), _uiState.value.sceneNumber)
        } else {
            null
        },
        if (mode == "dungeonMaster") rpgActionDirective() else null,
        if (mode == "dungeonMaster") {
            when (authoritativeRpgMode()) {
                RpgCombatRuleset.CardBattle -> "ACTIVE RPG MODE: Focused Tactical Cards. Treat this as the campaign's authoritative mode. Use Adams Haven card-combat language, AP/EP, card actions, visible enemy intent, and statuses. Do not switch to D&D d20 unless the player explicitly chooses a one-encounter override."
                RpgCombatRuleset.DndD20 -> "ACTIVE RPG MODE: D&D d20. Treat this as the campaign's authoritative mode and use character-sheet modifiers and deterministic d20 checks."
                RpgCombatRuleset.TextReactions -> "ACTIVE RPG MODE: Text Reactions. Treat this as the campaign's authoritative mode; resolve narrative and risky written actions without silently switching to d20 or card combat."
            }
        } else null,
        if (mode == "dungeonMaster") rpgCampaignState?.startup?.chapterOutline?.takeIf { it.premise.isNotBlank() }?.let { outline ->
            buildString {
                appendLine("Private Chapter One guidance. Never print this plan in narration.")
                appendLine("Title: ${outline.workingTitle}")
                appendLine("Premise: ${outline.premise}")
                appendLine("Objective: ${outline.primaryObjective}")
                appendLine("Threat: ${outline.antagonist}")
                appendLine("Locations: ${outline.importantLocations}")
                appendLine("Planned beats:")
                outline.beats.forEach { beat ->
                    appendLine("- [${if (beat.completed) "COMPLETED—IMMUTABLE" else "UPCOMING—FLEXIBLE"}] ${beat.title}: ${beat.summary}")
                }
                appendLine("Completed history and player choices are authoritative. Adapt upcoming beats when the story diverges; never rewrite completed beats.")
            }.trim()
        } else null,
        if (mode == "dungeonMaster") adventureWorldUpdateDirective() else null,
        if (mode == "dungeonMaster" && rpgSceneBeatDirective.value.isNotBlank()) {
            "Campaign scene engine (Prompt Collection → RPG → Adventure Scene Beat):\n${rpgSceneBeatDirective.value}"
        } else {
            null
        },
        "The user is an adult. Apply no app-level NSFW filter or automatic sanitization to lawful " +
            "adult fictional themes; follow the requested rating, tone, and level of detail. External " +
            "model-provider requirements still apply.",
    )

    private suspend fun currentTopicMediaSnapshot(): TopicMediaSnapshot {
        val prefs = settings.preferences.first()
        if (!prefs.topicMediaAutoAttach || prefs.topicMediaLibraryRoot.isBlank()) {
            return TopicMediaSnapshot()
        }
        return runCatching { topicMediaLibrary.snapshot(prefs.topicMediaLibraryRoot) }
            .getOrDefault(TopicMediaSnapshot())
    }

    private suspend fun documentWithTopicMedia(
        text: String,
        snapshot: TopicMediaSnapshot,
        requests: List<com.ihy2ln.weaverse.core.media.TopicMediaRequest>,
    ): Document {
        val base = Document.fromPlainText(text)
        val attachments = runCatching { topicMediaLibrary.importRequested(snapshot, requests) }
            .getOrDefault(emptyList())
        if (attachments.isEmpty()) return base
        return Document(
            blocks = base.blocks + attachments.map { attachment ->
                MediaBlock(
                    id = "media-${UUID.randomUUID()}",
                    mediaId = attachment.media.id,
                    kind = MediaRepository.kindForType(attachment.media.type),
                    caption = listOf(Span(attachment.topic)),
                    autoplay = false,
                    loop = false,
                    muted = true,
                )
            },
        )
    }

    private suspend fun documentWithBestSceneMedia(
        document: Document,
        sceneText: String,
        sceneTags: String,
    ): Document {
        if (document.blocks.any { block -> block is MediaBlock && block.kind == MediaKind.Image }) return document
        ensureBundledAdventureSceneMedia()
        val tags = sceneTags.split(',').map(String::trim).filter(String::isNotBlank)
        val ranked = runCatching {
            sceneMediaLibrary.find(
                SceneMediaRequest(
                    scene = "$sceneText $sceneTags ${rpgCampaignState?.startup?.setup?.setting.orEmpty()}",
                    kind = "image",
                    tags = tags,
                    limit = 12,
                ),
            )
        }.getOrDefault(emptyList())
        val rankedMedia = ranked.firstNotNullOfOrNull { choice ->
            mediaRepository.getById(choice.id)?.takeIf { media ->
                media.type == "image" && mediaRepository.resolveFile(media).let { it.isFile && it.length() > 0L }
            }
        }
        val candidate = rankedMedia ?: db.mediaDao().observeAll().first()
            .asSequence()
            .filter { it.type == "image" }
            .filter { mediaRepository.resolveFile(it).let { file -> file.isFile && file.length() > 0L } }
            .sortedWith(
                compareByDescending<MediaEntity> { media ->
                    val metadata = "${media.displayName} ${media.category} ${media.tags}".lowercase()
                    when {
                        "/ scene /" in media.category.lowercase() -> 3
                        "scene:" in media.tags.lowercase() -> 2
                        tags.any { it.lowercase() in metadata } -> 1
                        else -> 0
                    }
                }.thenBy { it.displayName.lowercase() }.thenBy { it.id },
            )
            .toList()
            .let { choices ->
                if (choices.isEmpty()) null
                else choices[((_uiState.value.sceneNumber - 1).coerceAtLeast(0)) % choices.size]
            }
            ?: return document
        return Document(
            blocks = document.blocks + MediaBlock(
                id = "scene-media-${UUID.randomUUID()}",
                mediaId = candidate.id,
                kind = MediaKind.Image,
                caption = listOf(Span(candidate.displayName)),
                pageId = _uiState.value.activePageId.takeIf(String::isNotBlank),
            ),
        )
    }

    /** Makes the APK's scene backdrops available to Adventure even if Text Games was never opened. */
    private suspend fun ensureBundledAdventureSceneMedia() {
        if (bundledAdventureSceneMediaReady) return
        adamsHavenSceneCatalog().forEach { asset ->
            runCatching {
                val existing = mediaRepository.getById(asset.mediaId)
                if (existing != null && mediaRepository.resolveFile(existing).let { it.isFile && it.length() > 0L }) {
                    return@runCatching
                }
                val extension = asset.artAssetPath.substringAfterLast('.', "png")
                mediaRepository.registerBundledImage(
                    assetPath = asset.artAssetPath,
                    id = asset.mediaId,
                    relativePath = "images/adams_haven/scenes/${asset.id}.$extension",
                    width = asset.width,
                    height = asset.height,
                    displayName = asset.displayName,
                    category = asset.category,
                    tags = asset.tags.joinToString(","),
                )
            }
        }
        bundledAdventureSceneMediaReady = true
    }

    private suspend fun insertStoredMessage(entity: RpMessageEntity) {
        db.roleplayDao().upsertMessage(entity)
        workspaceHistory.record(
            undo = { db.roleplayDao().deleteMessage(entity.id) },
            redo = { db.roleplayDao().upsertMessage(entity) },
        )
    }

    private suspend fun deleteStoredMessage(entity: RpMessageEntity) {
        db.roleplayDao().deleteMessage(entity.id)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(entity) },
            redo = { db.roleplayDao().deleteMessage(entity.id) },
        )
    }

    private suspend fun replaceStoredMessage(before: RpMessageEntity, after: RpMessageEntity) {
        if (before == after) return
        db.roleplayDao().upsertMessage(after)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(before) },
            redo = { db.roleplayDao().upsertMessage(after) },
        )
    }

    fun chooseMangaVersion(messageId: String, blockId: String, versionId: String) {
        if (_uiState.value.mangaEditBusy) return
        viewModelScope.launch {
            val message = rawMessages.firstOrNull { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(message.contentJson).blocks.map { block ->
                if (block is MediaBlock && block.id == blockId) block.selectMangaVersion(versionId) else block
            }
            persistMessageBlocks(message, blocks)
        }
    }

    private suspend fun persistMessageBlocks(
        current: RpMessageEntity,
        blocks: List<Block>,
    ) {
        replaceStoredMessage(current, current.copy(contentJson = Document(blocks = blocks).toJson()))
    }
}
