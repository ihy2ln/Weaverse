package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MangaPageVersionsTest {
    private fun page() = MediaBlock("page", "cleaned", MediaKind.Image,
        originalMediaId = "source", activeMangaVersionId = "run-edited",
        mangaVersions = listOf(MangaPageVersion("original", "Original", "source"),
            MangaPageVersion("run-text", "Translation without cleanup", "source"),
            MangaPageVersion("run-edited", "Translation with edits", "cleaned")))

    @Test fun versionsSurviveDocumentRoundTrip() {
        val page = page()
        assertEquals(page, documentFromJson(Document(listOf(page)).toJson()).blocks.single())
    }
    @Test fun switchingKeepsOriginalAndManualEdits() {
        val layer = TextOverlay("translation", "Hello", xPercent = 25f, rotationDeg = 15f)
        val edited = page().copy(mediaId = "manual-bitmap", overlays = listOf(layer))
        val original = edited.selectMangaVersion("original")
        assertEquals("source", original.mediaId)
        assertEquals("source", original.originalMediaId)
        assertEquals("manual-bitmap", original.selectMangaVersion("run-edited").mediaId)
        assertTrue(original.overlays.isEmpty())
        assertEquals(listOf(layer), original.selectMangaVersion("run-edited").overlays)
        assertEquals(3, original.mangaVersions.size)
    }
    @Test fun oldPagesKeepTheirExistingEdit() {
        val legacy = MediaBlock("page", "old-edit", MediaKind.Image, originalMediaId = "source")
        val original = legacy.selectMangaVersion("original")
        assertEquals("source", original.mediaId)
        assertEquals("old-edit", original.selectMangaVersion("previous").mediaId)
    }
    @Test fun unknownVersionDoesNotMutatePage() {
        assertEquals(page(), page().selectMangaVersion("missing"))
    }
}
