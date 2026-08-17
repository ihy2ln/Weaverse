package com.ihy2ln.weaverse.feature.novel.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.text.MentionCandidate
import com.ihy2ln.weaverse.core.ui.CodexMentionText
import com.ihy2ln.weaverse.core.ui.FormatPickerDialog
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatRole
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryEditorSheet
import com.ihy2ln.weaverse.feature.novel.codex.CodexViewModel
import kotlinx.coroutines.launch

/**
 * Workshop Chat screen (spec §8/§10): a real streaming conversation backed by
 * [ChatViewModel] — [ChatViewModel.currentProfile] falls back to an unsaved,
 * key-less connection profile when the user hasn't set one up yet, which
 * surfaces a real, explicit "no API key configured" error rather than
 * generating anything; real generations only ever happen once a profile with
 * a working API key exists.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    codexViewModel: CodexViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val mentionCandidates by viewModel.bookMentionCandidates.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPickerOpen by remember { mutableStateOf(false) }
    var importPickerOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var connectionRowExpanded by remember { mutableStateOf(false) }
    var openCodexEntryId by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = viewModel.exportChat(format)
                if (bytes == null) {
                    status = "No chat to export yet."
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
                    runCatching { viewModel.importChat(bytes, format) }
                        .fold(
                            onSuccess = { count -> if (count == null) "No chat to import into yet." else "Imported $count message(s)." },
                            onFailure = { "That file isn't a valid ${format.label} chat export." },
                        )
                }
            }
        }
    }

    LaunchedEffect(messages.size, streamingText) {
        val lastIndex = messages.size + if (streamingText != null) 1 else 0
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { exportPickerOpen = true }) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Text("Export", modifier = Modifier.padding(start = Spacing.xs))
            }
            TextButton(onClick = { importPickerOpen = true }) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Text("Import", modifier = Modifier.padding(start = Spacing.xs))
            }
            Spacer(modifier = Modifier.weight(1f))
            // Connection settings collapse behind one small icon by default — a profile label
            // and a model id can both be long, and showing both expanded at all times crowded
            // out the rest of the header (reported: "taking up less of the ui"). Tap to reveal
            // the compact pickers on the row below.
            IconButton(onClick = { connectionRowExpanded = !connectionRowExpanded }) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = if (connectionRowExpanded) "Hide connection settings" else "Show connection settings",
                    tint = if (connectionRowExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Real model list sourced from the same call "Test connection" makes — this screen used
        // to have no way to choose either and silently sent the placeholder model id "default"
        // on every send (guaranteed to 400 on a real provider).
        if (connectionRowExpanded) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { profileMenuExpanded = true }, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                        Text(currentProfile.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                        if (profiles.isEmpty()) {
                            DropdownMenuItem(text = { Text("No connection profiles — add one in Settings") }, onClick = { profileMenuExpanded = false }, enabled = false)
                        }
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.label) },
                                onClick = { profileMenuExpanded = false; viewModel.selectProfile(profile.id) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { modelMenuExpanded = true }, enabled = availableModels.isNotEmpty(), contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                        val label = when {
                            selectedModelId.isNotBlank() -> selectedModelId
                            availableModels.isNotEmpty() -> "Auto (${availableModels.first().id})"
                            else -> "Fetching models…"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName.ifBlank { model.id }) },
                                onClick = { modelMenuExpanded = false; viewModel.selectModelId(model.id) },
                            )
                        }
                    }
                }
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = Spacing.md),
        ) {
            items(items = messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    mentionCandidates = mentionCandidates,
                    onOpenCodexEntry = { entryId -> openCodexEntryId = entryId },
                    onEdit = { editTarget = message },
                    onCopy = { clipboardManager.setText(AnnotatedString(message.plainText)) },
                    onDelete = { viewModel.deleteMessage(message) },
                )
            }
            streamingText?.let { text ->
                item(key = "streaming") { StreamingBubble(text) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message the workshop…") },
                enabled = !isSending,
            )
            IconButton(
                onClick = {
                    val text = input
                    input = ""
                    viewModel.sendMessage(text)
                },
                enabled = !isSending && input.isNotBlank(),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }

    if (exportPickerOpen) {
        FormatPickerDialog(
            title = "Export chat as…",
            onDismiss = { exportPickerOpen = false },
            onSelect = { format ->
                exportPickerOpen = false
                pendingFormat = format
                exportLauncher.launch("weaverse-chat.${format.extension}")
            },
        )
    }
    if (importPickerOpen) {
        FormatPickerDialog(
            title = "Import chat from…",
            onDismiss = { importPickerOpen = false },
            onSelect = { format ->
                importPickerOpen = false
                pendingFormat = format
                importLauncher.launch("*/*")
            },
        )
    }

    editTarget?.let { message ->
        EditChatMessageDialog(
            initialText = message.plainText,
            onDismiss = { editTarget = null },
            onSave = { newText -> viewModel.editMessage(message, newText); editTarget = null },
        )
    }

    openCodexEntryId?.let { entryId ->
        val categories by codexViewModel.categories.collectAsState()
        val openEntry by remember(entryId) { codexViewModel.observeEntry(entryId) }.collectAsState(initial = null)
        CodexEntryEditorSheet(
            entryId = entryId,
            category = categories.firstOrNull { it.id == openEntry?.categoryId },
            viewModel = codexViewModel,
            onDismiss = { openCodexEntryId = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    mentionCandidates: List<MentionCandidate>,
    onOpenCodexEntry: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var longPressMenuExpanded by remember(message.id) { mutableStateOf(false) }
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { longPressMenuExpanded = true }),
            ) {
                Text(
                    text = roleLabel(message.role),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Nested inside the card's own combinedClickable (long-press for the actions
                // menu below) — ClickableText's tap detector only claims taps that land on a
                // codex mention, so a plain tap/long-press elsewhere on the bubble still reaches
                // the outer gesture detector untouched. Not verified against a running device
                // (no emulator in this sandbox — see BUILD_NOTES's other Compose gesture gaps).
                CodexMentionText(
                    text = message.plainText,
                    candidates = mentionCandidates,
                    onEntryClick = onOpenCodexEntry,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(modifier = Modifier.padding(top = Spacing.xxs)) {
                    IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
                }
            }
            // Press-and-hold anywhere on the message opens the same actions as the icon row
            // above, alongside it rather than instead of it.
            DropdownMenu(expanded = longPressMenuExpanded, onDismissRequest = { longPressMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { longPressMenuExpanded = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = { longPressMenuExpanded = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { longPressMenuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun EditChatMessageDialog(initialText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StreamingBubble(text: String) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "AI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text.ifEmpty { "…" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun roleLabel(role: ChatRole): String = when (role) {
    ChatRole.User -> "You"
    ChatRole.Assistant -> "AI"
    ChatRole.System -> "System"
}
