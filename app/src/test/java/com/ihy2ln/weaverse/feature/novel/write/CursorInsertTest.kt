package com.ihy2ln.weaverse.feature.novel.write

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CursorInsertTest {
    private val generation = WriteGeneration(mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun trailingBlankLinesTrimmedOnSaveDontRefuseTheDraft() {
        val original = "SUMMON.\n\n"
        assertEquals(9, generation.resolveCursorOffset(original, original, 9))
        // The editor saved it with one blank line fewer: the end of the paragraph.
        assertEquals(8, generation.resolveCursorOffset("SUMMON.\n", original, 9))
    }

    @Test
    fun aRealEditStillAsksForARetarget() {
        assertThrows(IllegalStateException::class.java) {
            generation.resolveCursorOffset("SUMMON! Edited.", "SUMMON.\n\n", 7)
        }
    }
}
