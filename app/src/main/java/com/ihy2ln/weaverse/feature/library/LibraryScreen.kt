package com.ihy2ln.weaverse.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkConfirmDeleteDialog
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.ItemAdminMenu
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    pane: LibraryPane = LibraryPane.Home,
    onOpenBook: (bookId: String, sceneId: String?) -> Unit,
    onWriteBook: (bookId: String, sceneId: String?) -> Unit = onOpenBook,
    onOpenExport: (bookId: String?) -> Unit = {},
    onOpenMode: (HomeWorkspace) -> Unit = {},
    onOpenRpChat: (chatId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    when (pane) {
        LibraryPane.Home -> HomeHub(
            state = state,
            onOpenMode = onOpenMode,
            onOpenBook = { bookId ->
                viewModel.openBook(bookId) { sceneId -> onOpenBook(bookId, sceneId) }
            },
            onOpenRpChat = onOpenRpChat,
            modifier = modifier,
        )
        LibraryPane.Bookshelf -> BookshelfPane(
            state = state,
            onTitle = viewModel::onNewBookTitle,
            onCreate = {
                viewModel.createBook { bookId, sceneId -> onWriteBook(bookId, sceneId) }
            },
            onSetTab = viewModel::setTab,
            onOpen = { book ->
                viewModel.openBook(book.id) { sceneId -> onOpenBook(book.id, sceneId) }
            },
            onDelete = viewModel::deleteBook,
            onExport = { bookId ->
                viewModel.openBook(bookId) { onOpenExport(bookId) }
            },
            onCopy = viewModel::copyBook,
            onCover = viewModel::setCoverFromUri,
            onSelectToRemove = viewModel::enterSelectToRemove,
            onExitSelect = viewModel::exitSelectToRemove,
            onToggleSelect = viewModel::toggleSelectedToRemove,
            onDeleteSelected = viewModel::deleteSelectedBooks,
            onNewSeriesTitle = viewModel::onNewSeriesTitle,
            onCreateSeries = viewModel::createSeries,
            onDeleteSeries = viewModel::deleteSeries,
            onRemoveFromSeries = viewModel::removeBookFromSeries,
            onAddToSeries = viewModel::addBookToSeries,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeHub(
    state: LibraryUiState,
    onOpenMode: (HomeWorkspace) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenRpChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPad = adaptiveContentPadding()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPad),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        item {
            Text(
                "Weaverse",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = InkSpacing.md, top = InkSpacing.sm),
            )
        }
        HomeWorkspace.entries.forEach { workspace ->
            item(key = workspace.name) {
                val recent = when (workspace) {
                    HomeWorkspace.Novel -> state.recentNovel
                    HomeWorkspace.Rpg -> state.recentRpg
                    HomeWorkspace.Chatting -> state.recentChat
                    HomeWorkspace.Storyboard -> state.recentStoryboard
                    HomeWorkspace.Notes -> state.recentNote
                }
                ModeCard(
                    workspace = workspace,
                    recent = recent,
                    onOpenMode = { onOpenMode(workspace) },
                    onOpenRecent = {
                        when (workspace) {
                            HomeWorkspace.Novel -> recent?.let { onOpenBook(it.id) }
                            HomeWorkspace.Rpg, HomeWorkspace.Chatting, HomeWorkspace.Storyboard ->
                                recent?.let { onOpenRpChat(it.id) }
                            HomeWorkspace.Notes -> onOpenMode(HomeWorkspace.Notes)
                        }
                    },
                )
            }
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun ModeCard(
    workspace: HomeWorkspace,
    recent: HomeRecentWork?,
    onOpenMode: () -> Unit,
    onOpenRecent: () -> Unit,
) {
    val tokens = inkTokens()
    InkCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMode),
    ) {
        Text(
            workspace.title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
        )
        Text(
            workspace.blurb,
            color = tokens.secondaryText,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = InkSpacing.xxs),
        )
        if (recent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.md)
                    .clip(RoundedCornerShape(InkSpacing.radiusSm))
                    .clickable(onClick = onOpenRecent)
                    .padding(InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recent.coverPath != null) {
                    AsyncImage(
                        model = File(recent.coverPath),
                        contentDescription = recent.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(40.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(InkSpacing.radiusSm)),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (recent.coverPath != null) InkSpacing.md else 0.dp),
                ) {
                    Text(
                        recent.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    if (recent.subtitle.isNotBlank()) {
                        Text(
                            recent.subtitle,
                            color = tokens.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookshelfPane(
    state: LibraryUiState,
    onTitle: (String) -> Unit,
    onCreate: () -> Unit,
    onSetTab: (LibraryTab) -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCover: (String, Uri) -> Unit,
    onSelectToRemove: (String?) -> Unit,
    onExitSelect: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onNewSeriesTitle: (String) -> Unit,
    onCreateSeries: () -> Unit,
    onDeleteSeries: (String) -> Unit,
    onRemoveFromSeries: (String) -> Unit,
    onAddToSeries: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPad),
    ) {
        Text("Bookshelf", style = MaterialTheme.typography.headlineSmall)
        InkOutlinedButton(
            label = "+ Create Novel",
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm, bottom = InkSpacing.md),
        )
        InkSegmentedPill(
            options = LibraryTab.entries.map { SegmentedOption(it.name, it.novelSubLabel()) },
            selectedId = state.tab.name,
            onSelect = { id ->
                LibraryTab.entries.find { it.name == id }?.let(onSetTab)
            },
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
        if (state.selectingToRemove) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.selectedToRemove.size} selected",
                    color = tokens.secondaryText,
                    fontSize = 13.sp,
                )
                Row {
                    InkTextButton(label = "Cancel", onClick = onExitSelect)
                    InkTextButton(
                        label = "Remove selected",
                        onClick = onDeleteSelected,
                        enabled = state.selectedToRemove.isNotEmpty(),
                    )
                }
            }
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
        when (state.tab) {
            LibraryTab.Novels -> NovelsTab(
                state = state,
                onTitle = onTitle,
                onOpen = onOpen,
                onDelete = onDelete,
                onExport = onExport,
                onCopy = onCopy,
                onCover = onCover,
                onSelectToRemove = onSelectToRemove,
                onToggleSelect = onToggleSelect,
            )
            LibraryTab.Series -> SeriesTab(
                state = state,
                onTitle = onNewSeriesTitle,
                onCreate = onCreateSeries,
                onDeleteSeries = onDeleteSeries,
                onOpenBook = onOpen,
                onRemoveFromSeries = onRemoveFromSeries,
                onAddToSeries = onAddToSeries,
            )
        }
    }
}

@Composable
private fun NovelsTab(
    state: LibraryUiState,
    onTitle: (String) -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCover: (String, Uri) -> Unit,
    onSelectToRemove: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
) {
    var coverBookId by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val bookId = coverBookId
        if (uri != null && bookId != null) onCover(bookId, uri)
        coverBookId = null
    }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.newBookTitle,
            onValueChange = onTitle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = InkSpacing.sm),
            singleLine = true,
            placeholder = { Text("New book title") },
        )
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            items(state.cards, key = { it.book.id }) { card ->
                NovelCard(
                    card = card,
                    selected = card.book.id == state.selectedBookId,
                    selecting = state.selectingToRemove,
                    checked = card.book.id in state.selectedToRemove,
                    onOpen = { onOpen(card.book) },
                    onDelete = { onDelete(card.book.id) },
                    onExport = { onExport(card.book.id) },
                    onCopy = { onCopy(card.book.id) },
                    onRequestCover = {
                        coverBookId = card.book.id
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSelectToRemove = { onSelectToRemove(card.book.id) },
                    onToggleSelect = { onToggleSelect(card.book.id) },
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCard(
    card: LibraryBookCard,
    selected: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onRequestCover: () -> Unit,
    onSelectToRemove: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val tokens = inkTokens()
    val updated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(card.book.updatedAt))
    var menuOpen by remember(card.book.id) { mutableStateOf(false) }
    var confirmDelete by remember(card.book.id) { mutableStateOf(false) }
    Box {
        InkCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelect() else onOpen() },
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Checkbox(checked = checked, onCheckedChange = { onToggleSelect() })
                }
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
        }
        ItemAdminMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onAction = { action ->
                when (action) {
                    ItemAdminAction.Export -> onExport()
                    ItemAdminAction.Copy -> onCopy()
                    ItemAdminAction.AddCover -> onRequestCover()
                    ItemAdminAction.Delete -> confirmDelete = true
                    ItemAdminAction.SelectToRemove -> onSelectToRemove()
                    else -> Unit
                }
            },
            actions = listOf(
                ItemAdminAction.Export,
                ItemAdminAction.Copy,
                ItemAdminAction.AddCover,
                ItemAdminAction.Delete,
                ItemAdminAction.SelectToRemove,
            ),
            title = card.book.title,
        )
    }
    if (confirmDelete) {
        InkConfirmDeleteDialog(
            itemName = card.book.title,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SeriesTab(
    state: LibraryUiState,
    onTitle: (String) -> Unit,
    onCreate: () -> Unit,
    onDeleteSeries: (String) -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onRemoveFromSeries: (String) -> Unit,
    onAddToSeries: (String, String) -> Unit,
) {
    val tokens = inkTokens()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            OutlinedTextField(
                value = state.newSeriesTitle,
                onValueChange = onTitle,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New series title") },
            )
            InkConfirmButton(
                onClick = onCreate,
                contentDescription = "Create series",
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            state.seriesGroups.forEach { group ->
                item(key = group.series?.id ?: "unassigned") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                group.series?.title ?: "Standalone novels",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                            Text(
                                "${group.books.size} books",
                                fontSize = 12.sp,
                                color = tokens.secondaryText,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (group.series != null) {
                            InkDeleteButton(
                                itemName = group.series.title,
                                onConfirmedDelete = { onDeleteSeries(group.series.id) },
                                buttonLabel = "Remove series",
                            )
                        }
                    }
                }
                items(group.books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBook(book) }
                            .padding(vertical = InkSpacing.sm, horizontal = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                            Text(
                                book.genre.ifBlank { "Book" },
                                fontSize = 12.sp,
                                color = tokens.secondaryText,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (group.series != null) {
                            InkDeleteButton(
                                itemName = book.title,
                                onConfirmedDelete = { onRemoveFromSeries(book.id) },
                            )
                        } else if (state.series.isNotEmpty()) {
                            InkTextButton(
                                label = "+ Series",
                                onClick = { onAddToSeries(book.id, state.series.first().id) },
                            )
                        }
                    }
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
