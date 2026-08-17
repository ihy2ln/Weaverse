package com.ihy2ln.weaverse.feature.novel.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class IssueSeverity { Info, Warning }

data class ConsistencyIssue(
    val sceneId: String,
    val sceneTitle: String,
    val message: String,
    val severity: IssueSeverity,
)

/**
 * Backs the Review screen (spec §9: consistency checks + pacing chart).
 * [bookId] follows whichever book the Books rail tab has selected (see
 * `data/repo/CurrentBook.kt`). Checks are deliberately simple, scriptable
 * heuristics over what's already in Room — see BUILD_NOTES "Phase 10
 * deviations/gaps" for the checks considered and cut from this pass
 * (repeated-word/phrase detection, act/chapter-level duplicate titles,
 * category-scoped POV matching).
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
    private val codexRepository: CodexRepository,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scenes: StateFlow<List<SceneEntity>> = bookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeScenesForBook(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val codexEntries: StateFlow<List<CodexEntryEntity>> = bookId.filterNotNull()
        .flatMapLatest { codexRepository.observeEntriesForScope(ScopeType.Book, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val issues: StateFlow<List<ConsistencyIssue>> = combine(scenes, codexEntries) { sceneList, entries ->
        buildList {
            addAll(emptySceneIssues(sceneList))
            addAll(duplicateTitleIssues(sceneList))
            addAll(povMismatchIssues(sceneList, entries))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun emptySceneIssues(sceneList: List<SceneEntity>): List<ConsistencyIssue> =
        sceneList.filter { it.wordCount == 0 }.map {
            ConsistencyIssue(it.id, it.title, "Scene has no content yet", IssueSeverity.Info)
        }

    private fun duplicateTitleIssues(sceneList: List<SceneEntity>): List<ConsistencyIssue> =
        sceneList.groupBy { it.title.trim().lowercase() }
            .filter { (title, group) -> title.isNotBlank() && group.size > 1 }
            .flatMap { (_, group) ->
                group.map { ConsistencyIssue(it.id, it.title, "Scene title is used ${group.size} times in this book", IssueSeverity.Warning) }
            }

    private fun povMismatchIssues(sceneList: List<SceneEntity>, entries: List<CodexEntryEntity>): List<ConsistencyIssue> =
        sceneList.filter { it.pov.isNotBlank() }.mapNotNull { scene ->
            val known = entries.any { entry ->
                entry.name.equals(scene.pov, ignoreCase = true) || entry.aliases.any { it.equals(scene.pov, ignoreCase = true) }
            }
            if (known) null else ConsistencyIssue(scene.id, scene.title, "POV \"${scene.pov}\" has no matching Codex entry", IssueSeverity.Warning)
        }
}
