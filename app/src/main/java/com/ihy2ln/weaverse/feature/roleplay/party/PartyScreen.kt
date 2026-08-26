package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
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

/** Wide, letter-spaced small caps — the section rule a rulebook app uses. */
@Composable
private fun PartyHeader(label: String, count: Int) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = InkSpacing.lg,
                end = InkSpacing.lg,
                top = InkSpacing.lg,
                bottom = InkSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = tokens.secondaryText,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(start = InkSpacing.sm),
        )
        Box(
            modifier = Modifier
                .padding(start = InkSpacing.sm)
                .weight(1f)
                .height(1.dp)
                .background(tokens.hairline),
        )
    }
}

/**
 * A character plate: framed portrait, name, then a stat strip of small-caps
 * labels — the shape a tabletop companion app uses for a party list.
 */
@Composable
private fun PartyRow(member: PartyMemberUi, onClick: () -> Unit) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .clickable(onClick = onClick)
            .padding(InkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        // Square framed portrait rather than a chat-style circle.
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm())),
            contentAlignment = Alignment.Center,
        ) {
            CharacterAvatar(
                name = member.name,
                colorHex = member.avatarColorHex,
                size = 52.dp,
                present = member.isPlayer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                member.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (member.isPlayer) {
                    if (member.isDefaultPersona) "You · playing" else "You"
                } else {
                    "Character"
                },
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = tokens.activePill,
            )
            if (member.summary.isNotBlank()) {
                Text(
                    member.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
            if (member.personality.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = InkSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    StatChip("TRAITS", member.personality)
                }
            }
        }
    }
}

/** Small-caps label over its value, the way a stat block prints one. */
@Composable
private fun StatChip(label: String, value: String) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(inkRadiusSm()))
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .padding(horizontal = InkSpacing.sm, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = tokens.secondaryText,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
