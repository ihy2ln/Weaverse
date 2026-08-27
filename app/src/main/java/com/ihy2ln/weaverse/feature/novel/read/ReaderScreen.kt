package com.ihy2ln.weaverse.feature.novel.read

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderTheme(val label: String) { Paper("Paper"), Sepia("Sepia"), Night("Night") }

data class ReaderScene(
    val id: String,
    val chapterId: String,
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
    val paragraphIndex: Int = 0,
    val scrollOffset: Int = 0,
    val speakingParagraph: Int = -1,
) {
    val current: ReaderScene? get() = scenes.getOrNull(currentIndex)
    val progress: Float get() = if (scenes.isEmpty()) 0f else (currentIndex + 1f) / scenes.size
    val chapterProgress: Float
        get() {
            val scene = current ?: return 0f
            val chapterScenes = scenes.filter { it.chapterId == scene.chapterId }
            if (chapterScenes.isEmpty()) return 0f
            val idx = chapterScenes.indexOfFirst { it.id == scene.id }.coerceAtLeast(0)
            return (idx + 1f) / chapterScenes.size
        }
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
                val scenes = db.manuscriptDao().getReaderScenes(bookId).map { row ->
                    ReaderScene(
                        id = row.id,
                        chapterId = row.chapterId,
                        chapterTitle = row.chapterTitle,
                        title = row.title,
                        text = row.plainText.ifBlank { documentFromJson(row.docJson).plainText() },
                        wordCount = row.wordCount,
                    )
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
                        paragraphIndex = saved.paragraphIndex,
                        scrollOffset = saved.scrollOffset,
                    )
                }
            }
        }
    }

    fun goTo(index: Int) {
        val state = _uiState.value
        val next = index.coerceIn(0, (state.scenes.size - 1).coerceAtLeast(0))
        val scene = state.scenes.getOrNull(next) ?: return
        _uiState.update { it.copy(currentIndex = next, status = "", speakingParagraph = -1, paragraphIndex = 0, scrollOffset = 0) }
        viewModelScope.launch { settings.setReaderPosition(state.bookId, scene.id) }
    }

    fun next() = goTo(_uiState.value.currentIndex + 1)
    fun previous() = goTo(_uiState.value.currentIndex - 1)

    fun saveScroll(paragraphIndex: Int, scrollOffset: Int) {
        val state = _uiState.value
        val scene = state.current ?: return
        _uiState.update { it.copy(paragraphIndex = paragraphIndex, scrollOffset = scrollOffset) }
        viewModelScope.launch {
            settings.setReaderScroll(state.bookId, scene.id, paragraphIndex, scrollOffset)
        }
    }

    fun toggleBookmark() {
        val state = _uiState.value
        val id = state.current?.id ?: return
        viewModelScope.launch { settings.toggleReaderBookmark(state.bookId, id) }
    }

    fun setFontSize(value: Int) = viewModelScope.launch { settings.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { settings.setLineHeight(value) }
    fun setTheme(theme: ReaderTheme) = viewModelScope.launch { settings.setReaderTheme(theme.name) }

    fun speak() {
        val paragraphs = _uiState.value.current?.text
            ?.split(Regex("\\n\\s*\\n|\\n"))
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (paragraphs.isEmpty()) return
        tts.speakParagraphs(paragraphs) { index ->
            _uiState.update { it.copy(speakingParagraph = index, status = "Reading paragraph ${index + 1}") }
        }
    }

    fun stopSpeaking() {
        tts.stop()
        _uiState.update { it.copy(status = "Read aloud stopped", speakingParagraph = -1) }
    }
}

private data class ReaderPalette(val background: Color, val page: Color, val text: Color, val secondary: Color)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.paragraphIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = state.scrollOffset.coerceAtLeast(0),
    )
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.bookId, state.currentIndex) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(state.speakingParagraph) {
        val idx = state.speakingParagraph
        if (idx >= 0) {
            runCatching { listState.animateScrollToItem(idx + 1) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(400)
            .collect { (idx, off) -> viewModel.saveScroll(idx, off) }
    }

    var drag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        viewModel.next()
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        viewModel.previous()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(state.currentIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 80f) viewModel.previous()
                        else if (drag < -80f) viewModel.next()
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
    ) {
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
                    InkTextButton(label = "Contents", onClick = { contentsOpen = true }, compact = true)
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
                LinearProgressIndicator(
                    progress = { state.chapterProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = palette.secondary,
                    trackColor = palette.secondary.copy(alpha = .15f),
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
                    state = listState,
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
                    itemsIndexed(paragraphs) { index, paragraph ->
                        val speaking = index == state.speakingParagraph
                        Text(
                            paragraph,
                            color = if (speaking) palette.text else palette.text,
                            fontFamily = FontFamily.Serif,
                            fontSize = state.fontSizeSp.sp,
                            lineHeight = (state.fontSizeSp * state.lineHeight).sp,
                            fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal,
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
                    val chapterPct = (state.chapterProgress * 100).toInt()
                    Text(
                        "${state.currentIndex + 1} / ${state.scenes.size} · $chapterPct% chapter · about $minutes min",
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
        if (contentsOpen) {
            ModalBottomSheet(
                onDismissRequest = { contentsOpen = false },
                sheetState = sheetState,
            ) {
                Text(
                    "Contents",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
                )
                LazyColumn {
                    itemsIndexed(state.scenes) { index, scene ->
                        TextButton(onClick = { contentsOpen = false; viewModel.goTo(index) }) {
                            Text(
                                "${if (scene.id in state.bookmarks) "★ " else ""}${scene.chapterTitle} · ${scene.title}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
