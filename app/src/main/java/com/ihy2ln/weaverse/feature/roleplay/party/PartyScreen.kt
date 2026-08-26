package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar

/**
 * Who is in the adventure — the player's personas above the cast of characters,
 * in one place rather than split across two separate screens.
 */
@Composable
fun PartyScreen(
    onOpenPersona: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    viewModel: PartyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    if (!state.loading && state.players.isEmpty() && state.cast.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(InkSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No one here yet. Add a character or import a card to build your party.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.players.isNotEmpty()) {
            item(key = "hdr-players") { PartyHeader("You", state.players.size) }
            items(state.players, key = { "p-${it.id}" }) { member ->
                PartyRow(member) { onOpenPersona(member.id) }
            }
        }
        if (state.cast.isNotEmpty()) {
            item(key = "hdr-cast") { PartyHeader("Cast", state.cast.size) }
            items(state.cast, key = { "c-${it.id}" }) { member ->
                PartyRow(member) { onOpenCharacter(member.id) }
            }
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun PartyHeader(label: String, count: Int) {
    Text(
        "${label.uppercase()} — $count",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = inkTokens().secondaryText,
        modifier = Modifier.padding(
            start = InkSpacing.lg,
            end = InkSpacing.lg,
            top = InkSpacing.md,
            bottom = InkSpacing.xs,
        ),
    )
}

@Composable
private fun PartyRow(member: PartyMemberUi, onClick: () -> Unit) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        CharacterAvatar(
            name = member.name,
            colorHex = member.avatarColorHex,
            size = 44.dp,
            present = member.isPlayer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.isDefaultPersona) {
                    Text(
                        "· playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(start = InkSpacing.xs),
                    )
                }
            }
            if (member.summary.isNotBlank()) {
                Text(
                    member.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (member.personality.isNotBlank()) {
                Text(
                    member.personality,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
