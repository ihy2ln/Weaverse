package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

/**
 * No database migration ships with the lettering fields, so every document written before they
 * existed has to keep decoding through defaults.
 */
class TextOverlayCompatibilityTest {

    /** Fields added for movable manga lettering; a pre-upgrade document has none of them. */
    private val addedForLettering = setOf(
        "heightPercent",
        "manuallyAdjusted",
        "autoFit",
        "alignment",
        "strokeHex",
        "strokeWidth",
        "sourceLanguage",
        "cleanupXPercent",
        "cleanupYPercent",
        "cleanupWidthPercent",
        "cleanupHeightPercent",
    )

    private fun documentWith(overlay: TextOverlay): Document = Document(
        listOf(MediaBlock(id = "m1", mediaId = "img-a", kind = MediaKind.Image, overlays = listOf(overlay))),
    )

    /** Re-serializes the document with the new overlay keys stripped out. */
    private fun legacyJsonFor(overlay: TextOverlay): String {
        val root = Json.parseToJsonElement(documentWith(overlay).toJson()).jsonObject
        val block = root.getValue("blocks").jsonArray.single().jsonObject
        val strippedOverlay = JsonObject(
            block.getValue("overlays").jsonArray.single().jsonObject.filterKeys { it !in addedForLettering },
        )
        val strippedBlock = JsonObject(
            block.toMutableMap().apply {
                this["overlays"] = kotlinx.serialization.json.JsonArray(listOf(strippedOverlay))
            },
        )
        return JsonObject(
            root.toMutableMap().apply {
                this["blocks"] = kotlinx.serialization.json.JsonArray(listOf(strippedBlock))
            },
        ).toString()
    }

    @Test
    fun anOverlaySavedBeforeTheLetteringFieldsExistedStillDecodes() {
        val legacy = legacyJsonFor(
            TextOverlay(id = "o1", text = "Hello", xPercent = 40f, yPercent = 60f, widthPercent = 50f),
        )
        addedForLettering.forEach { field ->
            assertFalse(field in legacy, "legacy fixture should not mention $field")
        }

        val overlay = (documentFromJson(legacy).blocks.single() as MediaBlock).overlays.single()

        assertEquals("Hello", overlay.text)
        assertTrue(overlay.manuallyAdjusted)
        assertEquals(40f, overlay.xPercent)
        // Every new field falls back to a value that reproduces the old behaviour.
        assertEquals(0f, overlay.heightPercent)
        assertFalse(overlay.autoFit)
        assertEquals("Center", overlay.alignment)
        assertEquals("#FFFFFF", overlay.strokeHex)
        assertEquals(0f, overlay.strokeWidth)
        assertEquals("", overlay.sourceLanguage)
        assertEquals(-1f, overlay.cleanupXPercent)
        assertEquals(-1f, overlay.cleanupYPercent)
        assertEquals(-1f, overlay.cleanupWidthPercent)
        assertEquals(-1f, overlay.cleanupHeightPercent)
    }

    @Test
    fun aTranslationLayerRoundTripsThroughJsonWithItsCleanupBounds() {
        val original = TextOverlay(
            id = "manga-translation-0",
            text = "Help me!",
            xPercent = 30f,
            yPercent = 45f,
            widthPercent = 24f,
            heightPercent = 18f,
            source = "manga-translation",
            autoFit = true,
            alignment = "Start",
            writingMode = "Vertical",
            lineSpacing = 1.3f,
            paddingFraction = .12f,
            rotationDeg = 15f,
            strokeHex = "#000000",
            strokeWidth = 3f,
            sourceLanguage = "ja",
            cleanupXPercent = 12f,
            cleanupYPercent = 14f,
            cleanupWidthPercent = 9f,
            cleanupHeightPercent = 42f,
        )

        val decoded = (documentFromJson(documentWith(original).toJson()).blocks.single() as MediaBlock)
            .overlays.single()

        assertEquals(original, decoded)
    }

    @Test
    fun movingALayerLeavesItsCleanupBoundsAlone() {
        val placed = TextOverlay(
            id = "manga-translation-0",
            text = "Help me!",
            xPercent = 30f,
            yPercent = 45f,
            source = "manga-translation",
            cleanupXPercent = 12f,
            cleanupYPercent = 14f,
            cleanupWidthPercent = 9f,
            cleanupHeightPercent = 42f,
        )

        val moved = placed.copy(xPercent = 80f, yPercent = 15f, widthPercent = 40f)
        val decoded = (documentFromJson(documentWith(moved).toJson()).blocks.single() as MediaBlock)
            .overlays.single()

        assertEquals(80f, decoded.xPercent)
        assertEquals(12f, decoded.cleanupXPercent)
        assertEquals(14f, decoded.cleanupYPercent)
        assertEquals(9f, decoded.cleanupWidthPercent)
        assertEquals(42f, decoded.cleanupHeightPercent)
    }

    @Test
    fun malformedDocumentsDecodeToAnEmptyDocumentRatherThanThrowing() {
        assertTrue(documentFromJson("{not json").blocks.isEmpty())
    }
}
