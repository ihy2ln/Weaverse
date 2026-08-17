package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpanEditingTest {
    private fun plain(spans: List<Span>) = spans.joinToString(separator = "") { it.text }

    @Test
    fun `splitSpansAt splits a single span in two`() {
        val (before, after) = splitSpansAt(listOf(Span("Hello world")), 5)
        assertEquals("Hello", plain(before))
        assertEquals(" world", plain(after))
    }

    @Test
    fun `splitSpansAt at a span boundary does not fragment either side`() {
        val spans = listOf(Span("Hello", marks = setOf(Mark.Bold)), Span(" world"))
        val (before, after) = splitSpansAt(spans, 5)
        assertEquals(1, before.size)
        assertEquals(1, after.size)
        assertEquals(setOf(Mark.Bold), before.single().marks)
    }

    @Test
    fun `typing at the end of a plain paragraph stays a single span`() {
        val updated = updateSpansForTextChange(listOf(Span("Hello")), "Hellox")
        assertEquals(1, updated.size)
        assertEquals("Hellox", updated.single().text)
    }

    @Test
    fun `typing preserves marks on unaffected text`() {
        val oldSpans = listOf(Span("Hello ", marks = setOf(Mark.Bold)), Span("world"))
        val updated = updateSpansForTextChange(oldSpans, "Hello brave world")
        assertEquals("Hello brave world", plain(updated))
        // The bold "Hello " prefix must survive the edit untouched.
        assertEquals(setOf(Mark.Bold), updated.first().marks)
    }

    @Test
    fun `deleting from the end preserves the remaining span`() {
        val updated = updateSpansForTextChange(listOf(Span("Hello world")), "Hello")
        assertEquals(1, updated.size)
        assertEquals("Hello", updated.single().text)
    }

    @Test
    fun `deleting from the middle preserves prefix and suffix marks`() {
        val oldSpans = listOf(Span("Hello ", marks = setOf(Mark.Italic)), Span("world"))
        val updated = updateSpansForTextChange(oldSpans, "Hello rld")
        assertEquals("Hello rld", plain(updated))
    }

    @Test
    fun `applyMarkToRange bolds only the selected range`() {
        val spans = listOf(Span("Hello world"))
        val updated = applyMarkToRange(spans, 0, 5, Mark.Bold)
        assertEquals("Hello", updated.first().text)
        assertEquals(setOf(Mark.Bold), updated.first().marks)
        assertEquals(" world", updated.last().text)
        assertEquals(emptySet<Mark>(), updated.last().marks)
    }

    @Test
    fun `applyMarkToRange toggles the mark off when the whole range already has it`() {
        val spans = listOf(Span("Hello", marks = setOf(Mark.Bold)), Span(" world"))
        val updated = applyMarkToRange(spans, 0, 5, Mark.Bold)
        assertEquals(emptySet<Mark>(), updated.first().marks)
    }

    @Test
    fun `applyHighlightToRange sets highlightHex only inside the range`() {
        val spans = listOf(Span("Hello world"))
        val updated = applyHighlightToRange(spans, 6, 11, "#FFFF00")
        assertEquals(null, updated.first().highlightHex)
        assertEquals("#FFFF00", updated.last().highlightHex)
        assertEquals("world", updated.last().text)
    }

    @Test
    fun `applyColorToRange with null clears an existing color`() {
        val spans = listOf(Span("Hello world", colorHex = "#FF0000"))
        val updated = applyColorToRange(spans, 0, 5, null)
        assertEquals(null, updated.first().colorHex)
        assertEquals("#FF0000", updated.last().colorHex)
    }

    @Test
    fun `mergeAdjacentSpans combines only style-identical neighbors`() {
        val spans = listOf(Span("A"), Span("B"), Span("C", marks = setOf(Mark.Bold)), Span("D", marks = setOf(Mark.Bold)))
        val merged = mergeAdjacentSpans(spans)
        assertEquals(2, merged.size)
        assertEquals("AB", merged[0].text)
        assertEquals("CD", merged[1].text)
    }
}
