package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

data class CampaignPresetBrowserItem(
    val id: String,
    val label: String,
    val description: String,
    val section: String,
    val theme: String,
    val removable: Boolean = false,
)

fun decodeCampaignSettingTemplates(rawTemplates: Set<String>): List<CampaignSettingTemplate> =
    rawTemplates.mapNotNull { raw ->
        val parts = raw.split('|', limit = 5)
        when {
            parts.size >= 5 && parts[0].isNotBlank() ->
                CampaignSettingTemplate(parts[0], parts[1], parts[4], parts[2], parts[3])
            parts.size >= 3 && parts[0].isNotBlank() ->
                CampaignSettingTemplate(parts[0], parts[1], parts[2], "Custom", "Saved templates")
            else -> null
        }
    }.sortedBy { it.label.lowercase() }

fun decodeCampaignSettingDetailTemplates(rawTemplates: Set<String>): List<CampaignSettingDetailTemplate> =
    rawTemplates.mapNotNull { raw ->
        val parts = raw.split('|', limit = 5)
        when {
            parts.size >= 5 && parts[0].isNotBlank() ->
                CampaignSettingDetailTemplate(parts[0], parts[1], parts[4], parts[2], parts[3])
            parts.size >= 3 && parts[0].isNotBlank() ->
                CampaignSettingDetailTemplate(parts[0], parts[1], parts[2], "Custom", "Saved presets")
            else -> null
        }
    }.sortedBy { it.label.lowercase() }

fun campaignSettingBrowserItems(
    custom: List<CampaignSettingTemplate> = emptyList(),
): List<CampaignPresetBrowserItem> = (CampaignSettingTemplates + custom).map { template ->
    CampaignPresetBrowserItem(
        id = template.id,
        label = template.label,
        description = template.directive,
        section = if (template.id.startsWith("custom-")) "Custom" else template.section,
        theme = if (template.id.startsWith("custom-")) "Saved templates" else template.theme,
        removable = template.id.startsWith("custom-"),
    )
}

fun campaignSettingDetailBrowserItems(
    custom: List<CampaignSettingDetailTemplate> = emptyList(),
): List<CampaignPresetBrowserItem> =
    (CampaignSettingDetailTemplates + custom).map { preset ->
        CampaignPresetBrowserItem(
            id = preset.id,
            label = preset.label,
            description = preset.details,
            section = preset.section,
            theme = preset.theme,
            removable = preset.id.startsWith("custom-detail-"),
        )
    }

/** Three-level RPG preset navigator: main section, theme, then editable selection. */
@Composable
fun CampaignPresetBrowserDialog(
    title: String,
    items: List<CampaignPresetBrowserItem>,
    selectedId: String,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelect: (CampaignPresetBrowserItem) -> Unit,
    onDismiss: () -> Unit,
    onRemove: ((String) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "Add new",
) {
    val tokens = inkTokens()
    var section by remember { mutableStateOf<String?>(null) }
    var theme by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    val favoritesLabel = "★ Favorites"
    val sections = buildList {
        if (favoriteIds.isNotEmpty()) add(favoritesLabel)
        addAll(items.map { it.section }.distinct())
    }
    val inFavorites = section == favoritesLabel
    val sectionItems = when {
        inFavorites -> items.filter { it.id in favoriteIds }
        section != null -> items.filter { it.section == section }
        else -> emptyList()
    }
    val themes = sectionItems.map { it.theme }.distinct()
    val visiblePresets = sectionItems.filter { preset ->
        (inFavorites || preset.theme == theme) &&
            (search.isBlank() || preset.label.contains(search, true) || preset.description.contains(search, true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(
                    when {
                        theme != null -> "${section.orEmpty()} › $theme"
                        section != null -> section.orEmpty()
                        else -> "Choose a main section"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.secondaryText,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                if (section != null) {
                    TextButton(
                        onClick = {
                            if (theme != null) theme = null else section = null
                            search = ""
                        },
                    ) { Text(if (theme != null) "‹ Themes" else "‹ Main sections") }
                }
                if (onAdd != null) {
                    InkOutlinedButton(
                        label = "＋ $addLabel",
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    section == null -> BrowserList(
                        rows = sections.map { current ->
                            BrowserRow(
                                id = current,
                                title = current,
                                subtitle = if (current == favoritesLabel) {
                                    "${favoriteIds.size} saved presets"
                                } else {
                                    val count = items.count { it.section == current }
                                    "${items.filter { it.section == current }.map { it.theme }.distinct().size} themes · $count presets"
                                },
                            )
                        },
                        onClick = { section = it.id },
                    )
                    !inFavorites && theme == null -> BrowserList(
                        rows = themes.map { current ->
                            BrowserRow(
                                id = current,
                                title = current,
                                subtitle = "${sectionItems.count { it.theme == current }} presets",
                            )
                        },
                        onClick = { theme = it.id },
                    )
                    else -> {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Search these presets") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (visiblePresets.isEmpty()) {
                            Text("No presets match this section.", color = tokens.secondaryText)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                                items(visiblePresets, key = { it.id }) { preset ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                if (preset.id == selectedId) 2.dp else 1.dp,
                                                if (preset.id == selectedId) MaterialTheme.colorScheme.primary else tokens.hairline,
                                                RoundedCornerShape(inkRadiusSm()),
                                            )
                                            .clickable(onClickLabel = "Use ${preset.label}") { onSelect(preset) }
                                            .padding(InkSpacing.sm),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.label, fontWeight = FontWeight.Bold)
                                            Text(
                                                preset.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.secondaryText,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            TextButton(onClick = { onToggleFavorite(preset.id) }) {
                                                Text(if (preset.id in favoriteIds) "★" else "☆")
                                            }
                                            if (preset.removable && onRemove != null) {
                                                TextButton(onClick = { onRemove(preset.id) }) { Text("Remove") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private data class BrowserRow(val id: String, val title: String, val subtitle: String)

@Composable
private fun BrowserList(rows: List<BrowserRow>, onClick: (BrowserRow) -> Unit) {
    val tokens = inkTokens()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        items(rows, key = { it.id }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { onClick(row) }
                    .padding(InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, fontWeight = FontWeight.Bold)
                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = tokens.secondaryText)
            }
        }
    }
}

/** Editor shared by Add Setting Template and Add Setting Details Preset. */
@Composable
fun CampaignPresetEditorDialog(
    title: String,
    guidanceLabel: String,
    defaultSection: String,
    defaultTheme: String,
    onDismiss: () -> Unit,
    onSave: (name: String, section: String, theme: String, guidance: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(defaultSection) }
    var theme by remember { mutableStateOf(defaultTheme) }
    var guidance by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it.take(60) },
                    label = { Text("Main section") },
                    placeholder = { Text("Custom, Fantasy, Action…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = theme,
                    onValueChange = { theme = it.take(60) },
                    label = { Text("Theme subsection") },
                    placeholder = { Text("Saved presets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = guidance,
                    onValueChange = { guidance = it.take(2000) },
                    label = { Text(guidanceLabel) },
                    minLines = 4,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && guidance.isNotBlank(),
                onClick = {
                    onSave(name, section, theme, guidance)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
