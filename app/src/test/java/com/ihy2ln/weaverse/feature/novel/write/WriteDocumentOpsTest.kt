package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.core.text.FindHit
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.plainText
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteDocumentOpsTest {
    private val ops = WriteDocumentOps(mockk(relaxed = true), mockk(relaxed = true))

    private fun paras(vararg texts: String) =
        texts.mapIndexed { i, text -> Paragraph("p$i", listOf(Span(text))) }

    @Test
    fun recomputeFind_tracksMatchIndex() {
        val state = FindReplaceState(query = "cat", matchIndex = 9)
        val next = ops.recomputeFind(paras("cat sat", "the cat"), state)
        assertEquals(2, next.matches.size)
        assertEquals(1, next.matchIndex)
    }

    @Test
    fun replaceCurrent_rewritesOnlyTheActiveHit() {
        val blocks = paras("cat sat on the cat")
        val state = ops.recomputeFind(blocks, FindReplaceState(query = "cat", replacement = "dog"))
        val next = ops.replaceCurrent(blocks, state)!!
        assertEquals("dog sat on the cat", (next[0] as Paragraph).spans.plainText())
    }

    @Test
    fun replaceAll_countsEveryMatch() {
        val blocks = paras("alpha beta alpha")
        val (next, count) = ops.replaceAll(blocks, FindReplaceState(query = "alpha", replacement = "omega"))
        assertEquals(2, count)
        assertEquals("omega beta omega", (next[0] as Paragraph).spans.plainText())
    }

    @Test
    fun stepFind_wrapsAround() {
        val state = FindReplaceState(
            matches = listOf(FindHit(0, 0, 3), FindHit(1, 0, 3)),
            matchIndex = 1,
        )
        assertEquals(0, ops.stepFind(state, 1).matchIndex)
        assertEquals(1, ops.stepFind(state.copy(matchIndex = 0), -1).matchIndex)
    }
}
