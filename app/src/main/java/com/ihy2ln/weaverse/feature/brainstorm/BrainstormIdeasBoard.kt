package com.ihy2ln.weaverse.feature.brainstorm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.db.entities.BrainstormIdeaEntity

/** Global idea library; books and campaigns are optional links on individual cards. */
@Composable
fun BrainstormIdeasBoard(
    compact: Boolean,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrainstormIdeasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var bookFilter by rememberSaveable { mutableStateOf("") }
    var filterMenu by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var promotingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.status) {
        if (state.status.isNotBlank()) {
            Toast.makeText(context, state.status, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }
    val visible = state.ideas.filter { idea ->
        (bookFilter.isBlank() || idea.bookId == bookFilter) &&
            (query.isBlank() || listOf(idea.title, idea.premise, idea.strengths, idea.risks)
                .any { it.contains(query, ignoreCase = true) })
    }

    Column(modifier = modifier.fillMaxSize().padding(InkSpacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ideas", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::createIdea) { Text("+ New idea") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Search ideas") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { filterMenu = true }) {
                Text(state.books.firstOrNull { it.id == bookFilter }?.title ?: "All projects")
            }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                DropdownMenuItem(text = { Text("All projects") }, onClick = { bookFilter = ""; filterMenu = false })
                state.books.forEach { book ->
                    DropdownMenuItem(text = { Text(book.title) }, onClick = {
                        bookFilter = book.id; filterMenu = false
                    })
                }
            }
            Text("${visible.size} saved", style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText, modifier = Modifier.padding(top = 15.dp))
        }
        if (visible.isEmpty()) {
            InkCard(modifier = Modifier.fillMaxWidth()) {
                Text("Ideas you choose in Brainstorm appear here. Explore alternatives in chat, or start a new idea.")
            }
        } else if (compact) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                BRAINSTORM_LANES.forEach { lane ->
                    val cards = visible.filter { it.status == lane }
                    Text("$lane · ${cards.size}", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = InkSpacing.sm))
                    cards.forEach { idea ->
                        IdeaCard(idea, state.books.firstOrNull { it.id == idea.bookId }?.title,
                            onEdit = { editingId = idea.id }, onPromote = { promotingId = idea.id },
                            onPin = { viewModel.togglePin(idea) }, onMove = { viewModel.move(idea, it) },
                            onSource = { idea.sourceThreadId?.let(onOpenSource) },
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                BRAINSTORM_LANES.forEach { lane ->
                    val cards = visible.filter { it.status == lane }
                    Column(modifier = Modifier.width(285.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                        .verticalScroll(rememberScrollState()).padding(InkSpacing.xs)) {
                        Text("$lane · ${cards.size}", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(InkSpacing.xs))
                        cards.forEach { idea ->
                            IdeaCard(idea, state.books.firstOrNull { it.id == idea.bookId }?.title,
                                onEdit = { editingId = idea.id }, onPromote = { promotingId = idea.id },
                                onPin = { viewModel.togglePin(idea) }, onMove = { viewModel.move(idea, it) },
                                onSource = { idea.sourceThreadId?.let(onOpenSource) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.ideas.firstOrNull { it.id == editingId }?.let { idea ->
        IdeaEditorDialog(idea, state.books.map { it.id to it.title },
            onSave = { viewModel.save(it); editingId = null }, onDismiss = { editingId = null },
            onDelete = { deletingId = idea.id; editingId = null })
    }
    state.ideas.firstOrNull { it.id == promotingId }?.let { idea ->
        IdeaPromoteDialog(idea, state, viewModel, onDismiss = { promotingId = null })
    }
    if (deletingId != null) {
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("Delete idea?") },
            text = { Text("This saved idea will be removed from the board.") },
            confirmButton = { TextButton(onClick = { deletingId?.let(viewModel::delete); deletingId = null }) {
                Text("Delete")
            } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } })
    }
}

@Composable
private fun IdeaCard(
    idea: BrainstormIdeaEntity,
    bookTitle: String?,
    onEdit: () -> Unit,
    onPromote: () -> Unit,
    onPin: () -> Unit,
    onMove: (String) -> Unit,
    onSource: () -> Unit,
) {
    var moveMenu by remember { mutableStateOf(false) }
    InkCard(modifier = Modifier.fillMaxWidth().padding(bottom = InkSpacing.xs)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text((if (idea.pinned) "★ " else "") + idea.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            if (bookTitle != null) Text(bookTitle, style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText)
            if (idea.premise.isNotBlank()) Text(idea.premise, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = InkSpacing.xs))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onPin) { Text(if (idea.pinned) "Unpin" else "Pin") }
                TextButton(onClick = { moveMenu = true }) { Text("Move") }
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    BRAINSTORM_LANES.forEach { lane ->
                        DropdownMenuItem(text = { Text(lane) }, onClick = { onMove(lane); moveMenu = false })
                    }
                }
                TextButton(onClick = onPromote) { Text("Send to…") }
                if (idea.sourceThreadId != null) TextButton(onClick = onSource) { Text("Source") }
            }
        }
    }
}

@Composable
private fun IdeaEditorDialog(
    idea: BrainstormIdeaEntity,
    books: List<Pair<String, String>>,
    onSave: (BrainstormIdeaEntity) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(idea.id) { mutableStateOf(idea.title) }
    var premise by remember(idea.id) { mutableStateOf(idea.premise) }
    var strengths by remember(idea.id) { mutableStateOf(idea.strengths) }
    var risks by remember(idea.id) { mutableStateOf(idea.risks) }
    var nextStep by remember(idea.id) { mutableStateOf(idea.nextStep) }
    var bookId by remember(idea.id) { mutableStateOf(idea.bookId) }
    var bookMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit idea") }, text = {
        Column(modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(premise, { premise = it }, label = { Text("Premise") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(strengths, { strengths = it }, label = { Text("Strengths") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(risks, { risks = it }, label = { Text("Risks") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nextStep, { nextStep = it }, label = { Text("Next step") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { bookMenu = true }) {
                Text("Project: ${books.firstOrNull { it.first == bookId }?.second ?: "None"}")
            }
            DropdownMenu(expanded = bookMenu, onDismissRequest = { bookMenu = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { bookId = null; bookMenu = false })
                books.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { bookId = id; bookMenu = false })
                }
            }
            TextButton(onClick = onDelete) { Text("Delete idea", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        onSave(idea.copy(title = title, premise = premise, strengths = strengths, risks = risks,
            nextStep = nextStep, bookId = bookId))
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun IdeaPromoteDialog(
    idea: BrainstormIdeaEntity,
    state: BrainstormIdeasUiState,
    viewModel: BrainstormIdeasViewModel,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf("Notes") }
    var bookId by remember(idea.id) { mutableStateOf(idea.bookId.orEmpty()) }
    var categoryId by remember { mutableStateOf("") }
    var chapterId by remember { mutableStateOf("") }
    var sceneId by remember { mutableStateOf("") }
    LaunchedEffect(bookId) { if (bookId.isNotBlank()) viewModel.loadPlanTargets(bookId) }
    val novels = state.books.filter { it.workType == "novel" }
    val ready = when (target) {
        "Notes" -> true
        "Codex" -> categoryId.isNotBlank()
        "Scene outline" -> novels.any { it.id == bookId }
        "Scene beat" -> sceneId.isNotBlank()
        else -> false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Send idea to…") }, text = {
        Column(modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            listOf("Notes", "Codex", "Scene outline", "Scene beat").forEach { option ->
                TextButton(onClick = { target = option }) {
                    Text((if (target == option) "● " else "○ ") + option)
                }
            }
            if (target == "Codex") {
                Text("Choose a Codex category", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { categoryId = "new-ideas-category" }) {
                    Text((if (categoryId == "new-ideas-category") "● " else "○ ") + "Ideas category")
                }
                state.categories.forEach { category ->
                    TextButton(onClick = { categoryId = category.id }) {
                        Text((if (categoryId == category.id) "● " else "○ ") + category.name)
                    }
                }
            }
            if (target == "Scene outline" || target == "Scene beat") {
                Text("Choose a novel", style = MaterialTheme.typography.labelMedium)
                novels.forEach { book ->
                    TextButton(onClick = { bookId = book.id; chapterId = ""; sceneId = "" }) {
                        Text((if (bookId == book.id) "● " else "○ ") + book.title)
                    }
                }
                if (bookId.isNotBlank() && target == "Scene outline") {
                    Text("Chapter", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { chapterId = "" }) {
                        Text((if (chapterId.isBlank()) "● " else "○ ") + "New chapter")
                    }
                    state.chapters.forEach { chapter ->
                        TextButton(onClick = { chapterId = chapter.id }) {
                            Text((if (chapterId == chapter.id) "● " else "○ ") + chapter.title)
                        }
                    }
                }
                if (bookId.isNotBlank() && target == "Scene beat") {
                    Text("Scene", style = MaterialTheme.typography.labelMedium)
                    state.scenes.forEach { scene ->
                        TextButton(onClick = { sceneId = scene.id }) {
                            Text((if (sceneId == scene.id) "● " else "○ ") + scene.title)
                        }
                    }
                    if (state.scenes.isEmpty()) Text("This novel has no scenes yet.")
                }
            }
        }
    }, confirmButton = { TextButton(enabled = ready, onClick = {
        when (target) {
            "Notes" -> viewModel.promoteToNote(idea.id)
            "Codex" -> viewModel.promoteToCodex(idea.id, categoryId)
            "Scene outline" -> viewModel.promoteToScene(idea.id, bookId, chapterId.ifBlank { null })
            "Scene beat" -> viewModel.promoteToBeat(idea.id, bookId, sceneId)
        }
        onDismiss()
    }) { Text("Copy") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
