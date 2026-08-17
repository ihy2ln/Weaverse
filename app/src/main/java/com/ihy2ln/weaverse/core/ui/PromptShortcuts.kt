package com.ihy2ln.weaverse.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** `/` opens the AI scene-beat generator; `\` opens manual entry. */
enum class PromptShortcutKind {
    Ai,
    Manual,
}

data class PromptShortcut(
    val kind: PromptShortcutKind,
    val remainder: String,
)

/**
 * Detect `/` and `\` typed as a command (alone or after a newline).
 * Returns the remainder to keep in the field, or null if this is normal typing.
 */
fun consumePromptShortcut(value: String): PromptShortcut? {
    return when {
        value == "/" || value.endsWith("\n/") -> PromptShortcut(
            kind = PromptShortcutKind.Ai,
            remainder = if (value == "/") "" else value.dropLast(1),
        )
        value == "\\" || value.endsWith("\n\\") -> PromptShortcut(
            kind = PromptShortcutKind.Manual,
            remainder = if (value == "\\") "" else value.dropLast(1),
        )
        else -> null
    }
}

/** Shell provides this so every text field can open the shared prompt bar. */
val LocalPromptShortcutHandler = staticCompositionLocalOf<((PromptShortcutKind) -> Unit)?> { null }
