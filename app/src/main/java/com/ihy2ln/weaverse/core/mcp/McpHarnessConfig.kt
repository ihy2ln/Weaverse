package com.ihy2ln.weaverse.core.mcp

/** Built-in connection details for harnesses that run against the Android emulator. */
object McpHarnessConfig {
    const val CODEX_NAME = "weaverse"
    const val CODEX_EMULATOR_ENDPOINT = "http://10.0.2.15:8787/mcp"

    val codexAddCommand: String
        get() = "codex mcp add $CODEX_NAME --url $CODEX_EMULATOR_ENDPOINT"
}
