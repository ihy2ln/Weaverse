package com.ihy2ln.weaverse.feature.novel.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

data class ReviewIssue(val title: String, val detail: String)

@Composable
fun ReviewScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val issues by viewModel.issues.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Review", style = MaterialTheme.typography.titleLarge)
        Text(
            "Consistency checks on seeded manuscript",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = InkSpacing.md),
        )
        LazyColumn {
            items(issues) { issue ->
                InkCard(modifier = Modifier.padding(vertical = InkSpacing.sm)) {
                    Text(issue.title, style = MaterialTheme.typography.titleMedium)
                    Text(issue.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
