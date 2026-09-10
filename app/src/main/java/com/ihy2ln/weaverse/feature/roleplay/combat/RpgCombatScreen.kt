package com.ihy2ln.weaverse.feature.roleplay.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** RPG-only encounter surface. It deliberately has no dependency on Text Game UI/state. */
@Composable
fun RpgCombatScreen(
    state: RpgCombatState,
    campaignRuleset: RpgCombatRuleset,
    selectedCardId: String?,
    selectedTargetId: String?,
    preview: RpgCombatPreview?,
    textAction: String,
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
                CombatantTile(member, selectedTargetId == member.id, onTargetSelected)
            }
        }
        Text("Enemies and intent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            items(state.combatants.filter { it.isEnemy }, key = { it.id }) { enemy ->
                CombatantTile(
                    combatant = enemy,
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
private fun CombatantTile(
    combatant: RpgCombatant,
    selected: Boolean,
    onSelected: (RpgCombatant) -> Unit,
    intent: String? = null,
) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier
            .width(190.dp)
            .heightIn(min = 88.dp)
            .background(tokens.panel, RoundedCornerShape(inkRadiusSm()))
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(inkRadiusSm()))
                else Modifier,
            )
            .clickable(enabled = combatant.hp > 0) { onSelected(combatant) }
            .padding(InkSpacing.sm),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(combatant.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("HP ${combatant.hp}/${combatant.maxHp} · AC ${combatant.armorClass}", style = MaterialTheme.typography.labelSmall)
            if (combatant.statuses.isNotEmpty()) {
                Text(combatant.statuses.joinToString(), style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
            }
            intent?.let { Text("Intent: $it", style = MaterialTheme.typography.labelSmall, color = tokens.activePill) }
        }
    }
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
