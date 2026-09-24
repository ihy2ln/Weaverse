package com.ihy2ln.weaverse.core.text

import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.data.db.entities.encodePages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The storyboard page split has to survive documents written before pages existed —
 * those blocks carry no pageId and must keep showing up on the first page.
 */
class StoryboardPagesTest {
    @Test
    fun mediaBlockWrittenBeforePages_deserializesWithNullPageId() {
        // Reproduce what older builds wrote by dropping every field added since,
        // rather than hand-writing the polymorphic discriminator.
        val addedSincePages =
            setOf(
                "pageId",
                "mediaScale",
                "mediaOffsetXPercent",
                "mediaOffsetYPercent",
                "overlays",
                "originalMediaId",
                "variantKind",
            )
        val current = Document(
            listOf(MediaBlock(id = "m1", mediaId = "img-a", kind = MediaKind.Image, gridCol = 2, gridRow = 3)),
        ).toJson()
        val root = Json.parseToJsonElement(current).jsonObject
        val legacyBlock = JsonObject(
            root.getValue("blocks").jsonArray.single().jsonObject
                .filterKeys { it !in addedSincePages },
        )
        val legacy = JsonObject(mapOf("blocks" to JsonArray(listOf(legacyBlock)))).toString()
        assertTrue("pageId" !in legacy, "legacy fixture should not mention pageId: $legacy")

        val block = documentFromJson(legacy).blocks.single() as MediaBlock
        assertNull(block.pageId)
        assertEquals(2, block.gridCol)
        assertEquals(3, block.gridRow)
        assertEquals(1f, block.mediaScale)
        assertTrue(block.overlays.isEmpty())
        assertNull(block.originalMediaId)
        assertEquals("original", block.variantKind)
    }

    @Test
    fun pageIdAndOverlaysRoundTrip() {
        val overlay = TextOverlay(
            id = "ov1",
            text = "Look out!",
            style = TextOverlayStyle.SpeechBubble,
            xPercent = 30f,
            tailAngleDeg = 90f,
            source = "manga-translation",
        )
        val original = Document(
            listOf(
                MediaBlock(
                    id = "m1",
                    mediaId = "img-a",
                    kind = MediaKind.Image,
                    pageId = "page-2",
                    mediaScale = 1.8f,
                    mediaOffsetXPercent = -12f,
                    overlays = listOf(overlay),
                    originalMediaId = "img-original",
                    variantKind = "colorized",
                ),
            ),
        )
        val restored = documentFromJson(original.toJson()).blocks.single() as MediaBlock
        assertEquals("page-2", restored.pageId)
        assertEquals(1.8f, restored.mediaScale)
        assertEquals(-12f, restored.mediaOffsetXPercent)
        assertEquals(overlay, restored.overlays.single())
        assertEquals("img-original", restored.originalMediaId)
        assertEquals("colorized", restored.variantKind)
        assertEquals("manga-translation", restored.overlays.single().source)
    }

    @Test
    fun stackBlockCarriesPageAndOverlays() {
        val original = Document(
            listOf(
                MediaStackBlock(
                    id = "s1",
                    mediaIds = listOf("a", "b"),
                    pageId = "page-3",
                    overlays = listOf(TextOverlay(id = "ov", text = "hi")),
                ),
            ),
        )
        val restored = documentFromJson(original.toJson()).blocks.single() as MediaStackBlock
        assertEquals("page-3", restored.pageId)
        assertEquals("hi", restored.overlays.single().text)
    }

    @Test
    fun pageListEncodesInOrderAndSurvivesGarbage() {
        val pages = listOf(
            RpPageMeta(id = "b", order = 1, title = "Two", readingOrder = "rtl", generationStatus = "offline"),
            RpPageMeta(id = "a", order = 0),
        )
        val decoded = decodePages(encodePages(pages))
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertNull(decoded.first().title)
        assertEquals("rtl", decoded.last().readingOrder)
        assertEquals("offline", decoded.last().generationStatus)
        // A chat that predates pages stores "[]" (or anything unparseable); never crash.
        assertTrue(decodePages("[]").isEmpty())
        assertTrue(decodePages("not json").isEmpty())
    }
}
