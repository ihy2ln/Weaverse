package com.ihy2ln.weaverse.feature.library

import com.ihy2ln.weaverse.core.ui.components.CreateWorkVocabulary
import com.ihy2ln.weaverse.core.ui.components.CreateWorkDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
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
    /** Home doubles as the way into every workspace, not just novels. */
    onOpenMode: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPad),
    ) {
        Text("Weaverse", style = MaterialTheme.typography.headlineSmall)
        ModeShelf(
            activeWorkByMode = state.activeWorkByMode,
            onOpenMode = onOpenMode,
            onOpenNovel = { card ->
                viewModel.openBook(card.book.id) { sceneId -> onOpenBook(card.book.id, sceneId) }
            },
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

    }
}

@Composable
private fun NovelsTab(
    state: LibraryUiState,
    onTitle: (String) -> Unit,
    onOpen: (BookEntity) -> Unit,
    onRead: (BookEntity) -> Unit,
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
                    onRead = { onRead(card.book) },
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
    onRead: () -> Unit,
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
                        .clip(RoundedCornerShape(inkRadiusSm())),
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
            InkTextButton(label = "Read", onClick = onRead)
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

/**
 * Home is the way into every workspace, not just novels. One card per mode,
 * in the same bordered-card language the book list uses.
 */
@Composable
private fun ModeShelf(
    activeWorkByMode: Map<String, LibraryBookCard>,
    onOpenMode: (String) -> Unit,
    onOpenNovel: (LibraryBookCard) -> Unit,
) {
    val tokens = inkTokens()
    val modes = listOf(
        Triple("Novel", "Novel", "Plan, write and review a book"),
        Triple("Roleplay", "RPG", "Run a campaign: adventures, party, lore"),
        Triple("Chatting", "Chatting", "Message the cast like a messenger app"),
        Triple("Storyboard", "Storyboard", "Build comic and manga pages"),
        Triple("Notes", "Notes", "One shared board across every mode"),
    )
    Column(
        modifier = Modifier.padding(top = InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        modes.forEach { (id, label, blurb) ->
            val active = activeWorkByMode[id]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .background(tokens.panel)
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { onOpenMode(id) }
                    .padding(InkSpacing.md),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
                if (active != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = InkSpacing.sm)
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(tokens.hover)
                            .clickable {
                                if (id == "Novel") onOpenNovel(active) else onOpenMode(id)
                            }
                            .padding(InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active.coverPath != null) {
                            AsyncImage(
                                model = File(active.coverPath),
                                contentDescription = active.book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(40.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(inkRadiusSm())),
                            )
                        }
                        Column(modifier = Modifier.padding(start = InkSpacing.sm)) {
                            Text(
                                active.book.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Continue active work",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}
