package com.ihy2ln.weaverse.feature.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.graphics.vector.ImageVector

/** Roleplay mode's left rail tabs (Revision 02 §1.4: "In roleplay mode the tabs become
 * Sessions · Codex · Snippets · Chats") — distinct from the Chats *destination*, same relationship
 * [NovelRailTab.Chats] has to [NovelDestination.Chat]. */
enum class RoleplayRailTab(val label: String, val icon: ImageVector) {
    Sessions("Sessions", Icons.Filled.Forum),
    Codex("Codex", Icons.Filled.MenuBook),
    Snippets("Snippets", Icons.Filled.Bookmark),
    Chats("Chats", Icons.Filled.Chat),
}
