package com.ihy2ln.weaverse.feature.novel.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ihy2ln.weaverse.core.ui.StatusStripeCard
import com.ihy2ln.weaverse.core.ui.color
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity

@Composable
internal fun GridActSection(act: ActEntity, viewModel: PlanViewModel, onOpenScene: (String) -> Unit) {
    val chapters by remember(act.id) { viewModel.chaptersForAct(act.id) }.collectAsState(initial = emptyList())
    var newChapterDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(act.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { newChapterDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add chapter to ${act.title}")
            }
        }
        chapters.forEach { chapter ->
            GridChapterSection(chapter = chapter, viewModel = viewModel, onOpenScene = onOpenScene)
        }
    }

    if (newChapterDialogOpen) {
        SimpleNameDialog(
            title = "New chapter",
            onDismiss = { newChapterDialogOpen = false },
            onCreate = { name -> viewModel.addChapter(act.id, name, chapters.size); newChapterDialogOpen = false },
        )
    }
}

@Composable
private fun GridChapterSection(chapter: ChapterEntity, viewModel: PlanViewModel, onOpenScene: (String) -> Unit) {
    val scenes by remember(chapter.id) { viewModel.scenesForChapter(chapter.id) }.collectAsState(initial = emptyList())
    val wordCount by remember(chapter.id) { viewModel.chapterWordCount(chapter.id) }.collectAsState(initial = 0)
    var newSceneDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${scenes.size} scene(s) — $wordCount words",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { newSceneDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add scene to ${chapter.title}")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            scenes.forEach { scene ->
                SceneCard(scene = scene, onClick = { onOpenScene(scene.id) })
            }
        }
    }

    if (newSceneDialogOpen) {
        SimpleNameDialog(
            title = "New scene",
            onDismiss = { newSceneDialogOpen = false },
            onCreate = { name -> viewModel.addScene(chapter.id, name, scenes.size); newSceneDialogOpen = false },
        )
    }
}

@Composable
private fun SceneCard(scene: SceneEntity, onClick: () -> Unit) {
    StatusStripeCard(stripeColor = scene.status.color, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(scene.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${scene.wordCount}w", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scene.pov.isNotBlank()) {
                Text(
                    "POV: ${scene.pov}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scene.summary.isNotBlank()) {
                Text(
                    scene.summary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}
