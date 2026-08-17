package com.ihy2ln.weaverse.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PromptShortcutsTest {
    @Test
    fun slashAloneOpensAi() {
        val shortcut = consumePromptShortcut("/")
        assertEquals(PromptShortcutKind.Ai, shortcut?.kind)
        assertEquals("", shortcut?.remainder)
    }

    @Test
    fun backslashAloneOpensManual() {
        val shortcut = consumePromptShortcut("\\")
        assertEquals(PromptShortcutKind.Manual, shortcut?.kind)
        assertEquals("", shortcut?.remainder)
    }

    @Test
    fun slashAfterNewlineKeepsPriorText() {
        val shortcut = consumePromptShortcut("keep this\n/")
        assertEquals(PromptShortcutKind.Ai, shortcut?.kind)
        assertEquals("keep this\n", shortcut?.remainder)
    }

    @Test
    fun normalTypingIsIgnored() {
        assertNull(consumePromptShortcut("hello"))
        assertNull(consumePromptShortcut("/inside"))
        assertNull(consumePromptShortcut("path\\file"))
    }
}
