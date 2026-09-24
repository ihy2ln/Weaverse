package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentFindReplaceTest {
    private fun paras(vararg texts: String): List<Block> =
        texts.mapIndexed { i, text -> Paragraph("p$i", listOf(Span(text))) }

    @Test
    fun findAll_isCaseInsensitiveByDefault() {
        val hits = DocumentFindReplace.findAll(paras("The Harbor at dawn", "harbor lights"), "harbor")
        assertEquals(2, hits.size)
        assertEquals(0, hits[0].blockIndex)
        assertEquals(4, hits[0].start)
        assertEquals(1, hits[1].blockIndex)
    }

    @Test
    fun replaceHit_rewritesOnlyThatMatch() {
        val blocks = paras("cat sat on the cat")
        val hits = DocumentFindReplace.findAll(blocks, "cat")
        val next = DocumentFindReplace.replaceHit(blocks, hits[0], "dog")
        assertEquals("dog sat on the cat", (next[0] as Paragraph).spans.plainText())
    }

    @Test
    fun replaceAll_rewritesEveryMatch() {
        val blocks = paras("alpha beta alpha", "ALPHA")
        val (next, count) = DocumentFindReplace.replaceAll(blocks, "alpha", "omega")
        assertEquals(3, count)
        assertEquals("omega beta omega", (next[0] as Paragraph).spans.plainText())
        assertEquals("omega", (next[1] as Paragraph).spans.plainText())
    }
}
