package com.ihy2ln.weaverse.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import com.ihy2ln.weaverse.core.media.formatByteSize
import com.ihy2ln.weaverse.core.ui.AppTheme
import com.ihy2ln.weaverse.core.ui.FormatPickerDialog
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.NamedFontFamily
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.TypographySettings
import com.ihy2ln.weaverse.feature.novel.aiproviders.ConnectionProfilesScreen
import com.ihy2ln.weaverse.feature.novel.prompts.PromptLibraryScreen
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    Providers("AI Providers"),
    Prompts("Prompts"),
    Storage("Storage"),
    Data("Export & Import"),
}

/** Settings overlay (spec §12): Appearance, AI Providers, Prompts, Storage, Export/Import —
 * shown the same way as [com.ihy2ln.weaverse.feature.search.GlobalSearchScreen]. */
@Composable
fun SettingsScreen(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (section != null) section = null else onDismiss() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(section?.title ?: "Settings", style = MaterialTheme.typography.titleLarge)
            }

            when (section) {
                null -> SettingsMenu(onSelect = { section = it })
                SettingsSection.Appearance -> AppearanceSection()
                SettingsSection.Providers -> ConnectionProfilesScreen(modifier = Modifier.padding(horizontal = Spacing.lg))
                SettingsSection.Prompts -> PromptLibraryScreen(modifier = Modifier.padding(horizontal = Spacing.lg))
                SettingsSection.Storage -> StorageSection()
                SettingsSection.Data -> DataSection()
            }
        }
    }
}

@Composable
private fun SettingsMenu(onSelect: (SettingsSection) -> Unit) {
    val icons = mapOf(
        SettingsSection.Appearance to Icons.Filled.Palette,
        SettingsSection.Providers to Icons.Filled.SmartToy,
        SettingsSection.Prompts to Icons.Filled.TextFields,
        SettingsSection.Storage to Icons.Filled.Storage,
        SettingsSection.Data to Icons.Filled.CloudUpload,
    )
    LazyColumn(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = SettingsSection.entries, key = { it.name }) { entry ->
            InkCard(onClick = { onSelect(entry) }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icons.getValue(entry), contentDescription = null)
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.md).weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(viewModel: AppearanceViewModel = hiltViewModel()) {
    val appTheme by viewModel.appTheme.collectAsState()
    val typography by viewModel.typography.collectAsState()
    var fontMenuOpen by remember { mutableStateOf(false) }
    var customThemeSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        AppTheme.entries.forEach { theme ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
            ) {
                RadioButton(selected = appTheme == theme, onClick = { viewModel.setAppTheme(theme) })
                Text(theme.label, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        if (appTheme == AppTheme.Custom) {
            TextButton(onClick = { customThemeSheetOpen = true }) {
                Text("Edit custom theme…")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))
        Text("Font family", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { fontMenuOpen = true }) { Text(typography.fontFamily.label) }
        DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
            NamedFontFamily.entries.forEach { family ->
                DropdownMenuItem(
                    text = { Text(family.label) },
                    onClick = { viewModel.setTypography { copy(fontFamily = family) }; fontMenuOpen = false },
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Text("Font size: ${typography.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                viewModel.setTypography { copy(fontSizeSp = (fontSizeSp - 1f).coerceIn(TypographySettingsRange)) }
            }) { Text("-") }
            TextButton(onClick = {
                viewModel.setTypography { copy(fontSizeSp = (fontSizeSp + 1f).coerceIn(TypographySettingsRange)) }
            }) { Text("+") }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }

    if (customThemeSheetOpen) {
        CustomThemeEditorSheet(onDismiss = { customThemeSheetOpen = false }, viewModel = viewModel)
    }
}

private val TypographySettingsRange = TypographySettings.FontSizeRange

@Composable
private fun StorageSection(viewModel: StorageViewModel = hiltViewModel()) {
    val totalBytes by viewModel.totalBytes.collectAsState()
    val lastCleanupCount by viewModel.lastCleanupCount.collectAsState()
    val isCleaning by viewModel.isCleaning.collectAsState()

    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        Text("Media storage used: ${formatByteSize(totalBytes)}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(Spacing.md))
        TextButton(onClick = viewModel::cleanUpOrphanedMedia, enabled = !isCleaning) {
            Text(if (isCleaning) "Cleaning up…" else "Clean up orphaned media")
        }
        lastCleanupCount?.let { count ->
            Text(
                "Removed $count orphaned file(s).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataSection(viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var exportPickerOpen by remember { mutableStateOf(false) }
    var importPickerOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }

    // CreateDocument's mime type is fixed at launcher-creation time, but which format the user
    // wants varies per tap — "*/*" plus the format-specific filename extension (set at launch)
    // is simpler than juggling four separate launchers, and every file manager accepts it.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = viewModel.exportCurrentBook(format)
                if (bytes == null) {
                    status = "No book to export yet."
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    status = "Exported as ${format.label}."
                }
            }
        }
    }
    // "*/*" here too rather than format.mimeType — Android file providers tag extensions like
    // .md inconsistently (text/markdown vs text/plain vs text/x-markdown depending on OEM), so a
    // strict mime filter can hide the very file the user picked a format to import. The chosen
    // format still tells the importer how to parse the bytes; it just doesn't gate the picker.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                status = if (bytes == null) {
                    "Could not read that file."
                } else {
                    runCatching { viewModel.importBook(bytes, format) }
                        .fold(
                            onSuccess = { title -> "Imported \"$title\" as a new book." },
                            onFailure = { "That file isn't a valid ${format.label} book export." },
                        )
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        Text(
            "Export the current book's structure and codex, or import one back in, in JSON " +
                "(full fidelity), Markdown, HTML, or Word (.docx) format. Media and full " +
                "rich-text formatting aren't included in any format yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        TextButton(onClick = { exportPickerOpen = true }) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Text("Export current book", modifier = Modifier.padding(start = Spacing.xs))
        }
        TextButton(onClick = { importPickerOpen = true }) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null)
            Text("Import a book", modifier = Modifier.padding(start = Spacing.xs))
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm)) }
    }

    if (exportPickerOpen) {
        FormatPickerDialog(
            title = "Export as…",
            onDismiss = { exportPickerOpen = false },
            onSelect = { format ->
                exportPickerOpen = false
                pendingFormat = format
                exportLauncher.launch("weaverse-backup.${format.extension}")
            },
        )
    }
    if (importPickerOpen) {
        FormatPickerDialog(
            title = "Import from…",
            onDismiss = { importPickerOpen = false },
            onSelect = { format ->
                importPickerOpen = false
                pendingFormat = format
                importLauncher.launch("*/*")
            },
        )
    }
}
