package com.ihy2ln.weaverse.feature.novel.codex

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.ui.ColorSwatch
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.FormatPickerDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import kotlinx.coroutines.launch

private val fallbackColor = Color(0xFF9E958A)

private fun colorOf(hex: String): Color = parseHex(hex) ?: fallbackColor

/** Resolves a category's `icon` string (spec's "glyph") to a real Material icon — the ten
 * built-in categories (see [com.ihy2ln.weaverse.core.ui.CodexCategoryKind]) all set one of these
 * keys; a category the user renamed/re-iconed away from a built-in, or created from scratch,
 * falls back to the generic book glyph. */
private fun iconFor(iconKey: String): ImageVector = when (iconKey) {
    "person" -> Icons.Filled.Person
    "place" -> Icons.Filled.Place
    "inventory" -> Icons.Filled.Inventory
    "flag" -> Icons.Filled.Flag
    "call_split" -> Icons.Filled.CallSplit
    "bolt" -> Icons.Filled.Bolt
    "event" -> Icons.Filled.Event
    "domain" -> Icons.Filled.Domain
    "note" -> Icons.Filled.Note
    else -> Icons.Filled.MenuBook
}

@Composable
fun CodexRailContent(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val entriesByCategory by viewModel.entriesByCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val scopeFilter by viewModel.scopeFilter.collectAsState()
    val scopeCounts by viewModel.scopeCounts.collectAsState()
    val currentSeriesId by viewModel.currentSeriesId.collectAsState()

    var newCategoryDialogOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CodexEntryEntity?>(null) }
    var creatingEntryForCategory by remember { mutableStateOf<String?>(null) }
    var exportPickerOpen by remember { mutableStateOf(false) }
    var importPickerOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = viewModel.exportCodex(format)
                if (bytes == null) {
                    status = "No codex to export yet."
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    status = "Exported as ${format.label}."
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                status = if (bytes == null) {
                    "Could not read that file."
                } else {
                    runCatching { viewModel.importCodex(bytes, format) }
                        .fold(
                            onSuccess = { count -> if (count == null) "No book to import into yet." else "Imported $count entries." },
                            onFailure = { "That file isn't a valid ${format.label} codex export." },
                        )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, top = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search all entries…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(CornerRadius.input),
            )
            IconButton(onClick = {}) {
                Icon(Icons.Filled.FilterAlt, contentDescription = "Filter entries")
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Settings, contentDescription = "Codex settings")
            }
        }

        ScopeTabRow(
            selected = scopeFilter,
            counts = scopeCounts,
            hasSeries = currentSeriesId != null,
            onSelect = viewModel::setScopeFilter,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = { exportPickerOpen = true }) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Text("Export", modifier = Modifier.padding(start = Spacing.xs))
            }
            TextButton(onClick = { importPickerOpen = true }) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Text("Import", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }

        if (categories.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MenuBook,
                title = "No codex categories yet",
                subtitle = "Categories group your characters, locations, and lore.",
                actionLabel = "New category",
                onAction = { newCategoryDialogOpen = true },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = categories, key = { it.id }) { category ->
                    CategoryAccordion(
                        category = category,
                        entries = entriesByCategory[category.id].orEmpty(),
                        onAddEntry = { creatingEntryForCategory = category.id },
                        onEntryClick = { editingEntry = it },
                    )
                }
                item {
                    TextButton(
                        onClick = { newCategoryDialogOpen = true },
                        modifier = Modifier.padding(Spacing.md),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("New category", modifier = Modifier.padding(start = Spacing.xs))
                    }
                }
            }
        }
    }

    if (newCategoryDialogOpen) {
        NewCategoryDialog(
            onDismiss = { newCategoryDialogOpen = false },
            onCreate = { name, colorHex ->
                viewModel.addCategory(name, colorHex)
                newCategoryDialogOpen = false
            },
        )
    }

    creatingEntryForCategory?.let { categoryId ->
        NewEntryDialog(
            onDismiss = { creatingEntryForCategory = null },
            onCreate = { name ->
                viewModel.createEntry(categoryId, name)
                creatingEntryForCategory = null
            },
        )
    }

    editingEntry?.let { entry ->
        val category = categories.firstOrNull { it.id == entry.categoryId }
        CodexEntryEditorSheet(
            entryId = entry.id,
            category = category,
            viewModel = viewModel,
            onDismiss = { editingEntry = null },
        )
    }

    if (exportPickerOpen) {
        FormatPickerDialog(
            title = "Export codex as…",
            onDismiss = { exportPickerOpen = false },
            onSelect = { format ->
                exportPickerOpen = false
                pendingFormat = format
                exportLauncher.launch("weaverse-codex.${format.extension}")
            },
        )
    }
    if (importPickerOpen) {
        FormatPickerDialog(
            title = "Import codex from…",
            onDismiss = { importPickerOpen = false },
            onSelect = { format ->
                importPickerOpen = false
                pendingFormat = format
                importLauncher.launch("*/*")
            },
        )
    }
}

/** The `All 101` / `Book` / `Series` scope tabs (spec §1.4/Revision 02 §3), with count badges and
 * an underline indicator on the active tab. The Series tab still renders (disabled, 0 count) when
 * the current book isn't in a series, rather than disappearing — a stable set of tabs is easier to
 * scan than tabs that shift around depending on series membership. */
@Composable
private fun ScopeTabRow(
    selected: CodexScopeFilter,
    counts: Map<CodexScopeFilter, Int>,
    hasSeries: Boolean,
    onSelect: (CodexScopeFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CodexScopeFilter.entries.forEach { filter ->
            val enabled = filter != CodexScopeFilter.Series || hasSeries
            Column(
                modifier = Modifier
                    .clickable(enabled = enabled, onClickLabel = filter.name, onClick = { onSelect(filter) }),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (filter == selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    Text(
                        text = " ${counts[filter] ?: 0}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filter == selected) {
                    Box(
                        modifier = Modifier
                            .padding(top = Spacing.xxs)
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryAccordion(
    category: CodexCategoryEntity,
    entries: List<CodexEntryEntity>,
    onAddEntry: () -> Unit,
    onEntryClick: (CodexEntryEntity) -> Unit,
) {
    var expanded by remember(category.id) { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            ColorSwatch(color = colorOf(category.colorHex), size = 12.dp)
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Spacing.sm).weight(1f),
            )
            Text(
                text = entries.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry to ${category.name}")
            }
        }

        if (expanded) {
            entries.forEach { entry ->
                EntryRow(entry = entry, category = category, onClick = { onEntryClick(entry) })
            }
        }
    }
}

/** ~64dp row (spec §1.4): a 36dp rounded-square tile tinted at ~15% opacity with the category's
 * glyph (resolved from `category.icon` via [iconFor]), the entry's name in its own colour, a
 * two-line ellipsized body preview, and trailing globe/AI badges. */
@Composable
private fun EntryRow(entry: CodexEntryEntity, category: CodexCategoryEntity, onClick: () -> Unit) {
    val entryColor = colorOf(entry.colorHex ?: category.colorHex)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(CornerRadius.card))
                    .background(entryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = iconFor(category.icon), contentDescription = null, tint = entryColor, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.name, style = MaterialTheme.typography.bodyMedium, color = entryColor)
                    if (entry.scopeType == ScopeType.Series) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = "Series-wide",
                            modifier = Modifier.padding(start = Spacing.xs).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.isAiGenerated) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = "AI-generated",
                            modifier = Modifier.padding(start = Spacing.xs).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (entry.plainText.isNotBlank()) {
                    Text(
                        text = entry.plainText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, "#8B6FD1") }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewEntryDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New entry") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
