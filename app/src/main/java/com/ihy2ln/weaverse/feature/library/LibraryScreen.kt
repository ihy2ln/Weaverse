package com.ihy2ln.weaverse.feature.library

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
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
    onOpenExport: (bookId: String?) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPad),
    ) {
        Text("Your Novels", style = MaterialTheme.typography.headlineSmall)
        InkOutlinedButton(
            label = "+ Create Novel",
            onClick = {
                viewModel.createBook { bookId, sceneId -> onWriteBook(bookId, sceneId) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm, bottom = InkSpacing.md),
        )
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
        InkSegmentedPill(
            options = LibraryTab.entries.map { SegmentedOption(it.name, it.novelSubLabel()) },
            selectedId = state.tab.name,
            onSelect = { id ->
                LibraryTab.entries.find { it.name == id }?.let(viewModel::setTab)
            },
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
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
                onTitle = viewModel::onNewBookTitle,
                onOpen = { book ->
                    viewModel.openBook(book.id) { sceneId -> onOpenBook(book.id, sceneId) }
                },
                onDelete = viewModel::deleteBook,
                onExport = { bookId ->
                    viewModel.openBook(bookId) { onOpenExport(bookId) }
                },
            )
            LibraryTab.Series -> SeriesTab(
                state = state,
                onTitle = viewModel::onNewSeriesTitle,
                onCreate = viewModel::createSeries,
                onDeleteSeries = viewModel::deleteSeries,
                onOpenBook = { book ->
                    viewModel.openBook(book.id) { sceneId -> onOpenBook(book.id, sceneId) }
                },
                onRemoveFromSeries = viewModel::removeBookFromSeries,
                onAddToSeries = viewModel::addBookToSeries,
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
) {
    val tokens = inkTokens()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "CONTINUE WHERE YOU LEFT OFF",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.secondaryText,
            modifier = Modifier.padding(vertical = InkSpacing.sm),
        )
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
                    onOpen = { onOpen(card.book) },
                    onDelete = { onDelete(card.book.id) },
                    onExport = { onExport(card.book.id) },
                )
            }
        }
    }
}

@Composable
private fun NovelCard(
    card: LibraryBookCard,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val tokens = inkTokens()
    val updated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(card.book.updatedAt))
    InkCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            InkTextButton(label = "Export", onClick = onExport)
            InkDeleteButton(itemName = card.book.title, onConfirmedDelete = onDelete)
        }
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
        }
    }
}
