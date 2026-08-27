package com.ihy2ln.weaverse.feature.novel.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.data.settings.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReadUiState(
    val bookTitle: String = "",
    val pages: List<ReadPage> = emptyList(),
    val pageIndex: Int = 0,
    val mediaPaths: Map<String, String> = emptyMap(),
    val keepScrollOnPageChange: Boolean = false,
    val fontSizeSp: Int = 16,
    val lineHeight: Float = 1.6f,
    val showFormat: Boolean = false,
) {
    val page: ReadPage? get() = pages.getOrNull(pageIndex)
    val canPrev: Boolean get() = pageIndex > 0
    val canNext: Boolean get() = pageIndex < pages.lastIndex
    val pageLabel: String
        get() = if (pages.isEmpty()) "0 / 0" else "${pageIndex + 1} / ${pages.size}"
}

@HiltViewModel
class ReadViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val bookRepository: BookRepository,
    private val manuscriptRepository: ManuscriptRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadUiState())
    val uiState: StateFlow<ReadUiState> = _uiState.asStateFlow()
    private var pendingSceneId: String? = null

    init {
        viewModelScope.launch {
            combine(
                settings.preferences,
                mediaRepository.observeAll(),
            ) { prefs, media ->
                prefs to media
            }.collect { (prefs, media) ->
                loadManuscript(prefs, media.associate { entity ->
                    entity.id to mediaRepository.resolveFile(entity).takeIf(File::exists)?.absolutePath.orEmpty()
                }.filterValues { it.isNotBlank() })
            }
        }
    }

    fun jumpToScene(sceneId: String?) {
        pendingSceneId = sceneId
        val pages = _uiState.value.pages
        if (pages.isNotEmpty() && !sceneId.isNullOrBlank()) {
            _uiState.update { it.copy(pageIndex = ReadPager.indexOfScene(pages, sceneId)) }
        }
    }

    fun nextPage() = turnPage { ReadPager.nextIndex(it.pages.size, it.pageIndex) }
    fun prevPage() = turnPage { ReadPager.prevIndex(it.pages.size, it.pageIndex) }
    fun nextChapter() = turnPage { ReadPager.nextChapterIndex(it.pages, it.pageIndex) }
    fun prevChapter() = turnPage { ReadPager.prevChapterIndex(it.pages, it.pageIndex) }

    fun toggleFormat() = _uiState.update { it.copy(showFormat = !it.showFormat) }
    fun dismissFormat() = _uiState.update { it.copy(showFormat = false) }

    fun setKeepScrollOnPageChange(enabled: Boolean) {
        viewModelScope.launch { settings.setKeepScrollOnPageChange(enabled) }
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { settings.setFontSize(sp) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { settings.setLineHeight(value) }
    }

    private fun turnPage(compute: (ReadUiState) -> Int) {
        _uiState.update { state ->
            val next = compute(state)
            state.copy(pageIndex = next)
        }
    }

    private suspend fun loadManuscript(prefs: UserPreferences, mediaPaths: Map<String, String>) {
        val bookId = prefs.selectedBookId
        val book = bookRepository.getBook(bookId)
        val coverPath = book?.coverMediaId
            ?.let { mediaPaths[it] }
        val chapters = mutableListOf<ReadChapterInput>()
        val acts = manuscriptRepository.observeActs(bookId).first()
        acts.forEach { act ->
            manuscriptRepository.observeChapters(act.id).first().forEach { chapter ->
                val scenes = manuscriptRepository.observeScenes(chapter.id).first().map { scene ->
                    ReadSceneInput(scene.id, scene.title, scene.docJson)
                }
                chapters += ReadChapterInput(chapter.id, chapter.title, scenes)
            }
        }
        val pages = ReadPager.buildPages(coverPath, chapters)
        val requested = pendingSceneId
        pendingSceneId = null
        val index = if (!requested.isNullOrBlank()) {
            ReadPager.indexOfScene(pages, requested)
        } else {
            _uiState.value.pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
        _uiState.update {
            it.copy(
                bookTitle = book?.title.orEmpty(),
                pages = pages,
                pageIndex = index,
                mediaPaths = mediaPaths,
                keepScrollOnPageChange = prefs.keepScrollOnPageChange,
                fontSizeSp = prefs.fontSizeSp,
                lineHeight = prefs.lineHeight,
            )
        }
    }
}
