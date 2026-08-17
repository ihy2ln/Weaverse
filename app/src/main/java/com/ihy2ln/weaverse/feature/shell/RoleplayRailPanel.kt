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
import com.ihy2ln.weaverse.feature.roleplay.codex.RoleplayCodexScreen
import com.ihy2ln.weaverse.feature.roleplay.sessions.RoleplaySessionsTab
import com.ihy2ln.weaverse.feature.roleplay.snippets.RoleplaySnippetsTab

/**
 * The Sessions | Codex | Snippets | Chats side panel for roleplay mode
 * (Revision 02 §1.4) — the counterpart to [NovelRailPanel], deferred from
 * rev02-01/03 until roleplay mode had a rail to put a header treatment on.
 * Sessions and Codex and Snippets are real; Chats mirrors [NovelRailTab.Chats]'s
 * own placeholder (a thread-list tab distinct from the primary Chats
 * destination) for the same reason: nothing outside this panel needs it yet.
 *
 * [onOpenChat] mirrors [NovelRailPanel.onOpenScene] — Sessions lists and
 * selects, the shell (`RoleplayShellContent`) navigates the actual content
 * area, never this panel.
 */
@Composable
fun RoleplayRailPanel(modifier: Modifier = Modifier, onOpenChat: (String) -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(RoleplayRailTab.Sessions) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
                RoleplayRailTab.entries.forEach { tab ->
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
                RoleplayRailTab.Sessions -> RoleplaySessionsTab(onOpenChat = onOpenChat)
                RoleplayRailTab.Codex -> RoleplayCodexScreen()
                RoleplayRailTab.Snippets -> RoleplaySnippetsTab()
                RoleplayRailTab.Chats -> EmptyState(
                    icon = RoleplayRailTab.Chats.icon,
                    title = "Chat threads",
                    subtitle = "A future pass adds grouping/filtering here — use Sessions to jump into a chat today.",
                )
            }
        }
    }
}
