package com.ihy2ln.weaverse.feature.novel.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity

@Composable
internal fun OutlineActSection(act: ActEntity, viewModel: PlanViewModel, onOpenScene: (String) -> Unit) {
    var expanded by remember(act.id) { mutableStateOf(true) }
    val chapters by remember(act.id) { viewModel.chaptersForAct(act.id) }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlineRow(text = act.title, style = MaterialTheme.typography.titleMedium, expanded = expanded, onToggle = { expanded = !expanded })
        if (expanded) {
            chapters.forEach { chapter ->
                OutlineChapterRow(chapter = chapter, viewModel = viewModel, onOpenScene = onOpenScene)
            }
        }
    }
}

@Composable
private fun OutlineChapterRow(chapter: ChapterEntity, viewModel: PlanViewModel, onOpenScene: (String) -> Unit) {
    var expanded by remember(chapter.id) { mutableStateOf(true) }
    val scenes by remember(chapter.id) { viewModel.scenesForChapter(chapter.id) }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(start = Spacing.lg)) {
        OutlineRow(text = chapter.title, style = MaterialTheme.typography.titleSmall, expanded = expanded, onToggle = { expanded = !expanded })
        if (chapter.summary.isNotBlank()) {
            Text(
                chapter.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xxl),
            )
        }
        if (expanded) {
            scenes.forEach { scene ->
                Surface(onClick = { onOpenScene(scene.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(start = Spacing.xxl, top = Spacing.xs, bottom = Spacing.xs)) {
                        Text("${scene.title} (${scene.wordCount}w)", style = MaterialTheme.typography.bodyMedium)
                        if (scene.summary.isNotBlank()) {
                            Text(
                                scene.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        Text(text, style = style)
    }
}
