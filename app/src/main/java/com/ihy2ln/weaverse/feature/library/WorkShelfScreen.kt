package com.ihy2ln.weaverse.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WorkShelfKind(val workType: String, val heading: String, val emptyText: String) {
    Novel("novel", "Bookshelf", "No novels yet. Add one to begin writing."),
    Campaign("campaign", "Campaigns", "No campaigns yet. Create one to begin an adventure."),
    TextGame("text_game", "Text Games", "No text-game sessions yet. Create one to enter Adams Haven."),
    Storyboard("storyboard", "Window", "No storyboards yet. Create one to build your first page."),
}

data class WorkShelfCard(
    val id: String,
    val workType: String,
    val bookId: String?,
    val chatId: String?,
    val title: String,
    val subtitle: String,
    val artPath: String?,
    val preferredStoryboardMode: String = "Manga",
)

@HiltViewModel
class WorkShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow("")
    val status: StateFlow<String> = _status
    val cards: StateFlow<List<WorkShelfCard>> = combine(
        bookRepository.observeBooks(),
        db.roleplayDao().observeChats(),
        mediaRepository.observeAll(),
    ) { books, chats, media ->
        val mediaById = media.associateBy { it.id }
        val typed = books.filter { it.workType in setOf("novel", "campaign", "text_game", "storyboard") }
            .map { book ->
                val chat = chats.firstOrNull { it.bookId == book.id }
                val artId = chat?.backgroundMediaId ?: book.coverMediaId
                WorkShelfCard(
                    id = book.id,
                    workType = book.workType,
                    bookId = book.id,
                    chatId = chat?.id,
                    title = book.title,
                    subtitle = book.genre,
                    preferredStoryboardMode = if (
                        book.tense.equals("Comic", true) ||
                        book.tense.equals("Webtoon", true) ||
                        book.pov.equals("Left to right", true)
                    ) "Comic" else "Manga",
                    artPath = artId?.let(mediaById::get)
                        ?.let(mediaRepository::resolveFile)
                        ?.takeIf(File::exists)?.absolutePath,
                )
            }
        val legacyBoards = chats.filter { chat ->
            chat.displayMode == "roleplay" && chat.characterId == null &&
                typed.none { it.chatId == chat.id }
        }.map { chat ->
            WorkShelfCard(
                id = chat.id,
                workType = "storyboard",
                bookId = null,
                chatId = chat.id,
                title = chat.title,
                subtitle = "Storyboard",
                artPath = chat.backgroundMediaId?.let(mediaById::get)
                    ?.let(mediaRepository::resolveFile)
                    ?.takeIf(File::exists)?.absolutePath,
                preferredStoryboardMode = "Manga",
            )
        }
        typed + legacyBoards
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun duplicate(bookId: String) {
        viewModelScope.launch {
            runCatching { bookRepository.duplicateBook(bookId) }
                .onSuccess { copy -> _status.value = copy?.let { "Copied as ${it.title}" } ?: "Could not find work" }
                .onFailure { _status.value = "Copy failed: ${it.message}" }
        }
    }

    fun setCover(bookId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val media = mediaRepository.importFromUri(uri)
                val book = bookRepository.getBook(bookId) ?: error("Could not find work")
                bookRepository.updateBook(book.copy(coverMediaId = media.id))
            }.onSuccess {
                _status.value = "Cover art updated"
            }.onFailure { _status.value = "Cover update failed: ${it.message}" }
        }
    }

    fun delete(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        viewModelScope.launch {
            val selectedBookId = settings.preferences.first().selectedBookId
            bookIds.forEach { bookRepository.deleteBook(it) }
            if (selectedBookId in bookIds) {
                settings.setSelectedBookId(cards.value.firstOrNull { it.bookId !in bookIds }?.bookId.orEmpty())
            }
            _status.value = if (bookIds.size == 1) "Work removed" else "${bookIds.size} works removed"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkShelfScreen(
    kind: WorkShelfKind,
    onCreate: () -> Unit,
    onOpen: (WorkShelfCard) -> Unit,
    onExport: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkShelfViewModel = hiltViewModel(),
) {
    val allCards by viewModel.cards.collectAsState()
    val status by viewModel.status.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteIds by remember { mutableStateOf(emptySet<String>()) }
    var coverTargetId by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val bookId = coverTargetId
        coverTargetId = null
        if (uri != null && bookId != null) viewModel.setCover(bookId, uri)
    }
    val cards = allCards.filter { card ->
        when (kind) {
            WorkShelfKind.Novel -> card.workType == "novel"
            WorkShelfKind.Campaign -> card.workType == "campaign"
            // Pre-1.3.58 text-game testers were stored as campaigns. Keep them
            // visible here while all newly-created sessions use text_game.
            WorkShelfKind.TextGame -> card.workType in setOf("text_game", "campaign")
            WorkShelfKind.Storyboard -> card.workType == "storyboard"
        }
    }
    val tokens = inkTokens()
    Column(modifier = modifier.fillMaxSize().padding(adaptiveContentPadding())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(kind.heading, style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (kind) {
                        WorkShelfKind.Novel -> "Choose a novel or start a new story"
                        WorkShelfKind.Storyboard -> "Your manga and comic library"
                        WorkShelfKind.Campaign -> "Your worlds at a glance"
                        WorkShelfKind.TextGame -> "Card-driven stories, battles, and haven simulations"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
            }
            InkOutlinedButton(
                label = when (kind) {
                    WorkShelfKind.Novel -> "+ Novel"
                    WorkShelfKind.Storyboard -> "+ Storyboard"
                    WorkShelfKind.Campaign -> "+ Campaign"
                    WorkShelfKind.TextGame -> "+ Text Game"
                },
                onClick = onCreate,
            )
        }
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelLarge)
                Row {
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Clear") }
                    TextButton(onClick = { pendingDeleteIds = selectedIds }) { Text("Quick remove") }
                }
            }
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
        if (cards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(kind.emptyText, color = tokens.secondaryText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize().padding(top = InkSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
            ) {
                items(cards, key = { it.id }) { card ->
                    WorkPosterCard(
                        card = card,
                        selected = card.id in selectedIds,
                        onClick = {
                            if (selectedIds.isEmpty()) onOpen(card)
                            else selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        },
                        onExport = card.bookId?.let { id -> { onExport(id) } },
                        onCopy = card.bookId?.let { id -> { viewModel.duplicate(id) } },
                        onCover = card.bookId?.let { id ->
                            {
                                coverTargetId = id
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        onSelect = {
                            selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        },
                        onDelete = card.bookId?.let { id -> { pendingDeleteIds = setOf(id) } },
                    )
                }
            }
        }
    }
    if (pendingDeleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = emptySet() },
            title = { Text(if (pendingDeleteIds.size == 1) "Remove this work?" else "Remove selected works?") },
            text = { Text("This permanently removes the selected work and its saved project data.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pendingDeleteIds)
                    selectedIds = selectedIds - pendingDeleteIds
                    pendingDeleteIds = emptySet()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteIds = emptySet() }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkPosterCard(
    card: WorkShelfCard,
    selected: Boolean,
    onClick: () -> Unit,
    onExport: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onCover: (() -> Unit)?,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val tokens = inkTokens()
    var menuOpen by remember(card.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else tokens.panel)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        if (card.artPath != null) {
            AsyncImage(
                model = File(card.artPath),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(tokens.hover), contentAlignment = Alignment.Center) {
                Text("W", style = MaterialTheme.typography.displayMedium, color = tokens.secondaryText)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                .padding(top = 48.dp, start = InkSpacing.md, end = InkSpacing.md, bottom = InkSpacing.md),
        ) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.subtitle.ifBlank { "Tap to open" },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = .78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onExport?.let { action ->
                DropdownMenuItem(text = { Text("Export") }, onClick = { menuOpen = false; action() })
            }
            onCopy?.let { action ->
                DropdownMenuItem(text = { Text("Copy") }, onClick = { menuOpen = false; action() })
            }
            onCover?.let { action ->
                DropdownMenuItem(text = { Text("Cover art") }, onClick = { menuOpen = false; action() })
            }
            DropdownMenuItem(
                text = { Text(if (selected) "Unselect" else "Select for quick remove") },
                onClick = { menuOpen = false; onSelect() },
            )
            onDelete?.let { action ->
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; action() })
            }
        }
    }
}
