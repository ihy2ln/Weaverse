package com.ihy2ln.weaverse.feature.novel.read

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.media.storedMediaPathOrNull
import com.ihy2ln.weaverse.core.text.Align
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodeBlock
import com.ihy2ln.weaverse.core.text.Divider
import com.ihy2ln.weaverse.core.text.Heading
import com.ihy2ln.weaverse.core.text.ListItem
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaGridBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Quote
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.readableText
import com.ihy2ln.weaverse.core.tts.TtsService
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderTheme(val label: String) { Paper("Paper"), Sepia("Sepia"), Night("Night") }

data class ReaderScene(
    val id: String,
    val chapterId: String,
    val chapterTitle: String,
    val title: String,
    val docJson: String,
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
    val mediaPaths: Map<String, String> = emptyMap(),
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
    private val mediaRepository: MediaRepository,
    private val tts: TtsService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collectLatest { prefs ->
                val bookId = prefs.selectedBookId
                combine(
                    mediaRepository.observeAll(),
                    settings.readerState(bookId),
                    db.manuscriptDao().observeReaderScenes(bookId),
                ) { mediaList, saved, sceneRows ->
                    Triple(mediaList, saved, sceneRows)
                }.collect { (mediaList, saved, sceneRows) ->
                    val book = db.bookDao().getById(bookId)
                    val paths = mediaRepository.pathMap(mediaList)
                    val scenes = sceneRows.map { row ->
                        ReaderScene(
                            id = row.id,
                            chapterId = row.chapterId,
                            chapterTitle = row.chapterTitle,
                            title = row.title,
                            docJson = row.docJson,
                            wordCount = row.wordCount,
                        )
                    }
                    val previous = _uiState.value
                    val preferredId = when {
                        previous.bookId == bookId -> previous.current?.id ?: saved.lastSceneId
                        else -> saved.lastSceneId
                    }
                    val savedIndex = scenes.indexOfFirst { it.id == preferredId }.let { idx ->
                        if (idx >= 0) {
                            idx
                        } else {
                            scenes.indexOfFirst { it.id == saved.lastSceneId }.coerceAtLeast(0)
                        }
                    }
                    val sceneChanged = previous.current?.id != scenes.getOrNull(savedIndex)?.id
                    val bookChanged = previous.bookId != bookId
                    _uiState.value = ReaderUiState(
                        bookId = bookId,
                        bookTitle = book?.title.orEmpty(),
                        scenes = scenes,
                        currentIndex = savedIndex,
                        fontSizeSp = prefs.fontSizeSp.coerceIn(14, 28),
                        lineHeight = prefs.lineHeight,
                        theme = ReaderTheme.entries.find { it.name == prefs.readerTheme }
                            ?: ReaderTheme.Paper,
                        bookmarks = saved.bookmarkedSceneIds,
                        mediaPaths = paths,
                        loading = false,
                        paragraphIndex = if (bookChanged || sceneChanged) {
                            saved.paragraphIndex
                        } else {
                            previous.paragraphIndex
                        },
                        scrollOffset = if (bookChanged || sceneChanged) {
                            saved.scrollOffset
                        } else {
                            previous.scrollOffset
                        },
                        speakingParagraph = if (sceneChanged) -1 else previous.speakingParagraph,
                        status = previous.status,
                    )
                }
            }
        }
    }

    fun goTo(index: Int) {
        val state = _uiState.value
        val next = index.coerceIn(0, (state.scenes.size - 1).coerceAtLeast(0))
        val scene = state.scenes.getOrNull(next) ?: return
        _uiState.update {
            it.copy(
                currentIndex = next,
                status = "",
                speakingParagraph = -1,
                paragraphIndex = 0,
                scrollOffset = 0,
            )
        }
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
        val paragraphs = currentBlocks().mapIndexedNotNull { index, block ->
            val text = block.readableText().trim()
            if (text.isBlank()) null else index to text
        }
        if (paragraphs.isEmpty()) return
        tts.speakParagraphs(paragraphs.map { it.second }) { spokenIndex ->
            val blockIndex = paragraphs.getOrNull(spokenIndex)?.first ?: -1
            _uiState.update {
                it.copy(
                    speakingParagraph = blockIndex,
                    status = "Reading paragraph ${spokenIndex + 1}",
                )
            }
        }
    }

    fun stopSpeaking() {
        tts.stop()
        _uiState.update { it.copy(status = "Read aloud stopped", speakingParagraph = -1) }
    }

    private fun currentBlocks(): List<Block> =
        documentFromJson(_uiState.value.current?.docJson).blocks
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
    val blocks = remember(current?.id, current?.docJson) {
        documentFromJson(current?.docJson).blocks
    }
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
                    itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                        ReaderBlockView(
                            block = block,
                            mediaPaths = state.mediaPaths,
                            fontSizeSp = state.fontSizeSp,
                            lineHeight = state.lineHeight,
                            textColor = palette.text,
                            secondaryColor = palette.secondary,
                            speaking = index == state.speakingParagraph,
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

@Composable
private fun ReaderBlockView(
    block: Block,
    mediaPaths: Map<String, String>,
    fontSizeSp: Int,
    lineHeight: Float,
    textColor: Color,
    secondaryColor: Color,
    speaking: Boolean,
) {
    when (block) {
        is Paragraph -> {
            val text = block.readableText()
            if (text.isNotBlank()) {
                Text(
                    text,
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                    fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        is Heading -> {
            val text = block.readableText()
            if (text.isNotBlank()) {
                Text(
                    text,
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSizeSp + 6 - block.level.coerceAtMost(3)).sp,
                )
            }
        }
        is Quote -> {
            val text = block.readableText()
            if (text.isNotBlank()) {
                Text(
                    text,
                    color = secondaryColor,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                    fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        is Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = InkSpacing.sm),
            color = secondaryColor.copy(alpha = .3f),
        )
        is ListItem -> {
            val text = block.readableText()
            if (text.isNotBlank()) {
                Text(
                    "• $text",
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                    modifier = Modifier.padding(start = (block.depth * 16).dp),
                    fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        is CodeBlock -> {
            if (block.text.isNotBlank()) {
                Text(
                    block.text,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSizeSp - 1).sp,
                    fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        is MediaBlock -> ReaderMediaBlock(
            block = block,
            path = storedMediaPathOrNull(mediaPaths[block.mediaId]),
            secondaryColor = secondaryColor,
        )
        is MediaStackBlock -> ReaderMediaStack(
            block = block,
            mediaPaths = mediaPaths,
            secondaryColor = secondaryColor,
        )
        is MediaGridBlock -> ReaderMediaGrid(
            block = block,
            mediaPaths = mediaPaths,
        )
        else -> Unit
    }
}

@Composable
private fun ReaderMediaBlock(
    block: MediaBlock,
    path: String?,
    secondaryColor: Color,
) {
    if (path == null) return
    val alignment = when (block.align) {
        Align.Start -> Alignment.CenterStart
        Align.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    val fraction = (block.widthPercent / 100f).coerceIn(0.2f, 1f)
    val caption = block.caption.plainText()
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(modifier = Modifier.fillMaxWidth(fraction)) {
            when (block.kind) {
                MediaKind.Audio -> AudioMediaPlayer(
                    path = path,
                    label = caption.ifBlank { "Audio" },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> ZoomableMedia(
                    path = path,
                    isVideo = block.kind == MediaKind.Video,
                    contentDescription = caption.ifBlank { "Scene media" },
                    maxHeight = 360.dp,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(InkSpacing.radiusSm)),
                )
            }
            if (caption.isNotBlank() && block.kind != MediaKind.Audio) {
                Text(
                    caption,
                    color = secondaryColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.xs),
                    textAlign = when (block.align) {
                        Align.Start -> TextAlign.Start
                        Align.End -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderMediaStack(
    block: MediaStackBlock,
    mediaPaths: Map<String, String>,
    secondaryColor: Color,
) {
    val resolved = remember(block.mediaIds, mediaPaths) {
        block.mediaIds.mapNotNull { id -> storedMediaPathOrNull(mediaPaths[id])?.let { id to it } }
    }
    if (resolved.isEmpty()) return
    var index by remember(block.id, block.currentIndex, resolved.size) {
        mutableIntStateOf(block.currentIndex.coerceIn(0, resolved.lastIndex))
    }
    val current = resolved.getOrNull(index.coerceIn(0, resolved.lastIndex)) ?: resolved.first()
    Column(modifier = Modifier.fillMaxWidth()) {
        ZoomableMedia(
            path = current.second,
            contentDescription = "Stacked media ${index + 1}/${resolved.size}",
            maxHeight = 360.dp,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(InkSpacing.radiusSm)),
        )
        if (resolved.size > 1) {
            Text(
                "Picture ${index + 1} of ${resolved.size} · tap to cycle",
                color = secondaryColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = InkSpacing.xs)
                    .clickable { index = (index + 1) % resolved.size },
            )
        }
    }
}

@Composable
private fun ReaderMediaGrid(
    block: MediaGridBlock,
    mediaPaths: Map<String, String>,
) {
    val paths = block.mediaIds.mapNotNull { storedMediaPathOrNull(mediaPaths[it]) }
    if (paths.isEmpty()) return
    val columns = if (block.template.contains("3")) 3 else 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(block.gutterDp.dp),
    ) {
        paths.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(block.gutterDp.dp),
            ) {
                row.forEach { path ->
                    ZoomableMedia(
                        path = path,
                        contentDescription = "Scene media",
                        maxHeight = 200.dp,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(InkSpacing.radiusSm)),
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
