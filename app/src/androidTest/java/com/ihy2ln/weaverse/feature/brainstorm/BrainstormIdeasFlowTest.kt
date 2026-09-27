package com.ihy2ln.weaverse.feature.brainstorm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.BrainstormIdeaEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.repo.SceneWriteStamps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainstormIdeasFlowTest {
    @Test
    fun savedIdeaMovesAndCopiesToNotesCodexAndNovelPlan() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WeaverseDatabase::class.java).build()
        try {
            val viewModel = BrainstormIdeasViewModel(db, CodexRepository(db),
                ManuscriptRepository(db, SceneWriteStamps()))
            val now = System.currentTimeMillis()
            val idea = BrainstormIdeaEntity(id = "idea-test", title = "Glass city",
                premise = "A city records every promise.", strengths = "Strong conflict",
                risks = "Scope", nextStep = "Find a keeper", createdAt = now, updatedAt = now)
            db.bookDao().upsert(BookEntity(id = "novel-test", seriesId = null,
                title = "Test novel", createdAt = now, updatedAt = now))
            viewModel.save(idea.copy(status = "Keep", pinned = true, bookId = "novel-test"))
            withTimeout(5_000) { viewModel.uiState.first { state ->
                state.ideas.any { it.id == idea.id && it.status == "Keep" && it.pinned }
            } }
            assertEquals("novel-test", db.brainstormIdeaDao().getById(idea.id)?.bookId)

            viewModel.promoteToNote(idea.id)
            withTimeout(5_000) { viewModel.uiState.first { it.status == "Copied to Notes" } }
            assertTrue(db.snippetDao().get("global").any { it.title == idea.title })

            viewModel.clearStatus()
            viewModel.promoteToCodex(idea.id, "new-ideas-category")
            withTimeout(5_000) { viewModel.uiState.first { it.status == "Copied to Codex" } }
            assertTrue(db.codexDao().getAllEntries().any { it.name == idea.title })

            viewModel.clearStatus()
            viewModel.promoteToScene(idea.id, "novel-test", null)
            withTimeout(5_000) { viewModel.uiState.first { it.status.startsWith("Created a draft scene") } }
            val act = db.manuscriptDao().getActs("novel-test").single()
            val chapter = db.manuscriptDao().getChapters(act.id).single()
            val scene = db.manuscriptDao().getScenes(chapter.id).single()
            assertEquals(idea.title, scene.title)
            assertTrue(scene.summary.contains(idea.premise))

            viewModel.clearStatus()
            viewModel.promoteToBeat(idea.id, "novel-test", scene.id)
            withTimeout(5_000) { viewModel.uiState.first { it.status.startsWith("Added a beat") } }
            val updated = db.manuscriptDao().getScene(scene.id)!!
            assertTrue(documentFromJson(updated.docJson).blocks.filterIsInstance<SceneBeatBlock>()
                .any { it.prompt.contains(idea.premise) })
        } finally {
            db.close()
        }
    }
}
