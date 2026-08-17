package com.ihy2ln.weaverse.feature.novel.write

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIRequestParams
import com.ihy2ln.weaverse.ai.AIService
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextScope
import com.ihy2ln.weaverse.ai.context.ContextTrigger
import com.ihy2ln.weaverse.ai.context.toContext
import com.ihy2ln.weaverse.core.media.MediaImporter
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.toPlainText
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ConnectionProfileRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.MediaRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.data.settings.SecretsStore
import com.ihy2ln.weaverse.feature.novel.write.editor.ContextChipInfo
import com.ihy2ln.weaverse.feature.novel.write.editor.SceneBeatGenerationState
import com.ihy2ln.weaverse.feature.novel.write.editor.SceneBeatOutputUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Write screen (spec §6/§9). [bookId] follows whichever book the
 * Books rail tab has selected (see `data/repo/CurrentBook.kt`). [scenes]
 * flattens every scene in that book (no act/chapter grouping yet, see
 * BUILD_NOTES "Phase 10 deviations/gaps") so the screen can offer a scene
 * picker; [currentScene] falls back to the book's first scene until
 * [selectScene] is called.
 */
@HiltViewModel
class WriteViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: AppSettingsRepository,
    val mediaRepository: MediaRepository,
    private val mediaImporter: MediaImporter,
    private val codexRepository: CodexRepository,
    private val connectionProfileRepository: ConnectionProfileRepository,
    private val aiService: AIService,
    private val secretsStore: SecretsStore,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scenes: StateFlow<List<SceneEntity>> = bookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeScenesForBook(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSceneId = MutableStateFlow<String?>(null)

    val currentScene: StateFlow<SceneEntity?> = combine(scenes, _selectedSceneId) { list, selectedId ->
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectScene(sceneId: String) {
        _selectedSceneId.value = sceneId
    }

    fun saveDocument(sceneId: String, document: Document) {
        val existing = scenes.value.firstOrNull { it.id == sceneId } ?: return
        viewModelScope.launch {
            libraryRepository.upsertScene(
                existing.copy(
                    docJson = document.toJson(),
                    plainText = document.toPlainText(),
                    wordCount = document.wordCount(),
                ),
            )
        }
    }

    suspend fun importMedia(uri: Uri): MediaEntity = mediaImporter.importFromUri(uri)

    // --- `/` AI overlay window (spec §6) ------------------------------------------------------

    val slashOverlayOpacity: StateFlow<Float> = settingsRepository.slashOverlayOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsRepository.SlashOverlayOpacityDefault)

    fun setSlashOverlayOpacity(opacity: Float) {
        viewModelScope.launch { settingsRepository.setSlashOverlayOpacity(opacity) }
    }

    val showSceneBeats: StateFlow<Boolean> = settingsRepository.showSceneBeats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShowSceneBeats(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowSceneBeats(show) }
    }

    /** Book-scoped codex entries for the `/Insert Codex Reference` picker (spec §6's Codex group). */
    val bookCodexEntries: StateFlow<List<CodexEntryEntity>> = bookId.filterNotNull()
        .flatMapLatest { codexRepository.observeEntriesForScope(ScopeType.Book, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ConnectionProfileEntity>> = connectionProfileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfileId = MutableStateFlow<String?>(null)

    /** Falls back to an unsaved, key-less profile when none exist yet — [AIService] reports a
     * real, explicit "no API key configured" [AIChunk.Error] for it rather than generating
     * anything, same fallback [ChatViewModel][com.ihy2ln.weaverse.feature.novel.chat.ChatViewModel] uses.
     * Prefers a profile that actually has a key over blindly picking whichever sorts first (see
     * the identical fix + full rationale on `RpChatsViewModel.currentProfile`). */
    val currentProfile: StateFlow<ConnectionProfileEntity> = combine(profiles, _selectedProfileId) { list, selectedId ->
        list.firstOrNull { it.id == selectedId }
            ?: list.firstOrNull { secretsStore.getApiKey(it.id)?.isNotBlank() == true }
            ?: list.firstOrNull()
            ?: fallbackProfile
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), fallbackProfile)

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId
    }

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId

    fun selectModelId(modelId: String) {
        _selectedModelId.value = modelId
    }

    /** Real model list for [currentProfile] (reusing the exact same call "Test connection" makes),
     * so the model row can offer an actual picker instead of a bare free-text field — a model id
     * a user types by hand (a human-readable name, not the exact API slug the provider expects,
     * e.g. "anthropic/Claude Sonnet 5" instead of a real Anthropic or OpenRouter model id) is
     * guaranteed to 400. Re-fetches whenever the active profile changes. */
    val availableModels: StateFlow<List<ModelInfo>> = currentProfile
        .flatMapLatest { profile -> flow { emit(aiService.testConnection(profile).getOrNull().orEmpty()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generationByBlock = MutableStateFlow<Map<String, SceneBeatGenerationState>>(emptyMap())
    val generationByBlock: StateFlow<Map<String, SceneBeatGenerationState>> = _generationByBlock

    private fun updateGeneration(blockId: String, transform: (SceneBeatGenerationState) -> SceneBeatGenerationState) {
        _generationByBlock.update { it + (blockId to transform(it[blockId] ?: SceneBeatGenerationState())) }
    }

    /** Clears a beat's ephemeral generation state (Clear Beat/Retry) without touching the
     * persisted [com.ihy2ln.weaverse.core.text.SceneBeatBlock.prompt] — the caller resets that separately. */
    fun clearGeneration(blockId: String) {
        _generationByBlock.update { it + (blockId to SceneBeatGenerationState()) }
    }

    /** Drops a beat's generation state entirely (block deleted or discarded from the document). */
    fun forgetGeneration(blockId: String) {
        _generationByBlock.update { it - blockId }
    }

    /**
     * Runs Scene Beat / Continue Writing (spec §6): both create a beat block in the flow and
     * share this one generation path — the only difference is [promptText] (user-authored for
     * Scene Beat, [CONTINUE_WRITING_DEFAULT_PROMPT] when left blank for Continue Writing).
     * [currentSceneText] is the *whole* open scene's plain text (from the live [Document] in the
     * editor, not the last-saved [SceneEntity.plainText]) so the model sees what's been typed
     * since the last autosave. Codex matching is Book-scoped only for this pass — Series-scoped
     * entries and previous-member summaries (already wired for Workshop Chat, see rev02-04b)
     * aren't folded in here yet, tracked alongside rev02-07b.
     */
    fun generateSceneBeat(
        blockId: String,
        currentSceneText: String,
        promptText: String,
        outputUnit: SceneBeatOutputUnit,
        outputCount: Int,
    ) {
        val currentBookId = bookId.value ?: return
        val profile = currentProfile.value
        viewModelScope.launch {
            // A model id the user hand-typed as a human-readable name (not the exact API slug a
            // provider expects) or an unresolved blank field used to silently fall back to the
            // literal string "default" - not a real model id for any provider, guaranteed to 400.
            // Prefer an explicit pick; otherwise the first model this profile's connection
            // actually reported (the model row auto-fills this the moment the list loads); fail
            // fast with a clear message rather than ever sending a fake id.
            val resolvedModel = selectedModelId.value.ifBlank { availableModels.value.firstOrNull()?.id.orEmpty() }
            if (resolvedModel.isBlank()) {
                updateGeneration(blockId) {
                    it.copy(errorMessage = "No model resolved for this connection yet — pick one from the model row, or wait for its model list to finish loading.")
                }
                return@launch
            }

            updateGeneration(blockId) { it.copy(isGenerating = true, streamingText = "", resultText = null, errorMessage = null) }

            val activeEntries = codexRepository.getActiveEntries(ScopeType.Book, currentBookId)
            val loreByEntryId = codexRepository.getLoreForEntries(activeEntries.map { it.id }).associateBy { it.entryId }
            val codexContexts = activeEntries.map { entry -> entry.toContext(loreByEntryId[entry.id]) }

            val effectivePrompt = promptText.ifBlank { CONTINUE_WRITING_DEFAULT_PROMPT }
            val lengthInstruction = "Write approximately $outputCount ${outputUnit.label}."

            val assembled = ContextBuilder.build(
                scope = ContextScope.Novel(currentSceneText = currentSceneText),
                trigger = ContextTrigger("$effectivePrompt\n\n$lengthInstruction"),
                codexEntries = codexContexts,
            )

            updateGeneration(blockId) {
                it.copy(
                    contextEntries = assembled.usedEntryIds.mapNotNull { id -> activeEntries.firstOrNull { entry -> entry.id == id } }
                        .map { entry -> ContextChipInfo(entry.id, entry.name, entry.aliases, entry.colorHex) },
                    contextTokenCount = assembled.tokenBreakdown.filter { it.included }.sumOf { it.tokenCount },
                )
            }

            // AIRequestParams' own default (1024) is close to the output-length selector's
            // default (750 words) but wouldn't scale with it — a user asking for 2000 words would
            // otherwise get silently truncated at ~750. Rough ~1.5 tokens/word, generous headroom.
            val estimatedMaxTokens = when (outputUnit) {
                SceneBeatOutputUnit.Words -> (outputCount * 2).coerceIn(256, 8000)
                SceneBeatOutputUnit.Sentences -> (outputCount * 40).coerceIn(256, 8000)
                SceneBeatOutputUnit.Paragraphs -> (outputCount * 150).coerceIn(256, 8000)
            }
            val request = AIRequest(
                model = resolvedModel,
                systemPrompt = assembled.systemBlocks.joinToString("\n\n").takeIf { it.isNotBlank() },
                messages = assembled.messages,
                params = AIRequestParams(maxTokens = estimatedMaxTokens),
            )

            val builder = StringBuilder()
            aiService.stream(profile, request).collect { chunk ->
                when (chunk) {
                    is AIChunk.Delta -> {
                        builder.append(chunk.text)
                        updateGeneration(blockId) { it.copy(streamingText = builder.toString()) }
                    }
                    is AIChunk.Done -> {
                        val finalText = chunk.fullText.ifBlank { builder.toString() }
                        updateGeneration(blockId) { it.copy(isGenerating = false, resultText = finalText, streamingText = "") }
                    }
                    is AIChunk.Error -> {
                        updateGeneration(blockId) { it.copy(isGenerating = false, errorMessage = chunk.message, streamingText = "") }
                    }
                }
            }
        }
    }

    private companion object {
        val fallbackProfile = ConnectionProfileEntity(
            providerType = AIProviderType.OpenAICompatible,
            label = "Mock (no connection profile configured)",
            baseUrl = "",
        )
        const val CONTINUE_WRITING_DEFAULT_PROMPT =
            "Continue the scene naturally from where it leaves off, matching the established tone, pacing, and POV."
    }
}
