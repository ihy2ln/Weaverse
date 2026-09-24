package com.ihy2ln.weaverse.feature.novel

import com.ihy2ln.weaverse.core.text.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NovelMediaScopeTest {
    @Test fun inlineUsageUsesStableIdsAndDoesNotInventReferences() {
        val blocks = listOf(Paragraph("p", listOf(Span("portrait.jpg"))),
            MediaBlock("a", "asset-1", MediaKind.Image), MediaStackBlock("stack", listOf("asset-1", "asset-2")))
        assertEquals(listOf("asset-1", "asset-2"), inlineNovelMediaIds(blocks))
        assertTrue(inlineNovelMediaIds(listOf(blocks.first())).isEmpty())
    }
}
