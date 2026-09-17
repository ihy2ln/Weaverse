package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.ai.context.*
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.core.text.*
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import com.ihy2ln.weaverse.data.repo.PromptRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NovelComposerTest {
    private fun entry(id: String, body: String) = CodexEntryEntity(id, "cat", "book", "book", id,
        docJson = "{}", plainText = body, alwaysInclude = true, createdAt = 0, updatedAt = 0)

    @Test fun droppedAndExcludedEntriesNeverAppearInEitherPayload() {
        val result = ContextBuilder().build(listOf(entry("small", "Kept fact"), entry("huge", "SECRET".repeat(500))),
            ContextBuildRequest("", maxContextTokens = 100, reserveResponseTokens = 10))
        assertFalse(result.systemBlocks.joinToString().contains("SECRET"))
        assertFalse(result.codexBlock.contains("SECRET"))
        assertTrue("huge" in result.droppedEntryIds)
        val excluded = ContextBuilder().build(listOf(entry("small", "SECRET")), ContextBuildRequest("", manualExcludeIds = setOf("small")))
        assertFalse(excluded.systemBlocks.joinToString().contains("SECRET"))
        assertTrue(excluded.usedEntries.isEmpty())
    }

    @Test fun selectedTemplateSurvivesDefaultResolution() = runTest {
        val repo = mockk<PromptRepository>()
        val default = PromptEntity(id = "default", folderId = "f", name = "Default", type = "continue", description = "Wrong default", isDefault = true, createdAt = 0)
        val chosen = default.copy(id = "chosen", description = "Chosen fidelity sentinel", isDefault = false)
        every { repo.observeByType(any()) } returns flowOf(listOf(default))
        coEvery { repo.getPrompt("chosen") } returns chosen
        val bundle = WritePromptAssembler(repo, mockk()).libraryPromptBundle("continue", PromptRenderContext(), listOf("chosen"))
        assertEquals("chosen", bundle.promptId)
        assertTrue(bundle.systemInstructions.contains("Chosen fidelity sentinel"))
        assertFalse(bundle.systemInstructions.contains("Wrong default"))
    }

    @Test fun severalTemplatesLayerSystemInstructionsInTickOrder() = runTest {
        val repo = mockk<PromptRepository>()
        val first = PromptEntity(id = "a", folderId = "f", name = "A", type = "continue", description = "FIRST_SENTINEL", createdAt = 0)
        val second = first.copy(id = "b", name = "B", description = "SECOND_SENTINEL")
        coEvery { repo.getPrompt("a") } returns first
        coEvery { repo.getPrompt("b") } returns second
        val bundle = WritePromptAssembler(repo, mockk()).libraryPromptBundle("continue", PromptRenderContext(), listOf("a", "b"))
        assertEquals(listOf("a", "b"), bundle.promptIds)
        assertTrue(bundle.systemInstructions.indexOf("FIRST_SENTINEL") < bundle.systemInstructions.indexOf("SECOND_SENTINEL"))
    }

    private fun generation(): WriteGeneration {
        val db = mockk<WeaverseDatabase>(relaxed = true)
        coEvery { db.bookDao().getById(any()) } returns null
        coEvery { db.novelWritingDao().settings(any()) } returns NovelWritingSettings("book", memory = "Remember the lighthouse", authorNote = "Keep a quiet tone")
        coEvery { db.codexDao().getAllEntries() } returns listOf(entry("small", "Kept fact"), entry("huge", "DROPPED_SENTINEL".repeat(10000)))
        val assembler = mockk<WritePromptAssembler>()
        coEvery { assembler.buildPromptRenderContext(any(), any(), any(), any(), any(), any(), any()) } returns PromptRenderContext()
        coEvery { assembler.libraryPromptBundle(any(), any(), any()) } answers { LibraryPromptBundle(thirdArg<List<String>>(), "Selected template", finalUserMessage = "Template message") }
        every { assembler.buildPovSystemBlock(any(), any()) } returns ""
        every { assembler.buildUserMessage(any(), any(), any()) } answers { "Current scene: " + secondArg<String>() }
        return WriteGeneration(assembler, db)
    }

    @Test fun previewAndGenerationPreserveInstructionTargetAndGuidance() = runTest {
        val generator = generation()
        val target = AiOverlayState(targetSceneId = "scene", promptIds = listOf("chosen"), prompt = "EXPLICIT_SENTINEL", replaceBlockIndex = 0,
            sourceParagraphText = "Before SELECTED_SENTINEL after", replaceStart = 7, replaceEnd = 24)
        val first = generator.prepareStream(target, "Scene", null, "book", true, false, 8000) as WriteGenerationPrep.Ready
        val second = generator.prepareStream(target, "Scene", null, "book", true, false, 8000) as WriteGenerationPrep.Ready
        assertEquals(first, second)
        assertEquals("chosen", first.plan.overlay.promptId)
        assertTrue(first.plan.userMessage.contains("EXPLICIT_SENTINEL"))
        assertTrue(first.plan.userMessage.contains("SELECTED_SENTINEL"))
        assertTrue(first.plan.assembled.systemBlocks.joinToString().contains("Remember the lighthouse"))
        assertFalse(first.plan.assembled.systemBlocks.joinToString().contains("DROPPED_SENTINEL"))
        assertTrue(first.plan.overlay.contextMeter!!.usedTokens <= 8000)
    }

    @Test fun requiredContentOverCapacityBlocksInsteadOfTruncating() = runTest {
        val result = generation().prepareStream(AiOverlayState(prompt = "Keep this"), "required".repeat(10000), null, "book", true, false, 2000)
        assertTrue(result is WriteGenerationPrep.Failed)
        assertTrue((result as WriteGenerationPrep.Failed).message.contains("Nothing was sent"))
    }

    @Test fun exactCursorInsertionPreservesSurroundingFormattingAndNoBeatLeaks() {
        val generator = WriteGeneration(mockk(), mockk())
        val p = Paragraph("stable", listOf(Span("Left ", setOf(Mark.Bold)), Span("right")))
        val overlay = AiOverlayState(commandId = "scene_beat", prompt = "Private beat", anchorBlockId = "stable", cursorOffset = 5, sourceParagraphText = p.plainText())
        val next = generator.acceptIntoBlocks(listOf(p), overlay, "new ")
        assertEquals("Left new right", (next.single() as Paragraph).plainText())
        assertTrue((next.single() as Paragraph).spans.first().marks.contains(Mark.Bold))
        assertFalse(Document(next).plainText().contains("Private beat"))
        assertThrows(IllegalStateException::class.java) { generator.acceptIntoBlocks(next, overlay, "duplicate") }
    }

    @Test fun persistedHistoryKeepsEachCandidatesTargetAndRestoresWithoutStreaming() {
        val original = AiOverlayState(targetSceneId = "scene", anchorBlockId = "p", cursorOffset = 3, sourceParagraphText = "abcdef", prompt = "draft", streamingText = "first")
        val retry = original.copy(streamingText = "second", candidates = listOf(original))
        val restored = Json.decodeFromString<AiOverlayState>(Json.encodeToString(retry))
        assertEquals(retry, restored)
        assertFalse(restored.isStreaming)
        assertEquals("first", restored.candidates.single().streamingText)
        assertEquals(3, restored.candidates.single().cursorOffset)
    }
}
