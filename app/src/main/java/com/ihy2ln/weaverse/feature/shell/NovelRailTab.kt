package com.ihy2ln.weaverse.feature.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Forum
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Novel mode's left rail tabs — distinct from the Chat destination.
 * [Manuscript] is Revision 02 §1.4's addition (spec calls for it "as the
 * first tab" among its own Codex/Snippets/Chats set; [Books] is this app's
 * own pre-Revision-02 addition for switching which story is open at all,
 * which spec doesn't model — placed first since it's the more fundamental
 * "which story" selection, with Manuscript first among spec's own four).
 */
enum class NovelRailTab(val label: String, val icon: ImageVector) {
    Books("Books", Icons.Filled.AutoStories),
    Manuscript("Manuscript", Icons.Filled.Book),
    Codex("Codex", Icons.Filled.MenuBook),
    Snippets("Snippets", Icons.Filled.Bookmark),
    Chats("Chats", Icons.Filled.Forum),
}
