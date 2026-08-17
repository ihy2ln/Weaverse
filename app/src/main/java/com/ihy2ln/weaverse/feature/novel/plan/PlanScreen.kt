package com.ihy2ln.weaverse.feature.novel.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.Spacing

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    onOpenScene: (String) -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val bookTitle by viewModel.bookTitle.collectAsState()
    val acts by viewModel.acts.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val bookWordCount by viewModel.bookWordCount().collectAsState(initial = 0)
    var newActDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Text(bookTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            "$bookWordCount words",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SingleChoiceSegmentedButtonRow {
                PlanViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PlanViewMode.entries.size),
                    ) {
                        Text(mode.name)
                    }
                }
            }
            TextButton(onClick = { newActDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Act", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        if (acts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = "No acts yet",
                subtitle = "Start structuring your manuscript with an act.",
                actionLabel = "New act",
                onAction = { newActDialogOpen = true },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(items = acts, key = { it.id }) { act ->
                    when (viewMode) {
                        PlanViewMode.Grid -> GridActSection(act = act, viewModel = viewModel, onOpenScene = onOpenScene)
                        PlanViewMode.Outline -> OutlineActSection(act = act, viewModel = viewModel, onOpenScene = onOpenScene)
                    }
                }
            }
        }
    }

    if (newActDialogOpen) {
        SimpleNameDialog(
            title = "New act",
            onDismiss = { newActDialogOpen = false },
            onCreate = { name -> viewModel.addAct(name); newActDialogOpen = false },
        )
    }
}

@Composable
internal fun SimpleNameDialog(title: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
