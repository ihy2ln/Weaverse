package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodexMediaIdsTest {
    @Test
    fun parsesSingleId() {
        assertEquals(listOf("abc"), CodexMediaIds.parse("abc"))
    }

    @Test
    fun parsesJsonArray() {
        assertEquals(listOf("a", "b"), CodexMediaIds.parse("""["a","b"]"""))
    }

    @Test
    fun encodesSingleAsPlain() {
        assertEquals("only", CodexMediaIds.encode(listOf("only")))
    }

    @Test
    fun encodesManyAsJson() {
        val encoded = CodexMediaIds.encode(listOf("a", "b"))
        assertEquals(listOf("a", "b"), CodexMediaIds.parse(encoded))
    }

    @Test
    fun emptyIsNull() {
        assertNull(CodexMediaIds.encode(emptyList()))
        assertEquals(emptyList<String>(), CodexMediaIds.parse(null))
    }
}
