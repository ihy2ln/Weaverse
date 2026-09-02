package com.ihy2ln.weaverse.feature.novel.codex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.InkThemeTokens
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.parseHexColor

/** Codex entries use the chrome/prompt panel color so they match the navy theme, not the greyer page. */
fun codexListBackground(tokens: InkThemeTokens) = tokens.panel

@Composable
fun CodexRailScreen(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel = hiltViewModel(),
    onEntryClick: (String) -> Unit = {},
    selectedEntryId: String? = null,
    compact: Boolean = false,
    showSharedSummary: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var showAddCategory by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(codexListBackground(tokens)),
    ) {
        if (showSharedSummary) {
            Text(
                text = "Shared · ${state.entries.size} entries · every book & mode",
                modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                color = tokens.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        if (compact) {
            CompactCodexStrip(
                groups = state.grouped,
                selectedEntryId = selectedEntryId,
                onEntryClick = onEntryClick,
                onAddEntry = viewModel::addEntry,
                onAddCategory = { showAddCategory = true },
            )
        } else LazyColumn {
            items(state.grouped, key = { it.category.id }) { group ->
                CodexCategoryHeader(
                    label = group.category.name,
                    count = group.entries.size,
                    expanded = group.expanded,
                    onToggle = { viewModel.toggleCategory(group.category.id) },
                    onAdd = { viewModel.addEntry(group.category.id) },
                )
                AnimatedVisibility(
                    visible = group.expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(codexListBackground(tokens)),
                    ) {
                        group.entries.forEach { entry ->
                            CodexEntryPlate(
                                entry = entry,
                                selected = entry.id == selectedEntryId,
                                onClick = { onEntryClick(entry.id) },
                                onDelete = { viewModel.removeEntry(entry.id) },
                            )
                        }
                    }
                }
            }
            item(key = "add-category") {
                Text(
                    "+ New category",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddCategory = true }
                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.sm),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.secondaryText,
                )
            }
        }
    }
    if (showAddCategory) {
        AddCategoryDialog(
            onCreate = { name ->
                viewModel.addCategory(name)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false },
        )
    }
}

@Composable
private fun AddCategoryDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** One-line master list used while a Codex entry is open. */
@Composable
private fun CompactCodexStrip(
    groups: List<CodexCategoryGroup>,
    selectedEntryId: String?,
    onEntryClick: (String) -> Unit,
    onAddEntry: (String) -> Unit,
    onAddCategory: () -> Unit,
) {
    val tokens = inkTokens()
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val selectedEntryCategory = groups.firstOrNull { group ->
        group.entries.any { it.id == selectedEntryId }
    }?.category?.id
    LaunchedEffect(selectedEntryId, groups) {
        selectedCategoryId = when {
            selectedEntryCategory != null -> selectedEntryCategory
            groups.any { it.category.id == selectedCategoryId } -> selectedCategoryId
            else -> groups.firstOrNull()?.category?.id
        }
    }
    val activeGroup = groups.firstOrNull { it.category.id == selectedCategoryId }
        ?: groups.firstOrNull()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = 46.dp)
            .background(codexListBackground(tokens)),
        contentPadding = PaddingValues(horizontal = InkSpacing.xs, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "category-picker") {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.hover)
                        .clickable { categoryMenuOpen = true }
                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        activeGroup?.let { "${it.category.name} · ${it.entries.size}" } ?: "No categories",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text("▾", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${group.category.name} · ${group.entries.size}",
                                    fontWeight = if (group.category.id == activeGroup?.category?.id) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            },
                            onClick = {
                                selectedCategoryId = group.category.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "＋ New category…",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        onClick = {
                            categoryMenuOpen = false
                            onAddCategory()
                        },
                    )
                }
            }
        }
        activeGroup?.let { group ->
            item(key = "add-${group.category.id}") {
                Text(
                    "+",
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.hover)
                        .clickable { onAddEntry(group.category.id) }
                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            items(group.entries, key = { "entry-${it.id}" }) { entry ->
                    val tint = parseHexColor(entry.colorHex, MaterialTheme.colorScheme.primary)
                    val selected = entry.id == selectedEntryId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else tokens.hover.copy(alpha = 0.55f),
                            )
                            .clickable { onEntryClick(entry.id) }
                            .padding(horizontal = InkSpacing.xs, vertical = InkSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(tint.copy(alpha = 0.24f)),
                        )
                        Text(
                            entry.name.ifBlank { "Untitled" },
                            color = tint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
        }
    }
}
