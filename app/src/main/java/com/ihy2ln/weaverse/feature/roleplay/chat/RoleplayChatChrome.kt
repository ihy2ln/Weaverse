package com.ihy2ln.weaverse.feature.roleplay.chat

/**
 * Chrome lifted into [com.ihy2ln.weaverse.feature.shell.AppShell] WorkspaceChrome
 * plus the Roleplay display-mode bar (Messenger / DM / Roleplay).
 * so Roleplay does not render a duplicate full-width secondary header.
 */
data class RoleplayChatChrome(
    val title: String,
    val displayMode: String,
    val onDisplayMode: (String) -> Unit,
    /** False when the workspace itself pins the mode (Chatting, RPG, Storyboard). */
    val showSwitcher: Boolean = true,
)

fun roleplayModeSubtitle(displayMode: String): String = when (displayMode) {
    "dungeonMaster" -> "DM · text & picture board"
    "roleplay" -> "Storyboard · comic pages"
    else -> "Messenger"
}
