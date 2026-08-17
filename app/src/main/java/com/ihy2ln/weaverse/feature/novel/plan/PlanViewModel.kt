package com.ihy2ln.weaverse.feature.novel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanOutlineNode(
    val act: ActEntity,
    val chapters: List<ChapterWithScenes>,
)

data class ChapterWithScenes(
    val chapter: ChapterEntity,
    val scenes: List<SceneEntity>,
)

enum class PlanViewMode { Grid, Outline }

val PlanPovOptions = listOf(
    "1st Person",
    "2nd Person",
    "3rd Person Limited",
    "3rd Person Omniscient",
    "3rd Person",
)

data class PlanUiState(
    val scenes: List<SceneEntity> = emptyList(),
    val outline: List<PlanOutlineNode> = emptyList(),
    val characters: List<CodexEntryEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val manuscriptRepository: ManuscriptRepository,
    private val codexRepository: CodexRepository,
    private val workspaceHistory: WorkspaceHistory,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val bookIdFlow = settings.preferences.map { it.selectedBookId }

    private val outlineSource = bookIdFlow.flatMapLatest { bookId ->
        manuscriptRepository.observeActs(bookId).flatMapLatest { acts ->
            if (acts.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList<PlanOutlineNode>() to emptyList<SceneEntity>())
            } else {
                val chapterFlows = acts.map { act -> manuscriptRepository.observeChapters(act.id) }
                combine(chapterFlows) { chapterLists ->
                    acts.zip(chapterLists.toList())
                }.flatMapLatest { actChapterPairs ->
                    val allChapters = actChapterPairs.flatMap { it.second }
                    if (allChapters.isEmpty()) {
                        kotlinx.coroutines.flow.flowOf(
                            actChapterPairs.map { (act, _) ->
                                PlanOutlineNode(act, emptyList())
                            } to emptyList(),
                        )
                    } else {
                        val sceneFlows = allChapters.map { ch -> manuscriptRepository.observeScenes(ch.id) }
                        combine(sceneFlows) { sceneLists ->
                            val chapterScenes = allChapters.zip(sceneLists.toList()).toMap()
                            val outline = actChapterPairs.map { (act, chapters) ->
                                PlanOutlineNode(
                                    act = act,
                                    chapters = chapters.map { chapter ->
                                        ChapterWithScenes(
                                            chapter = chapter,
                                            scenes = chapterScenes[chapter].orEmpty(),
                                        )
                                    },
                                )
                            }
                            val flatScenes = outline.flatMap { node ->
                                node.chapters.flatMap { it.scenes }
                            }
                            outline to flatScenes
                        }
                    }
                }
            }
        }
    }

    private val charactersSource = combine(
        codexRepository.observeAllCategories(),
        codexRepository.observeAllEntries(),
    ) { categories, entries ->
        val characterCatIds = categories
            .filter { it.name.equals("Characters", ignoreCase = true) }
            .map { it.id }
            .toSet()
        entries.filter { it.categoryId in characterCatIds || characterCatIds.isEmpty() }
            .sortedBy { it.name }
    }

    val uiState: StateFlow<PlanUiState> = combine(outlineSource, charactersSource) { outlinePair, characters ->
        PlanUiState(
            scenes = outlinePair.second,
            outline = outlinePair.first,
            characters = characters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** Back-compat for existing collectors. */
    val scenes: StateFlow<List<SceneEntity>> = uiState
        .map { it.scenes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outline: StateFlow<List<PlanOutlineNode>> = uiState
        .map { it.outline }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateScenePov(sceneId: String, pov: String) {
        viewModelScope.launch {
            val scene = uiState.value.scenes.firstOrNull { it.id == sceneId } ?: return@launch
            val characterName = scene.povCharacterId
                ?.let { id -> uiState.value.characters.firstOrNull { it.id == id }?.name }
            val label = if (!characterName.isNullOrBlank()) "$pov – $characterName" else pov
            val after = scene.copy(pov = label, updatedAt = System.currentTimeMillis())
            manuscriptRepository.saveScene(after)
            workspaceHistory.record(
                undo = { manuscriptRepository.saveScene(scene) },
                redo = { manuscriptRepository.saveScene(after) },
            )
        }
    }

    fun updateSceneCharacter(sceneId: String, characterId: String?) {
        viewModelScope.launch {
            val scene = uiState.value.scenes.firstOrNull { it.id == sceneId } ?: return@launch
            val character = characterId?.let { id -> uiState.value.characters.firstOrNull { it.id == id } }
            val basePov = extractPovBase(scene.pov)
            val label = if (character != null) "$basePov – ${character.name}" else basePov
            val after = scene.copy(
                pov = label,
                povCharacterId = character?.id,
                updatedAt = System.currentTimeMillis(),
            )
            manuscriptRepository.saveScene(after)
            workspaceHistory.record(
                undo = { manuscriptRepository.saveScene(scene) },
                redo = { manuscriptRepository.saveScene(after) },
            )
        }
    }

    fun removeScene(sceneId: String) {
        viewModelScope.launch {
            val scene = uiState.value.scenes.firstOrNull { it.id == sceneId } ?: return@launch
            manuscriptRepository.deleteScene(sceneId)
            workspaceHistory.record(
                undo = { manuscriptRepository.saveScene(scene) },
                redo = { manuscriptRepository.deleteScene(sceneId) },
            )
        }
    }

    fun addNewScene(selectedSceneId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val chapterId = PlanCreateTargets.chapterIdForNewScene(uiState.value.outline, selectedSceneId)
                ?: createChapterInternal(selectedSceneId)?.second?.chapterId
                ?: return@launch
            val scene = manuscriptRepository.createScene(chapterId)
            workspaceHistory.record(
                undo = { manuscriptRepository.deleteScene(scene.id) },
                redo = { manuscriptRepository.saveScene(scene) },
            )
            onCreated(scene.id)
        }
    }

    fun addNewChapter(selectedSceneId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val created = createChapterInternal(selectedSceneId) ?: return@launch
            val (chapter, scene) = created
            workspaceHistory.record(
                undo = { manuscriptRepository.deleteChapter(chapter.id) },
                redo = {
                    manuscriptRepository.saveChapter(chapter)
                    manuscriptRepository.saveScene(scene)
                },
            )
            onCreated(scene.id)
        }
    }

    fun addSceneBeat(selectedSceneId: String?, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val sceneId = PlanCreateTargets.sceneIdForNewBeat(uiState.value.outline, selectedSceneId)
                ?: addNewSceneAndWait(selectedSceneId)
                ?: return@launch
            val before = manuscriptRepository.getScene(sceneId)
            manuscriptRepository.appendSceneBeat(sceneId)
            val after = manuscriptRepository.getScene(sceneId)
            if (before != null && after != null && before.docJson != after.docJson) {
                workspaceHistory.record(
                    undo = { manuscriptRepository.saveScene(before) },
                    redo = { manuscriptRepository.saveScene(after) },
                )
            }
            onReady(sceneId)
        }
    }

    private suspend fun addNewSceneAndWait(selectedSceneId: String?): String? {
        val chapterId = PlanCreateTargets.chapterIdForNewScene(uiState.value.outline, selectedSceneId)
            ?: createChapterInternal(selectedSceneId)?.second?.chapterId
            ?: return null
        val scene = manuscriptRepository.createScene(chapterId)
        workspaceHistory.record(
            undo = { manuscriptRepository.deleteScene(scene.id) },
            redo = { manuscriptRepository.saveScene(scene) },
        )
        return scene.id
    }

    private suspend fun createChapterInternal(
        selectedSceneId: String?,
    ): Pair<ChapterEntity, SceneEntity>? {
        val actId = PlanCreateTargets.actIdForNewChapter(uiState.value.outline, selectedSceneId)
            ?: run {
                val bookId = settings.preferences.first().selectedBookId
                if (bookId.isBlank()) return null
                manuscriptRepository.ensureAct(bookId).id
            }
        return manuscriptRepository.createChapter(actId)
    }

    companion object {
        fun extractPovBase(pov: String): String {
            val beforeDash = pov.substringBefore("–").substringBefore("-").trim()
            return if (beforeDash.isBlank()) "3rd Person" else beforeDash
        }
    }
}
