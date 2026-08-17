package com.ihy2ln.weaverse.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectBundleTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTripNovelBundle() {
        val original = ProjectBundle(
            version = 1,
            kind = "novel",
            exportedAt = 123L,
            book = BookDto(id = "b1", title = "Test Book", createdAt = 1, updatedAt = 2),
            acts = listOf(ActDto("a1", "b1", "Act 1", 0)),
            chapters = listOf(ChapterDto("c1", "a1", "Chapter 1", 0, "sum")),
            scenes = listOf(
                SceneDto(
                    id = "s1",
                    chapterId = "c1",
                    title = "Scene 1",
                    sortOrder = 0,
                    plainText = "Hello world",
                    pov = "3rd Person – Hero",
                    povCharacterId = "char-1",
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ),
            prompts = listOf(
                PromptDto(
                    id = "p1",
                    folderId = "f1",
                    name = "Scene Beat",
                    type = "scene_beat",
                    description = "Beat",
                    instructionsJson = "[\"Focus\"]",
                    createdAt = 1,
                ),
            ),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ProjectBundle>(encoded)
        assertEquals("novel", decoded.kind)
        assertEquals("Test Book", decoded.book?.title)
        assertEquals(1, decoded.scenes.size)
        assertEquals("char-1", decoded.scenes.first().povCharacterId)
        assertTrue(decoded.prompts.first().instructionsJson.contains("Focus"))
    }
}
