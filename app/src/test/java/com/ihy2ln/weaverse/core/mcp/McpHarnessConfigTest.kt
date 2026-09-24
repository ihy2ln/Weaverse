package com.ihy2ln.weaverse.core.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class McpHarnessConfigTest {
    @Test
    fun `codex harness uses the built-in emulator endpoint`() {
        assertEquals("http://10.0.2.15:8787/mcp", McpHarnessConfig.CODEX_EMULATOR_ENDPOINT)
        assertEquals(
            "codex mcp add weaverse --url http://10.0.2.15:8787/mcp",
            McpHarnessConfig.codexAddCommand,
        )
    }
}
