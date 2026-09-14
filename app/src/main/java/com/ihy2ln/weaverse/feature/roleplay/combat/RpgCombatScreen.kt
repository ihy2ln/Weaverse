package com.ihy2ln.weaverse.feature.roleplay.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import com.ihy2ln.weaverse.feature.roleplay.textgame.pickGkomVariant
import java.io.File

/** RPG-only encounter surface. It deliberately has no dependency on Text Game UI/state. */
@Composable
fun RpgCombatScreen(
    state: RpgCombatState,
    campaignRuleset: RpgCombatRuleset,
    selectedCardId: String?,
    selectedTargetId: String?,
    preview: RpgCombatPreview?,
    textAction: String,
    unitArtPaths: Map<String, String> = emptyMap(),
    onRulesetSelected: (RpgCombatRuleset) -> Unit,
    onCardSelected: (RpgCombatCard) -> Unit,
    onTargetSelected: (RpgCombatant) -> Unit,
    onTextActionChange: (String) -> Unit,
    onConfirmAction: () -> Unit,
    onRetreat: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .background(tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(state.encounter.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.encounter.stakes, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
        Text(
            "${state.ruleset.label} combat · Round ${state.turn}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Campaign mode: ${campaignRuleset.label}. Choosing another style below overrides only this encounter.",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.secondaryText,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            items(RpgCombatRuleset.entries) { ruleset ->
                Text(
                    ruleset.label,
                    modifier = Modifier
                        .background(
                            if (ruleset == state.ruleset) MaterialTheme.colorScheme.primary else tokens.panel,
                            androidx.compose.foundation.shape.RoundedCornerShape(inkRadiusSm()),
                        )
                        .clickable { onRulesetSelected(ruleset) }
                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                    color = if (ruleset == state.ruleset) Color.White else tokens.primaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        HorizontalDivider(color = tokens.hairline)
        Text("Party", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            items(state.combatants.filterNot { it.isEnemy }, key = { it.id }) { member ->
                CombatantPlayingCard(
                    combatant = member,
                    artPath = unitArtPaths[member.id].orEmpty(),
                    selected = selectedTargetId == member.id,
                    onSelected = onTargetSelected,
                )
            }
        }
        Text("Enemies and intent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            items(state.combatants.filter { it.isEnemy }, key = { it.id }) { enemy ->
                CombatantPlayingCard(
                    combatant = enemy,
                    artPath = unitArtPaths[enemy.id].orEmpty(),
                    selected = selectedTargetId == enemy.id,
                    onSelected = onTargetSelected,
                    intent = state.intents.firstOrNull { it.enemyId == enemy.id }?.label,
                )
            }
        }
        if (state.finished) {
            val outcome = state.outcome
            Text(
                outcome?.result?.name ?: "Encounter complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(outcome?.recap.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Return to adventure") }
            return@Column
        }
        when (state.ruleset) {
            RpgCombatRuleset.CardBattle -> {
                Text("AP ${state.ap} · EP ${state.ep}", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    items(state.hand, key = { it.id }) { card ->
                        Column(
                            modifier = Modifier
                                .width(190.dp)
                                .heightIn(min = 116.dp)
                                .background(tokens.panel, RoundedCornerShape(inkRadiusSm()))
                                .then(
                                    if (selectedCardId == card.id) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(inkRadiusSm()),
                                    ) else Modifier,
                                )
                                .clickable { onCardSelected(card) }
                                .padding(InkSpacing.sm),
                        ) {
                            Text(card.title, style = MaterialTheme.typography.labelLarge)
                            Text("${card.apCost} AP · ${card.epCost} EP", style = MaterialTheme.typography.labelSmall)
                            Text(card.description, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            }
            RpgCombatRuleset.DndD20 -> {
                val actor = state.combatants.firstOrNull { it.id == state.activeCombatantId }
                val target = state.combatants.firstOrNull { it.id == selectedTargetId }
                Text("Select an enemy, then roll the app-owned d20 attack.", style = MaterialTheme.typography.bodyMedium)
                if (actor != null) {
                    Text(
                        "${actor.name}: d20 ${signed(actor.attackModifier)} vs ${target?.name ?: "target"} AC ${target?.armorClass ?: "—"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            RpgCombatRuleset.TextReactions -> {
                Text("Describe your reaction. Risky actions show a check preview before resolution.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = textAction,
                    onValueChange = onTextActionChange,
                    label = { Text("Your combat reaction") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        preview?.let {
            Text(
                text = when {
                    it.legal -> listOf(it.checkLabel, it.estimatedEffect).filter(String::isNotBlank).joinToString(" · ")
                    else -> it.reason
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (it.legal) tokens.activePill else MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = onConfirmAction,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                when (state.ruleset) {
                    RpgCombatRuleset.CardBattle -> "Play selected card"
                    RpgCombatRuleset.DndD20 -> "Roll d20 and attack"
                    RpgCombatRuleset.TextReactions -> "Resolve reaction"
                },
            )
        }
        OutlinedButton(onClick = onRetreat, modifier = Modifier.fillMaxWidth()) { Text("Retreat from encounter") }
        if (state.log.isNotEmpty()) {
            HorizontalDivider(color = tokens.hairline)
            Text("Combat log", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            state.log.takeLast(8).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(InkSpacing.lg))
    }
}

@Composable
private fun CombatantPlayingCard(
    combatant: RpgCombatant,
    artPath: String,
    selected: Boolean,
    onSelected: (RpgCombatant) -> Unit,
    intent: String? = null,
) {
    val alive = combatant.hp > 0
    val resolvedArtPath = artPath.ifBlank { combatant.artPath }.ifBlank {
        if (combatant.isEnemy) {
            pickGkomVariant(
                enemyId = combatant.id,
                seed = combatant.name.hashCode().toLong(),
            )?.artAssetPath?.let { "file:///android_asset/$it" }.orEmpty()
        } else {
            ""
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val accent = if (combatant.isEnemy) Color(0xFFD45757) else Color(0xFF4E91C9)
    val borderColor = when {
        selected -> Color(0xFFFFC857)
        combatant.isEnemy -> Color(0xFF9D4949)
        else -> Color(0xFF527EA3)
    }
    Box(
        modifier = Modifier
            .width(154.dp)
            .aspectRatio(0.68f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF242832), Color(0xFF111318))))
            .border(if (selected) 3.dp else 2.dp, borderColor, shape)
            .clickable(enabled = alive) { onSelected(combatant) }
            .alpha(if (alive) 1f else 0.48f),
    ) {
        if (resolvedArtPath.isNotBlank()) {
            AsyncImage(
                model = resolvedArtPath.takeIf {
                    it.startsWith("file:") || it.startsWith("content:")
                } ?: File(resolvedArtPath),
                contentDescription = "${combatant.name} unit card art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.72f), Color(0xFF191D25)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CharacterAvatar(
                    name = combatant.name,
                    colorHex = avatarColorHexFor(combatant.name, null),
                    size = 88.dp,
                )
            }
        }
        Text(
            text = if (selected) "TARGET" else if (combatant.isEnemy) "ENEMY" else "ALLY",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(if (selected) Color(0xFFE09B24) else accent)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        intent?.let {
            Text(
                text = "▶ $it",
                color = Color(0xFFFFE39A),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0xD9181115))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xF20B0D11), Color(0xFF0B0D11)),
                    ),
                )
                .padding(horizontal = InkSpacing.sm, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                combatant.name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { combatant.hp.toFloat() / combatant.maxHp.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (combatant.isEnemy) Color(0xFFFF6262) else Color(0xFF62C77A),
                trackColor = Color(0xFF343945),
            )
            Text(
                "HP ${combatant.hp}/${combatant.maxHp}  ·  AC ${combatant.armorClass}  ·  ATK ${signed(combatant.attackModifier)}",
                color = Color(0xFFE7E1D8),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            if (combatant.statuses.isNotEmpty()) {
                Text(
                    combatant.statuses.joinToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD36A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
