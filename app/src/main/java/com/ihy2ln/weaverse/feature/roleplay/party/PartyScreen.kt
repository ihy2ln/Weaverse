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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import java.io.File

/**
 * Who is in the adventure — the player's personas above the cast of characters,
 * in one place rather than split across two separate screens.
 */
@Composable
fun PartyScreen(
    onOpenPersona: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenInventory: (String) -> Unit,
    viewModel: PartyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var recruiting by remember { mutableStateOf(false) }

    if (recruiting) {
        RecruitDialog(
            inTeam = state.cast,
            bench = state.bench,
            onToggle = { id, inParty -> viewModel.setInParty(id, inParty) },
            onDismiss = { recruiting = false },
        )
    }

    if (!state.loading && state.players.isEmpty() && state.cast.isEmpty() && state.bench.isEmpty()) {
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
                PartyRow(
                    member = member,
                    onOpenSheet = {
                        member.sheetCharacterId?.let(onOpenCharacter) ?: onOpenPersona(member.id)
                    },
                    onOpenInventory = { onOpenInventory(member.sheetCharacterId ?: member.id) },
                )
            }
        }
        item(key = "hdr-team") { PartyHeader("Team", state.cast.size) }
        items(state.cast, key = { "c-${it.id}" }) { member ->
            PartyRow(
                member = member,
                onOpenSheet = { onOpenCharacter(member.id) },
                onOpenInventory = { onOpenInventory(member.id) },
            )
        }
        item(key = "recruit") {
            Text(
                if (state.cast.isEmpty()) "+ Add someone to your team" else "+ Add / remove",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.activePill,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { recruiting = true }
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
            )
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
private fun PartyRow(
    member: PartyMemberUi,
    onOpenSheet: () -> Unit,
    onOpenInventory: () -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .clickable(onClick = onOpenSheet)
            .padding(InkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        // Large picture-card portrait; tapping anywhere opens the full stat sheet.
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm())),
            contentAlignment = Alignment.Center,
        ) {
            if (member.portraitPath.isNotBlank()) {
                AsyncImage(
                    model = File(member.portraitPath),
                    contentDescription = "${member.name} portrait",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CharacterAvatar(
                    name = member.name,
                    colorHex = member.avatarColorHex,
                    size = 84.dp,
                    present = member.isPlayer,
                )
            }
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
            if (member.sheetLabel.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = InkSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    StatChip("CLASS", member.sheetLabel)
                    StatChip("HP", member.hpLabel)
                    StatChip("AC", member.armorClassLabel)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (member.isPlayer && member.sheetCharacterId == null) {
                        "Open profile ›"
                    } else {
                        "Open character sheet ›"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.activePill,
                )
                Text(
                    "Inventory & gear ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.activePill,
                    modifier = Modifier.clickable(onClick = onOpenInventory).padding(start = InkSpacing.sm),
                )
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

/** Pick who is on the immediate team; everyone else stays in the Lore cast. */
@Composable
private fun RecruitDialog(
    inTeam: List<PartyMemberUi>,
    bench: List<PartyMemberUi>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = inkTokens()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your team") },
        text = {
            if (inTeam.isEmpty() && bench.isEmpty()) {
                Text(
                    "No characters yet. Add one in Lore first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                )
            } else {
                LazyColumn {
                    items(inTeam, key = { "in-${it.id}" }) { member ->
                        RecruitRow(member.name, onTeam = true) { onToggle(member.id, false) }
                    }
                    items(bench, key = { "out-${it.id}" }) { member ->
                        RecruitRow(member.name, onTeam = false) { onToggle(member.id, true) }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun RecruitRow(name: String, onTeam: Boolean, onClick: () -> Unit) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (onTeam) "✓" else "+",
            style = MaterialTheme.typography.bodyMedium,
            color = if (onTeam) tokens.activePill else tokens.secondaryText,
            modifier = Modifier.padding(end = InkSpacing.sm),
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
