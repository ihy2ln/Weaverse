package com.ihy2ln.weaverse.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class WorkShelfKind(val workType: String, val heading: String, val emptyText: String) {
    Novel("novel", "Bookshelf", "No novels yet. Add one to begin writing."),
    Campaign("campaign", "Campaigns", "No campaigns yet. Create one to begin an adventure."),
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
    bookRepository: BookRepository,
    db: WeaverseDatabase,
    mediaRepository: MediaRepository,
) : ViewModel() {
    val cards: StateFlow<List<WorkShelfCard>> = combine(
        bookRepository.observeBooks(),
        db.roleplayDao().observeChats(),
        mediaRepository.observeAll(),
    ) { books, chats, media ->
        val mediaById = media.associateBy { it.id }
        val typed = books.filter { it.workType in setOf("novel", "campaign", "storyboard") }
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
}

@Composable
fun WorkShelfScreen(
    kind: WorkShelfKind,
    onCreate: () -> Unit,
    onOpen: (WorkShelfCard) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkShelfViewModel = hiltViewModel(),
) {
    val allCards by viewModel.cards.collectAsState()
    val cards = allCards.filter { card ->
        when (kind) {
            WorkShelfKind.Novel -> card.workType == "novel"
            WorkShelfKind.Campaign -> card.workType == "campaign"
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
                },
                onClick = onCreate,
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
                    WorkPosterCard(card = card, onClick = { onOpen(card) })
                }
            }
        }
    }
}

@Composable
private fun WorkPosterCard(card: WorkShelfCard, onClick: () -> Unit) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(tokens.panel)
            .clickable(onClick = onClick),
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
    }
}
