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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModelListTab { Writing, TextToSpeech, All }

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
    val ttsModels: List<ModelInfo> = emptyList(),
    val modelSearch: String = "",
    val modelTab: ModelListTab = ModelListTab.Writing,
    val modelsCachedAt: Long = 0L,
    val exportStatus: String = "",
    val backgroundLabel: String = "None",
    val backgroundNote: String = "",
    val sync: SyncUiSnapshot = SyncUiSnapshot(),
    val otherProviderModels: List<ModelInfo> = emptyList(),
    val crashLogText: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
    private val openRouterRepository: OpenRouterRepository,
    private val modelCache: OpenRouterModelCache,
    private val backupManager: BackupManager,
    private val mediaRepository: MediaRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {
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
                    media.type == "video" -> "Video (stored; playback deferred)"
                    else -> "Image · ${media.mimeType}"
                }
                _uiState.update {
                    it.copy(
                        prefs = prefs,
                        backgroundLabel = label,
                        backgroundNote = if (media?.type == "video") {
                            "Video backgrounds are saved but shell applies images only for now."
                        } else {
                            ""
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(modelCache.models, modelCache.cachedAt) { models, cachedAt ->
                models to cachedAt
            }.collect { (models, cachedAt) ->
                _uiState.update {
                    it.copy(
                        models = modelCache.toModelInfo(models),
                        writingModels = modelCache.writingModels(models),
                        ttsModels = modelCache.ttsModels(models),
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

    fun onOpenRouterKey(value: String) = _uiState.update { it.copy(openRouterKey = value) }
    fun onAnthropicKey(value: String) = _uiState.update { it.copy(anthropicKey = value) }
    fun onOpenAiKey(value: String) = _uiState.update { it.copy(openAiKey = value) }
    fun onGeminiKey(value: String) = _uiState.update { it.copy(geminiKey = value) }
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
                modelId.startsWith("openai/") ||
                modelId.startsWith("anthropic/") ||
                modelId.startsWith("gemini/") -> modelId
            else -> "$providerPrefix/$modelId"
        }
        viewModelScope.launch {
            settings.setDefaultModel(ref)
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

    fun importBackground(uri: Uri) {
        viewModelScope.launch {
            val media = mediaRepository.importFromUri(uri)
            settings.setBackgroundMediaId(media.id)
        }
    }

    fun clearBackground() {
        viewModelScope.launch { settings.setBackgroundMediaId("") }
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
            runCatching { backupManager.restoreLatestBackup() }
                .onSuccess { _uiState.update { it.copy(exportStatus = "Restore complete — restart app") } }
                .onFailure { err -> _uiState.update { it.copy(exportStatus = "Restore failed: ${err.message}") } }
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
