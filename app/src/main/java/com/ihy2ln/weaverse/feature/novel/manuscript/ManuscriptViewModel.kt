package com.ihy2ln.weaverse.feature.novel.manuscript

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the rail's Manuscript tab (Revision 02 §1.4: "Add a fourth tab,
 * Manuscript, as the first tab, holding the act/chapter/scene tree —
 * clicking any node loads it into the large right-hand content area").
 * Read-only and purpose-built rather than reusing `PlanViewModel` — Plan
 * carries its own view-mode/search state this tree doesn't need, and the
 * two are read from the same `LibraryRepository` Flows either way, so
 * nothing is duplicated at the data layer, only the thin ViewModel shell.
 */
@HiltViewModel
class ManuscriptViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val acts: StateFlow<List<ActEntity>> = bookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeActs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun chaptersForAct(actId: String): Flow<List<ChapterEntity>> = libraryRepository.observeChapters(actId)
    fun scenesForChapter(chapterId: String): Flow<List<SceneEntity>> = libraryRepository.observeScenes(chapterId)
}
