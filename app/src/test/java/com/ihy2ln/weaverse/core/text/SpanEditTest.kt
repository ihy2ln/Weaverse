package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpanEditTest {
    @Test
    fun toggleMark_appliesBoldToRange() {
        val spans = listOf(Span("Hello world"))
        val next = spans.toggleMark(0, 5, Mark.Bold)
        assertEquals("Hello", next[0].text)
        assertTrue(Mark.Bold in next[0].marks)
        assertEquals(" world", next[1].text)
        assertTrue(next[1].marks.isEmpty())
    }

    @Test
    fun applyColor_setsHexOnRange() {
        val spans = listOf(Span("abc"))
        val next = spans.applyColor(1, 3, "#FF0000")
        assertEquals("a", next[0].text)
        assertEquals(null, next[0].colorHex)
        assertEquals("bc", next[1].text)
        assertEquals("#FF0000", next[1].colorHex)
    }

    @Test
    fun replaceRangeText_preservesSurrounding() {
        val spans = listOf(Span("one two three"))
        val next = spans.replaceRangeText(4, 7, "2")
        assertEquals("one 2 three", next.plainText())
    }

    @Test
    fun remapAfterPlainEdit_insertsWithInheritedStyle() {
        val spans = listOf(Span("Hi", marks = setOf(Mark.Bold)))
        val next = spans.remapAfterPlainEdit("Hi", "Hip", 2, 2)
        assertEquals("Hip", next.plainText())
        assertTrue(Mark.Bold in next.first().marks)
    }

    @Test
    fun jsonRoundTrip_preservesMarksAndColor() {
        val original = Document(
            listOf(
                Paragraph(
                    "p1",
                    listOf(Span("Bold", marks = setOf(Mark.Bold), colorHex = "#112233")),
                ),
            ),
        )
        val restored = documentFromJson(original.toJson())
        val span = (restored.blocks.first() as Paragraph).spans.first()
        assertEquals(setOf(Mark.Bold), span.marks)
        assertEquals("#112233", span.colorHex)
    }
}
