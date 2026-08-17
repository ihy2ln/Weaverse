package com.ihy2ln.weaverse.feature.novel.write.editor

/** The output-length selector's unit (spec §6: "Words / Sentences / Paragraphs"). */
enum class SceneBeatOutputUnit(val label: String) {
    Words("words"),
    Sentences("sentences"),
    Paragraphs("paragraphs"),
}

/** One auto-detected codex entry shown as a chip in the `+ Context` strip — [id]/[aliases] also
 * feed [com.ihy2ln.weaverse.core.text.MentionScanner] so the same entries become tappable links
 * inline in the generated result text, not just as chips. */
data class ContextChipInfo(val id: String, val name: String, val aliases: List<String> = emptyList(), val colorHex: String?)

/**
 * Ephemeral (not persisted — only [com.ihy2ln.weaverse.core.text.SceneBeatBlock.prompt] and
 * [com.ihy2ln.weaverse.core.text.SceneBeatBlock.collapsed] survive to the document) generation
 * state for one open beat window, keyed by block id in [WriteViewModel] so more than one beat can
 * be mid-generation at once without stepping on each other.
 */
data class SceneBeatGenerationState(
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val resultText: String? = null,
    val errorMessage: String? = null,
    val contextEntries: List<ContextChipInfo> = emptyList(),
    val contextTokenCount: Int = 0,
)
