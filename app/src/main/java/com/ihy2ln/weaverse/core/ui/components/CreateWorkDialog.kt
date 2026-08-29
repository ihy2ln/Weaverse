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
    val settingId: String = "",
    val narrativePov: String = "",
    val campaignRoleId: String = "",
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

data class CampaignSettingTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

data class CampaignPerspectiveTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

val CampaignPerspectiveTemplates = listOf(
    CampaignPerspectiveTemplate(
        "third-multiple",
        "Third-person multiple",
        "Use third-person multiple perspective. Follow whichever player character is most relevant to the current beat, clearly anchor every perspective change, and never reveal knowledge that the viewpoint character does not possess.",
    ),
    CampaignPerspectiveTemplate(
        "third-limited",
        "Third-person limited",
        "Use close third-person limited perspective centered on the active player character. Describe only what that character can perceive, infer, remember, or feel unless the scene explicitly changes viewpoint.",
    ),
    CampaignPerspectiveTemplate(
        "first-multiple",
        "First-person rotating",
        "Use first-person perspective and rotate among selected player characters only at clear scene or section boundaries. Identify the new viewpoint immediately and keep each character's voice and knowledge distinct.",
    ),
    CampaignPerspectiveTemplate(
        "second-person",
        "Second person",
        "Address the active player character as you. When several protagonists are present, use names to disambiguate actions and perceptions while keeping the narration player-facing.",
    ),
    CampaignPerspectiveTemplate(
        "omniscient",
        "Omniscient ensemble",
        "Use an omniscient ensemble viewpoint that can move between characters and locations, but preserve suspense by withholding secrets when revealing them would undermine play or player agency.",
    ),
    CampaignPerspectiveTemplate(
        "cinematic",
        "Cinematic",
        "Use a cinematic external viewpoint focused on visible action, environment, dialogue, and staging. Avoid asserting private thoughts unless expressed through behavior or speech.",
    ),
)

val CampaignSettingTemplates = listOf(
    CampaignSettingTemplate(
        "high-fantasy",
        "High fantasy",
        "Run a high-fantasy world of ancient kingdoms, dungeon delves, dangerous wilderness, monsters, magic, gods, factions, treasure, and heroic quests. Present locations as explorable spaces and seed meaningful choices, secrets, and consequences.",
    ),
    CampaignSettingTemplate(
        "dark-fantasy",
        "Dark fantasy",
        "Run a morally difficult fantasy world shaped by curses, corruption, scarce safety, frightening magic, compromised factions, and costly victories. Keep danger serious without removing player agency or fair warning.",
    ),
    CampaignSettingTemplate(
        "sword-sorcery",
        "Sword & sorcery",
        "Run a pulpy sword-and-sorcery world of decadent city-states, dangerous ruins, personal ambition, strange cults, mercenary work, and rare unsettling magic. Favor immediate stakes and adventurous momentum over world-saving destiny.",
    ),
    CampaignSettingTemplate(
        "gothic-horror",
        "Gothic horror",
        "Run a gothic-horror setting of isolated communities, decaying estates, family secrets, supernatural dread, investigation, and temptation. Build tension through clues and atmosphere while keeping threats actionable at the table.",
    ),
    CampaignSettingTemplate(
        "urban-fantasy",
        "Urban fantasy",
        "Run a modern city where supernatural communities, hidden magic, institutions, neighborhoods, and mundane life collide. Treat information, favors, territory, and relationships as important adventure resources.",
    ),
    CampaignSettingTemplate(
        "science-fantasy",
        "Science fantasy",
        "Run a science-fantasy world where advanced relics, strange planets or ruins, sorcery, nonhuman cultures, and lost civilizations coexist. Keep technology and magic wondrous but internally consistent.",
    ),
    CampaignSettingTemplate(
        "custom",
        "Custom setting",
        "Use the player's setting details as the authoritative world guide. Infer only what is needed for play, remain internally consistent, and ask or offer choices instead of overwriting established lore.",
    ),
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
    var settingId by remember { mutableStateOf("high-fantasy") }
    var settingMenuOpen by remember { mutableStateOf(false) }
    var narrativePovId by remember { mutableStateOf("third-multiple") }
    var perspectiveMenuOpen by remember { mutableStateOf(false) }
    var campaignRoleId by remember { mutableStateOf("player") }
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
                    Text("Setting template", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        InkOutlinedButton(
                            label = CampaignSettingTemplates.first { it.id == settingId }.label + " ▾",
                            onClick = { settingMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = settingMenuOpen,
                            onDismissRequest = { settingMenuOpen = false },
                        ) {
                            CampaignSettingTemplates.forEach { template ->
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
                                        settingId = template.id
                                        settingMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Setting details") },
                        placeholder = { Text("Kingdom, era, locations, factions, tone…") },
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
                        val selectedCharacters = characterOptions.filter { it.id in selectedCharacterIds }
                        Text(
                            if (selectedCharacters.isEmpty()) {
                                "No character selected — the AI DM will help you create one when play begins."
                            } else {
                                selectedCharacters.joinToString(" · ") { "${it.name} (${it.source})" }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 2,
                        )
                    }
                    Text("Play as", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("player", "Character(s)"),
                            SegmentedOption("dm", "Dungeon Master"),
                        ),
                        selectedId = campaignRoleId,
                        onSelect = { campaignRoleId = it },
                        compact = true,
                    )
                    Text(
                        if (campaignRoleId == "dm") {
                            "You run the world and rulings; the AI plays the selected party."
                        } else {
                            "You play the selected character(s); the AI runs the world and its cast."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                    )
                    Text("Point of view", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        InkOutlinedButton(
                            label = CampaignPerspectiveTemplates.first { it.id == narrativePovId }.label + " ▾",
                            onClick = { perspectiveMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = perspectiveMenuOpen,
                            onDismissRequest = { perspectiveMenuOpen = false },
                        ) {
                            CampaignPerspectiveTemplates.forEach { template ->
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
                                        narrativePovId = template.id
                                        perspectiveMenuOpen = false
                                    },
                                )
                            }
                        }
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
                        compact = true,
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
                        genre = if (isCampaign) {
                            val settingLabel = CampaignSettingTemplates.first { it.id == settingId }.label
                            listOf(settingLabel, genre.trim()).filter { it.isNotBlank() }.joinToString(" — ")
                        } else genre.trim(),
                        pov = if (isCampaign) {
                            characterOptions.filter { it.id in selectedCharacterIds }.joinToString(", ") { it.name }
                        } else pov.trim(),
                        tense = tense.trim().ifBlank { if (isCampaign) "Past tense" else "" },
                        styleGuide = if (isCampaign) {
                            listOf(
                                "Setting guidance: ${CampaignSettingTemplates.first { it.id == settingId }.directive}",
                                "Rules guidance: ${CampaignRulesetTemplates.first { it.id == rulesetId }.directive}",
                                "Perspective guidance: ${CampaignPerspectiveTemplates.first { it.id == narrativePovId }.directive}",
                                if (campaignRoleId == "dm") {
                                    "User role guidance: The user is the Dungeon Master and has authority over the world, scenes, NPCs, and rulings. The AI controls the selected player-character party and must respond with their decisions, actions, and dialogue without overriding the user's world narration."
                                } else {
                                    "User role guidance: The user controls the selected player character(s). The AI is the Dungeon Master and controls the world, NPCs, opposition, and consequences without choosing the player's actions."
                                },
                                styleGuide.trim().takeIf { it.isNotBlank() }?.let { "House rules: $it" }.orEmpty(),
                            ).filter { it.isNotBlank() }.joinToString("\n\n")
                        } else styleGuide.trim(),
                        mainCharacters = characterOptions.filter { it.id in selectedCharacterIds },
                        rulesetId = if (isCampaign) rulesetId else "",
                        settingId = if (isCampaign) settingId else "",
                        narrativePov = if (isCampaign) {
                            CampaignPerspectiveTemplates.first { it.id == narrativePovId }.label
                        } else "",
                        campaignRoleId = if (isCampaign) campaignRoleId else "",
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
