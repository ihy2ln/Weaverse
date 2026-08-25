package com.ihy2ln.weaverse.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentModelTest {
    @Test
    fun plainText_extractsFromParagraphs() {
        val doc = Document(
            listOf(
                Paragraph("1", listOf(Span("Hello"))),
                Paragraph("2", listOf(Span(" world"))),
            ),
        )
        assertTrue(doc.plainText().contains("Hello"))
        assertEquals(2, doc.wordCount())
    }

    @Test
    fun jsonRoundTrip_preservesSpanText() {
        val original = Document(listOf(Paragraph("p1", listOf(Span("Persist me.")))))
        val restored = documentFromJson(original.toJson())
        assertEquals("Persist me.", restored.plainText())
    }
}
