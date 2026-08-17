package com.ihy2ln.weaverse.feature.novel.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.SeriesEntity

/**
 * The series picker/manager opened from [com.ihy2ln.weaverse.feature.shell.AppHeaderBar]'s series
 * line (spec §1.2: "tap the series line to open the series picker"). Combines what spec calls the
 * creation-time "New series / Add to existing series / Standalone" choice with the ongoing
 * "Series screen" (member list with reorder, both editable summaries) into one sheet — this app's
 * header only has room for one series-line tap target, not a separate dedicated destination.
 */
@Composable
fun SeriesSheet(onDismiss: () -> Unit, viewModel: SeriesViewModel = hiltViewModel()) {
    val currentSeries by viewModel.currentSeries.collectAsState()
    val series = currentSeries

    InkModalBottomSheet(onDismiss = onDismiss, title = "Series") {
        if (series == null) {
            NoSeriesContent(viewModel = viewModel)
        } else {
            SeriesDetailContent(series = series, viewModel = viewModel)
        }
    }
}

@Composable
private fun NoSeriesContent(viewModel: SeriesViewModel) {
    val allSeries by viewModel.allSeries.collectAsState()
    var newSeriesDialogOpen by remember { mutableStateOf(false) }
    var existingPickerOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        EmptyState(
            icon = Icons.Filled.AutoStories,
            title = "Standalone",
            subtitle = "This book isn't part of a series yet.",
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = { newSeriesDialogOpen = true }) { Text("New series") }
            TextButton(onClick = { existingPickerOpen = true }, enabled = allSeries.isNotEmpty()) { Text("Add to existing") }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }

    if (newSeriesDialogOpen) {
        NameEntryDialog(
            title = "New series",
            onDismiss = { newSeriesDialogOpen = false },
            onCreate = { title -> viewModel.createSeriesAndJoin(title); newSeriesDialogOpen = false },
        )
    }

    if (existingPickerOpen) {
        AlertDialog(
            onDismissRequest = { existingPickerOpen = false },
            title = { Text("Add to existing series") },
            text = {
                Column {
                    allSeries.forEach { series ->
                        TextButton(onClick = { viewModel.joinExistingSeries(series.id); existingPickerOpen = false }) {
                            Text(series.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { existingPickerOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeriesDetailContent(series: SeriesEntity, viewModel: SeriesViewModel) {
    val members by viewModel.members.collectAsState()
    var premise by remember(series.id) { mutableStateOf(series.premise) }
    var rollingSummary by remember(series.id) { mutableStateOf(series.rollingSummary) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Text(series.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
            value = premise,
            onValueChange = { premise = it; viewModel.updatePremise(it) },
            label = { Text("Premise") },
            placeholder = { Text("What's this series about, across every book in it?") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = rollingSummary,
            onValueChange = { rollingSummary = it; viewModel.updateRollingSummary(it) },
            label = { Text("Rolling summary") },
            placeholder = { Text("What's happened so far, across every book — edit by hand for now.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))

        Text("Members", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = members, key = { it.member.id }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.bookTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.moveMemberUp(row) }) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${row.bookTitle} earlier")
                    }
                    IconButton(onClick = { viewModel.moveMemberDown(row) }) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${row.bookTitle} later")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        TextButton(onClick = viewModel::leaveSeries) { Text("Remove this book from the series") }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}
