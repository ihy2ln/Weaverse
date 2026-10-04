package com.ihy2ln.weaverse.feature.templates

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.story.StartSlotStore
import com.ihy2ln.weaverse.core.story.StartTemplate
import com.ihy2ln.weaverse.core.story.StartTemplateKind
import com.ihy2ln.weaverse.core.story.StartTemplateLibrary
import com.ihy2ln.weaverse.core.story.startTemplateFileName
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class StartTemplatesViewModel @Inject constructor(
    private val library: StartTemplateLibrary,
) : ViewModel() {
    private val _templates = MutableStateFlow<List<StartTemplate>>(emptyList())
    val templates: StateFlow<List<StartTemplate>> = _templates
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message
    val folder: StateFlow<Uri?> = library.folder

    private var kind = StartTemplateKind.Novel

    fun load(kind: StartTemplateKind) {
        this.kind = kind
        refresh()
    }

    private fun refresh() = viewModelScope.launch { _templates.value = library.list(kind) }

    fun rename(id: String, name: String) = viewModelScope.launch { library.rename(id, name); refresh() }

    fun delete(id: String) = viewModelScope.launch { library.delete(id); refresh() }

    fun import(uri: Uri) = viewModelScope.launch {
        val template = library.import(uri)
        _message.value = when {
            template == null -> "That file isn't a Weaverse start template."
            template.kind != kind.key -> "Imported \"${template.name}\" (a ${StartTemplateKind.of(template.kind)?.label} template)."
            else -> "Imported \"${template.name}\"."
        }
        refresh()
    }

    fun export(id: String, uri: Uri) = viewModelScope.launch {
        _message.value = if (library.export(id, uri)) "Exported." else "Couldn't write that file."
    }

    fun chooseFolder(uri: Uri) = viewModelScope.launch {
        library.chooseFolder(uri)
        _message.value = "Templates are kept in ${library.folderLabel(uri)} too."
        refresh()
    }

    fun folderLabel(uri: Uri?) = library.folderLabel(uri)

    fun fileName(template: StartTemplate) = startTemplateFileName(template)
}

/**
 * Choose which start a new Novel or RPG campaign begins from: the built-in one or any saved
 * template. Templates can be renamed, exported, deleted and imported here, and kept in a shared
 * folder so the release and debug builds see the same ones.
 */
@Composable
fun StartTemplatePickerDialog(
    kind: StartTemplateKind,
    onPick: (templateId: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StartTemplatesViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind) { viewModel.load(kind) }
    val templates by viewModel.templates.collectAsState()
    val message by viewModel.message.collectAsState()
    val folder by viewModel.folder.collectAsState()
    val tokens = inkTokens()
    var renaming by remember { mutableStateOf<StartTemplate?>(null) }
    var deleting by remember { mutableStateOf<StartTemplate?>(null) }
    var exporting by remember { mutableStateOf<StartTemplate?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val template = exporting
        if (uri != null && template != null) viewModel.export(template.id, uri)
        exporting = null
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::chooseFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${kind.label} templates") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pick the start this ${if (kind == StartTemplateKind.Novel) "book" else "campaign"} begins from.", color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall)
                TemplateRow("Built-in start", "Comes with Weaverse", onClick = { onPick(StartSlotStore.BUILT_IN_TEMPLATE, "") })
                templates.forEach { template ->
                    TemplateRow(
                        title = template.name,
                        subtitle = "Saved " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(template.savedAt)),
                        onClick = { onPick(template.id, template.name) },
                        onRename = { renaming = template },
                        onExport = { exporting = template; exporter.launch(viewModel.fileName(template)) },
                        onDelete = { deleting = template },
                    )
                }
                if (templates.isEmpty()) {
                    Text(
                        "No saved templates yet. Use \"Save as template\" in a start, or import a template file.",
                        color = tokens.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import template file")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Templates folder", style = MaterialTheme.typography.labelLarge)
                        Text(viewModel.folderLabel(folder), color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text(if (folder == null) "Choose" else "Change") }
                }
                Text(
                    "Choose the same folder (e.g. Documents/Weaverse/Templates) in every Weaverse build and they share their templates.",
                    color = tokens.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (message.isNotBlank()) Text(message, color = tokens.activePill, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    renaming?.let { template ->
        TemplateNameDialog(
            title = "Rename template",
            initial = template.name,
            confirm = "Rename",
            onConfirm = { viewModel.rename(template.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${template.name}\"?") },
            text = { Text("It is removed from this app and from the templates folder. Export it first to keep a copy.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(template.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TemplateRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val tokens = inkTokens()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onRename != null || onExport != null || onDelete != null) {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Template actions") }
            DropdownMenu(menu, { menu = false }) {
                onRename?.let { DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; it() }) }
                onExport?.let { DropdownMenuItem(text = { Text("Export file") }, onClick = { menu = false; it() }) }
                onDelete?.let { DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; it() }) }
            }
        }
    }
}

/** Asks for a template's name (saving or renaming). */
@Composable
fun TemplateNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
