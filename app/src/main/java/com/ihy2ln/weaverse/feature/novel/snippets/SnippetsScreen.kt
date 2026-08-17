package com.ihy2ln.weaverse.feature.novel.snippets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.FormatPickerDialog
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import kotlinx.coroutines.launch

/** Snippets rail tab (hamburger menu → Snippets): reusable bits of prose, scoped to the
 * current book — never built before this (was a placeholder EmptyState since Phase 4). */
@Composable
fun SnippetsScreen(modifier: Modifier = Modifier, viewModel: SnippetsViewModel = hiltViewModel()) {
    val snippets by viewModel.snippets.collectAsState()
    val sortedSnippets = remember(snippets) { snippets.sortedWith(compareByDescending<SnippetEntity> { it.pinned }.thenBy { it.title }) }

    var newDialogOpen by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }
    var exportPickerOpen by remember { mutableStateOf(false) }
    var importPickerOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = viewModel.exportSnippets(format)
                if (bytes == null) {
                    status = "No book to export from yet."
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    status = "Exported as ${format.label}."
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                status = if (bytes == null) {
                    "Could not read that file."
                } else {
                    runCatching { viewModel.importSnippets(bytes, format) }
                        .fold(
                            onSuccess = { count -> if (count == null) "No book to import into yet." else "Imported $count snippet(s)." },
                            onFailure = { "That file isn't a valid ${format.label} snippets export." },
                        )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Snippets", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Row {
            TextButton(onClick = { exportPickerOpen = true }) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Text("Export", modifier = Modifier.padding(start = Spacing.xs))
            }
            TextButton(onClick = { importPickerOpen = true }) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Text("Import", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (sortedSnippets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = "No snippets yet",
                subtitle = "Save reusable bits of prose — voice notes, recurring SFX, anything you reach for often.",
                actionLabel = "New snippet",
                onAction = { newDialogOpen = true },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(items = sortedSnippets, key = { it.id }) { snippet ->
                    SnippetRow(
                        snippet = snippet,
                        onClick = { editingSnippet = snippet },
                        onTogglePin = { viewModel.togglePinned(snippet) },
                        onDelete = { viewModel.deleteSnippet(snippet) },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New snippet", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createSnippet(name); newDialogOpen = false })
    }

    editingSnippet?.let { snippet ->
        SnippetEditorSheet(snippet = snippet, onSave = viewModel::updateSnippet, onDismiss = { editingSnippet = null })
    }

    if (exportPickerOpen) {
        FormatPickerDialog(
            title = "Export snippets as…",
            onDismiss = { exportPickerOpen = false },
            onSelect = { format ->
                exportPickerOpen = false
                pendingFormat = format
                exportLauncher.launch("weaverse-snippets.${format.extension}")
            },
        )
    }
    if (importPickerOpen) {
        FormatPickerDialog(
            title = "Import snippets from…",
            onDismiss = { importPickerOpen = false },
            onSelect = { format ->
                importPickerOpen = false
                pendingFormat = format
                importLauncher.launch("*/*")
            },
        )
    }
}

@Composable
private fun SnippetRow(snippet: SnippetEntity, onClick: () -> Unit, onTogglePin: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(snippet.title, style = MaterialTheme.typography.titleSmall)
                if (snippet.body.isNotBlank()) {
                    Text(
                        snippet.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (snippet.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (snippet.pinned) "Unpin ${snippet.title}" else "Pin ${snippet.title}",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${snippet.title}")
            }
        }
    }
}

@Composable
private fun SnippetEditorSheet(snippet: SnippetEntity, onSave: (SnippetEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember(snippet.id) { mutableStateOf(snippet.title) }
    var body by remember(snippet.id) { mutableStateOf(snippet.body) }

    InkModalBottomSheet(
        onDismiss = {
            onSave(snippet.copy(title = title.ifBlank { snippet.title }, body = body))
            onDismiss()
        },
        title = "Edit snippet",
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Body") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}
