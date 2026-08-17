package com.ihy2ln.weaverse.feature.novel.manuscript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity

/**
 * The rail's Manuscript tab (Revision 02 §1.4): a read-only act → chapter →
 * scene tree. Tapping a scene calls [onOpenScene] with its id — the rail
 * lists and selects, the content area on the right displays and edits (spec's
 * own rule for the whole panel), so this never opens an editor itself.
 */
@Composable
fun ManuscriptTab(
    modifier: Modifier = Modifier,
    onOpenScene: (String) -> Unit,
    viewModel: ManuscriptViewModel = hiltViewModel(),
) {
    val acts by viewModel.acts.collectAsState()

    if (acts.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.MenuBook,
            title = "No manuscript yet",
            subtitle = "Add an act from the Plan tab to start structuring your book.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = acts, key = { it.id }) { act ->
            ActNode(act = act, viewModel = viewModel, onOpenScene = onOpenScene)
        }
    }
}

@Composable
private fun ActNode(act: ActEntity, viewModel: ManuscriptViewModel, onOpenScene: (String) -> Unit) {
    var expanded by remember(act.id) { mutableStateOf(true) }
    val chapters by remember(act.id) { viewModel.chaptersForAct(act.id) }.collectAsState(initial = emptyList())

    Column {
        TreeRow(
            label = act.title,
            style = MaterialTheme.typography.titleSmall,
            expanded = expanded,
            expandable = true,
            onToggle = { expanded = !expanded },
        )
        if (expanded) {
            chapters.forEach { chapter ->
                ChapterNode(chapter = chapter, viewModel = viewModel, onOpenScene = onOpenScene, indent = 1)
            }
        }
    }
}

@Composable
private fun ChapterNode(chapter: ChapterEntity, viewModel: ManuscriptViewModel, onOpenScene: (String) -> Unit, indent: Int) {
    var expanded by remember(chapter.id) { mutableStateOf(true) }
    val scenes by remember(chapter.id) { viewModel.scenesForChapter(chapter.id) }.collectAsState(initial = emptyList())

    Column {
        TreeRow(
            label = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            expanded = expanded,
            expandable = true,
            onToggle = { expanded = !expanded },
            indent = indent,
        )
        if (expanded) {
            scenes.forEach { scene ->
                TreeRow(
                    label = scene.title,
                    style = MaterialTheme.typography.bodySmall,
                    icon = Icons.Filled.Description,
                    indent = indent + 1,
                    onClick = { onOpenScene(scene.id) },
                )
            }
        }
    }
}

@Composable
private fun TreeRow(
    label: String,
    style: TextStyle,
    indent: Int = 0,
    icon: ImageVector? = null,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Surface(onClick = onClick ?: onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg * indent, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expandable) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            Text(
                text = label,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
