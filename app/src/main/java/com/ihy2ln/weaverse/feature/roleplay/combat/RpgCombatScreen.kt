package com.ihy2ln.weaverse.feature.roleplay.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** RPG-only encounter surface. It deliberately has no dependency on Text Game UI/state. */
@Composable
fun RpgCombatScreen(
    state: RpgCombatState,
    onRulesetSelected: (RpgCombatRuleset) -> Unit,
    onCardSelected: (RpgCombatCard) -> Unit,
    onTargetSelected: (RpgCombatant) -> Unit,
    onTextAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier.padding(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(state.encounter.title, style = MaterialTheme.typography.headlineSmall)
        Text(state.encounter.stakes, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
        Text("Combat style", style = MaterialTheme.typography.labelLarge, color = tokens.activePill)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            state.combatants.filter { it.isEnemy }.forEach { enemy ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 76.dp)
                        .background(tokens.panel, androidx.compose.foundation.shape.RoundedCornerShape(inkRadiusSm()))
                        .clickable { onTargetSelected(enemy) }
                        .padding(InkSpacing.sm),
                ) {
                    Text(enemy.name, style = MaterialTheme.typography.labelLarge)
                    Text("HP ${enemy.hp}/${enemy.maxHp} · AC ${enemy.armorClass}", style = MaterialTheme.typography.labelSmall)
                    state.intents.firstOrNull { it.enemyId == enemy.id }?.let { intent ->
                        Text("Intent: ${intent.label}", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                    }
                }
            }
        }
        when (state.ruleset) {
            RpgCombatRuleset.CardBattle -> {
                Text("AP ${state.ap} · EP ${state.ep}", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    items(state.hand) { card ->
                        Column(
                            modifier = Modifier
                                .background(tokens.panel, androidx.compose.foundation.shape.RoundedCornerShape(inkRadiusSm()))
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
            RpgCombatRuleset.DndD20 -> Text("Select a target, then resolve a d20 check from the action composer.", style = MaterialTheme.typography.bodyMedium)
            RpgCombatRuleset.TextReactions -> Text("Describe your action below; the AI will narrate the reaction while the app guards the rules.", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.ruleset == RpgCombatRuleset.TextReactions) {
            Text("Use the adventure action composer to submit a text reaction.", style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
        }
    }
}
