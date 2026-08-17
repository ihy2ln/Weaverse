package com.ihy2ln.weaverse.core.tts

import android.content.Context
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks text via Android system TTS, or OpenRouter /audio/speech when the
 * selected default model is a TTS/speech model.
 */
@Singleton
class TtsService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: TextToSpeechController,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
    private val modelCache: OpenRouterModelCache,
) {
    fun speakLocal(text: String) = controller.speak(text)

    fun stop() = controller.stop()

    suspend fun speak(text: String): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext "Nothing to speak"
        val modelRef = settings.preferences.first().defaultModelRef
        val modelId = modelRef.removePrefix("openrouter/")
        val cached = modelCache.getCachedModels()
        val isTts = modelCache.toModelInfo(cached).any { it.id == modelId && it.isTts } ||
            modelId.contains("tts", ignoreCase = true) ||
            modelId.contains("speech", ignoreCase = true)
        if (isTts && aiGeneration.hasApiKey()) {
            return@withContext runCatching {
                val out = File(context.cacheDir, "tts-${System.currentTimeMillis()}.mp3")
                aiGeneration.synthesizeSpeech(trimmed, modelId, out)
                controller.playAudioFile(out)
                "Playing OpenRouter TTS"
            }.getOrElse { err ->
                controller.speak(trimmed)
                "OpenRouter TTS failed (${err.message}); used system TTS"
            }
        }
        controller.speak(trimmed)
        "Speaking (system TTS)"
    }
}
