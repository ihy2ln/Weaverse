package com.ihy2ln.weaverse.ai.prompt

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One ordered message block in a prompt's Instructions tab (spec §8.2). */
@Serializable
data class PromptInstructionMessage(
    val role: PromptMessageRole,
    /** Raw template text containing `{{variable}}` placeholders — see [PromptVariables]. */
    val template: String,
)

enum class PromptMessageRole { System, User, Assistant }

@Serializable
data class PromptInstructions(val messages: List<PromptInstructionMessage> = emptyList())

/** Sampler overrides / stop sequences / streaming / max tokens (spec §8.2 Advanced tab). */
@Serializable
data class PromptAdvancedSettings(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
    val streaming: Boolean = true,
)

/** The full variable set prompts can reference (spec §8.2). */
object PromptVariables {
    const val SCENE = "{{scene}}"
    const val PREVIOUS_SCENES = "{{previousScenes}}"
    const val SCENE_BEATS = "{{sceneBeats}}"
    const val SUMMARY = "{{summary}}"
    const val CODEX = "{{codex}}"
    const val STYLE_GUIDE = "{{styleGuide}}"
    const val POV = "{{pov}}"
    const val TENSE = "{{tense}}"
    const val GENRE = "{{genre}}"
    const val SELECTION = "{{selection}}"
    const val USER_INPUT = "{{userInput}}"
    const val CHAR = "{{char}}"
    const val USER = "{{user}}"
    const val PERSONA = "{{persona}}"
    const val WORLD_INFO = "{{worldInfo}}"
    const val CHAT_HISTORY = "{{chatHistory}}"
    const val AUTHORS_NOTE = "{{authorsNote}}"

    val all = listOf(
        SCENE, PREVIOUS_SCENES, SCENE_BEATS, SUMMARY, CODEX, STYLE_GUIDE, POV, TENSE, GENRE,
        SELECTION, USER_INPUT, CHAR, USER, PERSONA, WORLD_INFO, CHAT_HISTORY, AUTHORS_NOTE,
    )
}

private val promptJson = Json { ignoreUnknownKeys = true }

fun PromptInstructions.toJson(): String = promptJson.encodeToString(this)

fun String.toPromptInstructions(): PromptInstructions =
    if (isBlank()) PromptInstructions() else runCatching { promptJson.decodeFromString<PromptInstructions>(this) }.getOrDefault(PromptInstructions())

fun PromptAdvancedSettings.toJson(): String = promptJson.encodeToString(this)

fun String.toPromptAdvancedSettings(): PromptAdvancedSettings =
    if (isBlank()) PromptAdvancedSettings() else runCatching { promptJson.decodeFromString<PromptAdvancedSettings>(this) }.getOrDefault(PromptAdvancedSettings())

/** Resolves `{{variable}}` placeholders in a template. Missing keys are left as-is rather than blanked. */
fun String.resolveVariables(values: Map<String, String>): String {
    var result = this
    for ((key, value) in values) {
        result = result.replace(key, value)
    }
    return result
}
