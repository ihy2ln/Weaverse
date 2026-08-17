package com.ihy2ln.weaverse.feature.novel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlanViewMode { Grid, Outline }

/**
 * Backs the Plan screen (spec §9). [bookId] follows whichever book the
 * Books rail tab has selected (see `data/repo/CurrentBook.kt`), falling
 * back to the first book in the library. Chapter/scene lists are exposed
 * as per-parent Flow-returning functions rather than pre-combined into one
 * nested tree — each section composable subscribes to just its own slice
 * (the same pattern `CodexRailContent`'s per-category prompt lists used in
 * Phase 7/8), which is simpler and lower-risk than composing deeply-nested
 * `combine()` chains in the ViewModel.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bookTitle: StateFlow<String> = bookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeBook(it) }
        .map { it?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val acts: StateFlow<List<ActEntity>> = bookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeActs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _viewMode = MutableStateFlow(PlanViewMode.Grid)
    val viewMode: StateFlow<PlanViewMode> = _viewMode
    fun setViewMode(mode: PlanViewMode) { _viewMode.value = mode }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun chaptersForAct(actId: String): Flow<List<ChapterEntity>> = libraryRepository.observeChapters(actId)
    fun scenesForChapter(chapterId: String): Flow<List<SceneEntity>> = libraryRepository.observeScenes(chapterId)
    fun chapterWordCount(chapterId: String): Flow<Int> = libraryRepository.observeChapterWordCount(chapterId)
    fun bookWordCount(): Flow<Int> = bookId.filterNotNull().flatMapLatest { libraryRepository.observeBookWordCount(it) }

    fun addAct(title: String) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            libraryRepository.upsertAct(ActEntity(bookId = id, title = title, sortOrder = acts.value.size))
        }
    }

    fun addChapter(actId: String, title: String, existingCount: Int) {
        viewModelScope.launch {
            libraryRepository.upsertChapter(ChapterEntity(actId = actId, title = title, sortOrder = existingCount))
        }
    }

    fun addScene(chapterId: String, title: String, existingCount: Int) {
        viewModelScope.launch {
            libraryRepository.upsertScene(SceneEntity(chapterId = chapterId, title = title, sortOrder = existingCount))
        }
    }
}
