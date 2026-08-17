package com.ihy2ln.weaverse.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageRole
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.SceneFtsEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun cascadeDelete_bookRemovesActsChaptersAndScenes() = runBlocking {
        val book = BookEntity(title = "Test Book")
        db.bookDao().upsert(book)
        val act = ActEntity(bookId = book.id, title = "Act I")
        db.actDao().upsert(act)
        val chapter = ChapterEntity(actId = act.id, title = "Ch 1")
        db.chapterDao().upsert(chapter)
        val scene = SceneEntity(chapterId = chapter.id, title = "Scene 1")
        db.sceneDao().upsert(scene)

        db.bookDao().delete(book)

        assertTrue(db.actDao().observeByBook(book.id).first().isEmpty())
        assertTrue(db.chapterDao().observeByAct(act.id).first().isEmpty())
        assertTrue(db.sceneDao().observeByChapter(chapter.id).first().isEmpty())
    }

    @Test
    fun codexEntry_aliasesRoundTripThroughTypeConverter() = runBlocking {
        val category = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = "book-1",
            name = "Characters",
            colorHex = "#4A90D9",
        )
        db.codexCategoryDao().upsert(category)
        val entry = CodexEntryEntity(
            categoryId = category.id,
            scopeType = ScopeType.Book,
            scopeId = "book-1",
            name = "John Zhao",
            aliases = listOf("John", "Zhao"),
            alwaysInclude = true,
        )
        db.codexEntryDao().upsert(entry)

        val loaded = db.codexEntryDao().getById(entry.id)
        assertEquals(listOf("John", "Zhao"), loaded?.aliases)
        assertTrue(loaded?.alwaysInclude == true)
    }

    @Test
    fun codexEntryLore_keysRoundTrip() = runBlocking {
        val category = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = "book-1",
            name = "Characters",
            colorHex = "#4A90D9",
        )
        db.codexCategoryDao().upsert(category)
        val entry = CodexEntryEntity(
            categoryId = category.id,
            scopeType = ScopeType.Book,
            scopeId = "book-1",
            name = "Mara Voss",
        )
        db.codexEntryDao().upsert(entry)
        db.codexEntryLoreDao().upsert(
            CodexEntryLoreEntity(entryId = entry.id, keys = listOf("Mara", "Mara Voss"), insertionOrder = 5),
        )

        val lore = db.codexEntryLoreDao().getByEntryId(entry.id)
        assertEquals(listOf("Mara", "Mara Voss"), lore?.keys)
        assertEquals(5, lore?.insertionOrder)
    }

    @Test
    fun sceneFts_searchFindsReindexedScene() = runBlocking {
        val scene = SceneEntity(chapterId = "chapter-1", title = "The Arrival", plainText = "John Zhao steps off the bus.")
        db.sceneDao().upsert(scene)
        db.sceneFtsDao().insert(SceneFtsEntity(entityId = scene.id, plainText = scene.plainText))

        val hits = db.sceneFtsDao().search("Zhao*")
        assertEquals(listOf(scene.id), hits)
    }

    @Test
    fun sceneFts_reindexReplacesPreviousEntry() = runBlocking {
        val entityId = newId()
        db.sceneFtsDao().reindex(entityId, "original text about lighthouses")
        db.sceneFtsDao().reindex(entityId, "revised text about mountains")

        assertTrue(db.sceneFtsDao().search("lighthouses*").isEmpty())
        assertEquals(listOf(entityId), db.sceneFtsDao().search("mountains*"))
    }

    @Test
    fun rpMessage_swipeCycling_activatesOnlyOneMessagePerGroup() = runBlocking {
        val persona = RpPersonaEntity(name = "Reader")
        db.rpPersonaDao().upsert(persona)
        val chat = RpChatEntity(personaId = persona.id, title = "Test chat")
        db.rpChatDao().upsert(chat)

        val swipeGroupId = newId()
        val firstReply = RpMessageEntity(chatId = chat.id, swipeGroupId = swipeGroupId, swipeIndex = 0, role = RpMessageRole.Char)
        val secondReply = RpMessageEntity(chatId = chat.id, swipeGroupId = swipeGroupId, swipeIndex = 1, role = RpMessageRole.Char, isActiveSwipe = false)
        db.rpMessageDao().upsert(firstReply)
        db.rpMessageDao().upsert(secondReply)

        db.rpMessageDao().deactivateSwipeGroup(swipeGroupId)
        db.rpMessageDao().activateSwipe(secondReply.id)

        val active = db.rpMessageDao().observeActiveByChat(chat.id).first()
        assertEquals(listOf(secondReply.id), active.map { it.id })
    }

    @Test
    fun deletingBook_doesNotThrow_whenBookHasNoSeries() = runBlocking {
        val book = BookEntity(title = "Standalone", seriesId = null)
        db.bookDao().upsert(book)
        db.bookDao().delete(book)
        assertNull(db.bookDao().getById(book.id))
    }
}
