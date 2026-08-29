package com.ihy2ln.weaverse.feature.roleplay.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import coil3.compose.AsyncImage
import java.io.File

private enum class SheetSection(val label: String, val symbol: String, val hint: String) {
    Abilities("Abilities & Skills", "◆", "Scores, modifiers, skills and proficiencies"),
    Combat("Combat & Defenses", "⚔", "Armor class, speed, initiative and conditions"),
    Health("Health & Death", "♥", "Hit points, hit dice and death saves"),
    Saves("Saving Throws", "⬟", "Class and ancestry saving-throw bonuses"),
    Attacks("Attacks & Actions", "➶", "Weapons, attacks, damage and actions"),
    Spells("Spells", "✦", "Spellcasting, prepared spells and slots"),
    Features("Features & Traits", "♜", "Class, ancestry, feats and special abilities"),
    Inventory("Inventory & Gear", "▣", "Equipped gear, carried items and currency"),
    Resources("Resources & Tools", "⌁", "Languages, tools and limited-use resources"),
    Bio("Character & Story", "●", "Description, personality and roleplay details"),
    Settings("Sheet Settings", "⚙", "Name, class, level and background"),
}

@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    viewModel: CharacterDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(characterId) { viewModel.load(characterId) }
    val state by viewModel.uiState.collectAsState()
    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setPortrait(uri)
    }
    val expanded = remember { mutableStateMapOf(SheetSection.Abilities to true) }
    val tokens = inkTokens()

    Column(Modifier.fillMaxSize().background(tokens.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(state.name.ifBlank { "Character" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        CharacterCombatHeader(
            state = state,
            adjustHp = viewModel::adjustHp,
            choosePortrait = { portraitPicker.launch(arrayOf("image/*")) },
            removePortrait = viewModel::removePortrait,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = InkSpacing.md),
        ) {
            Text(
                "CHARACTER SHEET",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.md, bottom = InkSpacing.xs),
            )
            SheetSection.entries.forEach { section ->
                ExpandableSheetSection(
                    section = section,
                    expanded = expanded[section] == true,
                    onToggle = { expanded[section] = expanded[section] != true },
                ) {
                    SheetSectionContent(section, state, viewModel)
                }
            }
            if (state.statusMessage.isNotBlank()) Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
            InkConfirmButton(
                onClick = viewModel::save,
                label = if (state.saved) "Saved" else "Save character sheet",
                contentDescription = "Save character sheet",
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.lg),
            )
            InkConfirmButton(
                onClick = viewModel::exportPngCard,
                label = "Export PNG card",
                contentDescription = "Export SillyTavern PNG character card",
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
            )
            InkConfirmButton(
                onClick = viewModel::exportJsonCard,
                label = "Export JSON card",
                contentDescription = "Export character card JSON",
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
            )
            Spacer(Modifier.height(AlwaysScrollEndPadding))
        }
    }
}

@Composable
private fun CharacterCombatHeader(
    state: CharacterDetailUiState,
    adjustHp: (Int) -> Unit,
    choosePortrait: () -> Unit,
    removePortrait: () -> Unit,
) {
    val s = state.sheet
    val tokens = inkTokens()
    Column(Modifier.fillMaxWidth().background(tokens.panel).padding(InkSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = choosePortrait),
                contentAlignment = Alignment.Center,
            ) {
                if (state.portraitPath.isNotBlank()) {
                    AsyncImage(
                        model = File(state.portraitPath),
                        contentDescription = "${state.name} portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CharacterAvatar(state.name, state.colorHex, size = 68.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniRoundButton(false) { adjustHp(-1) }
                    Surface(
                        Modifier.weight(1f).padding(horizontal = InkSpacing.xs),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f),
                        shape = RoundedCornerShape(10.dp),
                    ) { Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HIT POINTS", style = MaterialTheme.typography.labelSmall)
                        Text("${s.currentHp}/${s.maxHp}", fontWeight = FontWeight.Bold)
                    } }
                    MiniRoundButton(true) { adjustHp(1) }
                }
                Text(
                    listOf(s.characterClass.ifBlank { "Adventurer" }, "Level ${s.level}", s.background)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = InkSpacing.sm), horizontalArrangement = Arrangement.SpaceEvenly) {
            HeaderStat("AC", s.armorClass.toString())
            HeaderStat("PB", signed(s.proficiencyBonus))
            HeaderStat("SPEED", "${s.speedFeet}ft")
            HeaderStat("INIT", signed(s.initiative))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = choosePortrait) {
                Text(if (state.portraitPath.isBlank()) "Add portrait" else "Replace portrait")
            }
            if (state.portraitPath.isNotBlank()) TextButton(onClick = removePortrait) { Text("Remove") }
        }
    }
}

@Composable private fun MiniRoundButton(add: Boolean, onClick: () -> Unit) = IconButton(onClick, Modifier.size(40.dp)) {
    Icon(if (add) Icons.Rounded.Add else Icons.Rounded.Remove, if (add) "Increase" else "Decrease", tint = MaterialTheme.colorScheme.primary)
}

@Composable private fun HeaderStat(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = inkTokens().secondaryText)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ExpandableSheetSection(
    section: SheetSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = inkTokens()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = InkSpacing.sm),
        color = tokens.panel,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (expanded) 1.dp else .5.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = .7f) else tokens.hairline,
        ),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(InkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(section.symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal = InkSpacing.sm)) {
                    Text(section.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(section.hint, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (expanded) "Collapse ${section.label}" else "Expand ${section.label}",
                )
            }
            if (expanded) {
                HorizontalDivider(color = tokens.hairline)
                Column(Modifier.padding(start = InkSpacing.md, end = InkSpacing.md, bottom = InkSpacing.md)) { content() }
            }
        }
    }
}

@Composable
private fun SheetSectionContent(section: SheetSection, state: CharacterDetailUiState, vm: CharacterDetailViewModel) {
    val sheet = state.sheet
    when (section) {
        SheetSection.Abilities -> {
            AbilitiesPanel(sheet, vm::adjustAbility)
            NotesPanel("Skills & proficiencies", sheet.skillsAndProficiencies) {
                vm.onSheet(sheet.copy(skillsAndProficiencies = it))
            }
        }
        SheetSection.Combat -> CombatPanel(sheet, vm::onSheet)
        SheetSection.Health -> HealthPanel(sheet, vm::onSheet, vm::adjustHp)
        SheetSection.Saves -> NotesPanel("Saving throws", sheet.savingThrows) {
            vm.onSheet(sheet.copy(savingThrows = it))
        }
        SheetSection.Attacks -> {
            NotesPanel("Weapons & damage cantrips", sheet.weaponsAndDamageCantrips) {
                vm.onSheet(sheet.copy(weaponsAndDamageCantrips = it))
            }
            NotesPanel("Attacks & actions", sheet.attacksAndActions) {
                vm.onSheet(sheet.copy(attacksAndActions = it))
            }
        }
        SheetSection.Spells -> SpellPanel(sheet, vm::onSheet)
        SheetSection.Features -> FeaturePanel(sheet, vm::onSheet)
        SheetSection.Inventory -> {
            InventoryPanel(state.inventory, state.equipment, sheet.currency) {
                vm.onSheet(sheet.copy(currency = it))
            }
            NotesPanel("Equipment notes", sheet.equipmentNotes) {
                vm.onSheet(sheet.copy(equipmentNotes = it))
            }
            NotesPanel("Magic item attunement", sheet.magicItemAttunement) {
                vm.onSheet(sheet.copy(magicItemAttunement = it))
            }
        }
        SheetSection.Resources -> {
            NotesPanel("Resources & tools", sheet.resourcesAndTools) { vm.onSheet(sheet.copy(resourcesAndTools = it)) }
            NotesPanel("Languages", sheet.languages) { vm.onSheet(sheet.copy(languages = it)) }
            NotesPanel("Armor training", sheet.armorTraining) { vm.onSheet(sheet.copy(armorTraining = it)) }
            NotesPanel("Weapon proficiencies", sheet.weaponProficiencies) { vm.onSheet(sheet.copy(weaponProficiencies = it)) }
            NotesPanel("Tool proficiencies", sheet.toolProficiencies) { vm.onSheet(sheet.copy(toolProficiencies = it)) }
        }
        SheetSection.Bio -> BioPanel(state, vm)
        SheetSection.Settings -> SettingsPanel(state, vm)
    }
}

@Composable
private fun AbilitiesPanel(sheet: RpgCharacterSheet, adjust: (String, Int) -> Unit) {
    val abilities = listOf("Strength" to sheet.strength, "Dexterity" to sheet.dexterity, "Constitution" to sheet.constitution,
        "Intelligence" to sheet.intelligence, "Wisdom" to sheet.wisdom, "Charisma" to sheet.charisma)
    PanelTitle("ABILITIES")
    abilities.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = InkSpacing.sm), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            row.forEach { (name, score) -> AbilityCard(Modifier.weight(1f), name, score, { adjust(name, -1) }, { adjust(name, 1) }) }
        }
    }
}

@Composable
private fun AbilityCard(modifier: Modifier, name: String, score: Int, minus: () -> Unit, plus: () -> Unit) {
    Surface(modifier, color = inkTokens().panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f))) {
        Column(Modifier.padding(vertical = InkSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatModifier(score), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("−", Modifier.clickable(onClick = minus).padding(8.dp), color = MaterialTheme.colorScheme.primary)
                Text(score.toString(), fontWeight = FontWeight.Bold)
                Text("+", Modifier.clickable(onClick = plus).padding(8.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HealthPanel(s: RpgCharacterSheet, change: (RpgCharacterSheet) -> Unit, adjustHp: (Int) -> Unit) {
    PanelTitle("CURRENT HIT POINTS")
    Surface(Modifier.fillMaxWidth(), color = inkTokens().panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(InkSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.currentHp}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(" / ${s.maxHp}", style = MaterialTheme.typography.headlineMedium, color = inkTokens().secondaryText)
            Spacer(Modifier.weight(1f))
            Column { MiniRoundButton(true) { adjustHp(1) }; MiniRoundButton(false) { adjustHp(-1) } }
        }
    }
    PanelTitle("HP STATS")
    NumberEditor("Temporary", s.temporaryHp, 0) { change(s.copy(temporaryHp = it)) }
    NumberEditor("Maximum", s.maxHp, 1) { change(s.copy(maxHp = it, currentHp = s.currentHp.coerceAtMost(it))) }
    PanelTitle("HIT DICE & DEATH SAVES")
    NumberEditor("Hit dice (${s.hitDieType})", s.hitDiceCount, 0) { change(s.copy(hitDiceCount = it)) }
    NumberEditor("Death save successes", s.deathSaveSuccesses, 0, max = 3) { change(s.copy(deathSaveSuccesses = it)) }
    NumberEditor("Death save failures", s.deathSaveFailures, 0, max = 3) { change(s.copy(deathSaveFailures = it)) }
}

@Composable
private fun CombatPanel(s: RpgCharacterSheet, change: (RpgCharacterSheet) -> Unit) {
    PanelTitle("COMBAT")
    NumberEditor("Armor class", s.armorClass, 0) { change(s.copy(armorClass = it)) }
    NumberEditor("Proficiency bonus", s.proficiencyBonus, 0) { change(s.copy(proficiencyBonus = it)) }
    NumberEditor("Speed (feet)", s.speedFeet, 0) { change(s.copy(speedFeet = it)) }
    NumberEditor("Initiative", s.initiative, -20) { change(s.copy(initiative = it)) }
    NumberEditor("Passive perception", s.passivePerception, 0) { change(s.copy(passivePerception = it)) }
    VoiceToTextField(s.size, { change(s.copy(size = it)) }, label = "Size", singleLine = true)
    NotesPanel("Conditions & defenses", s.conditions) { change(s.copy(conditions = it)) }
    NotesPanel("Combat notes", s.combatNotes) { change(s.copy(combatNotes = it)) }
}

@Composable
private fun NumberEditor(label: String, value: Int, min: Int, modifier: Modifier = Modifier.fillMaxWidth(), max: Int = 999, change: (Int) -> Unit) {
    Row(modifier.padding(vertical = InkSpacing.xs).clip(RoundedCornerShape(10.dp)).background(inkTokens().panel).padding(InkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        MiniRoundButton(false) { change((value - 1).coerceAtLeast(min)) }
        Text(value.toString(), Modifier.padding(horizontal = InkSpacing.sm), fontWeight = FontWeight.Bold)
        MiniRoundButton(true) { change((value + 1).coerceAtMost(max)) }
    }
}

@Composable private fun NotesPanel(title: String, value: String, change: (String) -> Unit) {
    PanelTitle(title.uppercase())
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), minLines = 7, placeholder = { Text("Add ${title.lowercase()}…") })
}

@Composable private fun InventoryPanel(inventory: List<String>, equipment: List<String>, currency: String, changeCurrency: (String) -> Unit) {
    PanelTitle("EQUIPPED")
    Text(if (equipment.isEmpty()) "Nothing equipped yet." else equipment.joinToString("  •  "), color = inkTokens().secondaryText)
    PanelTitle("CARRIED")
    if (inventory.isEmpty()) Text("Your pack is empty. Add gear from the RPG Inventory tab.", color = inkTokens().secondaryText)
    else inventory.forEach { Text("• $it", Modifier.padding(vertical = 4.dp)) }
    NotesPanel("Currency & valuables", currency, changeCurrency)
}

@Composable private fun SpellPanel(s: RpgCharacterSheet, change: (RpgCharacterSheet) -> Unit) {
    PanelTitle("SPELLCASTING")
    VoiceToTextField(s.spellcastingAbility, { change(s.copy(spellcastingAbility = it)) }, label = "Spellcasting ability", singleLine = true)
    NumberEditor("Spell save DC", s.spellSaveDc, 0) { change(s.copy(spellSaveDc = it)) }
    NumberEditor("Spellcasting modifier", s.spellcastingModifier, -20) { change(s.copy(spellcastingModifier = it)) }
    NumberEditor("Spell attack bonus", s.spellAttackBonus, -20) { change(s.copy(spellAttackBonus = it)) }
    NotesPanel("Spell slots (levels 1–9)", s.spellSlots) { change(s.copy(spellSlots = it)) }
    NotesPanel("Cantrips & prepared spells", s.preparedSpells.ifBlank { s.spells }) {
        change(s.copy(preparedSpells = it, spells = it))
    }
}

@Composable private fun FeaturePanel(s: RpgCharacterSheet, change: (RpgCharacterSheet) -> Unit) {
    NotesPanel("Class features", s.classFeatures.ifBlank { s.featuresAndTraits }) {
        change(s.copy(classFeatures = it, featuresAndTraits = it))
    }
    NotesPanel("Species traits", s.speciesTraits) { change(s.copy(speciesTraits = it)) }
    NotesPanel("Feats", s.feats) { change(s.copy(feats = it)) }
}

@Composable private fun BioPanel(state: CharacterDetailUiState, vm: CharacterDetailViewModel) {
    PanelTitle("CHARACTER BIO")
    VoiceToTextField(state.description, vm::onDescription, label = "Description", minLines = 3)
    VoiceToTextField(state.personality, vm::onPersonality, label = "Personality", minLines = 3, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(state.scenario, vm::onScenario, label = "Scenario", minLines = 3, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(state.firstMes, vm::onFirstMes, label = "First message", minLines = 3, modifier = Modifier.padding(top = InkSpacing.sm))
    val s = state.sheet
    VoiceToTextField(s.appearance, { vm.onSheet(s.copy(appearance = it)) }, label = "Appearance", minLines = 3, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(s.backstoryAndPersonality, { vm.onSheet(s.copy(backstoryAndPersonality = it)) }, label = "Backstory & personality", minLines = 4, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(s.alignment, { vm.onSheet(s.copy(alignment = it)) }, label = "Alignment", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
}

@Composable private fun SettingsPanel(state: CharacterDetailUiState, vm: CharacterDetailViewModel) {
    val s = state.sheet
    PanelTitle("CHARACTER SETTINGS")
    VoiceToTextField(state.name, vm::onName, label = "Name", singleLine = true)
    VoiceToTextField(s.characterClass, { vm.onSheet(s.copy(characterClass = it)) }, label = "Class", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(s.subclass, { vm.onSheet(s.copy(subclass = it)) }, label = "Subclass", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(s.species, { vm.onSheet(s.copy(species = it)) }, label = "Species / ancestry", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
    NumberEditor("Level", s.level, 1, max = 20) { vm.onSheet(s.copy(level = it)) }
    NumberEditor("Experience points", s.experiencePoints, 0, max = 999999) { vm.onSheet(s.copy(experiencePoints = it)) }
    VoiceToTextField(s.background, { vm.onSheet(s.copy(background = it)) }, label = "Background", singleLine = true)
    VoiceToTextField(s.hitDieType, { vm.onSheet(s.copy(hitDieType = it)) }, label = "Hit die (for example d8)", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = s.heroicInspiration,
            onCheckedChange = { vm.onSheet(s.copy(heroicInspiration = it)) },
        )
        Text("Heroic inspiration")
    }
    VoiceToTextField(state.tags, vm::onTags, label = "Tags (comma-separated)", singleLine = true, modifier = Modifier.padding(top = InkSpacing.sm))
    VoiceToTextField(state.systemPrompt, vm::onSystemPrompt, label = "System prompt", minLines = 2, modifier = Modifier.padding(top = InkSpacing.sm))
}

@Composable private fun PanelTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
    modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.lg, bottom = InkSpacing.sm), textAlign = TextAlign.Center,
)

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
