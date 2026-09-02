package com.ihy2ln.weaverse.feature.novel.codex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import java.io.File

/** One expandable card on a codex sheet, in the Roster sheet's shape. */
private data class CodexSection(
    val label: String,
    val symbol: String,
    val hint: String,
    val content: @Composable () -> Unit,
)

/**
 * A codex entry that is not a character, drawn with the Roster sheet's
 * furniture — framed portrait, stat strip, expandable sections — but with the
 * fields its kind actually needs. Locations get a census, objects get a stat
 * block, lore gets room to be long, and Other gets fields the writer names.
 */
@Composable
fun CodexSheetScreen(
    kind: CodexEntryKind,
    name: String,
    portraitPath: String,
    avatarColorHex: String,
    sheet: CodexSheetData,
    saved: Boolean,
    statusMessage: String,
    onName: (String) -> Unit,
    onSheet: (CodexSheetData) -> Unit,
    onPickPortrait: () -> Unit,
    onSave: () -> Unit,
) {
    val tokens = inkTokens()
    val sections = when (kind) {
        CodexEntryKind.Location -> locationSections(sheet, onSheet)
        CodexEntryKind.Item -> itemSections(sheet, onSheet)
        CodexEntryKind.Lore -> loreSections(sheet, onSheet)
        else -> otherSections(sheet, onSheet)
    }
    val expanded = remember(kind) { mutableStateMapOf(sections.first().label to true) }

    Column(Modifier.fillMaxSize().background(tokens.background)) {
        CodexSheetHeader(
            kind = kind,
            name = name,
            portraitPath = portraitPath,
            avatarColorHex = avatarColorHex,
            stats = headerStats(kind, sheet),
            onPickPortrait = onPickPortrait,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = InkSpacing.md),
        ) {
            Text(
                "${kind.label.uppercase()} SHEET",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.md, bottom = InkSpacing.xs),
            )
            CodexSectionCard(
                label = "Identity",
                symbol = kind.symbol,
                hint = "Name and what this entry is",
                expanded = expanded["Identity"] == true,
                onToggle = { expanded["Identity"] = expanded["Identity"] != true },
            ) {
                VoiceToTextField(name, onName, label = "Name", singleLine = true)
                CodexKindPicker(kind = kind, sheet = sheet, onSheet = onSheet)
            }
            sections.forEach { section ->
                CodexSectionCard(
                    label = section.label,
                    symbol = section.symbol,
                    hint = section.hint,
                    expanded = expanded[section.label] == true,
                    onToggle = { expanded[section.label] = expanded[section.label] != true },
                    content = section.content,
                )
            }
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
            }
            InkConfirmButton(
                onClick = onSave,
                label = if (saved) "Saved" else "Save ${kind.label.lowercase()} sheet",
                contentDescription = "Save sheet",
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.lg),
            )
            Spacer(Modifier.height(AlwaysScrollEndPadding))
        }
    }
}

/** Portrait plus the four numbers that matter for this kind, as the Roster prints them. */
@Composable
private fun CodexSheetHeader(
    kind: CodexEntryKind,
    name: String,
    portraitPath: String,
    avatarColorHex: String,
    stats: List<Pair<String, String>>,
    onPickPortrait: () -> Unit,
) {
    val tokens = inkTokens()
    Column(Modifier.fillMaxWidth().background(tokens.panel).padding(InkSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onPickPortrait),
                contentAlignment = Alignment.Center,
            ) {
                if (portraitPath.isNotBlank()) {
                    AsyncImage(
                        model = File(portraitPath),
                        contentDescription = "$name picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CharacterAvatar(name.ifBlank { kind.label }, avatarColorHex, size = 68.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    kind.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.activePill,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = InkSpacing.sm), horizontalArrangement = Arrangement.SpaceEvenly) {
            stats.forEach { (label, value) -> CodexHeaderStat(label, value) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onPickPortrait) {
                Text(if (portraitPath.isBlank()) "Add picture" else "Add another picture")
            }
        }
    }
}

@Composable
private fun CodexHeaderStat(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = inkTokens().secondaryText)
    Text(
        value.ifBlank { "—" },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun headerStats(kind: CodexEntryKind, sheet: CodexSheetData): List<Pair<String, String>> = when (kind) {
    CodexEntryKind.Location -> listOf(
        "TYPE" to sheet.location.locationType,
        "SCALE" to sheet.location.scale,
        "POP" to sheet.location.population.takeIf { it > 0 }?.toString().orEmpty(),
        "RULER" to sheet.location.ruler,
    )
    CodexEntryKind.Item -> listOf(
        "TYPE" to sheet.item.itemType,
        "RARITY" to sheet.item.rarity,
        "VALUE" to sheet.item.value,
        "WEIGHT" to sheet.item.weight,
    )
    CodexEntryKind.Lore -> listOf(
        "TYPE" to sheet.lore.loreType,
        "ERA" to sheet.lore.era,
    )
    else -> listOf("TYPE" to sheet.other.subtype)
}

/** Lets one entry use a different template than its category implies. */
@Composable
private fun CodexKindPicker(
    kind: CodexEntryKind,
    sheet: CodexSheetData,
    onSheet: (CodexSheetData) -> Unit,
) {
    val tokens = inkTokens()
    CodexPanelTitle("TEMPLATE")
    Row(
        Modifier.fillMaxWidth().padding(bottom = InkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        CodexEntryKind.entries.forEach { option ->
            val active = option == kind
            Text(
                option.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) MaterialTheme.colorScheme.primary else tokens.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) tokens.hover else tokens.panel)
                    .clickable { onSheet(sheet.copy(kind = option.name)) }
                    .padding(vertical = InkSpacing.sm),
            )
        }
    }
    Text(
        "Template: ${kind.label}",
        style = MaterialTheme.typography.bodySmall,
        color = tokens.secondaryText,
    )
}

// ---------------------------------------------------------------- templates

private fun locationSections(
    sheet: CodexSheetData,
    onSheet: (CodexSheetData) -> Unit,
): List<CodexSection> {
    val place = sheet.location
    fun edit(block: LocationSheet.() -> LocationSheet) = onSheet(sheet.copy(location = place.block()))
    return listOf(
        CodexSection("Description", "◈", "What this place is and how it feels") {
            CodexNotes("Description", place.description) { edit { copy(description = it) } }
            CodexNotes("Sights, sounds & atmosphere", place.appearance) { edit { copy(appearance = it) } }
        },
        CodexSection("Placement", "⌖", "Where it sits and who holds it") {
            CodexLine("Location type (city, keep, tavern…)", place.locationType) { edit { copy(locationType = it) } }
            CodexLine("Region / what it belongs to", place.region) { edit { copy(region = it) } }
            CodexLine("Ruler / authority", place.ruler) { edit { copy(ruler = it) } }
            CodexLine("Scale (hamlet, town, city…)", place.scale) { edit { copy(scale = it) } }
        },
        CodexSection("Census", "☷", "Who lives here, and how many") {
            CodexNumber("Population", place.population, step = 25) { edit { copy(population = it) } }
            CodexNotes("Census & demographics", place.census) { edit { copy(census = it) } }
            CodexNotes("Notable residents", place.notableResidents) { edit { copy(notableResidents = it) } }
            CodexNotes("Factions & powers", place.factions) { edit { copy(factions = it) } }
        },
        CodexSection("History & Lore", "✦", "Background history and the stories told about it") {
            CodexNotes("Background history", place.history) { edit { copy(history = it) } }
            CodexNotes("Lore, legends & myths", place.lore) { edit { copy(lore = it) } }
        },
        CodexSection("Points of Interest", "▣", "What is worth visiting, and what is sold") {
            CodexNotes("Points of interest", place.pointsOfInterest) { edit { copy(pointsOfInterest = it) } }
            CodexNotes("Services, trade & goods", place.services) { edit { copy(services = it) } }
            CodexNotes("Defenses & dangers", place.defenses) { edit { copy(defenses = it) } }
        },
        CodexSection("Hooks & Secrets", "⌁", "Rumors, secrets and reasons to come back") {
            CodexNotes("Hooks, rumors & secrets", place.hooks) { edit { copy(hooks = it) } }
            CodexNotes("Notes", place.notes) { edit { copy(notes = it) } }
        },
    )
}

private fun itemSections(
    sheet: CodexSheetData,
    onSheet: (CodexSheetData) -> Unit,
): List<CodexSection> {
    val item = sheet.item
    fun edit(block: ItemSheet.() -> ItemSheet) = onSheet(sheet.copy(item = item.block()))
    return listOf(
        CodexSection("Description & Appearance", "◈", "What it is and what it looks like") {
            CodexNotes("Description", item.description) { edit { copy(description = it) } }
            CodexNotes("Appearance & markings", item.appearance) { edit { copy(appearance = it) } }
        },
        CodexSection("Item Details", "▣", "Type, rarity, value and weight") {
            CodexLine("Item type (weapon, armor, relic…)", item.itemType) { edit { copy(itemType = it) } }
            CodexLine("Rarity", item.rarity) { edit { copy(rarity = it) } }
            CodexLine("Value", item.value) { edit { copy(value = it) } }
            CodexLine("Weight", item.weight) { edit { copy(weight = it) } }
            CodexNumber("Quantity", item.quantity, min = 1) { edit { copy(quantity = it) } }
            CodexToggle("Requires attunement", item.requiresAttunement) { edit { copy(requiresAttunement = it) } }
            if (item.requiresAttunement) {
                CodexNotes("Attunement notes", item.attunementNotes) { edit { copy(attunementNotes = it) } }
            }
        },
        CodexSection("Stats & Mechanics", "⚔", "The stat block, when this thing has one") {
            CodexToggle("This item has stats", item.statsFilledIn) { edit { copy(hasStats = it) } }
            if (item.statsFilledIn) {
                CodexLine("Damage (for example 1d8 slashing)", item.damage) { edit { copy(damage = it) } }
                CodexLine("Attack / to-hit bonus", item.attackBonus) { edit { copy(attackBonus = it) } }
                CodexLine("Armor class bonus", item.armorClassBonus) { edit { copy(armorClassBonus = it) } }
                CodexLine("Range", item.range) { edit { copy(range = it) } }
                CodexLine("Charges / uses", item.charges) { edit { copy(charges = it) } }
                CodexLine("Save DC", item.saveDc) { edit { copy(saveDc = it) } }
                CodexNotes("Properties (finesse, versatile…)", item.properties) { edit { copy(properties = it) } }
                CodexNotes("Effects & abilities", item.effects) { edit { copy(effects = it) } }
            } else {
                Text(
                    "Mundane for now — turn this on when the item gets a stat block.",
                    style = MaterialTheme.typography.bodySmall,
                    color = inkTokens().secondaryText,
                )
            }
        },
        CodexSection("History & Lore", "✦", "Where it came from and what is said about it") {
            CodexLine("Origin / maker", item.origin) { edit { copy(origin = it) } }
            CodexNotes("Background history", item.history) { edit { copy(history = it) } }
            CodexNotes("Lore & legends", item.lore) { edit { copy(lore = it) } }
        },
        CodexSection("Ownership", "☗", "Who holds it now, and who held it before") {
            CodexLine("Current owner / carrier", item.owner) { edit { copy(owner = it) } }
            CodexNotes("Past owners", item.pastOwners) { edit { copy(pastOwners = it) } }
            CodexLine("Where it is kept", item.location) { edit { copy(location = it) } }
            CodexNotes("Notes", item.notes) { edit { copy(notes = it) } }
        },
    )
}

private fun loreSections(
    sheet: CodexSheetData,
    onSheet: (CodexSheetData) -> Unit,
): List<CodexSection> {
    val lore = sheet.lore
    fun edit(block: LoreSheet.() -> LoreSheet) = onSheet(sheet.copy(lore = lore.block()))
    return listOf(
        CodexSection("Explanation", "✦", "The full, in-depth account") {
            CodexNotes("Summary (one or two lines)", lore.summary, minLines = 3) { edit { copy(summary = it) } }
            CodexNotes("In-depth explanation", lore.explanation, minLines = 14) { edit { copy(explanation = it) } }
            CodexNotes("Further detail", lore.details, minLines = 10) { edit { copy(details = it) } }
        },
        CodexSection("Placement", "⌖", "What kind of lore this is, and when") {
            CodexLine("Lore type (event, religion, system…)", lore.loreType) { edit { copy(loreType = it) } }
            CodexLine("Era / date", lore.era) { edit { copy(era = it) } }
        },
        CodexSection("Origins & Timeline", "☷", "How it began and how it unfolded") {
            CodexNotes("Origins", lore.origins) { edit { copy(origins = it) } }
            CodexNotes("Timeline", lore.timeline) { edit { copy(timeline = it) } }
            CodexNotes("Why it matters", lore.significance) { edit { copy(significance = it) } }
        },
        CodexSection("Connections", "⌁", "The people, places and things it touches") {
            CodexNotes("Related people", lore.relatedPeople) { edit { copy(relatedPeople = it) } }
            CodexNotes("Related places", lore.relatedPlaces) { edit { copy(relatedPlaces = it) } }
            CodexNotes("Related objects", lore.relatedThings) { edit { copy(relatedThings = it) } }
        },
        CodexSection("Beliefs & Secrets", "◈", "What the world thinks, and what is true") {
            CodexNotes("In-world sources", lore.sources) { edit { copy(sources = it) } }
            CodexNotes("Common beliefs & misconceptions", lore.beliefs) { edit { copy(beliefs = it) } }
            CodexNotes("Secrets", lore.secrets) { edit { copy(secrets = it) } }
            CodexNotes("Notes", lore.notes) { edit { copy(notes = it) } }
        },
    )
}

private fun otherSections(
    sheet: CodexSheetData,
    onSheet: (CodexSheetData) -> Unit,
): List<CodexSection> {
    val other = sheet.other
    fun edit(block: OtherSheet.() -> OtherSheet) = onSheet(sheet.copy(other = other.block()))
    return listOf(
        CodexSection("Description", "◈", "What this is, in short and in full") {
            CodexLine("What kind of thing is this?", other.subtype) { edit { copy(subtype = it) } }
            CodexNotes("Summary", other.summary, minLines = 3) { edit { copy(summary = it) } }
            CodexNotes("Description", other.description) { edit { copy(description = it) } }
        },
        CodexSection("Details", "☷", "Everything worth writing down") {
            CodexNotes("Details", other.details, minLines = 10) { edit { copy(details = it) } }
            CodexNotes("Background", other.background) { edit { copy(background = it) } }
            CodexNotes("Connections", other.connections) { edit { copy(connections = it) } }
        },
        CodexSection("Your Own Fields", "⌁", "Fields you name yourself") {
            other.customFields.forEachIndexed { index, field ->
                CodexLine("Field name", field.label) { value ->
                    edit {
                        copy(
                            customFields = customFields.toMutableList().also {
                                it[index] = it[index].copy(label = value)
                            },
                        )
                    }
                }
                CodexNotes(field.label.ifBlank { "Value" }, field.value, minLines = 4) { value ->
                    edit {
                        copy(
                            customFields = customFields.toMutableList().also {
                                it[index] = it[index].copy(value = value)
                            },
                        )
                    }
                }
                HorizontalDivider(color = inkTokens().hairline)
            }
            TextButton(onClick = { edit { copy(customFields = customFields + CodexCustomField()) } }) {
                Text("+ Add a field")
            }
            CodexNotes("Notes", other.notes) { edit { copy(notes = it) } }
        },
    )
}

// --------------------------------------------------------------- components

/** The Roster sheet's expandable card, reused so every codex sheet reads the same. */
@Composable
private fun CodexSectionCard(
    label: String,
    symbol: String,
    hint: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = inkTokens()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = InkSpacing.sm),
        color = tokens.panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
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
                    Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal = InkSpacing.sm)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (expanded) "Collapse $label" else "Expand $label",
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
private fun CodexPanelTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.ExtraBold,
    modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
    textAlign = TextAlign.Center,
)

@Composable
private fun CodexNotes(label: String, value: String, minLines: Int = 7, onValue: (String) -> Unit) {
    CodexPanelTitle(label.uppercase())
    VoiceToTextField(value, onValue, label = label, minLines = minLines, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CodexLine(label: String, value: String, onValue: (String) -> Unit) {
    VoiceToTextField(
        value,
        onValue,
        label = label,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
    )
}

@Composable
private fun CodexNumber(label: String, value: Int, min: Int = 0, step: Int = 1, onValue: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = InkSpacing.xs)
            .clip(RoundedCornerShape(10.dp)).background(inkTokens().panel).padding(InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onValue((value - step).coerceAtLeast(min)) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Remove, "Decrease $label", tint = MaterialTheme.colorScheme.primary)
        }
        Text(value.toString(), Modifier.padding(horizontal = InkSpacing.sm), fontWeight = FontWeight.Bold)
        IconButton(onClick = { onValue(value + step) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, "Increase $label", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CodexToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
