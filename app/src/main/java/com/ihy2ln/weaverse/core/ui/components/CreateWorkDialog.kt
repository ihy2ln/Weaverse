package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** What the user typed when creating a novel, campaign or storyboard. */
data class NewWorkDetails(
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
    val mainCharacters: List<WorkCharacterOption> = emptyList(),
    val rulesetId: String = "",
)

data class WorkCharacterOption(
    val id: String,
    val name: String,
    val source: String,
)

data class CampaignRulesetTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

val CampaignRulesetTemplates = listOf(
    CampaignRulesetTemplate(
        "dnd-5e",
        "D&D 5e",
        "Use D&D 5e conventions: d20 ability checks, proficiency, advantage/disadvantage, armor class, hit points, spell slots, conditions, and death saves. Ask for rolls when outcomes are uncertain and state the DC before resolving when appropriate.",
    ),
    CampaignRulesetTemplate(
        "pathfinder-2e",
        "Pathfinder 2e",
        "Use Pathfinder 2e conventions: d20 checks with level-based proficiency, four degrees of success, the three-action economy, reactions, conditions, armor class, saving throws, and encounter-appropriate DCs.",
    ),
    CampaignRulesetTemplate(
        "dnd-3-5",
        "D&D 3.5e",
        "Use D&D 3.5e conventions: d20 checks, skill ranks, base attack bonus, fortitude/reflex/will saves, armor class categories, attacks of opportunity, prepared or spontaneous spellcasting, and tactical modifiers.",
    ),
    CampaignRulesetTemplate(
        "osr",
        "OSR / B/X",
        "Use old-school fantasy conventions: rulings over exhaustive rules, reaction and morale checks, resource pressure, dangerous combat, exploration turns, meaningful encumbrance, and saving throws when danger cannot be avoided.",
    ),
    CampaignRulesetTemplate(
        "pbta",
        "Powered by the Apocalypse",
        "Use Powered by the Apocalypse conventions: fiction-first moves, 2d6 results where 10+ succeeds, 7–9 succeeds with a cost, and 6- invites a game-master move. Never call for a roll unless a move is triggered by the fiction.",
    ),
    CampaignRulesetTemplate(
        "fate-core",
        "Fate Core",
        "Use Fate Core conventions: aspects, invokes and compels, 4dF checks, overcome/create advantage/attack/defend actions, stress, consequences, and success at a cost.",
    ),
    CampaignRulesetTemplate("custom", "Custom / systemless", "Use only the custom house rules supplied by the player and resolve uncertain actions consistently."),
)

/**
 * How a workspace words its "new work" dialog. The fields are the same because
 * they all end up on the same manuscript record — only the language differs.
 */
data class CreateWorkVocabulary(
    val what: String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val genreLabel: String,
    val povLabel: String,
    val styleLabel: String,
    val styleHint: String,
    val storyboardSpecific: Boolean = false,
) {
    companion object {
        val Novel = CreateWorkVocabulary(
            what = "novel",
            titleLabel = "Title",
            titlePlaceholder = "Untitled Book",
            genreLabel = "Genre",
            povLabel = "Point of view",
            styleLabel = "Style guide",
            styleHint = "Voice, pacing, anything the AI should keep to.",
        )
        val Campaign = CreateWorkVocabulary(
            what = "campaign",
            titleLabel = "Campaign title",
            titlePlaceholder = "Untitled Campaign",
            genreLabel = "Setting",
            povLabel = "Main character(s)",
            styleLabel = "House rules",
            styleHint = "Choose a base rules system, then add campaign-specific rulings, tone, and boundaries.",
        )
        val Storyboard = CreateWorkVocabulary(
            what = "storyboard",
            titleLabel = "Series title",
            titlePlaceholder = "Untitled Manga",
            genreLabel = "Visual genre",
            povLabel = "Reading direction",
            styleLabel = "Art direction",
            styleHint = "Linework, palette, character design, lettering, and panel rhythm.",
            storyboardSpecific = true,
        )
    }
}

/**
 * Details popup shared by every workspace that can start a new work. Only the
 * title is required; everything else can be filled in later from the editor.
 */
@Composable
fun CreateWorkDialog(
    vocabulary: CreateWorkVocabulary,
    characterOptions: List<WorkCharacterOption> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (NewWorkDetails) -> Unit,
) {
    val tokens = inkTokens()
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var pov by remember { mutableStateOf(if (vocabulary.storyboardSpecific) "Right to left" else "") }
    var tense by remember {
        mutableStateOf(if (vocabulary.storyboardSpecific) "Manga" else "Past tense")
    }
    var styleGuide by remember { mutableStateOf("") }
    var selectedCharacterIds by remember { mutableStateOf(setOf<String>()) }
    var rulesetId by remember { mutableStateOf("dnd-5e") }
    var rulesetMenuOpen by remember { mutableStateOf(false) }
    val isCampaign = vocabulary == CreateWorkVocabulary.Campaign

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${vocabulary.what}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(vocabulary.titleLabel) },
                    placeholder = { Text(vocabulary.titlePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vocabulary.storyboardSpecific) {
                    Text(
                        "Set up the visual language of the series. You can change layouts page by page later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                    Text("Format", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Manga", "Manga"),
                            SegmentedOption("Comic", "Comic"),
                            SegmentedOption("Webtoon", "Webtoon"),
                        ),
                        selectedId = tense,
                        onSelect = { tense = it },
                    )
                    Text("Reading direction", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Right to left", "Right → left"),
                            SegmentedOption("Left to right", "Left → right"),
                            SegmentedOption("Vertical", "Vertical"),
                        ),
                        selectedId = pov,
                        onSelect = { pov = it },
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(vocabulary.genreLabel) },
                        placeholder = { Text("Shōnen action, noir, romance, superhero…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (isCampaign) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(vocabulary.genreLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Main character(s)", style = MaterialTheme.typography.labelMedium)
                    if (characterOptions.isEmpty()) {
                        Text(
                            "Add a persona, roster member, or Characters Codex entry first—or continue without one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                        ) {
                            characterOptions.forEach { option ->
                                val selected = option.id in selectedCharacterIds
                                InkChip(
                                    label = if (selected) "✓ ${option.name}" else option.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    selected = selected,
                                    onClick = {
                                        selectedCharacterIds = if (selected) {
                                            selectedCharacterIds - option.id
                                        } else {
                                            selectedCharacterIds + option.id
                                        }
                                    },
                                )
                            }
                        }
                        Text(
                            characterOptions.joinToString(" · ") { "${it.name} (${it.source})" },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 2,
                        )
                    }
                    Text("Tense", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Past tense", "Past"),
                            SegmentedOption("Present tense", "Present"),
                            SegmentedOption("Future tense", "Future"),
                        ),
                        selectedId = tense.ifBlank { "Past tense" },
                        onSelect = { tense = it },
                    )
                    Text("Rules system", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        InkOutlinedButton(
                            label = CampaignRulesetTemplates.first { it.id == rulesetId }.label + " ▾",
                            onClick = { rulesetMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = rulesetMenuOpen,
                            onDismissRequest = { rulesetMenuOpen = false },
                        ) {
                            CampaignRulesetTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.label)
                                            Text(
                                                template.directive,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.secondaryText,
                                                maxLines = 3,
                                            )
                                        }
                                    },
                                    onClick = {
                                        rulesetId = template.id
                                        rulesetMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = pov,
                        onValueChange = { pov = it },
                        label = { Text(vocabulary.povLabel) },
                        placeholder = { Text("First person, third limited…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Tense", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Past tense", "Past"),
                            SegmentedOption("Present tense", "Present"),
                            SegmentedOption("Future tense", "Future"),
                        ),
                        selectedId = tense.ifBlank { "Past tense" },
                        onSelect = { tense = it },
                    )
                }
                OutlinedTextField(
                    value = styleGuide,
                    onValueChange = { styleGuide = it },
                    label = { Text(if (isCampaign) "Additional house rules" else vocabulary.styleLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    vocabulary.styleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
                Text(
                    if (vocabulary.storyboardSpecific) {
                        "Only the series title is required. Main art can be set later and appears in Window."
                    } else {
                        "Only the title is needed now — the rest can be filled in later, " +
                            "and a cover is set from the editor."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(
                    NewWorkDetails(
                        title = title.trim().ifBlank { vocabulary.titlePlaceholder },
                        genre = genre.trim(),
                        pov = if (isCampaign) {
                            characterOptions.filter { it.id in selectedCharacterIds }.joinToString(", ") { it.name }
                        } else pov.trim(),
                        tense = tense.trim().ifBlank { if (isCampaign) "Past tense" else "" },
                        styleGuide = if (isCampaign) {
                            listOf(
                                CampaignRulesetTemplates.first { it.id == rulesetId }.directive,
                                styleGuide.trim(),
                            ).filter { it.isNotBlank() }.joinToString("\n\nHouse rules: ")
                        } else styleGuide.trim(),
                        mainCharacters = characterOptions.filter { it.id in selectedCharacterIds },
                        rulesetId = if (isCampaign) rulesetId else "",
                    ),
                )
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
