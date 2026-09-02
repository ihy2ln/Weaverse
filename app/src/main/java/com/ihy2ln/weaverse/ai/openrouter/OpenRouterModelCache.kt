package com.ihy2ln.weaverse.ai.openrouter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ihy2ln.weaverse.ai.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.openRouterDataStore: DataStore<Preferences> by preferencesDataStore("openrouter_cache")

object WritingModelSeeds {
    const val DEFAULT_MODEL_ID = "deepseek/deepseek-v4-flash"
    const val DEFAULT_MODEL_REF = "openrouter/$DEFAULT_MODEL_ID"

    private val exactIds = listOf(
        "deepseek/deepseek-v4-flash",
        "xiaomi/mimo-v2.5-20260422",
        "deepseek/deepseek-v4-flash-20260731",
        "deepseek/deepseek-v4-pro-20260423",
        "google/gemma-4-31b-it-20260402",
    )

    private val prefixHints = listOf(
        "tencent/" to "Tencent Hy3",
        "openai/" to "OpenAI GPT-5.6 Luna",
        "z-ai/" to "Z.ai GLM 5.2",
        "minimax/" to "MiniMax M3",
        "moonshotai/" to "MoonshotAI Kimi K3",
    )

    fun resolveWritingModels(allModels: List<ModelInfo>): List<ModelInfo> {
        val byId = allModels.associateBy { it.id }
        val resolved = mutableListOf<ModelInfo>()
        exactIds.forEach { id ->
            resolved += byId[id] ?: ModelInfo(id, id.substringAfterLast('/'), available = false)
        }
        prefixHints.forEach { (prefix, label) ->
            val match = allModels.firstOrNull { it.id.startsWith(prefix) && it.id !in exactIds }
            resolved += match?.copy(displayName = label)
                ?: ModelInfo("$prefix*", label, available = false)
        }
        return resolved
    }

    /**
     * Seeded writing models first, then every other live OpenRouter text model
     * so Settings can pick from the full catalog.
     */
    fun resolveAllWritingModels(allModels: List<ModelInfo>): List<ModelInfo> {
        val writing = allModels.filter { !it.isTts }
        val pinned = resolveWritingModels(writing)
        val pinnedIds = pinned.map { it.id }.toSet()
        val rest = writing
            .filter { it.available && it.id !in pinnedIds }
            .sortedBy { it.displayName.lowercase() }
        return pinned + rest
    }
}

object TtsModelSeeds {
    private val exactIds = listOf(
        "openai/gpt-4o-mini-tts",
        "openai/gpt-4o-mini-tts-2025-12-15",
        "mistralai/voxtral-mini-tts-2603",
        "google/gemini-2.5-flash-preview-tts",
    )

    private val idHints = listOf("tts", "speech", "voxtral")

    fun resolveTtsModels(allModels: List<ModelInfo>): List<ModelInfo> {
        val byId = allModels.associateBy { it.id }
        val resolved = linkedMapOf<String, ModelInfo>()
        exactIds.forEach { id ->
            val hit = byId[id]
            resolved[id] = (hit ?: ModelInfo(
                id = id,
                displayName = id.substringAfterLast('/'),
                available = false,
                isTts = true,
                tags = listOf("TTS"),
            )).copy(isTts = true, tags = (hit?.tags.orEmpty() + "TTS").distinct())
        }
        allModels.filter { model ->
            model.isTts || idHints.any { hint ->
                model.id.contains(hint, ignoreCase = true) ||
                    model.displayName.contains(hint, ignoreCase = true)
            }
        }.forEach { model ->
            resolved[model.id] = model.copy(
                isTts = true,
                tags = (model.tags + "TTS").distinct(),
            )
        }
        return resolved.values.toList()
    }
}

@Singleton
class OpenRouterModelCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val models: Flow<List<OpenRouterModelDto>> = context.openRouterDataStore.data.map { prefs ->
        decodeModels(prefs[KEY_MODELS_JSON].orEmpty())
    }

    val cachedAt: Flow<Long> = context.openRouterDataStore.data.map { prefs ->
        prefs[KEY_CACHED_AT] ?: 0L
    }

    suspend fun getCachedModels(): List<OpenRouterModelDto> =
        context.openRouterDataStore.data.map { prefs ->
            decodeModels(prefs[KEY_MODELS_JSON].orEmpty())
        }.first()

    suspend fun save(response: OpenRouterModelsResponse) {
        context.openRouterDataStore.edit { prefs ->
            prefs[KEY_MODELS_JSON] = json.encodeToString(response.data)
            prefs[KEY_CACHED_AT] = System.currentTimeMillis()
        }
    }

    fun toModelInfo(dtos: List<OpenRouterModelDto>): List<ModelInfo> =
        dtos.map { dto ->
            val tts = dto.isSpeechOutput()
            val vision = dto.supportsImageInput()
            val imageGen = dto.generatesImages()
            val tags = buildList {
                if (tts) add("TTS")
                if (vision) add("Vision")
                if (imageGen) add("Image generation")
            }
            ModelInfo(
                id = dto.id,
                displayName = dto.name ?: dto.id,
                contextLength = dto.contextLength,
                promptPricePerMillion = dto.pricing?.prompt?.toDoubleOrNull()?.times(1_000_000),
                completionPricePerMillion = dto.pricing?.completion?.toDoubleOrNull()?.times(1_000_000),
                available = true,
                isTts = tts,
                supportsImages = vision,
                generatesImages = imageGen,
                tags = tags,
            )
        }

    fun writingModels(dtos: List<OpenRouterModelDto>): List<ModelInfo> =
        WritingModelSeeds.resolveAllWritingModels(
            toModelInfo(dtos.filter { it.isTextGeneration() }),
        )

    fun ttsModels(dtos: List<OpenRouterModelDto>): List<ModelInfo> {
        val tagged = toModelInfo(dtos).filter { it.isTts }
        if (tagged.isNotEmpty()) return tagged.sortedBy { it.displayName }
        return TtsModelSeeds.resolveTtsModels(toModelInfo(dtos))
    }

    fun isKnownModel(modelId: String, dtos: List<OpenRouterModelDto>): Boolean =
        dtos.any { it.id == modelId }

    fun modelSupportsImages(modelId: String, dtos: List<OpenRouterModelDto>): Boolean {
        val id = modelId.removePrefix("openrouter/")
        return dtos.firstOrNull { it.id == id }?.supportsImageInput() == true
    }

    private fun decodeModels(raw: String): List<OpenRouterModelDto> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<OpenRouterModelDto>>(raw) }.getOrDefault(emptyList())

    companion object {
        private val KEY_MODELS_JSON = stringPreferencesKey("openrouter_models_json")
        private val KEY_CACHED_AT = longPreferencesKey("openrouter_cached_at")
    }
}
