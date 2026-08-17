package com.ihy2ln.weaverse.feature.novel.aiproviders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIService
import com.ihy2ln.weaverse.ai.providers.OpenRouterKeyInfo
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.repo.ConnectionProfileRepository
import com.ihy2ln.weaverse.data.settings.SecretsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTestState {
    data object Testing : ConnectionTestState
    data class Success(val modelCount: Int) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

fun defaultBaseUrlFor(type: AIProviderType): String = when (type) {
    AIProviderType.Anthropic -> "https://api.anthropic.com"
    AIProviderType.OpenAICompatible -> "https://api.openai.com/v1"
    AIProviderType.Gemini -> "https://generativelanguage.googleapis.com"
    AIProviderType.OpenRouter -> OpenRouterProvider.DEFAULT_BASE_URL
}

@HiltViewModel
class ConnectionProfilesViewModel @Inject constructor(
    private val repository: ConnectionProfileRepository,
    private val secretsStore: SecretsStore,
    private val aiService: AIService,
    private val openRouterProvider: OpenRouterProvider,
) : ViewModel() {
    val profiles: StateFlow<List<ConnectionProfileEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testResults = MutableStateFlow<Map<String, ConnectionTestState>>(emptyMap())
    val testResults: StateFlow<Map<String, ConnectionTestState>> = _testResults

    /** OpenRouter-only: the real `GET /auth/key` payload (label, usage, credit limit, rate
     * limit, free-tier flag), keyed by profile id. Populated only by a successful key save/
     * refresh — never fabricated, and cleared whenever the key changes so a stale profile's old
     * numbers can't linger under a new key. */
    private val _keyInfo = MutableStateFlow<Map<String, OpenRouterKeyInfo>>(emptyMap())
    val keyInfo: StateFlow<Map<String, OpenRouterKeyInfo>> = _keyInfo

    fun fetchKeyInfo(profile: ConnectionProfileEntity) {
        val apiKey = secretsStore.getApiKey(profile.id) ?: return
        viewModelScope.launch {
            val result = openRouterProvider.validateKey(profile.baseUrl, apiKey)
            result.onSuccess { info -> _keyInfo.update { it + (profile.id to info) } }
        }
    }

    /** A key is written to [SecretsStore] — and any "connected" state is shown — only after a
     * real network round-trip against the provider returns success. [providerType]/[baseUrl] are
     * needed ahead of the profile existing in the repository (validation happens before the
     * profile row is even created for [addProfile]'s case). */
    private suspend fun validateAndStore(
        profileId: String,
        providerType: AIProviderType,
        baseUrl: String,
        apiKey: String,
    ): Boolean {
        _testResults.update { it + (profileId to ConnectionTestState.Testing) }
        val probe = ConnectionProfileEntity(id = profileId, providerType = providerType, label = "", baseUrl = baseUrl)
        val validation: Result<Any> = if (providerType == AIProviderType.OpenRouter) {
            openRouterProvider.validateKey(baseUrl, apiKey).onSuccess { info ->
                _keyInfo.update { it + (profileId to info) }
            }
        } else {
            aiService.validateKey(probe, apiKey)
        }
        return validation.fold(
            onSuccess = {
                secretsStore.setApiKey(profileId, apiKey)
                viewModelScope.launch { runConnectionTest(probe) }
                true
            },
            onFailure = { error ->
                _testResults.update { it + (profileId to ConnectionTestState.Failure(error.message ?: "Connection failed")) }
                false
            },
        )
    }

    /** Creating a profile with a blank key is still allowed (the user can add the key later via
     * [updateApiKey]) — only a *non-blank* key goes through [validateAndStore], and only a
     * validated key is ever persisted. */
    fun addProfile(providerType: AIProviderType, label: String, baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            val resolvedBaseUrl = baseUrl.ifBlank { defaultBaseUrlFor(providerType) }
            val profile = ConnectionProfileEntity(
                providerType = providerType,
                label = label.ifBlank { providerType.name },
                baseUrl = resolvedBaseUrl,
                sortOrder = profiles.value.size,
            )
            repository.upsert(profile)
            if (apiKey.isNotBlank()) {
                validateAndStore(profile.id, providerType, resolvedBaseUrl, apiKey)
            }
        }
    }

    fun deleteProfile(profile: ConnectionProfileEntity) {
        viewModelScope.launch {
            repository.delete(profile)
            secretsStore.removeApiKey(profile.id)
            _testResults.update { it - profile.id }
            _keyInfo.update { it - profile.id }
        }
    }

    /** The only way to set a key used to be creating a brand-new profile (`addProfile`) — an
     * existing profile with a wrong/expired/mistyped key had no way to fix it short of delete-
     * and-recreate, losing its label/base URL customization too. A blank [apiKey] clears the
     * stored key outright (no validation call to make); a non-blank one only replaces the
     * stored key if [validateAndStore] confirms it actually works — the old key stays in place
     * on failure rather than being clobbered by one that doesn't. */
    fun updateApiKey(profile: ConnectionProfileEntity, apiKey: String) {
        if (apiKey.isBlank()) {
            secretsStore.removeApiKey(profile.id)
            _testResults.update { it - profile.id }
            _keyInfo.update { it - profile.id }
            return
        }
        _keyInfo.update { it - profile.id }
        viewModelScope.launch { validateAndStore(profile.id, profile.providerType, profile.baseUrl, apiKey) }
    }

    fun hasApiKey(profileId: String): Boolean = !secretsStore.getApiKey(profileId).isNullOrBlank()

    fun testConnection(profile: ConnectionProfileEntity) {
        viewModelScope.launch { runConnectionTest(profile) }
    }

    private suspend fun runConnectionTest(profile: ConnectionProfileEntity) {
        _testResults.update { it + (profile.id to ConnectionTestState.Testing) }
        val result = aiService.testConnection(profile)
        val newState = result.fold(
            onSuccess = { models -> ConnectionTestState.Success(models.size) },
            onFailure = { error -> ConnectionTestState.Failure(error.message ?: "Connection failed") },
        )
        _testResults.update { it + (profile.id to newState) }
    }
}
