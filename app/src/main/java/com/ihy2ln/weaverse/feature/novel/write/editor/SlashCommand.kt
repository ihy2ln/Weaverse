package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/** One entry in the `/` command palette (spec §6), grouped into the reference's three sections. */
enum class SlashCommandGroup(val label: String) {
    Ai("AI"),
    Codex("Codex"),
    Formatting("Formatting"),
}

/**
 * Whether a command runs immediately against the block it was invoked from, or needs a real text
 * *selection* to operate on meaningfully (Rewrite Selection, Expand, Shorten, Describe, Dialogue
 * Pass, Text colour, Highlight, New Codex Entry from Selection). No selection-tracking model
 * exists yet — `/` fires on a whole empty block, not an arbitrary caret/selection range (see
 * `BlockEditor`'s own documented focus-tracking scope cut) — so [NeedsSelection] commands are
 * listed (the palette's grouping/search must match the reference) but not wired to an action
 * yet; tapping one shows a short explanation instead. Rev02-08 (press-and-hold + selection) is
 * where a real selection model lands, at which point these become functional.
 */
enum class SlashCommandReadiness { Ready, NeedsSelection }

data class SlashCommand(
    val id: String,
    val group: SlashCommandGroup,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val readiness: SlashCommandReadiness = SlashCommandReadiness.Ready,
)

/** The full command list (spec §6) and the search/filter over it. Roleplay's own `/Narrate`,
 * `/Impersonate`, `/OOC note`, `/Insert character`, `/Scene change` group isn't included here —
 * roleplay's chat input is a flat `OutlinedTextField`, not this block editor, and has no
 * `Document`/`Block` to insert into; it needs its own host, tracked as a follow-up. */
object SlashCommands {
    const val SCENE_BEAT = "scene_beat"
    const val CONTINUE_WRITING = "continue_writing"
    const val REWRITE_SELECTION = "rewrite_selection"
    const val EXPAND = "expand"
    const val SHORTEN = "shorten"
    const val DESCRIBE = "describe"
    const val DIALOGUE_PASS = "dialogue_pass"

    const val CODEX_PROGRESSION = "codex_progression"
    const val NEW_CODEX_ENTRY_FROM_SELECTION = "new_codex_entry_from_selection"
    const val INSERT_CODEX_REFERENCE = "insert_codex_reference"

    const val HEADING_1 = "heading_1"
    const val HEADING_2 = "heading_2"
    const val HEADING_3 = "heading_3"
    const val QUOTE = "quote"
    const val BULLETED_LIST = "bulleted_list"
    const val NUMBERED_LIST = "numbered_list"
    const val SCENE_BREAK = "scene_break"
    const val TEXT_COLOUR = "text_colour"
    const val HIGHLIGHT = "highlight"
    const val ALIGN_LEFT = "align_left"
    const val ALIGN_CENTER = "align_center"
    const val ALIGN_RIGHT = "align_right"
    const val INSERT_IMAGE = "insert_image"
    const val INSERT_VIDEO = "insert_video"

    /** Commands whose window this ID drives are also the ones that create a [com.ihy2ln.weaverse.core.text.SceneBeatBlock]. */
    val aiGenerationCommands: Set<String> = setOf(SCENE_BEAT, CONTINUE_WRITING)

    val all: List<SlashCommand> = listOf(
        SlashCommand(SCENE_BEAT, SlashCommandGroup.Ai, "Scene Beat", "A pivotal moment where something important changes, driving the narrative forward.", Icons.Filled.Bolt),
        SlashCommand(CONTINUE_WRITING, SlashCommandGroup.Ai, "Continue Writing", "Creates a new scene beat to continue writing.", Icons.Filled.AutoAwesome),
        SlashCommand(REWRITE_SELECTION, SlashCommandGroup.Ai, "Rewrite Selection", "Rewrites the selected passage.", Icons.Filled.AutoAwesome, SlashCommandReadiness.NeedsSelection),
        SlashCommand(EXPAND, SlashCommandGroup.Ai, "Expand", "Adds detail to the selected passage.", Icons.Filled.OpenInFull, SlashCommandReadiness.NeedsSelection),
        SlashCommand(SHORTEN, SlashCommandGroup.Ai, "Shorten", "Tightens the selected passage.", Icons.Filled.Compress, SlashCommandReadiness.NeedsSelection),
        SlashCommand(DESCRIBE, SlashCommandGroup.Ai, "Describe", "Writes a descriptive passage from the selection.", Icons.Filled.Description, SlashCommandReadiness.NeedsSelection),
        SlashCommand(DIALOGUE_PASS, SlashCommandGroup.Ai, "Dialogue Pass", "Sharpens dialogue in the selected passage.", Icons.Filled.RecordVoiceOver, SlashCommandReadiness.NeedsSelection),

        SlashCommand(CODEX_PROGRESSION, SlashCommandGroup.Codex, "Codex Progression", "Add additional information about the world, characters, or events to track your story arcs.", Icons.Filled.MenuBook, SlashCommandReadiness.NeedsSelection),
        SlashCommand(NEW_CODEX_ENTRY_FROM_SELECTION, SlashCommandGroup.Codex, "New Codex Entry from Selection", "Creates a codex entry from the selected text.", Icons.Filled.MenuBook, SlashCommandReadiness.NeedsSelection),
        SlashCommand(INSERT_CODEX_REFERENCE, SlashCommandGroup.Codex, "Insert Codex Reference", "Mentions an existing codex entry here.", Icons.Filled.MenuBook),

        SlashCommand(HEADING_1, SlashCommandGroup.Formatting, "Heading 1", "Large section heading.", Icons.Filled.Title),
        SlashCommand(HEADING_2, SlashCommandGroup.Formatting, "Heading 2", "Medium section heading.", Icons.Filled.Title),
        SlashCommand(HEADING_3, SlashCommandGroup.Formatting, "Heading 3", "Small section heading.", Icons.Filled.Title),
        SlashCommand(QUOTE, SlashCommandGroup.Formatting, "Quote", "Block quote.", Icons.Filled.FormatQuote),
        SlashCommand(BULLETED_LIST, SlashCommandGroup.Formatting, "Bulleted List", "Unordered list item.", Icons.Filled.FormatListBulleted),
        SlashCommand(NUMBERED_LIST, SlashCommandGroup.Formatting, "Numbered List", "Ordered list item.", Icons.Filled.FormatListNumbered),
        SlashCommand(SCENE_BREAK, SlashCommandGroup.Formatting, "Scene Break", "A `***` divider.", Icons.Filled.HorizontalRule),
        SlashCommand(TEXT_COLOUR, SlashCommandGroup.Formatting, "Text Colour", "Colours the selected text.", Icons.Filled.Title, SlashCommandReadiness.NeedsSelection),
        SlashCommand(HIGHLIGHT, SlashCommandGroup.Formatting, "Highlight", "Highlights the selected text.", Icons.Filled.Title, SlashCommandReadiness.NeedsSelection),
        SlashCommand(ALIGN_LEFT, SlashCommandGroup.Formatting, "Align Left", "Left-aligns this block.", Icons.Filled.FormatAlignLeft),
        SlashCommand(ALIGN_CENTER, SlashCommandGroup.Formatting, "Align Center", "Center-aligns this block.", Icons.Filled.FormatAlignCenter),
        SlashCommand(ALIGN_RIGHT, SlashCommandGroup.Formatting, "Align Right", "Right-aligns this block.", Icons.Filled.FormatAlignRight),
        SlashCommand(INSERT_IMAGE, SlashCommandGroup.Formatting, "Image", "Inserts an image.", Icons.Filled.Image),
        SlashCommand(INSERT_VIDEO, SlashCommandGroup.Formatting, "Video", "Inserts a video.", Icons.Filled.Videocam),
    )

    /** Filters by label substring (case-insensitive), preserving group order — matches the
     * reference's "searchable list... filtered as the user keeps typing." */
    fun filter(query: String): List<SlashCommand> =
        if (query.isBlank()) all else all.filter { it.label.contains(query, ignoreCase = true) }

    /** Instruction template for the five selection-driven AI commands, reached from
     * [SelectionToolbar]'s "Ask AI" once rev02-08 gives the editor a real selection to act on —
     * `{{text}}` is the caller's job to substitute with the actual selected passage. */
    fun selectionInstructionTemplate(commandId: String): String? = when (commandId) {
        REWRITE_SELECTION -> "Rewrite the following passage, preserving its meaning and POV but improving prose quality:\n\n{{text}}"
        EXPAND -> "Expand the following passage with additional sensory detail and interiority, preserving its meaning:\n\n{{text}}"
        SHORTEN -> "Tighten the following passage, cutting redundant words while preserving its meaning:\n\n{{text}}"
        DESCRIBE -> "Write a vivid descriptive passage inspired by this text:\n\n{{text}}"
        DIALOGUE_PASS -> "Rewrite the following passage to sharpen and naturalize any dialogue, keeping narration intact:\n\n{{text}}"
        else -> null
    }
}
