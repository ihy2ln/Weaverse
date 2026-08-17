package com.ihy2ln.weaverse.feature.novel.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.color
import com.ihy2ln.weaverse.data.db.entity.SceneEntity

private enum class ReviewTab { Pacing, Consistency }

/**
 * Review screen (spec §9): a pacing chart and a set of automated
 * consistency checks over the current book's scenes — see `ReviewViewModel`
 * for exactly which checks are implemented and which are scoped out this
 * pass (documented in BUILD_NOTES).
 */
@Composable
fun ReviewScreen(modifier: Modifier = Modifier, viewModel: ReviewViewModel = hiltViewModel()) {
    val scenes by viewModel.scenes.collectAsState()
    val issues by viewModel.issues.collectAsState()
    var tab by remember { mutableStateOf(ReviewTab.Pacing) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Text("Review", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))

        SingleChoiceSegmentedButtonRow {
            ReviewTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ReviewTab.entries.size),
                ) {
                    Text(entry.name)
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        if (scenes.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.RateReview,
                title = "Nothing to review yet",
                subtitle = "Add scenes from the Plan tab to see pacing and consistency checks.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        when (tab) {
            ReviewTab.Pacing -> PacingChart(scenes)
            ReviewTab.Consistency -> ConsistencyList(issues)
        }
    }
}

@Composable
private fun PacingChart(scenes: List<SceneEntity>) {
    val maxWordCount = remember(scenes) { scenes.maxOfOrNull { it.wordCount }?.coerceAtLeast(1) ?: 1 }
    val totalWords = remember(scenes) { scenes.sumOf { it.wordCount } }

    Text(
        "$totalWords words across ${scenes.size} scene(s)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.md))

    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val barGap = 4.dp.toPx()
        val barWidth = (size.width - barGap * (scenes.size - 1).coerceAtLeast(0)) / scenes.size
        scenes.forEachIndexed { index, scene ->
            val barHeight = (scene.wordCount.toFloat() / maxWordCount) * size.height
            drawRect(
                color = scene.status.color,
                topLeft = Offset(x = index * (barWidth + barGap), y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
            )
        }
    }
}

@Composable
private fun ConsistencyList(issues: List<ConsistencyIssue>) {
    if (issues.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.CheckCircle,
            title = "No issues found",
            subtitle = "Every scene has content, a unique title, and a recognized POV.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(items = issues, key = { it.sceneId + it.message }) { issue -> IssueRow(issue) }
    }
}

@Composable
private fun IssueRow(issue: ConsistencyIssue) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            val icon = if (issue.severity == IssueSeverity.Warning) Icons.Filled.Warning else Icons.Filled.Info
            val tint = if (issue.severity == IssueSeverity.Warning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = issue.severity.name, tint = tint)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(issue.sceneTitle, style = MaterialTheme.typography.titleSmall)
                Text(issue.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
