package com.ihy2ln.weaverse.feature.novel.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.tts.TtsService
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderTheme(val label: String) { Paper("Paper"), Sepia("Sepia"), Night("Night") }

data class ReaderScene(
    val id: String,
    val chapterTitle: String,
    val title: String,
    val text: String,
    val wordCount: Int,
)

data class ReaderUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val scenes: List<ReaderScene> = emptyList(),
    val currentIndex: Int = 0,
    val fontSizeSp: Int = 18,
    val lineHeight: Float = 1.65f,
    val theme: ReaderTheme = ReaderTheme.Paper,
    val bookmarks: Set<String> = emptySet(),
    val status: String = "",
    val loading: Boolean = true,
) {
    val current: ReaderScene? get() = scenes.getOrNull(currentIndex)
    val progress: Float get() = if (scenes.isEmpty()) 0f else (currentIndex + 1f) / scenes.size
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val tts: TtsService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collectLatest { prefs ->
                val bookId = prefs.selectedBookId
                val book = db.bookDao().getById(bookId)
                val scenes = buildList {
                    db.manuscriptDao().getActs(bookId).forEach { act ->
                        db.manuscriptDao().getChapters(act.id).forEach { chapter ->
                            db.manuscriptDao().getScenes(chapter.id).forEach { scene ->
                                add(
                                    ReaderScene(
                                        id = scene.id,
                                        chapterTitle = chapter.title,
                                        title = scene.title,
                                        text = scene.plainText.ifBlank {
                                            documentFromJson(scene.docJson).plainText()
                                        },
                                        wordCount = scene.wordCount,
                                    ),
                                )
                            }
                        }
                    }
                }
                settings.readerState(bookId).collect { saved ->
                    val savedIndex = scenes.indexOfFirst { it.id == saved.lastSceneId }
                    _uiState.value = ReaderUiState(
                        bookId = bookId,
                        bookTitle = book?.title.orEmpty(),
                        scenes = scenes,
                        currentIndex = savedIndex.coerceAtLeast(0),
                        fontSizeSp = prefs.fontSizeSp.coerceIn(14, 28),
                        lineHeight = prefs.lineHeight,
                        theme = ReaderTheme.entries.find { it.name == prefs.readerTheme }
                            ?: ReaderTheme.Paper,
                        bookmarks = saved.bookmarkedSceneIds,
                        loading = false,
                    )
                }
            }
        }
    }

    fun goTo(index: Int) {
        val state = _uiState.value
        val next = index.coerceIn(0, (state.scenes.size - 1).coerceAtLeast(0))
        val scene = state.scenes.getOrNull(next) ?: return
        _uiState.update { it.copy(currentIndex = next, status = "") }
        viewModelScope.launch { settings.setReaderPosition(state.bookId, scene.id) }
    }

    fun next() = goTo(_uiState.value.currentIndex + 1)
    fun previous() = goTo(_uiState.value.currentIndex - 1)

    fun toggleBookmark() {
        val state = _uiState.value
        val id = state.current?.id ?: return
        viewModelScope.launch { settings.toggleReaderBookmark(state.bookId, id) }
    }

    fun setFontSize(value: Int) = viewModelScope.launch { settings.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { settings.setLineHeight(value) }
    fun setTheme(theme: ReaderTheme) = viewModelScope.launch { settings.setReaderTheme(theme.name) }

    fun speak() {
        val text = _uiState.value.current?.text.orEmpty()
        viewModelScope.launch {
            val status = tts.speak(text)
            _uiState.update { it.copy(status = status) }
        }
    }

    fun stopSpeaking() {
        tts.stop()
        _uiState.update { it.copy(status = "Read aloud stopped") }
    }
}

private data class ReaderPalette(val background: Color, val page: Color, val text: Color, val secondary: Color)

@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var contentsOpen by remember { mutableStateOf(false) }
    val palette = when (state.theme) {
        ReaderTheme.Paper -> ReaderPalette(Color(0xFFF4F1EA), Color(0xFFFFFDF8), Color(0xFF24211D), Color(0xFF706A61))
        ReaderTheme.Sepia -> ReaderPalette(Color(0xFFE8D8B7), Color(0xFFF4E6C8), Color(0xFF3B2B1F), Color(0xFF765D47))
        ReaderTheme.Night -> ReaderPalette(Color(0xFF101316), Color(0xFF171B1F), Color(0xFFE3E6E8), Color(0xFF9AA2A8))
    }
    val current = state.current

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .align(Alignment.TopCenter)
                .background(palette.page),
        ) {
            Column(modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        InkTextButton(label = "Contents", onClick = { contentsOpen = true }, compact = true)
                        DropdownMenu(expanded = contentsOpen, onDismissRequest = { contentsOpen = false }) {
                            state.scenes.forEachIndexed { index, scene ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${if (scene.id in state.bookmarks) "★ " else ""}${scene.chapterTitle} · ${scene.title}",
                                            maxLines = 1,
                                        )
                                    },
                                    onClick = { contentsOpen = false; viewModel.goTo(index) },
                                )
                            }
                        }
                    }
                    Text(
                        state.bookTitle.ifBlank { "Reader" },
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    InkTextButton(label = "Aa", onClick = { settingsOpen = !settingsOpen }, compact = true)
                }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.text,
                    trackColor = palette.secondary.copy(alpha = .2f),
                )
                AnimatedVisibility(settingsOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Text", color = palette.secondary, modifier = Modifier.weight(1f))
                            InkTextButton("A−", { viewModel.setFontSize(state.fontSizeSp - 1) }, compact = true)
                            Text("${state.fontSizeSp}", color = palette.text)
                            InkTextButton("A+", { viewModel.setFontSize(state.fontSizeSp + 1) }, compact = true)
                        }
                        Text("Line spacing", color = palette.secondary, style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.lineHeight,
                            onValueChange = viewModel::setLineHeight,
                            valueRange = 1.2f..2.2f,
                            steps = 4,
                        )
                        InkSegmentedPill(
                            options = ReaderTheme.entries.map { SegmentedOption(it.name, it.label) },
                            selectedId = state.theme.name,
                            onSelect = { id -> ReaderTheme.entries.find { it.name == id }?.let(viewModel::setTheme) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                            InkTextButton("Read aloud", viewModel::speak, compact = true)
                            InkTextButton("Stop", viewModel::stopSpeaking, compact = true)
                            InkTextButton(
                                if (current?.id in state.bookmarks) "★ Bookmarked" else "☆ Bookmark",
                                viewModel::toggleBookmark,
                                compact = true,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.secondary.copy(alpha = .2f))
            if (current == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This book has no readable scenes yet.", color = palette.secondary)
                }
            } else {
                val paragraphs = current.text.split(Regex("\\n\\s*\\n|\\n")).filter { it.isNotBlank() }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 28.dp,
                        vertical = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(current.chapterTitle.uppercase(), color = palette.secondary, style = MaterialTheme.typography.labelLarge)
                        Text(
                            current.title,
                            color = palette.text,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = (state.fontSizeSp + 8).sp,
                            modifier = Modifier.padding(top = InkSpacing.xs, bottom = InkSpacing.lg),
                        )
                    }
                    items(paragraphs) { paragraph ->
                        Text(
                            paragraph,
                            color = palette.text,
                            fontFamily = FontFamily.Serif,
                            fontSize = state.fontSizeSp.sp,
                            lineHeight = (state.fontSizeSp * state.lineHeight).sp,
                        )
                    }
                }
                HorizontalDivider(color = palette.secondary.copy(alpha = .2f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(InkSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkTextButton("← Previous", viewModel::previous, enabled = state.currentIndex > 0)
                    val minutes = (current.wordCount / 220f).coerceAtLeast(1f).toInt()
                    Text(
                        "${state.currentIndex + 1} / ${state.scenes.size} · about $minutes min",
                        color = palette.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    InkTextButton("Next →", viewModel::next, enabled = state.currentIndex < state.scenes.lastIndex)
                }
                if (state.status.isNotBlank()) {
                    Text(
                        state.status,
                        color = palette.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = InkSpacing.xs),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
