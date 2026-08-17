package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.RailItem
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.feature.novel.books.BooksSection
import com.ihy2ln.weaverse.feature.novel.codex.CodexRailContent
import com.ihy2ln.weaverse.feature.novel.manuscript.ManuscriptTab
import com.ihy2ln.weaverse.feature.novel.snippets.SnippetsScreen

/**
 * The Books | Manuscript | Codex | Snippets | Chats side panel (spec §5/
 * Revision 02 §1.4, plus Books which the spec doesn't name explicitly but
 * the app needs somewhere to manage which book Plan/Write/Chat/Review/Codex
 * all operate on). Books, Manuscript, Codex, and Snippets are real; a
 * Workshop Chats thread-list tab ships in a future phase (the Chat
 * *destination* itself already has a real screen, this would just be a
 * switcher between multiple threads per book).
 *
 * [onOpenScene] is how the Manuscript tab honors the rail's own rule (spec
 * §1.4: "the rail lists and selects; the right side displays and edits") —
 * tapping a scene node calls up to whichever shell composed this panel
 * (`NovelShellContent`) to navigate the actual content area, rather than
 * this panel opening anything itself.
 */
@Composable
fun NovelRailPanel(modifier: Modifier = Modifier, onOpenScene: (String) -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(NovelRailTab.Books) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
                NovelRailTab.entries.forEach { tab ->
                    RailItem(
                        label = tab.label,
                        icon = tab.icon,
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.padding(end = Spacing.xxs),
                    )
                }
            }
            when (selectedTab) {
                NovelRailTab.Books -> BooksSection()
                NovelRailTab.Manuscript -> ManuscriptTab(onOpenScene = onOpenScene)
                NovelRailTab.Codex -> CodexRailContent()
                NovelRailTab.Snippets -> SnippetsScreen()
                NovelRailTab.Chats -> EmptyState(
                    icon = NovelRailTab.Chats.icon,
                    title = "Workshop Chats",
                    subtitle = "Thread list lands in Phase 10.",
                )
            }
        }
    }
}
