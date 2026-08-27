package com.ihy2ln.weaverse.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkLongPressMenuBox
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.LongPressMenuItem
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    onOpenBook: (bookId: String, sceneId: String?) -> Unit,
    onWriteBook: (bookId: String, sceneId: String?) -> Unit = onOpenBook,
    onReadBook: (bookId: String, sceneId: String?) -> Unit = onOpenBook,
    onOpenExport: (bookId: String?) -> Unit = {},
    onOpenMode: (String, ModeActiveWork?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    var coverPickBookId by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val bookId = coverPickBookId
        if (uri != null && bookId != null) {
            viewModel.setCoverFromUri(bookId, uri)
        }
        coverPickBookId = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPad),
    ) {
        Text(
            "Weaverse",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
        ModeShelf(
            activeWorks = state.modeActiveWorks,
            onOpenMode = { modeId, work -> onOpenMode(modeId, work) },
            onOpenActiveWork = { work ->
                when {
                    work.bookId != null -> viewModel.openBook(work.bookId) { sceneId ->
                        onOpenBook(work.bookId, sceneId)
                    }
                    work.chatId != null -> onOpenMode("Roleplay", work)
                    work.noteId != null -> onOpenMode("Notes", work)
                    else -> onOpenMode(work.modeId, work)
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your Novels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.selectionMode) {
                InkTextButton(label = "Cancel", onClick = viewModel::exitSelectionMode, compact = true)
                InkTextButton(
                    label = "Remove (${state.selectedForRemoval.size})",
                    onClick = viewModel::removeSelected,
                    enabled = state.selectedForRemoval.isNotEmpty(),
                    compact = true,
                )
            }
        }
        if (!state.hasIsekaiGacha) {
            InkOutlinedButton(
                label = "Import Isekai Gacha ZIP",
                onClick = {
                    viewModel.importBundledSample { bookId ->
                        if (bookId != null) {
                            viewModel.openBook(bookId) { sceneId -> onOpenBook(bookId, sceneId) }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.md),
            )
        }
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                color = tokens.secondaryText,
                fontSize = 12.sp,
                maxLines = 3,
                modifier = Modifier.padding(vertical = InkSpacing.xs),
            )
        }
        NovelsTab(
            state = state,
            onOpen = { book ->
                if (state.selectionMode) {
                    viewModel.toggleSelectedForRemoval(book.id)
                } else {
                    viewModel.openBook(book.id) { sceneId -> onOpenBook(book.id, sceneId) }
                }
            },
            onRead = { book ->
                viewModel.openBook(book.id) { sceneId -> onReadBook(book.id, sceneId) }
            },
            onDelete = viewModel::deleteBook,
            onExport = { bookId -> viewModel.openBook(bookId) { onOpenExport(bookId) } },
            onCopy = viewModel::copyBook,
            onAddCover = { bookId ->
                coverPickBookId = bookId
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onEnterSelectMode = viewModel::enterSelectionMode,
            isSelected = { state.selectedForRemoval.contains(it) },
            selectionMode = state.selectionMode,
        )
    }
}

@Composable
private fun NovelsTab(
    state: LibraryUiState,
    onOpen: (BookEntity) -> Unit,
    onRead: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    onCopy: (String) -> Unit,
    onAddCover: (String) -> Unit,
    onEnterSelectMode: () -> Unit,
    isSelected: (String) -> Boolean,
    selectionMode: Boolean,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        items(state.cards, key = { it.book.id }) { card ->
            NovelCard(
                card = card,
                selected = card.book.id == state.selectedBookId,
                markedForRemoval = isSelected(card.book.id),
                selectionMode = selectionMode,
                onOpen = { onOpen(card.book) },
                onRead = { onRead(card.book) },
                onDelete = { onDelete(card.book.id) },
                onExport = { onExport(card.book.id) },
                onCopy = { onCopy(card.book.id) },
                onAddCover = { onAddCover(card.book.id) },
                onEnterSelectMode = onEnterSelectMode,
            )
        }
    }
}

@Composable
private fun NovelCard(
    card: LibraryBookCard,
    selected: Boolean,
    markedForRemoval: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onAddCover: () -> Unit,
    onEnterSelectMode: () -> Unit,
) {
    val tokens = inkTokens()
    val updated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(card.book.updatedAt))
    val shape = RoundedCornerShape(InkSpacing.radiusMd)
    InkLongPressMenuBox(
        onClick = onOpen,
        onRemove = onDelete,
        onEnterSelectMode = onEnterSelectMode,
        selectionMode = selectionMode,
        extraItems = listOf(
            LongPressMenuItem("Open", onOpen),
            LongPressMenuItem("Read", onRead),
            LongPressMenuItem("Export", onExport),
            LongPressMenuItem("Copy", onCopy),
            LongPressMenuItem("Add cover art", onAddCover),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        InkCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (markedForRemoval) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.coverPath != null) {
                    AsyncImage(
                        model = File(card.coverPath),
                        contentDescription = card.book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(InkSpacing.radiusSm)),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (card.coverPath != null) InkSpacing.md else 0.dp),
                ) {
                    Text(
                        card.book.title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    Text(
                        listOfNotNull(
                            card.seriesTitle,
                            card.book.genre.takeIf { it.isNotBlank() },
                            if (selected) "Open" else null,
                        ).joinToString(" · ").ifBlank { "Novel" },
                        color = tokens.secondaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    Text(
                        updated,
                        color = tokens.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (!selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                    horizontalArrangement = Arrangement.End,
                ) {
                    InkTextButton(label = "Read", onClick = onRead)
                    InkTextButton(label = "Export", onClick = onExport)
                }
            }
        }
    }
}

private data class HomeMode(val id: String, val label: String, val blurb: String)

@Composable
private fun ModeShelf(
    activeWorks: Map<String, ModeActiveWork>,
    onOpenMode: (String, ModeActiveWork?) -> Unit,
    onOpenActiveWork: (ModeActiveWork) -> Unit,
) {
    val tokens = inkTokens()
    val modes = listOf(
        HomeMode("Novel", "Novel", "Plan, write and review a book"),
        HomeMode("Roleplay", "RPG", "Run a campaign: adventures, party, lore"),
        HomeMode("Chatting", "Chatting", "Message the cast like a messenger app"),
        HomeMode("Storyboard", "Storyboard", "Build comic and manga pages"),
        HomeMode("Notes", "Notes", "One shared board across every mode"),
    )
    Column(
        modifier = Modifier.padding(top = InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        modes.forEach { mode ->
            val active = activeWorks[mode.id] ?: activeWorks["NovelChat"]?.takeIf { mode.id == "Novel" }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(InkSpacing.radiusSm))
                    .background(tokens.panel)
                    .border(1.dp, tokens.hairline, RoundedCornerShape(InkSpacing.radiusSm))
                    .clickable { onOpenMode(mode.id, active) }
                    .padding(InkSpacing.md),
            ) {
                Text(mode.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                active?.let { work ->
                    ActiveWorkCard(
                        work = work,
                        onClick = { onOpenActiveWork(work) },
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkCard(
    work: ModeActiveWork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InkSpacing.radiusSm))
            .background(tokens.hover)
            .clickable(onClick = onClick)
            .padding(InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (work.coverPath != null) {
            AsyncImage(
                model = File(work.coverPath),
                contentDescription = work.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(InkSpacing.radiusSm)),
            )
        }
        Column(modifier = Modifier.padding(start = if (work.coverPath != null) InkSpacing.sm else 0.dp)) {
            Text(
                work.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                work.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
