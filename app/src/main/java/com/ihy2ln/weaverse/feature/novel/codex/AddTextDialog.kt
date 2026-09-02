package com.ihy2ln.weaverse.feature.novel.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Free-text sheet sections a snippet can be appended into. */
enum class SheetTextTarget(val label: String) {
    SavingThrows("Saving Throws"),
    CharacterStory("Character story"),
    BodyDescription("Body description"),
    Appearance("Appearance"),
    Conditions("Conditions"),
    CombatNotes("Combat notes"),
    AttacksAndActions("Attacks & Actions"),
    FeaturesAndTraits("Features & Traits"),
    Spells("Spells"),
    Languages("Languages"),
    Description("Description"),
    Personality("Personality"),
}

data class AddTextUiState(
    val categories: List<com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity> = emptyList(),
    val characters: List<com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity> = emptyList(),
)

/**
 * Drops a text snippet into the destination the user picks: a new Codex entry
 * in a chosen category, a chosen roster character's sheet section, or an
 * inventory item. Used by the "＋ Add text" buttons in RPG and Brainstorm.
 */
@HiltViewModel
class AddTextViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val db: WeaverseDatabase,
) : ViewModel() {

    val uiState: StateFlow<AddTextUiState> = combine(
        codexRepository.observeAllCategories(),
        db.roleplayDao().observeCharacters(),
    ) { categories, characters ->
        AddTextUiState(categories = categories, characters = characters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTextUiState())

    fun addToCodex(categoryId: String, title: String, text: String, onDone: (String) -> Unit) {
        if (categoryId.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val entry = codexRepository.addEntry(categoryId, name = title.ifBlank { "New entry" })
            codexRepository.updateEntryText(entry.id, title.ifBlank { entry.name }, text)
            onDone("Added \"${title.ifBlank { entry.name }}\" to Codex")
        }
    }

    fun appendToSheet(characterId: String, target: SheetTextTarget, text: String, onDone: (String) -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            val gap: (String) -> String = { current ->
                if (current.isBlank() || current.endsWith("\n")) "" else "\n"
            }
            val updated = when (target) {
                SheetTextTarget.Description -> character.copy(
                    description = character.description + gap(character.description) + text,
                )
                SheetTextTarget.Personality -> character.copy(
                    personality = character.personality + gap(character.personality) + text,
                )
                else -> {
                    val sheet = decodeRpgSheet(character.extensionsJson)
                    val updatedSheet = when (target) {
                        SheetTextTarget.SavingThrows -> sheet.copy(savingThrows = sheet.savingThrows + gap(sheet.savingThrows) + text)
                        SheetTextTarget.CharacterStory -> sheet.copy(
                            backstoryAndPersonality = sheet.backstoryAndPersonality + gap(sheet.backstoryAndPersonality) + text,
                        )
                        SheetTextTarget.BodyDescription -> sheet.copy(
                            bodyDescription = sheet.bodyDescription + gap(sheet.bodyDescription) + text,
                        )
                        SheetTextTarget.Appearance -> sheet.copy(appearance = sheet.appearance + gap(sheet.appearance) + text)
                        SheetTextTarget.Conditions -> sheet.copy(conditions = sheet.conditions + gap(sheet.conditions) + text)
                        SheetTextTarget.CombatNotes -> sheet.copy(combatNotes = sheet.combatNotes + gap(sheet.combatNotes) + text)
                        SheetTextTarget.AttacksAndActions -> sheet.copy(
                            attacksAndActions = sheet.attacksAndActions + gap(sheet.attacksAndActions) + text,
                        )
                        SheetTextTarget.FeaturesAndTraits -> sheet.copy(
                            featuresAndTraits = sheet.featuresAndTraits + gap(sheet.featuresAndTraits) + text,
                        )
                        SheetTextTarget.Spells -> sheet.copy(spells = sheet.spells + gap(sheet.spells) + text)
                        SheetTextTarget.Languages -> sheet.copy(languages = sheet.languages + gap(sheet.languages) + text)
                        else -> sheet
                    }
                    character.copy(extensionsJson = encodeRpgSheet(character.extensionsJson, updatedSheet))
                }
            }
            db.roleplayDao().upsertCharacter(updated.copy(updatedAt = System.currentTimeMillis()))
            onDone("Added to ${character.name} · ${target.label}")
        }
    }

    fun addToInventory(carrierId: String, itemName: String, text: String, onDone: (String) -> Unit) {
        if (itemName.isBlank() && text.isBlank()) return
        viewModelScope.launch {
            val character = db.roleplayDao().getCharacter(carrierId) ?: return@launch
            val item = RpItem(
                id = "item-${UUID.randomUUID()}",
                name = itemName.ifBlank { "New item" },
                notes = text,
            )
            db.roleplayDao().upsertCharacter(
                character.copy(
                    inventoryJson = encodeItems(decodeItems(character.inventoryJson) + item),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            onDone("Added \"${item.name}\" to ${character.name}'s inventory")
        }
    }
}

private enum class AddTextDestination(val label: String) {
    Codex("Codex"),
    Roster("Roster"),
    Inventory("Inventory"),
}

/**
 * Pick-where-it-goes dialog: paste or type a snippet, choose Codex / Roster /
 * Inventory, pick the exact target, and the text lands there.
 */
@Composable
fun AddTextDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit,
    viewModel: AddTextViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var title by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf(AddTextDestination.Codex.name) }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var characterId by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(SheetTextTarget.CharacterStory.name) }
    var itemName by rememberSaveable { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var characterMenuOpen by remember { mutableStateOf(false) }

    val categories = state.categories
    val characters = state.characters
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val selectedCharacter = characters.firstOrNull { it.id == characterId }
    val dest = AddTextDestination.valueOf(destination)

    fun submit() {
        when (dest) {
            AddTextDestination.Codex -> viewModel.addToCodex(categoryId, title, text) {
                onStatus(it); onDismiss()
            }
            AddTextDestination.Roster -> viewModel.appendToSheet(
                characterId,
                SheetTextTarget.valueOf(section),
                text,
            ) {
                onStatus(it); onDismiss()
            }
            AddTextDestination.Inventory -> viewModel.addToInventory(characterId, itemName, text) {
                onStatus(it); onDismiss()
            }
        }
    }

    val ready = when (dest) {
        AddTextDestination.Codex -> text.isNotBlank() && categoryId.isNotBlank()
        AddTextDestination.Roster -> text.isNotBlank() && characterId.isNotBlank()
        AddTextDestination.Inventory -> text.isNotBlank() && characterId.isNotBlank() &&
            (itemName.isNotBlank() || text.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text to…") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = InkSpacing.sm),
                ) {
                    InkSegmentedPill(
                        options = AddTextDestination.entries.map { SegmentedOption(it.name, it.label) },
                        selectedId = destination,
                        onSelect = { destination = it },
                    )
                }
                when (dest) {
                    AddTextDestination.Codex -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Entry title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
                        )
                        PickerField(
                            label = selectedCategory?.name ?: "Choose category",
                            open = categoryMenuOpen,
                            onToggle = { categoryMenuOpen = !categoryMenuOpen },
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        categoryId = category.id
                                        categoryMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    AddTextDestination.Roster -> {
                        PickerField(
                            label = selectedCharacter?.name ?: "Choose character",
                            open = characterMenuOpen,
                            onToggle = { characterMenuOpen = !characterMenuOpen },
                        ) {
                            characters.forEach { character ->
                                DropdownMenuItem(
                                    text = { Text(character.name) },
                                    onClick = {
                                        characterId = character.id
                                        characterMenuOpen = false
                                    },
                                )
                            }
                        }
                        Text(
                            "Section",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = InkSpacing.sm),
                        )
                        Column {
                            SheetTextTarget.entries.chunked(2).forEach { rowTargets ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    rowTargets.forEach { target ->
                                        InkSegmentedPill(
                                            options = listOf(SegmentedOption(target.name, target.label)),
                                            selectedId = if (section == target.name) target.name else "",
                                            onSelect = { section = target.name },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AddTextDestination.Inventory -> {
                        PickerField(
                            label = selectedCharacter?.name ?: "Choose carrier",
                            open = characterMenuOpen,
                            onToggle = { characterMenuOpen = !characterMenuOpen },
                        ) {
                            characters.forEach { character ->
                                DropdownMenuItem(
                                    text = { Text(character.name) },
                                    onClick = {
                                        characterId = character.id
                                        characterMenuOpen = false
                                    },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text("Item name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = ready) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PickerField(
    label: String,
    open: Boolean,
    onToggle: () -> Unit,
    menu: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm)) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(inkRadiusSm()))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onToggle)
                .padding(horizontal = InkSpacing.md, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        DropdownMenu(expanded = open, onDismissRequest = onToggle) { menu() }
    }
}
