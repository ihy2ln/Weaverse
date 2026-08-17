package com.ihy2ln.weaverse.feature.novel.codex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(codexListBackground(tokens)),
    ) {
        Text(
            text = "Shared · ${state.entries.size} entries · every book & mode",
            modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            color = tokens.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        LazyColumn {
            items(state.grouped, key = { it.category.id }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleCategory(group.category.id) }
                        .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
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
                                        .clip(RoundedCornerShape(InkSpacing.radiusSm))
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
