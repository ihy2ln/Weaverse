package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.components.CampaignPerspectiveTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignRulesetTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignHouseRuleTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplate
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplate
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignPresetBrowserDialog
import com.ihy2ln.weaverse.core.ui.components.CampaignPresetEditorDialog
import com.ihy2ln.weaverse.core.ui.components.campaignSettingBrowserItems
import com.ihy2ln.weaverse.core.ui.components.campaignSettingDetailBrowserItems
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.NewWorkDetails
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset

/**
 * The campaign setup sheet from the new-campaign menu, reopened for an existing
 * adventure: character perspectives can be added/removed and setting, play
 * role, point of view, tense, and rules system changed at any time.
 */
@Composable
fun CampaignOptionsDialog(
    initial: NewWorkDetails,
    characterOptions: List<WorkCharacterOption>,
    onDismiss: () -> Unit,
    onApply: (NewWorkDetails) -> Unit,
    onRestart: (() -> Unit)? = null,
    customSettings: List<CampaignSettingTemplate> = emptyList(),
    customSettingDetails: List<CampaignSettingDetailTemplate> = emptyList(),
    favoriteSettingIds: Set<String> = emptySet(),
    favoriteSettingDetailIds: Set<String> = emptySet(),
    onToggleSettingFavorite: (String) -> Unit = {},
    onToggleSettingDetailFavorite: (String) -> Unit = {},
    onAddSetting: ((name: String, section: String, theme: String, guidance: String) -> Unit)? = null,
    onRemoveSetting: ((id: String) -> Unit)? = null,
    onAddSettingDetail: ((name: String, section: String, theme: String, guidance: String) -> Unit)? = null,
    onRemoveSettingDetail: ((id: String) -> Unit)? = null,
) {
    val tokens = inkTokens()
    val effectiveSettings = CampaignSettingTemplates + customSettings
    val effectiveSettingDetails = CampaignSettingDetailTemplates + customSettingDetails
    var settingId by remember { mutableStateOf(initial.settingId.ifBlank { "high-fantasy" }) }
    var settingMenuOpen by remember { mutableStateOf(false) }
    var genre by remember { mutableStateOf(initial.genre) }
    var settingDetailId by remember { mutableStateOf(initial.settingDetailId.ifBlank { "custom" }) }
    var settingDetailMenuOpen by remember { mutableStateOf(false) }
    var selectedCharacterIds by remember {
        mutableStateOf(initial.mainCharacters.map { it.id }.toSet())
    }
    var campaignRoleId by remember { mutableStateOf(initial.campaignRoleId.ifBlank { "player" }) }
    var narrativePovId by remember {
        mutableStateOf(
            CampaignPerspectiveTemplates.firstOrNull { it.label == initial.narrativePov }?.id
                ?: "third-multiple",
        )
    }
    var perspectiveMenuOpen by remember { mutableStateOf(false) }
    var tense by remember { mutableStateOf(initial.tense.ifBlank { "Past tense" }) }
    // RPG campaigns use these as persistent play modes. Legacy campaign ruleset
    // ids are intentionally mapped to the safe d20 default during migration.
    var gameModeId by remember { mutableStateOf(initial.gameModeId.ifBlank { RpgCombatRuleset.fromId(initial.rulesetId).id }) }
    var rulesetId by remember { mutableStateOf(initial.rulesetId.takeUnless { it.startsWith("rpg-") }.orEmpty().ifBlank { "dnd-5e" }) }
    var rulesetMenuOpen by remember { mutableStateOf(false) }
    var gameModeMenuOpen by remember { mutableStateOf(false) }
    var styleGuide by remember { mutableStateOf(initial.styleGuide) }
    var houseRuleId by remember { mutableStateOf(initial.houseRuleId.ifBlank { "custom" }) }
    var houseRuleMenuOpen by remember { mutableStateOf(false) }
    var showAddSetting by remember { mutableStateOf(false) }
    var showAddSettingDetail by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Campaign options") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                Text("Setting template", style = MaterialTheme.typography.labelMedium)
                InkOutlinedButton(
                    label = (effectiveSettings.firstOrNull { it.id == settingId }?.label ?: "Custom setting") + " ▸",
                    onClick = { settingMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Setting Details preset", style = MaterialTheme.typography.labelMedium)
                InkOutlinedButton(
                    label = (effectiveSettingDetails.firstOrNull { it.id == settingDetailId }?.label ?: "Choose details") + " ▸",
                    onClick = { settingDetailMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        "Add a persona, roster member, or Characters Codex entry first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                } else {
                    var characterQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = characterQuery,
                        onValueChange = { characterQuery = it },
                        label = { Text("Search characters") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val visibleCharacters = if (characterQuery.isBlank()) {
                        characterOptions
                    } else {
                        characterOptions.filter { it.name.contains(characterQuery, ignoreCase = true) }
                    }
                    if (visibleCharacters.isEmpty()) {
                        Text(
                            "No characters match \"${characterQuery}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                        )
                    } else {
                        // Two visible rows of chips; scrolls for longer casts.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                                visibleCharacters.chunked(3).forEach { rowCharacters ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                                    ) {
                                        rowCharacters.forEach { option ->
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
                                }
                            }
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
                Text("Mode", style = MaterialTheme.typography.labelMedium)
                Text(
                    "This campaign's default battle mode. You can override a single encounter without changing this choice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    InkOutlinedButton(
                        label = RpgCombatRuleset.fromId(gameModeId).label + " ▾",
                        onClick = { gameModeMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = gameModeMenuOpen,
                        onDismissRequest = { gameModeMenuOpen = false },
                    ) {
                        RpgCombatRuleset.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.label)
                                        Text(
                                            when (mode) {
                                                RpgCombatRuleset.CardBattle -> "Play with AP/EP tactical cards, visible intents, and statuses."
                                                RpgCombatRuleset.DndD20 -> "Use the character sheet, deterministic d20 checks, AC, HP, and conditions."
                                                RpgCombatRuleset.TextReactions -> "Describe actions in text; risky actions show a check before AI narration."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.secondaryText,
                                            maxLines = 3,
                                        )
                                    }
                                },
                                onClick = {
                                    gameModeId = mode.id
                                    gameModeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Text("Rule system", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    InkOutlinedButton(
                        label = (CampaignRulesetTemplates.firstOrNull { it.id == rulesetId } ?: CampaignRulesetTemplates.first()).label + " ▾",
                        onClick = { rulesetMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = rulesetMenuOpen, onDismissRequest = { rulesetMenuOpen = false }) {
                        CampaignRulesetTemplates.forEach { template ->
                            DropdownMenuItem(
                                text = { Column { Text(template.label); Text(template.directive, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText, maxLines = 3) } },
                                onClick = { rulesetId = template.id; rulesetMenuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = styleGuide,
                    onValueChange = { styleGuide = it },
                    label = { Text("Additional house rules") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Additional House Rules preset", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    InkOutlinedButton(label = CampaignHouseRuleTemplates.first { it.id == houseRuleId }.label + " ▾", onClick = { houseRuleMenuOpen = true }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = houseRuleMenuOpen, onDismissRequest = { houseRuleMenuOpen = false }) {
                        CampaignHouseRuleTemplates.forEach { preset ->
                            DropdownMenuItem(text = { Column { Text(preset.label); Text(preset.directive, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText, maxLines = 3) } }, onClick = { houseRuleId = preset.id; houseRuleMenuOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    NewWorkDetails(
                        title = initial.title,
                        genre = listOf(
                            (effectiveSettings.firstOrNull { it.id == settingId }?.label ?: "Custom setting"),
                            genre.trim(),
                        ).filter { it.isNotBlank() }.joinToString(" — "),
                        pov = characterOptions
                            .filter { it.id in selectedCharacterIds }
                            .joinToString(", ") { it.name },
                        tense = tense.trim().ifBlank { "Past tense" },
                        styleGuide = listOf(
                            "Setting guidance: " +
                                (effectiveSettings.firstOrNull { it.id == settingId }?.directive ?: ""),
                            "Setting details preset: ${effectiveSettingDetails.firstOrNull { it.id == settingDetailId }?.details.orEmpty()}",
                            "Game mode: ${RpgCombatRuleset.fromId(gameModeId).label}. " +
                                "Resolve encounters only through the RPG combat mode selected above.",
                            "Rule system guidance: " + (CampaignRulesetTemplates.firstOrNull { it.id == rulesetId } ?: CampaignRulesetTemplates.first()).directive,
                            "House rules preset: ${CampaignHouseRuleTemplates.first { it.id == houseRuleId }.directive}",
                            "Perspective guidance: " +
                                CampaignPerspectiveTemplates.first { it.id == narrativePovId }.directive,
                            if (campaignRoleId == "dm") {
                                "User role guidance: The user is the Dungeon Master and has authority over the world, scenes, NPCs, and rulings. The AI controls the selected player-character party and must respond with their decisions, actions, and dialogue without overriding the user's world narration."
                            } else {
                                "User role guidance: The user controls the selected player character(s). The AI is the Dungeon Master and controls the world, NPCs, opposition, and consequences without choosing the player's actions."
                            },
                            styleGuide.trim().takeIf { it.isNotBlank() }?.let { "House rules: $it" }.orEmpty(),
                        ).filter { it.isNotBlank() }.joinToString("\n\n"),
                        mainCharacters = characterOptions.filter { it.id in selectedCharacterIds },
         rulesetId = rulesetId,
         gameModeId = gameModeId,
                        settingId = settingId,
                        settingDetailId = settingDetailId,
                        houseRuleId = houseRuleId,
                        narrativePov = CampaignPerspectiveTemplates.first { it.id == narrativePovId }.label,
                        campaignRoleId = campaignRoleId,
                    ),
                )
            }) { Text("Save setup") }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRestart != null) {
                    TextButton(onClick = onRestart) {
                        Text("Restart adventure", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (settingMenuOpen) {
        CampaignPresetBrowserDialog(
            title = "Setting templates",
            items = campaignSettingBrowserItems(customSettings),
            selectedId = settingId,
            favoriteIds = favoriteSettingIds,
            onToggleFavorite = onToggleSettingFavorite,
            onSelect = { preset ->
                settingId = preset.id
                settingMenuOpen = false
            },
            onDismiss = { settingMenuOpen = false },
            onRemove = onRemoveSetting,
            onAdd = onAddSetting?.let {
                {
                    settingMenuOpen = false
                    showAddSetting = true
                }
            },
            addLabel = "Add setting template",
        )
    }
    if (settingDetailMenuOpen) {
        CampaignPresetBrowserDialog(
            title = "Setting details",
            items = campaignSettingDetailBrowserItems(customSettingDetails),
            selectedId = settingDetailId,
            favoriteIds = favoriteSettingDetailIds,
            onToggleFavorite = onToggleSettingDetailFavorite,
            onSelect = { preset ->
                settingDetailId = preset.id
                genre = preset.description
                settingDetailMenuOpen = false
            },
            onDismiss = { settingDetailMenuOpen = false },
            onRemove = onRemoveSettingDetail,
            onAdd = onAddSettingDetail?.let {
                {
                    settingDetailMenuOpen = false
                    showAddSettingDetail = true
                }
            },
            addLabel = "Add details preset",
        )
    }
    if (showAddSetting && onAddSetting != null) {
        CampaignPresetEditorDialog(
            title = "Add Setting Template",
            guidanceLabel = "World guidance for the AI",
            defaultSection = "Custom",
            defaultTheme = "Saved templates",
            onDismiss = { showAddSetting = false },
            onSave = onAddSetting,
        )
    }
    if (showAddSettingDetail && onAddSettingDetail != null) {
        CampaignPresetEditorDialog(
            title = "Add Setting Details Preset",
            guidanceLabel = "Setting details for the AI",
            defaultSection = "Custom",
            defaultTheme = "Saved presets",
            onDismiss = { showAddSettingDetail = false },
            onSave = onAddSettingDetail,
        )
    }
}
