package com.ihy2ln.weaverse.feature.novel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.feature.novel.write.WriteScreen
import com.ihy2ln.weaverse.feature.novel.plan.PlanScreen
import com.ihy2ln.weaverse.feature.novel.codex.CodexRailScreen
import com.ihy2ln.weaverse.feature.novel.chat.WorkshopChatScreen
import com.ihy2ln.weaverse.feature.novel.chat.WorkshopThreadsRail
import com.ihy2ln.weaverse.feature.novel.read.ReaderScreen
import com.ihy2ln.weaverse.feature.novel.review.ReviewScreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelWorkspaceScreen(title: String, initialSceneId: String, initialDestination: String,
    onExit: () -> Unit, onOpenCodexEntry: (String) -> Unit,
    onSettings: () -> Unit = {}, onExport: () -> Unit = {},
    viewModel: NovelWorkspaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var tab by rememberSaveable(state.bookId) { mutableStateOf(when (initialDestination) {
        "Chat" -> "Workshop"; "Read" -> "Read"; "Review" -> "Review"; else -> "Write"
    }) }
    var sceneId by rememberSaveable(state.bookId) { mutableStateOf(initialSceneId) }
    val scene = state.scenes.firstOrNull { it.id == sceneId } ?: state.scenes.firstOrNull()
    var picker by rememberSaveable { mutableStateOf(false) }
    var contextSheet by rememberSaveable { mutableStateOf(false) }
    var sceneDetails by remember { mutableStateOf<com.ihy2ln.weaverse.data.db.entities.SceneEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pendingMedia by rememberSaveable { mutableStateOf<String?>(null) }
    var threadId by rememberSaveable(state.bookId) { mutableStateOf<String?>(null) }
    val savedStates = rememberSaveableStateHolder()
    BackHandler { when { picker -> picker = false; tab != "Write" -> tab = "Write"; else -> onExit() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Books") }
                Column(Modifier.weight(1f).clickable { picker = true }.padding(horizontal = 4.dp)) {
                    Text(title.ifBlank { "Novel" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text(scene?.title ?: "Choose or create a scene", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                TextButton(onClick = { picker = true }) { Text("Scenes") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("More") }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("Read", "Review").forEach { target -> DropdownMenuItem(text = { Text(target) }, onClick = { tab = target; menu = false }) }
                        DropdownMenuItem(text = { Text("AI & app settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Export / import") }, onClick = { menu = false; onExport() })
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            savedStates.SaveableStateProvider("${state.bookId}:$tab:${if (tab == "Write") scene?.id.orEmpty() else ""}") {
                when (tab) {
                    "Write" -> if (scene != null) {
                        WriteScreen(sceneId = scene.id, onOpenCodexEntry = onOpenCodexEntry,
                            pendingMediaId = pendingMedia, onMediaConsumed = { pendingMedia = null },
                            onOpenMediaLibrary = { tab = "Media" })
                    } else Column(Modifier.padding(24.dp)) {
                        Text("Start with a scene", style = MaterialTheme.typography.headlineSmall)
                        Text("Build your chapter outline, then return here to write and add illustrations.")
                        Button(onClick = { tab = "Plan" }) { Text("Open planner") }
                    }
                    "Plan" -> PlanScreen(onWrite = { id, _ -> sceneId = id; tab = "Write" })
                    "Codex" -> Column {
                        TextButton(onClick = { contextSheet = true }, enabled = scene != null) { Text("Scene context · pin story knowledge") }
                        Box(Modifier.weight(1f)) { CodexRailScreen(onEntryClick = onOpenCodexEntry, showSharedSummary = false) }
                    }
                    "Media" -> NovelMediaPanel(state, scene?.id, status, busy, viewModel,
                        onInsert = { pendingMedia = it; tab = "Write" })
                    "Workshop" -> if (threadId == null) WorkshopThreadsRail(selectedThreadId = "", onThreadClick = { threadId = it })
                        else Column { TextButton(onClick = { threadId = null }) { Text("Workshop conversations") }; Box(Modifier.weight(1f)) { WorkshopChatScreen(threadId = threadId!!) } }
                    "Read" -> ReaderScreen()
                    "Review" -> ReviewScreen()
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Write", "Plan", "Codex", "Media", "Workshop").forEach { target ->
                    TextButton(onClick = { tab = target }, modifier = Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(target, maxLines = 1, softWrap = false, fontSize = 12.sp, color = if (tab == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    if (contextSheet) ModalBottomSheet(onDismissRequest = { contextSheet = false }) {
        Text("Context for ${scene?.title.orEmpty()}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        Text("Pinned entries join automatically detected story knowledge. Reference media is not uploaded.", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp)
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(state.entries, key = { it.id }) { entry ->
                val pinned = state.contextLinks.any { it.sceneId == scene?.id && it.entryId == entry.id }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pinned, { checked -> scene?.let { viewModel.pinContext(it.id, entry.id, checked) } })
                    Text(entry.name, Modifier.weight(1f))
                    TextButton(onClick = { contextSheet = false; onOpenCodexEntry(entry.id) }) { Text("Open") }
                }
            }
        }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
        Text("Scenes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(state.scenes, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text("${item.status} · ${item.wordCount} words · ${item.pov.ifBlank { "No POV" }}") },
                    modifier = Modifier.clickable { sceneId = item.id; tab = "Write"; picker = false })
                Row {
                    TextButton(onClick = { viewModel.moveScene(item.id, -1) }) { Text("Move up") }
                    TextButton(onClick = { viewModel.moveScene(item.id, 1) }) { Text("Move down") }
                    TextButton(onClick = { picker = false; sceneDetails = item }) { Text("Details") }
                }
            }
            item { TextButton(onClick = { tab = "Plan"; picker = false }) { Text("Manage chapters and scenes") } }
        }
    }
    sceneDetails?.let { current ->
        var summary by remember(current.id) { mutableStateOf(current.summary) }
        var pov by remember(current.id) { mutableStateOf(current.pov) }
        var status by remember(current.id) { mutableStateOf(current.status) }
        AlertDialog(onDismissRequest = { sceneDetails = null }, title = { Text(current.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(summary, { summary = it }, label = { Text("Scene summary / beats") }, minLines = 3)
                OutlinedTextField(pov, { pov = it }, label = { Text("Point of view") })
                OutlinedTextField(status, { status = it }, label = { Text("Writing status") })
            }
        }, confirmButton = { TextButton(onClick = { viewModel.updateScene(current, summary, pov, status); sceneDetails = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { sceneDetails = null }) { Text("Cancel") } })
    }
}

@Composable
private fun NovelMediaPanel(state: NovelWorkspaceState, sceneId: String?, status: String, busy: Boolean,
    viewModel: NovelWorkspaceViewModel, onInsert: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var pendingScene by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<com.ihy2ln.weaverse.data.db.entities.NovelMediaLink?>(null) }
    var remove by remember { mutableStateOf<com.ihy2ln.weaverse.data.db.entities.NovelMediaLink?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingScene?.let { if (uris.isNotEmpty()) viewModel.importReferences(uris, it) }; pendingScene = null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Story media", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(enabled = sceneId != null && !busy, onClick = {
                pendingScene = sceneId; importer.launch(arrayOf("image/*", "audio/*", "video/*"))
            }) { Text(if (busy) "Importing…" else "+ Reference") }
        }
        Text("Reference-only attachments stay out of your manuscript and AI requests until you choose to use them.", fontSize = 12.sp)
        if (status.isNotBlank()) Text(status, fontSize = 12.sp)
        OutlinedTextField(filter, { filter = it }, label = { Text("Search this novel’s media") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        LazyColumn(Modifier.weight(1f)) {
            items(state.assets.filter { it.name.contains(filter, true) }, key = { it.mediaId }) { asset ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        if (asset.kind == "image") AsyncImage(File(asset.path), contentDescription = asset.references.firstOrNull()?.altText?.ifBlank { asset.name } ?: asset.name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(160.dp))
                        Text(asset.name, style = MaterialTheme.typography.titleMedium)
                        Text("${asset.kind} · In story: ${asset.sceneIds.size} · References: ${asset.references.size}", fontSize = 12.sp)
                        if (!File(asset.path).exists()) Text("Original file is missing. Restore it from a backup.", color = MaterialTheme.colorScheme.error)
                        val usages = (asset.sceneIds + asset.references.map { it.sceneId }).distinct()
                        Text(usages.mapNotNull { id -> state.scenes.firstOrNull { it.id == id }?.title }.joinToString(" · "), fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(enabled = sceneId != null && File(asset.path).exists(), onClick = { onInsert(asset.mediaId) }) { Text("Insert in scene") }
                            TextButton(enabled = sceneId != null, onClick = { sceneId?.let { viewModel.linkReference(asset, it) } }) { Text("Link reference") }
                        }
                        asset.references.forEach { link ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(link.caption.ifBlank { "Reference" }, Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = { editing = link }) { Text("Details") }
                                TextButton(onClick = { remove = link }) { Text("Unlink") }
                            }
                        }
                    }
                }
            }
            if (state.assets.isEmpty()) item { Text("Import a reference, or add media while writing. Your novel’s linked assets appear here.", Modifier.padding(24.dp)) }
        }
    }
    editing?.let { link ->
        var caption by remember(link.id) { mutableStateOf(link.caption) }
        var alt by remember(link.id) { mutableStateOf(link.altText) }
        var entryId by remember(link.id) { mutableStateOf(link.entryId) }
        var entryMenu by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Reference details") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(caption, { caption = it }, label = { Text("Caption / notes") })
                OutlinedTextField(alt, { alt = it }, label = { Text("Accessible description") })
                Box {
                    TextButton(onClick = { entryMenu = true }) { Text("Codex: " + (state.entries.firstOrNull { it.id == entryId }?.name ?: "None")) }
                    DropdownMenu(entryMenu, { entryMenu = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { entryId = ""; entryMenu = false })
                        state.entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.name) }, onClick = { entryId = entry.id; entryMenu = false }) }
                    }
                }
                Text(link.provenance)
            }
        }, confirmButton = { TextButton(onClick = { viewModel.saveReference(link, caption, alt, entryId); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    remove?.let { link -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Unlink reference?") },
        text = { Text("Only this reference link is removed. The original file and story placements are kept.") },
        confirmButton = { TextButton(onClick = { viewModel.removeReference(link); remove = null }) { Text("Unlink") } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}
