package com.ihy2ln.weaverse.feature.brainstorm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.appendSceneBeat
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.BrainstormIdeaEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

val BRAINSTORM_LANES = listOf("Inbox", "Exploring", "Keep", "Archived")

data class BrainstormIdeasUiState(
    val ideas: List<BrainstormIdeaEntity> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    val categories: List<CodexCategoryEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val scenes: List<SceneEntity> = emptyList(),
    val status: String = "",
)

@HiltViewModel
class BrainstormIdeasViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val codexRepository: CodexRepository,
    private val manuscriptRepository: ManuscriptRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrainstormIdeasUiState())
    val uiState: StateFlow<BrainstormIdeasUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.brainstormIdeaDao().observeAll(),
                db.bookDao().observeAll(),
                db.codexDao().observeAllCategories(),
            ) { ideas, books, categories -> Triple(ideas, books, categories) }
                .collect { (ideas, books, categories) ->
                    _uiState.update { it.copy(ideas = ideas, books = books, categories = categories) }
                }
        }
    }

    fun createIdea() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            db.brainstormIdeaDao().upsert(
                BrainstormIdeaEntity(
                    id = "idea-${UUID.randomUUID()}", title = "New idea", premise = "",
                    createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    fun save(idea: BrainstormIdeaEntity) {
        viewModelScope.launch {
            db.brainstormIdeaDao().upsert(idea.copy(
                title = idea.title.trim().ifBlank { "Untitled idea" },
                premise = idea.premise.trim(),
                status = idea.status.takeIf { it in BRAINSTORM_LANES } ?: "Inbox",
                updatedAt = System.currentTimeMillis(),
            ))
        }
    }

    fun move(idea: BrainstormIdeaEntity, lane: String) {
        if (lane in BRAINSTORM_LANES) save(idea.copy(status = lane))
    }

    fun togglePin(idea: BrainstormIdeaEntity) = save(idea.copy(pinned = !idea.pinned))

    fun delete(ideaId: String) {
        viewModelScope.launch { db.brainstormIdeaDao().deleteById(ideaId) }
    }

    fun loadPlanTargets(bookId: String) {
        viewModelScope.launch {
            val acts = db.manuscriptDao().getActs(bookId)
            val chapters = acts.flatMap { db.manuscriptDao().getChapters(it.id) }
            val scenes = chapters.flatMap { db.manuscriptDao().getScenes(it.id) }
            _uiState.update { it.copy(chapters = chapters, scenes = scenes) }
        }
    }

    fun promoteToNote(ideaId: String) = promote(ideaId) { idea ->
        val now = System.currentTimeMillis()
        db.snippetDao().upsert(SnippetEntity(
            id = "note-${UUID.randomUUID()}", scopeType = "app", scopeId = "global",
            title = idea.title, body = Document.fromPlainText(idea.asText()).toJson(),
            category = "notes", createdAt = now, updatedAt = now,
        ))
        "Copied to Notes"
    }

    fun promoteToCodex(ideaId: String, categoryId: String) = promote(ideaId) { idea ->
        val chosenId = if (categoryId == "new-ideas-category") {
            codexRepository.ensureCategory("Ideas").id
        } else {
            require(db.codexDao().getAllCategories().any { it.id == categoryId }) { "Choose a Codex category" }
            categoryId
        }
        val entry = codexRepository.addEntry(chosenId, name = idea.title)
        codexRepository.updateEntryText(entry.id, idea.title, idea.asText())
        "Copied to Codex"
    }

    fun promoteToScene(ideaId: String, bookId: String, chapterId: String?) = promote(ideaId) { idea ->
        val book = db.bookDao().getById(bookId)
        require(book?.workType == "novel") { "Choose a novel" }
        val chosenChapter = chapterId?.let { db.manuscriptDao().getChapter(it) }
        val scene = if (chosenChapter != null) {
            require(db.manuscriptDao().getAct(chosenChapter.actId)?.bookId == bookId) { "Chapter is in another book" }
            manuscriptRepository.createScene(chosenChapter.id)
        } else {
            val act = manuscriptRepository.ensureAct(bookId)
            manuscriptRepository.createChapter(act.id).second
        }
        manuscriptRepository.saveScene(scene.copy(
            title = idea.title, summary = idea.asText(), updatedAt = System.currentTimeMillis(),
        ))
        "Created a draft scene in ${book.title}"
    }

    fun promoteToBeat(ideaId: String, bookId: String, sceneId: String) = promote(ideaId) { idea ->
        val scene = db.manuscriptDao().getScene(sceneId) ?: error("Choose a scene")
        val chapter = db.manuscriptDao().getChapter(scene.chapterId) ?: error("Scene has no chapter")
        require(db.manuscriptDao().getAct(chapter.actId)?.bookId == bookId) { "Scene is in another book" }
        val doc = Document(documentFromJson(scene.docJson).blocks.appendSceneBeat(idea.asText()))
        manuscriptRepository.saveScene(scene.copy(
            docJson = doc.toJson(), plainText = doc.plainText(), wordCount = doc.wordCount(),
            updatedAt = System.currentTimeMillis(),
        ))
        "Added a beat to ${scene.title}"
    }

    private fun promote(ideaId: String, action: suspend (BrainstormIdeaEntity) -> String) {
        viewModelScope.launch {
            val result = runCatching {
                val idea = db.brainstormIdeaDao().getById(ideaId) ?: error("Idea was removed")
                action(idea)
            }
            _uiState.update { it.copy(status = result.getOrElse { err -> err.message ?: "Could not copy idea" }) }
        }
    }

    fun clearStatus() = _uiState.update { it.copy(status = "") }
}

private fun BrainstormIdeaEntity.asText(): String = buildString {
    append(premise)
    if (strengths.isNotBlank()) append("\n\nStrengths: ").append(strengths)
    if (risks.isNotBlank()) append("\nRisks: ").append(risks)
    if (nextStep.isNotBlank()) append("\nNext step: ").append(nextStep)
}
