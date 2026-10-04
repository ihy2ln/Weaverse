package com.ihy2ln.weaverse.data.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AdamsHavenCodexPackTest {
    @Test
    fun theGameVersionLeadsAndEarlierTextIsKept() {
        val first = mergedPackText("Kaela Stormfang, the Frost-Fang Guardian.", "Kaela is a tiger-blooded knight from the novel.")
        assertTrue(first.startsWith("Kaela Stormfang, the Frost-Fang Guardian."))
        assertTrue(first.endsWith("$EARLIER_NOTES_MARKER\nKaela is a tiger-blooded knight from the novel."))
        // A later pack replaces the game part only.
        val second = mergedPackText("Kaela Stormfang, updated.", first)
        assertEquals("Kaela Stormfang, updated.\n\n$EARLIER_NOTES_MARKER\nKaela is a tiger-blooded knight from the novel.", second)
        assertEquals("New entry.", mergedPackText("New entry.", ""))
    }

    @Test
    fun namesMatchTheSameCharacterOnly() {
        assertTrue(isSameCodexName("Kaela Stormfang", "kaela stormfang"))
        assertTrue(isSameCodexName("Daisy Wildheart", "Daisy (Bonfire) Wildheart"))
        assertTrue(isSameCodexName("JD", "John", listOf("JD")))
        assertFalse(isSameCodexName("Kaela Stormfang", "Jasmine Kaela Buttacks"))
        assertFalse(isSameCodexName("JD", "JD Smith"))
    }

    @Test
    fun theBundledPackIsCompleteAndItsPicturesExist() {
        val text = File("src/main/assets/codex/adams_haven/pack.json").readText()
        val pack = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(CodexPack.serializer(), text)
        val byCategory = pack.entries.groupBy { it.category }
        assertTrue((byCategory["Characters"]?.size ?: 0) >= 60)
        assertTrue((byCategory["Monsters"]?.size ?: 0) >= 30)
        assertTrue((byCategory["Locations"]?.size ?: 0) >= 13)
        assertEquals(pack.entries.size, pack.entries.map { it.id }.distinct().size)
        pack.entries.flatMap { it.images }.forEach { assertTrue(File("src/main/assets/$it").isFile, it) }
    }

    @Test
    fun onlyPicturesTheAppInstalledAreReplaced() {
        assertTrue(isBundledPicture("adams-haven-characters-kaela-stormfang"))
        assertTrue(isBundledPicture("codex-ah-fighter-kaela"))
        assertFalse(isBundledPicture("3f2a9c1e-user-picked"))
    }

    @Test
    fun charactersHaveGalleries() {
        val text = File("src/main/assets/codex/adams_haven/pack.json").readText()
        val pack = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(CodexPack.serializer(), text)
        val chars = pack.entries.filter { it.category == "Characters" }
        assertTrue(chars.count { it.images.size >= 4 } >= 50, "most characters have a gallery")
    }
}
