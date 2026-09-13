package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.ihy2ln.weaverse.core.text.PanelTemplates

/** The durable, user-visible lifecycle of an AI storyboard page request. */
enum class StoryboardGenerationPhase {
    Idle,
    Generating,
    Stopped,
    Failed,
    ReadyToApply,
    OfflineFallback,
}

data class StoryboardGenerationUiState(
    val phase: StoryboardGenerationPhase = StoryboardGenerationPhase.Idle,
    val progress: Int = 0,
    val prompt: String = "",
    val status: String = "",
    val draft: StoryboardPageDraft? = null,
    val modelRef: String = "",
    val rightToLeft: Boolean = false,
    val generateMissingArt: Boolean = false,
)

@Serializable
data class StoryboardPanelDraft(
    val description: String = "",
    val caption: String = "",
    val dialogue: String = "",
    val speaker: String = "",
    val mediaQuery: String = "",
)

@Serializable
data class StoryboardPageDraft(
    val title: String = "Generated page",
    val templateId: String = "classic-6",
    val readingOrder: String = "ltr",
    val panels: List<StoryboardPanelDraft> = emptyList(),
)

private val storyboardJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private fun jsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
}

/** Parses a model response without allowing malformed output to reach the document model. */
fun parseStoryboardPageDraft(raw: String): StoryboardPageDraft? {
    val draft = runCatching {
        storyboardJson.decodeFromString<StoryboardPageDraft>(jsonObject(raw) ?: return null)
    }.getOrNull() ?: return null
    val template = PanelTemplates.byId(draft.templateId) ?: PanelTemplates.byId("classic-6")!!
    val panels = draft.panels
        .filter { it.description.isNotBlank() || it.caption.isNotBlank() || it.dialogue.isNotBlank() }
        .take(template.panelCount)
        .ifEmpty { return null }
    return draft.copy(
        templateId = template.id,
        readingOrder = if (draft.readingOrder.equals("rtl", ignoreCase = true)) "rtl" else "ltr",
        title = draft.title.trim().ifBlank { "Generated page" },
        panels = panels,
    )
}

/** Deterministic page plan used when no model/key is available or generation is stopped. */
fun fallbackStoryboardPageDraft(source: String, rightToLeft: Boolean): StoryboardPageDraft {
    val clean = source.trim().replace(Regex("\\s+"), " ").ifBlank { "A new moment in the story." }
    val short = clean.take(180)
    val beats = listOf(
        StoryboardPanelDraft(
            description = "Establish the setting and the mood of the scene.",
            caption = short,
            mediaQuery = "setting background scene",
        ),
        StoryboardPanelDraft(
            description = "Show the main character reacting to the new situation.",
            dialogue = "Something is different here.",
            speaker = "Main character",
            mediaQuery = "character portrait scene",
        ),
        StoryboardPanelDraft(
            description = "Reveal the immediate obstacle or discovery.",
            caption = "A new problem takes shape.",
            mediaQuery = "action obstacle scene",
        ),
        StoryboardPanelDraft(
            description = "End on a clear choice or hook for the next page.",
            dialogue = "What do we do now?",
            speaker = "Main character",
            mediaQuery = "dramatic scene",
        ),
    )
    return StoryboardPageDraft(
        title = "Offline storyboard page",
        templateId = "establishing",
        readingOrder = if (rightToLeft) "rtl" else "ltr",
        panels = beats,
    )
}

fun storyboardPagePrompt(source: String, rightToLeft: Boolean): String = """
Create one editable ${if (rightToLeft) "manga" else "comic"} storyboard page from this scene or story beat.
Return ONLY one JSON object matching this schema:
{"title":"short page title","templateId":"classic-6|pair-wide-split|establishing|tall-wide-full|vertical-strip|splash","readingOrder":"rtl|ltr","panels":[{"description":"visual direction","caption":"narration or empty string","dialogue":"spoken words or empty string","speaker":"speaker or empty string","mediaQuery":"keywords for existing artwork"}]}
Use 1 to 6 panels. Keep dialogue concise. Do not include markdown or prose outside the JSON object.
Reading order is ${if (rightToLeft) "rtl" else "ltr"}.
Scene or beat:
${source.trim().ifBlank { "Create a strong opening moment for the story." }}
""".trimIndent()
