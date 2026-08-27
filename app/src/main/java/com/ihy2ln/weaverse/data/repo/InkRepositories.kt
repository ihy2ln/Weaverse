package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.appendSceneBeat
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeSeries(): Flow<List<SeriesEntity>> = db.seriesDao().observeAll()
    fun observeSeries(id: String): Flow<SeriesEntity?> = db.seriesDao().observeById(id)
    suspend fun getSeries(id: String): SeriesEntity? = db.seriesDao().observeById(id).first()

    suspend fun createSeries(title: String, description: String = ""): SeriesEntity {
        val now = System.currentTimeMillis()
        val entity = SeriesEntity(
            id = "series-${UUID.randomUUID()}",
            title = title.ifBlank { "Untitled Series" },
            description = description,
            createdAt = now,
        )
        db.seriesDao().upsert(entity)
        return entity
    }

    suspend fun updateSeries(entity: SeriesEntity) = db.seriesDao().upsert(entity)

    suspend fun deleteSeries(id: String) {
        db.bookDao().observeBySeries(id).first().forEach { book ->
            db.bookDao().upsert(book.copy(seriesId = null, updatedAt = System.currentTimeMillis()))
        }
        db.seriesDao().deleteById(id)
    }
}

@Singleton
class BookRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    private val defaultCategoryNames = listOf(
        "Characters", "Locations", "Objects/Items", "Lore",
        "Factions", "Subplots", "Magic/Tech Systems", "Events/Timeline",
        "Organizations", "Notes",
    )

    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = db.bookDao().observeById(id)
    suspend fun getBook(id: String): BookEntity? = db.bookDao().getById(id)
    fun observeBooksInSeries(seriesId: String): Flow<List<BookEntity>> = db.bookDao().observeBySeries(seriesId)

    suspend fun createBook(title: String, seriesId: String? = null): BookEntity {
        val now = System.currentTimeMillis()
        val bookId = "book-${UUID.randomUUID()}"
        val actId = "act-$bookId"
        val chapterId = "chapter-$bookId"
        val sceneId = "scene-$bookId-1"
        val book = BookEntity(
            id = bookId,
            seriesId = seriesId,
            title = title.ifBlank { "Untitled Book" },
            createdAt = now,
            updatedAt = now,
        )
        db.bookDao().upsert(book)
        db.manuscriptDao().upsertAct(ActEntity(actId, bookId, "Act I", 0))
        db.manuscriptDao().upsertChapter(ChapterEntity(chapterId, actId, "Chapter 1", 0))
        val doc = Document(listOf(Paragraph("p-$sceneId", listOf(Span("")))))
        db.manuscriptDao().upsertScene(
            SceneEntity(
                id = sceneId,
                chapterId = chapterId,
                title = "Scene 1",
                sortOrder = 0,
                docJson = doc.toJson(),
                plainText = doc.plainText(),
                wordCount = doc.wordCount(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        defaultCategoryNames.forEachIndexed { index, name ->
            db.codexDao().upsertCategory(
                CodexCategoryEntity(
                    id = "cat-$bookId-$index",
                    scopeType = "book",
                    scopeId = bookId,
                    name = name,
                    colorHex = CodexCategoryColors[index % CodexCategoryColors.size].toHexString(),
                    sortOrder = index,
                    isSystem = index < 6,
                    isBuiltIn = true,
                ),
            )
        }
        return book
    }

    suspend fun updateBook(entity: BookEntity) =
        db.bookDao().upsert(entity.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setBookSeries(bookId: String, seriesId: String?) {
        val book = db.bookDao().observeById(bookId).first() ?: return
        db.bookDao().upsert(book.copy(seriesId = seriesId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteBook(bookId: String) {
        db.codexDao().deleteEntriesForScope(bookId)
        db.codexDao().deleteCategoriesForScope(bookId)
        db.bookDao().deleteById(bookId)
    }

    suspend fun copyBook(bookId: String): BookEntity? {
        val source = getBook(bookId) ?: return null
        val now = System.currentTimeMillis()
        val newBookId = "book-${UUID.randomUUID()}"
        val copy = source.copy(
            id = newBookId,
            title = "Copy of ${source.title}",
            createdAt = now,
            updatedAt = now,
        )
        db.bookDao().upsert(copy)
        db.manuscriptDao().getActs(bookId).forEach { act ->
            val newActId = "act-${UUID.randomUUID()}"
            db.manuscriptDao().upsertAct(act.copy(id = newActId, bookId = newBookId))
            db.manuscriptDao().getChapters(act.id).forEach { chapter ->
                val newChapterId = "chapter-${UUID.randomUUID()}"
                db.manuscriptDao().upsertChapter(chapter.copy(id = newChapterId, actId = newActId))
                db.manuscriptDao().getScenes(chapter.id).forEach { scene ->
                    db.manuscriptDao().upsertScene(
                        scene.copy(
                            id = "scene-${UUID.randomUUID()}",
                            chapterId = newChapterId,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
        return copy
    }

    suspend fun setCoverMediaId(bookId: String, mediaId: String?) {
        val book = getBook(bookId) ?: return
        updateBook(book.copy(coverMediaId = mediaId))
    }

    suspend fun firstSceneId(bookId: String): String? {
        val acts = db.manuscriptDao().observeActs(bookId).first()
        val act = acts.firstOrNull() ?: return null
        val chapters = db.manuscriptDao().observeChapters(act.id).first()
        val chapter = chapters.firstOrNull() ?: return null
        return db.manuscriptDao().observeScenes(chapter.id).first().firstOrNull()?.id
    }

    suspend fun primaryChapterId(bookId: String): String? {
        val acts = db.manuscriptDao().observeActs(bookId).first()
        val act = acts.firstOrNull() ?: return null
        return db.manuscriptDao().observeChapters(act.id).first().firstOrNull()?.id
    }

    suspend fun primaryActId(bookId: String): String? =
        db.manuscriptDao().observeActs(bookId).first().firstOrNull()?.id
}

@Singleton
class ManuscriptRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeScene(id: String): Flow<SceneEntity?> = db.manuscriptDao().observeScene(id)
    suspend fun getScene(id: String): SceneEntity? = db.manuscriptDao().getScene(id)
    fun observeScenes(chapterId: String) = db.manuscriptDao().observeScenes(chapterId)
    fun observeActs(bookId: String) = db.manuscriptDao().observeActs(bookId)
    fun observeChapters(actId: String) = db.manuscriptDao().observeChapters(actId)
    suspend fun saveScene(scene: SceneEntity) = db.manuscriptDao().upsertScene(scene)

    suspend fun saveChapter(chapter: ChapterEntity) = db.manuscriptDao().upsertChapter(chapter)

    suspend fun deleteScene(sceneId: String) = db.manuscriptDao().deleteScene(sceneId)

    suspend fun deleteChapter(chapterId: String) {
        db.manuscriptDao().getScenes(chapterId).forEach { db.manuscriptDao().deleteScene(it.id) }
        db.manuscriptDao().deleteChapter(chapterId)
    }

    suspend fun ensureAct(bookId: String): ActEntity {
        val existing = db.manuscriptDao().getActs(bookId)
        existing.firstOrNull()?.let { return it }
        val act = ActEntity(
            id = "act-${UUID.randomUUID()}",
            bookId = bookId,
            title = "Act I",
            sortOrder = 0,
        )
        db.manuscriptDao().upsertAct(act)
        return act
    }

    suspend fun createScene(chapterId: String): SceneEntity {
        val existing = db.manuscriptDao().getScenes(chapterId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val now = System.currentTimeMillis()
        val id = "scene-${UUID.randomUUID()}"
        val doc = Document(listOf(Paragraph("p-$id", listOf(Span("")))))
        val entity = SceneEntity(
            id = id,
            chapterId = chapterId,
            title = "Scene ${nextOrder + 1}",
            sortOrder = nextOrder,
            docJson = doc.toJson(),
            plainText = "",
            wordCount = 0,
            pov = "3rd Person",
            createdAt = now,
            updatedAt = now,
        )
        db.manuscriptDao().upsertScene(entity)
        return entity
    }

    suspend fun createChapter(actId: String): Pair<ChapterEntity, SceneEntity> {
        val existing = db.manuscriptDao().getChapters(actId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val chapter = ChapterEntity(
            id = "chapter-${UUID.randomUUID()}",
            actId = actId,
            title = "Chapter ${nextOrder + 1}",
            sortOrder = nextOrder,
        )
        db.manuscriptDao().upsertChapter(chapter)
        return chapter to createScene(chapter.id)
    }

    suspend fun appendSceneBeat(sceneId: String): SceneEntity? {
        val scene = db.manuscriptDao().getScene(sceneId) ?: return null
        val doc = documentFromJson(scene.docJson)
        val next = Document(blocks = doc.blocks.appendSceneBeat())
        val updated = scene.copy(
            docJson = next.toJson(),
            plainText = next.plainText(),
            wordCount = next.wordCount(),
            updatedAt = System.currentTimeMillis(),
        )
        db.manuscriptDao().upsertScene(updated)
        return updated
    }
}

@Singleton
class CodexRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeCategories(scopeId: String) = db.codexDao().observeCategories(scopeId)
    fun observeEntries(scopeId: String) = db.codexDao().observeEntries(scopeId)
    fun observeAllCategories() = db.codexDao().observeAllCategories()
    fun observeAllEntries() = db.codexDao().observeAllEntries()
    fun observeEntry(id: String) = db.codexDao().observeEntry(id)

    suspend fun upsertEntry(entity: CodexEntryEntity) = db.codexDao().upsertEntry(entity)

    /**
     * Fold every book's Codex into one shared library so Plan / Write / Roleplay / Notes
     * all see the same characters, locations, and lore.
     */
    suspend fun ensureGlobalAndMigrate() {
        val categories = db.codexDao().getAllCategories()
        val entries = db.codexDao().getAllEntries()
        if (categories.isEmpty() && entries.isEmpty()) return
        val remap = linkedMapOf<String, String>()
        categories.groupBy { it.name.trim().lowercase() }.forEach { (_, group) ->
            val keep = group.firstOrNull { it.scopeId == CodexScopes.ID } ?: group.first()
            val global = keep.copy(scopeType = CodexScopes.TYPE, scopeId = CodexScopes.ID)
            if (global != keep) db.codexDao().upsertCategory(global)
            group.forEach { remap[it.id] = global.id }
            group.filter { it.id != global.id }.forEach { extra ->
                db.codexDao().deleteCategory(extra.id)
            }
        }
        entries.forEach { entry ->
            val newCat = remap[entry.categoryId] ?: entry.categoryId
            if (entry.scopeId != CodexScopes.ID ||
                entry.scopeType != CodexScopes.TYPE ||
                entry.categoryId != newCat
            ) {
                db.codexDao().upsertEntry(
                    entry.copy(
                        categoryId = newCat,
                        scopeType = CodexScopes.TYPE,
                        scopeId = CodexScopes.ID,
                    ),
                )
            }
        }
    }

    suspend fun getEntry(id: String): CodexEntryEntity? = db.codexDao().observeEntry(id).first()

    suspend fun saveEntry(entity: CodexEntryEntity) = db.codexDao().upsertEntry(entity)

    suspend fun addEntry(categoryId: String, scopeId: String = CodexScopes.ID, name: String = "New entry"): CodexEntryEntity {
        val now = System.currentTimeMillis()
        val doc = Document(listOf(Paragraph(UUID.randomUUID().toString(), listOf(Span("")))))
        val entity = CodexEntryEntity(
            id = "entry-${UUID.randomUUID()}",
            categoryId = categoryId,
            scopeType = CodexScopes.TYPE,
            scopeId = CodexScopes.ID,
            name = name,
            docJson = doc.toJson(),
            plainText = "",
            createdAt = now,
            updatedAt = now,
        )
        db.codexDao().upsertEntry(entity)
        return entity
    }

    suspend fun deleteEntry(id: String) {
        db.codexDao().deleteLore(id)
        db.codexDao().deleteEntry(id)
    }

    suspend fun updateEntryText(id: String, name: String, plainText: String) {
        updateEntry(id, name, plainText)
    }

    suspend fun updateEntry(
        id: String,
        name: String,
        plainText: String,
        aliases: List<String>? = null,
        alwaysInclude: Boolean? = null,
        trackMentions: Boolean? = null,
        caseSensitiveMatching: Boolean? = null,
        imageMediaId: String? = null,
        clearImageMediaId: Boolean = false,
    ) {
        val current = db.codexDao().observeEntry(id).first() ?: return
        val doc = Document.fromPlainText(plainText)
        db.codexDao().upsertEntry(
            current.copy(
                name = name,
                docJson = doc.toJson(),
                plainText = plainText,
                aliasesJson = aliases?.let { com.ihy2ln.weaverse.core.text.encodeAliases(it) } ?: current.aliasesJson,
                alwaysInclude = alwaysInclude ?: current.alwaysInclude,
                trackMentions = trackMentions ?: current.trackMentions,
                caseSensitiveMatching = caseSensitiveMatching ?: current.caseSensitiveMatching,
                imageMediaId = when {
                    clearImageMediaId -> null
                    imageMediaId != null -> imageMediaId
                    else -> current.imageMediaId
                },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }


    suspend fun setEntryMediaIds(id: String, mediaIds: List<String>) {
        val current = db.codexDao().observeEntry(id).first() ?: return
        db.codexDao().upsertEntry(
            current.copy(
                imageMediaId = com.ihy2ln.weaverse.core.media.CodexMediaIds.encode(mediaIds),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

@Singleton
class PromptRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeFolders() = db.promptDao().observeFolders()
    fun observePrompts() = db.promptDao().observeAll()
    fun observePrompt(id: String) = db.promptDao().observeById(id)
    fun observeByType(type: String) = db.promptDao().observeByType(type)

    suspend fun upsertFolder(entity: PromptFolderEntity) = db.promptDao().upsertFolder(entity)
    suspend fun upsert(entity: PromptEntity) = db.promptDao().upsert(entity)
    suspend fun deletePrompt(id: String) = db.promptDao().deleteById(id)
    suspend fun deleteFolder(id: String) = db.promptDao().deleteFolder(id)
    suspend fun getPrompt(id: String): PromptEntity? = db.promptDao().observeById(id).first()

    suspend fun createFolder(name: String, type: String = "custom"): PromptFolderEntity {
        val folder = PromptFolderEntity(
            id = "folder-${UUID.randomUUID()}",
            name = name.ifBlank { "New folder" },
            type = type,
        )
        db.promptDao().upsertFolder(folder)
        return folder
    }

    suspend fun createPrompt(
        folderId: String,
        name: String,
        type: String = "scene_beat",
        description: String = "",
        instructionsJson: String = "[]",
        advancedJson: String = "{}",
    ): PromptEntity {
        val entity = PromptEntity(
            id = "prompt-${UUID.randomUUID()}",
            folderId = folderId,
            name = name.ifBlank { "New prompt" },
            type = type,
            description = description,
            instructionsJson = instructionsJson,
            advancedJson = advancedJson,
            createdAt = System.currentTimeMillis(),
        )
        db.promptDao().upsert(entity)
        return entity
    }
}
