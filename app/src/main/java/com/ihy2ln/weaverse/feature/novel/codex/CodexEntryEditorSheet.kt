package com.ihy2ln.weaverse.feature.novel.codex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.core.media.rememberMediaPickerActions
import com.ihy2ln.weaverse.core.media.ui.InlineVideoPlayer
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.toDocument
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.toPlainText
import com.ihy2ln.weaverse.core.ui.ColorPickerDialog
import com.ihy2ln.weaverse.core.ui.ColorSwatch
import com.ihy2ln.weaverse.core.ui.InkChip
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.core.ui.toHex
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.LorePosition
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaType
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SelectiveLogic
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * General / Lore / Mentions editor for one codex entry (spec §9). The body
 * is a plain multi-line text field for now, not the Phase 5/6 `BlockEditor`
 * — wiring that in needs a `MediaRepository` threaded through a Compose
 * entry point this sheet doesn't have yet (see BUILD_NOTES.md "Codex body
 * editor"); Phase 10 does that integration for the Write screen and can
 * extend it here too.
 *
 * Takes [entryId] rather than a snapshot `CodexEntryEntity` and observes it
 * live from [viewModel] — every field write (alias add/remove, toggles,
 * color) reads the *current* entry off that live Flow before copying, so
 * rapid successive edits within one sheet session don't clobber each other
 * against a stale snapshot.
 */
@Composable
fun CodexEntryEditorSheet(
    entryId: String,
    category: CodexCategoryEntity?,
    viewModel: CodexViewModel,
    onDismiss: () -> Unit,
) {
    val entry by remember(entryId) { viewModel.observeEntry(entryId) }.collectAsState(initial = null)
    val current = entry ?: return

    var selectedTab by remember(entryId) { mutableStateOf(0) }

    InkModalBottomSheet(
        onDismiss = onDismiss,
        title = current.name,
        actions = { EntryAdminMenu(entry = current, viewModel = viewModel, onDismissSheet = onDismiss) },
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("General") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Advanced") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Mentions") })
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        when (selectedTab) {
            0 -> GeneralTab(entry = current, category = category, viewModel = viewModel)
            1 -> LoreTab(entry = current, viewModel = viewModel)
            else -> MentionsTab(entry = current, viewModel = viewModel)
        }
    }
}

@Composable
private fun GeneralTab(entry: CodexEntryEntity, category: CodexCategoryEntity?, viewModel: CodexViewModel) {
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var body by remember(entry.id) { mutableStateOf(entry.docJson.toDocument().toPlainText().ifBlank { entry.plainText }) }
    var newAlias by remember(entry.id) { mutableStateOf("") }
    var colorPickerOpen by remember { mutableStateOf(false) }

    fun save(update: CodexEntryEntity.() -> CodexEntryEntity) {
        viewModel.updateEntry(entry.update())
    }

    val fallbackColor = category?.colorHex?.let { parseHex(it) } ?: Color.Gray
    val entryColor = entry.colorHex?.let { parseHex(it) } ?: fallbackColor

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        EntryMediaSection(entry = entry, viewModel = viewModel, onSave = ::save)

        Spacer(modifier = Modifier.height(Spacing.md))
        Text("Aliases", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
            items(entry.aliases) { alias ->
                InkChip(label = alias, color = fallbackColor, onRemove = { save { copy(aliases = aliases - alias) } })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            OutlinedTextField(
                value = newAlias,
                onValueChange = { newAlias = it },
                placeholder = { Text("Add alias") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                if (newAlias.isNotBlank()) {
                    save { copy(aliases = aliases + newAlias.trim()) }
                    newAlias = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add alias")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color override", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            ColorSwatch(color = entryColor, onClick = { colorPickerOpen = true })
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Always include", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = entry.alwaysInclude, onCheckedChange = { save { copy(alwaysInclude = it) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Disabled", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = entry.disabled, onCheckedChange = { save { copy(disabled = it) } })
        }

        val currentSeriesId by viewModel.currentSeriesId.collectAsState()
        if (currentSeriesId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Series-wide", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Visible and injectable in every book in this series",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = entry.scopeType == ScopeType.Series,
                    onCheckedChange = { viewModel.setEntrySeriesWide(entry, it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Text("Body", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            minLines = 4,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
    }

    LaunchedEffect(name, body) {
        val currentPlainText = entry.docJson.toDocument().toPlainText()
        if (name != entry.name || body != currentPlainText) {
            val document = Document(listOf(Paragraph(newId(), listOf(Span(body)))))
            save { copy(name = name, docJson = document.toJson(), plainText = document.toPlainText()) }
        }
    }

    if (colorPickerOpen) {
        ColorPickerDialog(
            initialColor = entryColor,
            onDismiss = { colorPickerOpen = false },
            onColorSelected = { color ->
                save { copy(colorHex = color.toHex()) }
                colorPickerOpen = false
            },
        )
    }
}

/**
 * The Advanced tab (spec Revision 02 §2): SillyTavern's World Info fields
 * ([CodexEntryLoreEntity]'s `keys`/`secondaryKeys`/`insertionOrder`/
 * `position`/`depth`/`probability`/`isConstant`/`recursionAllowed`/
 * `selectiveLogic`), in plain language with the technical name as helper
 * text underneath each field — this is the *entire* rename's substance for
 * the entry editor: same underlying fields, no schema change, just labels a
 * non-SillyTavern-user can read.
 */
@Composable
private fun LoreTab(entry: CodexEntryEntity, viewModel: CodexViewModel) {
    var lore by remember(entry.id) { mutableStateOf<CodexEntryLoreEntity?>(null) }
    LaunchedEffect(entry.id) {
        lore = viewModel.getLore(entry.id) ?: CodexEntryLoreEntity(entryId = entry.id)
    }

    val current = lore ?: return
    var keysText by remember(entry.id) { mutableStateOf(current.keys.joinToString(", ")) }
    var secondaryKeysText by remember(entry.id) { mutableStateOf(current.secondaryKeys.joinToString(", ")) }
    var insertionOrderText by remember(entry.id) { mutableStateOf(current.insertionOrder.toString()) }
    var depthText by remember(entry.id) { mutableStateOf(current.depth.toString()) }
    var probabilityText by remember(entry.id) { mutableStateOf(current.probability.toString()) }

    fun saveLore(update: CodexEntryLoreEntity.() -> CodexEntryLoreEntity) {
        val updated = current.update()
        lore = updated
        viewModel.upsertLore(updated)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Track this entry by name/alias", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Detected automatically in your prose, in the AI prompt, and made tappable in generated text. Turn off for a name/alias that's also a common word.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = current.trackByNameAlias, onCheckedChange = { saveLore { copy(trackByNameAlias = it) } })
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        LabeledField(label = "Trigger words", helper = "keys") {
            OutlinedTextField(
                value = keysText,
                onValueChange = { text ->
                    keysText = text
                    saveLore { copy(keys = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
                },
                placeholder = { Text("Comma-separated") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        LabeledField(label = "Also requires", helper = "secondary keys") {
            OutlinedTextField(
                value = secondaryKeysText,
                onValueChange = { text ->
                    secondaryKeysText = text
                    saveLore { copy(secondaryKeys = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
                },
                placeholder = { Text("Comma-separated") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        LabeledField(label = "Match logic", helper = "selective logic") {
            EnumPicker(
                value = current.selectiveLogic,
                options = SelectiveLogic.entries,
                onSelect = { saveLore { copy(selectiveLogic = it) } },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        LabeledField(label = "Injection position", helper = "position") {
            EnumPicker(
                value = current.position,
                options = LorePosition.entries,
                onSelect = { saveLore { copy(position = it) } },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        Row {
            LabeledField(label = "Injection depth", helper = "depth", modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = depthText,
                    onValueChange = { text ->
                        depthText = text
                        text.toIntOrNull()?.let { saveLore { copy(depth = it) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            LabeledField(label = "Priority", helper = "insertion order", modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = insertionOrderText,
                    onValueChange = { text ->
                        insertionOrderText = text
                        text.toIntOrNull()?.let { saveLore { copy(insertionOrder = it) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        LabeledField(label = "Chance to trigger (%)", helper = "probability") {
            OutlinedTextField(
                value = probabilityText,
                onValueChange = { text ->
                    probabilityText = text
                    text.toIntOrNull()?.let { saveLore { copy(probability = it.coerceIn(0, 100)) } }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Always include", style = MaterialTheme.typography.bodyLarge)
                Text("constant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = current.isConstant, onCheckedChange = { saveLore { copy(isConstant = it) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Case sensitive", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = current.caseSensitive, onCheckedChange = { saveLore { copy(caseSensitive = it) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Match whole words", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = current.matchWholeWords, onCheckedChange = { saveLore { copy(matchWholeWords = it) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Follow references", style = MaterialTheme.typography.bodyLarge)
                Text("recursion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = current.recursionAllowed, onCheckedChange = { saveLore { copy(recursionAllowed = it) } })
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
private fun LabeledField(label: String, helper: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
        Text(helper, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T : Enum<T>> EnumPicker(value: T, options: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(value.name)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

/** Photo/video attached to a codex entry (spec §9 "entries can carry an illustration") —
 * previously `imageMediaId` had no Compose entry point to import into (see BUILD_NOTES
 * "Phase 7 deviations/gaps"). Accepts either an image or a video, rendered accordingly. */
@Composable
private fun EntryMediaSection(
    entry: CodexEntryEntity,
    viewModel: CodexViewModel,
    onSave: (CodexEntryEntity.() -> CodexEntryEntity) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var media by remember(entry.imageMediaId) { mutableStateOf<MediaEntity?>(null) }
    LaunchedEffect(entry.imageMediaId) {
        media = entry.imageMediaId?.let { viewModel.mediaRepository.getById(it) }
    }
    val mediaPickerActions = rememberMediaPickerActions(onPicked = { uris ->
        scope.launch {
            uris.firstOrNull()?.let { uri ->
                val imported = viewModel.importEntryMedia(uri)
                onSave { copy(imageMediaId = imported.id) }
            }
        }
    })

    Text("Photo or video", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(Spacing.xs))

    val current = media
    if (current != null) {
        val fileUri = remember(current.relativePath) { Uri.fromFile(MediaPaths.resolve(context, current.relativePath)) }
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            if (current.type == MediaType.Video) {
                InlineVideoPlayer(
                    mediaId = current.id,
                    uri = fileUri,
                    muted = true,
                    loop = true,
                    autoplay = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AsyncImage(
                    model = fileUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Surface(
                onClick = { onSave { copy(imageMediaId = null) } },
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.5f),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove photo/video",
                    tint = Color.White,
                    modifier = Modifier.padding(Spacing.xxs),
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        TextButton(onClick = mediaPickerActions.pickImagesAndVideos) { Text("Change photo or video") }
    } else {
        TextButton(onClick = mediaPickerActions.pickImagesAndVideos) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Text("Add photo or video", modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun MentionsTab(entry: CodexEntryEntity, viewModel: CodexViewModel) {
    val sceneIds by remember(entry.id) { viewModel.observeMentioningSceneIds(entry.id) }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Text(
            text = if (sceneIds.isEmpty()) "Not linked to any scenes yet." else "Appears in ${sceneIds.size} scene(s).",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        // Scene titles aren't resolved here — Plan/Write navigation (Phase
        // 10) is what makes "tap to jump" meaningful; for now this just
        // proves the link data is there.
        sceneIds.forEach { sceneId ->
            Text(
                text = "Scene $sceneId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

/**
 * The entry editor's header cog wheel — previously present only at the Codex rail level and
 * wired to nothing (`IconButton(onClick = {})`); this is the per-entry admin menu a Novelcrafter-
 * style codex needs: quick active/inactive, name/alias tracking, duplicate, and delete, without
 * digging into the General/Advanced tabs for the two toggles that live there too.
 */
@Composable
private fun EntryAdminMenu(entry: CodexEntryEntity, viewModel: CodexViewModel, onDismissSheet: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lore by remember(entry.id) { mutableStateOf<CodexEntryLoreEntity?>(null) }
    LaunchedEffect(entry.id) {
        lore = viewModel.getLore(entry.id) ?: CodexEntryLoreEntity(entryId = entry.id)
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Entry options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (entry.disabled) "Mark active" else "Mark inactive") },
                leadingIcon = {
                    Icon(if (entry.disabled) Icons.Filled.CheckCircle else Icons.Filled.Cancel, contentDescription = null)
                },
                onClick = {
                    viewModel.updateEntry(entry.copy(disabled = !entry.disabled))
                    expanded = false
                },
            )
            lore?.let { currentLore ->
                DropdownMenuItem(
                    text = { Text(if (currentLore.trackByNameAlias) "Stop tracking name/alias" else "Track by name/alias") },
                    leadingIcon = { Icon(Icons.Filled.TrackChanges, contentDescription = null) },
                    onClick = {
                        val updated = currentLore.copy(trackByNameAlias = !currentLore.trackByNameAlias)
                        lore = updated
                        viewModel.upsertLore(updated)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = {
                    viewModel.duplicateEntry(entry) {}
                    expanded = false
                    onDismissSheet()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    confirmDelete = true
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${entry.name}\"?") },
            text = { Text("This removes the entry and its lore settings. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry)
                    confirmDelete = false
                    onDismissSheet()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
