package com.ihy2ln.weaverse.feature.novel.write

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelStore
import com.ihy2ln.weaverse.ai.*
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.core.text.*
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import com.ihy2ln.weaverse.data.repo.*
import com.ihy2ln.weaverse.data.settings.*
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NovelComposerWorkflowTest {
    @Test fun explicitGenerateStopRetrySceneSwitchAndRestartUseSavedTargetsAndModel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val db = mockk<WeaverseDatabase>(relaxed = true)
            val drafts = mutableMapOf<String, NovelPromptDraft>()
            coEvery { db.novelWritingDao().draft(any()) } answers { drafts[firstArg()] }
            coEvery { db.novelWritingDao().saveDraft(any()) } coAnswers { firstArg<NovelPromptDraft>().let { drafts[it.sceneId] = it } }
            coEvery { db.novelWritingDao().settings(any()) } returns NovelWritingSettings("book", modelRef = "openai/test-model")
            coEvery { db.bookDao().getById(any()) } returns null
            val settings = mockk<SettingsRepository>()
            every { settings.preferences } returns flowOf(UserPreferences(selectedBookId = "book"))
            val docs = mockk<WriteDocumentOps>(relaxed = true)
            fun scene(id: String) = SceneEntity(id, "chapter", id, 0, Document.fromPlainText("Original text", "stable").toJson(), "Original text", createdAt = 1, updatedAt = 1)
            every { docs.observeScene(any()) } answers { flowOf(scene(firstArg())) }
            every { docs.observeRevisions(any()) } returns flowOf(emptyList())
            val media = mockk<WriteMediaOps>(relaxed = true)
            coEvery { media.resolvePaths(any()) } returns emptyMap()
            val codex = mockk<CodexRepository>()
            every { codex.observeAllEntries() } returns flowOf(emptyList())
            val prompts = mockk<PromptRepository>()
            every { prompts.observePrompts() } returns flowOf(emptyList())
            val cache = mockk<OpenRouterModelCache>()
            every { cache.models } returns flowOf(emptyList())
            every { cache.toModelInfo(any()) } returns emptyList()
            val ai = mockk<AiGenerationService>()
            coEvery { ai.resolveModelRef(any()) } answers { firstArg<String>() }
            every { ai.hasApiKey(any()) } returns true
            coEvery { ai.modelSupportsImages(any()) } returns false
            coEvery { ai.stream(any(), any(), any(), any(), any(), any(), any()) } returns flow {
                emit(AIChunk.Delta("Partial candidate")); awaitCancellation()
            }
            val generation = spyk(WriteGeneration(mockk(), db))
            coEvery { generation.prepareStream(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                WriteGenerationPrep.Ready(WriteStreamPlan(firstArg(), AssembledPrompt(emptyList(), emptyList(), emptyList(), emptyList()), "Mock request", 200, emptyList()))
            }
            fun newModel(): WriteViewModel = WriteViewModel(prompts, docs, generation, media, ai, mockk(), settings, codex, db, mockk(), PromptEntryBus(), WorkspaceHistory(), cache, SceneWriteStamps())
            val vm = newModel(); store.put("first", vm)
            vm.loadScene("scene-a"); advanceUntilIdle()
            vm.onSelectionChange(0, TextRange(0, 8)); vm.startSelectionAi("continue", "Continue")
            assertNull(vm.uiState.value.aiOverlay!!.replaceBlockIndex)
            vm.updateAiPrompt("Retained instruction"); advanceUntilIdle()
            coVerify(exactly = 0) { ai.stream(any(), any(), any(), any(), any(), any(), any()) }
            vm.runAiGeneration(); runCurrent()
            assertTrue(vm.uiState.value.aiOverlay!!.isStreaming)
            assertEquals("Partial candidate", vm.uiState.value.aiOverlay!!.streamingText)
            vm.cancelAiGeneration(); advanceUntilIdle()
            assertFalse(vm.uiState.value.aiOverlay!!.isStreaming)
            coVerify { ai.stream(any(), any(), "openai/test-model", any(), any(), any(), any()) }
            coEvery { ai.stream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(AIChunk.Delta("Second candidate"), AIChunk.Done)
            vm.retryAiGeneration(); advanceUntilIdle()
            assertEquals("Partial candidate", vm.uiState.value.aiOverlay!!.candidates.single().streamingText)
            vm.dismissAiOverlay(); vm.resumeAiOverlay()
            assertEquals("Second candidate", vm.uiState.value.aiOverlay!!.streamingText)
            vm.loadScene("scene-b"); advanceUntilIdle()
            vm.loadScene("scene-a"); advanceUntilIdle()
            assertEquals("Second candidate", vm.uiState.value.aiOverlay!!.streamingText)
            assertEquals("Retained instruction", vm.uiState.value.aiOverlay!!.prompt)
            val restored = newModel(); store.put("restored", restored)
            restored.loadScene("scene-a"); advanceUntilIdle()
            assertEquals("Second candidate", restored.uiState.value.aiOverlay!!.streamingText)
            assertFalse(restored.uiState.value.aiOverlay!!.isStreaming)
            coVerify(exactly = 2) { ai.stream(any(), any(), any(), any(), any(), any(), any()) }
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
