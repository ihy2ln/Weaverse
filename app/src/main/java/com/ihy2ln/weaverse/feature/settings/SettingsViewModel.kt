package com.ihy2ln.weaverse.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.OtherProviderSeeds
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterKeyData
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.core.crash.CrashLogStore
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.media.TopicMediaLibrary
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.core.ui.theme.AppearanceProfile
import com.ihy2ln.weaverse.data.backup.AutoBackupScheduler
import com.ihy2ln.weaverse.data.backup.BackupManager
import com.ihy2ln.weaverse.data.settings.ExtraPromptSurface
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.data.settings.UserPreferences
import com.ihy2ln.weaverse.data.sync.SyncCoordinator
import com.ihy2ln.weaverse.data.sync.SyncUiSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModelListTab { Writing, Vision, ImageGeneration, TextToSpeech, All }

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val openRouterKey: String = "",
    val anthropicKey: String = "",
    val openAiKey: String = "",
    val geminiKey: String = "",
    val openRouterKeyInfo: OpenRouterKeyData? = null,
    val keyStatus: String = "",
    val keyStatusIsError: Boolean = false,
    val isValidatingKey: Boolean = false,
    val isRefreshingModels: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val writingModels: List<ModelInfo> = emptyList(),
    val visionModels: List<ModelInfo> = emptyList(),
    val ttsModels: List<ModelInfo> = emptyList(),
    val imageModels: List<ModelInfo> = emptyList(),
    val modelSearch: String = "",
    val modelTab: ModelListTab = ModelListTab.Writing,
    val modelsCachedAt: Long = 0L,
    val exportStatus: String = "",
    val backgroundLabel: String = "None",
    val backgroundNote: String = "",
    val sync: SyncUiSnapshot = SyncUiSnapshot(),
    val otherProviderModels: List<ModelInfo> = emptyList(),
    val crashLogText: String = "",
    val topicMediaStatus: String = "No media library selected",
    /** One line per PC harness from the last "Check PC" (✓/✗ + detail). */
    val pcStatus: String = "",
    val pcChecking: Boolean = false,
    val falKey: String = "",
    val falStatus: String = "",
    val falChecking: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
    private val openRouterRepository: OpenRouterRepository,
    private val modelCache: OpenRouterModelCache,
    private val backupManager: BackupManager,
    private val mediaRepository: MediaRepository,
    private val topicMediaLibrary: TopicMediaLibrary,
    private val syncCoordinator: SyncCoordinator,
    private val pcBridge: com.ihy2ln.weaverse.ai.pc.PcBridgeClient,
    private val fal: com.ihy2ln.weaverse.ai.fal.FalClient,
) : ViewModel() {
    private var lastScannedTopicMediaRoot: String? = null
    val preferences: StateFlow<UserPreferences> = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                openRouterKey = settings.apiKey(SecureKeyStore.OPENROUTER).orEmpty(),
                anthropicKey = settings.apiKey(SecureKeyStore.ANTHROPIC).orEmpty(),
                openAiKey = settings.apiKey(SecureKeyStore.OPENAI).orEmpty(),
                geminiKey = settings.apiKey(SecureKeyStore.GEMINI).orEmpty(),
                falKey = settings.apiKey(SecureKeyStore.FAL).orEmpty(),
                otherProviderModels = OtherProviderSeeds.seeded(
                    openai = !settings.apiKey(SecureKeyStore.OPENAI).isNullOrBlank(),
                    anthropic = !settings.apiKey(SecureKeyStore.ANTHROPIC).isNullOrBlank(),
                    gemini = !settings.apiKey(SecureKeyStore.GEMINI).isNullOrBlank(),
                ),
            )
        }
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val media = prefs.backgroundMediaId.takeIf { it.isNotBlank() }
                    ?.let { mediaRepository.getById(it) }
                val label = when {
                    media == null -> "None"
                    media.type == "video" -> "Video · loops muted behind the shell"
                    else -> "Image · ${media.mimeType}"
                }
                _uiState.update {
                    it.copy(
                        prefs = prefs,
                        backgroundLabel = label,
                        backgroundNote = "",
                    )
                }
                if (lastScannedTopicMediaRoot != prefs.topicMediaLibraryRoot) {
                    lastScannedTopicMediaRoot = prefs.topicMediaLibraryRoot
                    refreshTopicMedia(prefs.topicMediaLibraryRoot)
                }
            }
        }
        viewModelScope.launch {
            combine(modelCache.models, modelCache.cachedAt, pcBridge.capabilities, fal.imageModels) { models, cachedAt, caps, falImages ->
                Triple(models, cachedAt, caps to falImages)
            }.collect { (models, cachedAt, extra) ->
                val (caps, falCatalog) = extra
                // PC harness models (Claude Code, Codex, Ollama, ComfyUI) lead each list they belong to,
                // then fal.ai once its key is saved.
                val (pcText, pcImages) = caps?.let(com.ihy2ln.weaverse.ai.pc.PcHarness::modelsFrom)
                    ?: (com.ihy2ln.weaverse.ai.pc.PcHarness.defaultTextModels to com.ihy2ln.weaverse.ai.pc.PcHarness.defaultImageModels)
                val falOn = fal.hasKey()
                val falText = if (falOn) com.ihy2ln.weaverse.ai.fal.Fal.chatModels else emptyList()
                val falImages = if (falOn) falCatalog else emptyList()
                _uiState.update {
                    it.copy(
                        models = pcText + falText + pcImages + falImages + modelCache.toModelInfo(models),
                        writingModels = pcText + falText + modelCache.writingModels(models),
                        visionModels = pcText.filter { m -> m.supportsImages } + modelCache.visionModels(models),
                        ttsModels = modelCache.ttsModels(models),
                        imageModels = pcImages + falImages + modelCache.imageModels(models),
                        modelsCachedAt = cachedAt,
                    )
                }
            }
        }
        viewModelScope.launch {
            syncCoordinator.state.collect { snap ->
                _uiState.update { it.copy(sync = snap) }
            }
        }
        // If a key is already stored, refresh key info and models without optimistic success.
        if (!settings.apiKey(SecureKeyStore.OPENROUTER).isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { openRouterRepository.testStoredKey() }
                    .onSuccess { data -> _uiState.update { it.copy(openRouterKeyInfo = data) } }
                runCatching { openRouterRepository.fetchModels(forceRefresh = false) }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                keyStatus = err.message ?: "Could not load models",
                                keyStatusIsError = true,
                            )
                        }
                    }
            }
        }
    }

    fun startSyncHost() {
        viewModelScope.launch {
            runCatching { syncCoordinator.startHost() }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(sync = it.sync.copy(lastError = err.message ?: "Could not start host"))
                    }
                }
        }
    }

    fun stopSyncHost() {
        viewModelScope.launch { syncCoordinator.stopHost() }
    }

    fun setCodexMcpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setCodexMcpEnabled(enabled)
            if (enabled) {
                runCatching { syncCoordinator.startHost() }
                    .onFailure { err ->
                        settings.setCodexMcpEnabled(false)
                        _uiState.update {
                            it.copy(sync = it.sync.copy(lastError = err.message ?: "Could not start Codex MCP"))
                        }
                    }
            }
        }
    }

    fun setSyncPeer(host: String, pin: String) {
        syncCoordinator.setPeer(host, pin)
    }

    fun setAutoSync(enabled: Boolean) {
        syncCoordinator.setAutoSync(enabled)
    }

    fun setSyncTls(enabled: Boolean) {
        syncCoordinator.setTlsEnabled(enabled)
    }

    fun keepSyncMine(id: Long) {
        val entry = _uiState.value.sync.conflicts.find { it.id == id } ?: return
        syncCoordinator.keepMine(entry)
    }

    fun keepSyncTheirs(id: Long) {
        val entry = _uiState.value.sync.conflicts.find { it.id == id } ?: return
        syncCoordinator.keepTheirs(entry)
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoBackupEnabled(enabled)
            if (enabled) {
                runCatching { backupManager.maybeAutoBackup() }
                AutoBackupScheduler.ensure(appContext)
            } else {
                AutoBackupScheduler.cancel(appContext)
            }
        }
    }

    fun setDailyCharactersEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setDailyCharactersEnabled(enabled) }
    }

    fun pushSyncToPeer() {
        viewModelScope.launch { syncCoordinator.pushToPeer() }
    }

    fun pullSyncFromPeer() {
        viewModelScope.launch { syncCoordinator.pullFromPeer() }
    }

    fun suggestedWebUrl(): String = syncCoordinator.suggestedWebUrl()

    fun regenerateMcpToken() = syncCoordinator.regenerateMcpToken()

    fun onOpenRouterKey(value: String) = _uiState.update { it.copy(openRouterKey = value) }
    fun onAnthropicKey(value: String) = _uiState.update { it.copy(anthropicKey = value) }
    fun onOpenAiKey(value: String) = _uiState.update { it.copy(openAiKey = value) }
    fun onGeminiKey(value: String) = _uiState.update { it.copy(geminiKey = value) }
    fun onFalKey(value: String) = _uiState.update { it.copy(falKey = value) }

    /** Stores the fal.ai key and loads fal's picture catalog with it, which also checks the key. */
    fun saveFalKey() {
        val key = _uiState.value.falKey.trim()
        if (key.isBlank()) {
            settings.setApiKey(SecureKeyStore.FAL, "")
            _uiState.update { it.copy(falStatus = "fal.ai key removed.") }
            return
        }
        settings.setApiKey(SecureKeyStore.FAL, key)
        viewModelScope.launch {
            _uiState.update { it.copy(falChecking = true, falStatus = "Checking the key with fal.ai…") }
            val status = runCatching { fal.refreshCatalog() to fal.balance() }.fold(
                { (models, balance) ->
                    val edits = models.count { it.supportsImages }
                    val credit = when {
                        balance == null -> "\nfal shows the balance only to admin keys. If a picture fails with \"no credit\", " +
                            "add credit at ${com.ihy2ln.weaverse.ai.fal.Fal.BILLING_PAGE}."
                        balance <= 0.0 -> "\n✗ No credit yet: add some at ${com.ihy2ln.weaverse.ai.fal.Fal.BILLING_PAGE} before generating."
                        else -> "\nCredit: $" + String.format(java.util.Locale.US, "%.2f", balance)
                    }
                    "✓ Key works. $edits picture-editing and ${models.size - edits} text-to-image models from fal.ai, " +
                        "plus ${com.ihy2ln.weaverse.ai.fal.Fal.chatModels.size} text models through fal's router." + credit
                },
                { "✗ ${it.message ?: "fal.ai did not answer"}" },
            )
            _uiState.update { it.copy(falChecking = false, falStatus = status) }
        }
    }
    fun onModelSearch(value: String) = _uiState.update { it.copy(modelSearch = value) }
    fun onModelTab(tab: ModelListTab) = _uiState.update { it.copy(modelTab = tab) }

    fun saveOtherKeys() {
        val state = _uiState.value
        settings.setApiKey(SecureKeyStore.ANTHROPIC, state.anthropicKey)
        settings.setApiKey(SecureKeyStore.OPENAI, state.openAiKey)
        settings.setApiKey(SecureKeyStore.GEMINI, state.geminiKey)
        _uiState.update {
            it.copy(
                keyStatus = "Other provider keys saved locally.",
                keyStatusIsError = false,
                otherProviderModels = OtherProviderSeeds.seeded(
                    openai = state.openAiKey.isNotBlank(),
                    anthropic = state.anthropicKey.isNotBlank(),
                    gemini = state.geminiKey.isNotBlank(),
                ),
            )
        }
    }

    /**
     * Validates OpenRouter key via GET /api/v1/key before storing.
     * Never reports success unless a real 2xx response was received.
     */
    fun saveOpenRouterKey() {
        viewModelScope.launch {
            val key = _uiState.value.openRouterKey.trim()
            if (key.isBlank()) {
                settings.secureKeys.clear(SecureKeyStore.OPENROUTER)
                _uiState.update {
                    it.copy(
                        openRouterKeyInfo = null,
                        keyStatus = "Key removed",
                        keyStatusIsError = false,
                        isValidatingKey = false,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isValidatingKey = true, keyStatus = "Validating…", keyStatusIsError = false) }
            try {
                val data = openRouterRepository.validateKey(key)
                settings.setApiKey(SecureKeyStore.OPENROUTER, key)
                _uiState.update {
                    it.copy(
                        openRouterKeyInfo = data,
                        isValidatingKey = false,
                        keyStatus = formatKeyInfo(data),
                        keyStatusIsError = false,
                    )
                }
                runCatching { openRouterRepository.fetchModels(forceRefresh = true) }
            } catch (e: AIError) {
                _uiState.update {
                    it.copy(
                        isValidatingKey = false,
                        keyStatus = e.message ?: "Validation failed",
                        keyStatusIsError = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidatingKey = false,
                        keyStatus = e.message ?: "Validation failed",
                        keyStatusIsError = true,
                    )
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingKey = true, keyStatus = "Testing…", keyStatusIsError = false) }
            try {
                // Prefer the field value if user typed a new key but hasn't saved yet — still validate via network.
                val typed = _uiState.value.openRouterKey.trim()
                val data = if (typed.isNotBlank()) {
                    openRouterRepository.validateKey(typed).also {
                        settings.setApiKey(SecureKeyStore.OPENROUTER, typed)
                    }
                } else {
                    openRouterRepository.testStoredKey()
                }
                _uiState.update {
                    it.copy(
                        openRouterKeyInfo = data,
                        isValidatingKey = false,
                        keyStatus = "Connection OK — ${formatKeyInfo(data)}",
                        keyStatusIsError = false,
                    )
                }
            } catch (e: AIError) {
                _uiState.update {
                    it.copy(
                        isValidatingKey = false,
                        keyStatus = e.message ?: "Test failed",
                        keyStatusIsError = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidatingKey = false,
                        keyStatus = e.message ?: "Test failed",
                        keyStatusIsError = true,
                    )
                }
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingModels = true) }
            try {
                openRouterRepository.fetchModels(forceRefresh = true)
                _uiState.update {
                    it.copy(isRefreshingModels = false, keyStatus = "Models refreshed from OpenRouter.", keyStatusIsError = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshingModels = false,
                        keyStatus = e.message ?: "Failed to refresh models",
                        keyStatusIsError = true,
                    )
                }
            }
        }
    }

    fun selectDefaultModel(modelId: String, available: Boolean, providerPrefix: String = "openrouter") {
        if (!available) return
        val ref = when {
            modelId.startsWith("openrouter/") ||
                com.ihy2ln.weaverse.ai.fal.Fal.isFalRef(modelId) ||
                modelId.startsWith("openai/") ||
                modelId.startsWith("anthropic/") ||
                modelId.startsWith("gemini/") -> modelId
            else -> "$providerPrefix/$modelId"
        }
        viewModelScope.launch {
            settings.setDefaultModel(ref)
        }
    }

    /** Asks the PC companion which harnesses it can run right now. */
    fun checkPc() {
        if (_uiState.value.pcChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(pcChecking = true, pcStatus = "Checking the PC…") }
            val status = runCatching { pcBridge.refreshCapabilities() }
                .fold({ pcBridge.status.value }, { it.message ?: "Could not reach the PC." })
            _uiState.update { it.copy(pcChecking = false, pcStatus = status) }
        }
    }

    fun selectModelForCurrentTab(modelId: String, available: Boolean) {
        if (!available) return
        val ref = com.ihy2ln.weaverse.feature.prompt.PromptModelSelection.modelRef(modelId)
        viewModelScope.launch {
            // A ComfyUI workflow or fal.ai picture model only makes pictures, whichever tab it was picked from.
            if (ref.startsWith(com.ihy2ln.weaverse.ai.pc.PcHarness.COMFY) || com.ihy2ln.weaverse.ai.fal.Fal.isImageRef(ref)) {
                settings.setMangaImageModel(ref)
                return@launch
            }
            when (_uiState.value.modelTab) {
                ModelListTab.Vision -> settings.setMangaVisionModel(ref)
                ModelListTab.ImageGeneration -> settings.setMangaImageModel(ref)
                ModelListTab.Writing, ModelListTab.TextToSpeech, ModelListTab.All -> settings.setDefaultModel(ref)
            }
        }
    }

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAppearanceProfile(profile: AppearanceProfile) {
        viewModelScope.launch { settings.setAppearanceProfile(profile) }
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { settings.setFontSize(sp) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { settings.setLineHeight(value) }
    }

    fun setUiTextScale(percent: Int) {
        viewModelScope.launch { settings.setUiTextScale(percent) }
    }

    fun setUiLineSpacing(value: Float) {
        viewModelScope.launch { settings.setUiLineSpacing(value) }
    }

    fun setBackdropStyle(style: String) {
        viewModelScope.launch {
            settings.setBackdropStyle(style)
            settings.setProfileBackgroundEnabled(true)
        }
    }

    fun setGlassClarity(percent: Int) {
        viewModelScope.launch { settings.setGlassClarity(percent) }
    }

    fun setSectionAppearance(sectionKey: String, colorHex: String, opacityPercent: Int) {
        viewModelScope.launch { settings.setSectionAppearance(sectionKey, colorHex, opacityPercent) }
    }

    fun setAppBrightness(percent: Int) {
        viewModelScope.launch { settings.setAppBrightnessPercent(percent) }
    }

    fun resetAppearanceColors() {
        viewModelScope.launch { settings.resetAppearanceColors() }
    }

    fun setShowExtraPromptSurfaces(enabled: Boolean) {
        viewModelScope.launch { settings.setShowExtraPromptSurfaces(enabled) }
    }

    fun setExtraPromptSurface(surface: ExtraPromptSurface, enabled: Boolean) {
        viewModelScope.launch { settings.setExtraPromptSurface(surface, enabled) }
    }

    fun chooseTopicMediaFolder(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        setTopicMediaLibraryRoot(uri.toString())
    }

    fun setTopicMediaLibraryRoot(root: String) {
        viewModelScope.launch {
            settings.setTopicMediaLibraryRoot(root)
            refreshTopicMedia(root)
        }
    }

    fun setTopicMediaAutoAttach(enabled: Boolean) {
        viewModelScope.launch { settings.setTopicMediaAutoAttach(enabled) }
    }

    fun refreshTopicMedia(root: String = _uiState.value.prefs.topicMediaLibraryRoot) {
        viewModelScope.launch {
            _uiState.update { it.copy(topicMediaStatus = if (root.isBlank()) "No media library selected" else "Scanning…") }
            runCatching { topicMediaLibrary.snapshot(root) }
                .onSuccess { snapshot ->
                    val message = when {
                        root.isBlank() -> "No media library selected"
                        snapshot.topics.isEmpty() -> "No topic folders found or this device cannot read the path"
                        else -> "${snapshot.topics.size} topics: ${snapshot.topics.take(8).joinToString(", ")}" +
                            if (snapshot.topics.size > 8) "…" else ""
                    }
                    _uiState.update { it.copy(topicMediaStatus = message) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(topicMediaStatus = "Could not read library: ${error.message}") }
                }
        }
    }

    fun importBackground(uri: Uri) {
        viewModelScope.launch {
            val media = mediaRepository.importFromUri(uri)
            settings.setBackgroundMediaId(media.id)
        }
    }

    fun clearBackground() {
        viewModelScope.launch { settings.setBackgroundMediaId("") }
    }

    /** Adds a custom `!keyword` command that files entries under [kindName]. */
    fun addBangCommand(keyword: String, kindName: String) {
        viewModelScope.launch { settings.addBangCommand(keyword, kindName) }
    }

    /** Removes a composer command row (built-ins are hidden, customs deleted). */
    fun removeBangCommand(keyword: String, isBuiltIn: Boolean) {
        viewModelScope.launch { settings.removeBangCommand(keyword, isBuiltIn) }
    }

    /** Restores all built-in composer commands and clears custom ones. */
    fun resetBangCommands() {
        viewModelScope.launch { settings.resetBangCommands() }
    }

    /** Adds a custom `*keyword` RPG turn command. */
    fun addStarCommand(keyword: String, description: String, requiresRoll: Boolean) {
        viewModelScope.launch { settings.addStarCommand(keyword, description, requiresRoll) }
    }

    /** Removes a `*` RPG turn command row (built-ins are hidden, customs deleted). */
    fun removeStarCommand(keyword: String, isBuiltIn: Boolean) {
        viewModelScope.launch { settings.removeStarCommand(keyword, isBuiltIn) }
    }

    /** Restores all built-in `*` commands and clears custom ones. */
    fun resetStarCommands() {
        viewModelScope.launch { settings.resetStarCommands() }
    }

    fun setProfileBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setProfileBackgroundEnabled(enabled) }
    }

    fun setHomeSplashArt(key: String) {
        viewModelScope.launch { settings.setHomeSplashArt(key) }
    }

    /** Pictures and videos from the media library, for the mode-backgrounds previews. */
    val backdropFiles: kotlinx.coroutines.flow.StateFlow<Map<String, com.ihy2ln.weaverse.feature.shell.BackdropFile>> =
        mediaRepository.observeAll().map { all ->
            all.filter { it.type == "image" || it.type == "video" }.associate { entity ->
                entity.id to com.ihy2ln.weaverse.feature.shell.BackdropFile(
                    mediaRepository.resolveFile(entity).absolutePath,
                    video = entity.type == "video",
                )
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleModeBackdrop(modeKey: String, entry: String) {
        viewModelScope.launch {
            val current = settings.preferences.first().modeBackdrops[modeKey].orEmpty()
            settings.setModeBackdrops(modeKey, if (entry in current) current - entry else current + entry)
        }
    }

    fun removeModeBackdrop(modeKey: String, entry: String) {
        viewModelScope.launch {
            val current = settings.preferences.first().modeBackdrops[modeKey].orEmpty()
            settings.setModeBackdrops(modeKey, current - entry)
        }
    }

    /** Adds a picture or video from the phone to a mode's backgrounds (copied into the media library). */
    fun importModeBackdrop(modeKey: String, uri: Uri) {
        viewModelScope.launch {
            val media = mediaRepository.importFromUri(uri)
            val current = settings.preferences.first().modeBackdrops[modeKey].orEmpty()
            settings.setModeBackdrops(modeKey, current + (com.ihy2ln.weaverse.data.settings.ModeBackdrops.MEDIA + media.id))
        }
    }

    fun setBackdropSlideshow(enabled: Boolean) {
        viewModelScope.launch { settings.setBackdropSlideshow(enabled) }
    }

    fun setHomeIntroEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHomeIntroEnabled(enabled) }
    }

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { backupManager.exportBackup() }
                .onSuccess { result -> _uiState.update { it.copy(exportStatus = result.statusMessage()) } }
                .onFailure { err -> _uiState.update { it.copy(exportStatus = "Export failed: ${err.message}") } }
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = "Restoring… your current library is saved first.") }
            runCatching { backupManager.restoreLatestBackup() }
                .onSuccess {
                    _uiState.update { it.copy(exportStatus = "Restore complete — restarting Weaverse…") }
                    kotlinx.coroutines.delay(900)
                    com.ihy2ln.weaverse.core.AppRestarter.restart(appContext)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(exportStatus = "Restore failed: ${err.message}") }
                    // Past the point the database was closed, only a fresh start can reopen it.
                    if (backupManager.libraryClosed()) {
                        kotlinx.coroutines.delay(2500)
                        com.ihy2ln.weaverse.core.AppRestarter.restart(appContext)
                    }
                }
        }
    }

    fun loadCrashLog() {
        _uiState.update { it.copy(crashLogText = CrashLogStore.latestText(appContext)) }
    }

    fun copyCrashLog(): String = CrashLogStore.latestText(appContext)

    private fun formatKeyInfo(data: OpenRouterKeyData): String {
        val parts = mutableListOf<String>()
        data.label?.takeIf { it.isNotBlank() }?.let { parts += "label=$it" }
        data.usage?.let { parts += "usage=$it" }
        data.limit?.let { parts += "limit=$it" }
        data.limitRemaining?.let { parts += "remaining=$it" }
        data.isFreeTier?.let { parts += "free_tier=$it" }
        data.rateLimit?.let { rl ->
            parts += "rate_limit=${rl.requests ?: "?"} / ${rl.interval ?: "?"}"
        }
        return if (parts.isEmpty()) "Key validated" else parts.joinToString(" · ")
    }
}
