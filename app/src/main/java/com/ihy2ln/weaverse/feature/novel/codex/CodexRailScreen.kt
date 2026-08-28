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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
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
            )
        } else LazyColumn {
            items(state.grouped, key = { it.category.id }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleCategory(group.category.id) }
                        .padding(
                            horizontal = InkSpacing.md,
                            vertical = if (compact) InkSpacing.xs else InkSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (group.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (group.expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "${group.category.name} · ${group.entries.size}",
                        modifier = Modifier.weight(1f).padding(start = InkSpacing.xs),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    InkTextButton(
                        label = "+",
                        onClick = { viewModel.addEntry(group.category.id) },
                    )
                }
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(codexListBackground(tokens))
                                    .clickable { onEntryClick(entry.id) }
                                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val tint = parseHexColor(entry.colorHex, MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(InkSpacing.iconTile)
                                        .clip(RoundedCornerShape(inkRadiusSm()))
                                        .background(tint.copy(alpha = 0.15f)),
                                )
                                Column(modifier = Modifier.padding(start = InkSpacing.md).weight(1f)) {
                                    Text(
                                        entry.name,
                                        color = tint,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                    Text(
                                        entry.plainText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = tokens.secondaryText,
                                        fontSize = 12.sp,
                                    )
                                }
                                InkDeleteButton(
                                    itemName = entry.name,
                                    onConfirmedDelete = { viewModel.removeEntry(entry.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One-line master list used while a Codex entry is open. */
@Composable
private fun CompactCodexStrip(
    groups: List<CodexCategoryGroup>,
    selectedEntryId: String?,
    onEntryClick: (String) -> Unit,
    onAddEntry: (String) -> Unit,
) {
    val tokens = inkTokens()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = 46.dp)
            .background(codexListBackground(tokens)),
        contentPadding = PaddingValues(horizontal = InkSpacing.xs, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEach { group ->
            item(key = "category-${group.category.id}") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.hover)
                        .padding(start = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${group.category.name} · ${group.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        "+",
                        modifier = Modifier
                            .clickable { onAddEntry(group.category.id) }
                            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
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
