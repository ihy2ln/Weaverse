package com.ihy2ln.weaverse.feature.games.battle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleTarget
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog

/** The fight: enemies with intents on top, the party and JD below, the shared hand along the bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BattleScreen(vm: CardBattleViewModel, ui: CardBattleUi) {
    val b = vm.battle ?: return
    val tokens = inkTokens()
    val dir = vm.dir
    BackHandler { vm.askLeave(true) }
    // Read after every action: the engine's objects are mutable, so the tick keys recomposition.
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val pending = ui.pending
    val pendingOwner = pending?.let { b.ownerOf(it) }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = CardArt.battleBackground, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                // Header: round, chain, piles, log, withdraw.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Round ${b.round}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (b.cardsThisTurn > 0) Chip("CHAIN ${b.cardsThisTurn}", GoldColor.copy(alpha = 1f))
                    Text(
                        "Deck ${b.drawPile.size} · Discard ${b.discard.size}" + if (b.exhausted.isNotEmpty()) " · Exhausted ${b.exhausted.size}" else "",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    TextButton(onClick = vm::toggleLog) { Text("Log", color = Color.White) }
                    TextButton(onClick = { vm.askLeave(true) }) { Text("Withdraw", color = Color.White.copy(alpha = 0.8f)) }
                }

                // Enemies.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.enemies.forEach { enemy ->
                        val intentCard = b.intents[enemy.id]
                        val intent = intentCard?.let { c ->
                            val target = b.intentTargets[enemy.id]
                            IntentInfo(c, target, if (target != null && c.effectivePower > 0) b.previewDamage(c, enemy, target).amount else if (c.effectivePower > 0) b.previewDamage(c, enemy, b.allies.firstOrNull { it.alive } ?: enemy).amount else 0)
                        }
                        val targetable = pending != null && b.isTarget(pending, pendingOwner, enemy)
                        val preview = if (targetable && pendingOwner != null) b.previewDamage(pending, pendingOwner, enemy).amount else null
                        UnitTile(
                            unit = enemy, dir = dir, popups = ui.popups.filter { it.unitId == enemy.id }, modifier = Modifier.weight(1f),
                            targetable = targetable, preview = preview, intent = if (enemy.alive) intent else null, tileHeight = 150,
                            onClick = { vm.tapUnitOrAssist(enemy) },
                        )
                    }
                }
                b.enemySummoner?.takeIf { it.alive }?.let { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Bar(c.hp / c.maxHp.toFloat(), FoeColor, Modifier.weight(1f), 6)
                        Text("SP ${b.enemySp}/${BattleState.SP_MAX}", color = SpColor, fontSize = 10.sp)
                    }
                }

                // Info line.
                Text(
                    ui.message.ifBlank { b.lastMessage },
                    color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                )

                // The party on the field, then the bench.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.allies.forEach { ally ->
                        val targetable = pending != null && b.isTarget(pending, pendingOwner, ally)
                        UnitTile(
                            unit = ally, dir = dir, popups = ui.popups.filter { it.unitId == ally.id }, modifier = Modifier.weight(1f),
                            targetable = targetable, focused = ui.focus === ally, onClick = { vm.tapUnitOrAssist(ally) },
                        )
                    }
                }
                if (b.reserves.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.reserves.forEach { reserve ->
                            ReserveStrip(
                                reserve, dir, b.partneredBy(reserve), ui.focus === reserve, ui.popups.filter { it.unitId == reserve.id },
                                Modifier.weight(1f), onClick = { vm.tapUnitOrAssist(reserve) },
                            )
                        }
                    }
                }
                SummonerBar(b, ui, vm)
                ui.focus?.let { FocusActions(vm, b, it) }
                pending?.let { card ->
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(card.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(com.ihy2ln.weaverse.feature.games.battle.engine.CardText.describe(card), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                        if (card.target == BattleTarget.AllEnemies || card.target == BattleTarget.AllAllies || card.target == BattleTarget.None) {
                            Button(onClick = vm::confirmPending) { Text("Play") }
                        }
                        TextButton(onClick = vm::cancelPending) { Text("Cancel", color = Color.White) }
                    }
                }
            }

            // Hand and turn controls.
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(b.hand.toList(), key = { System.identityHashCode(it) }) { card ->
                        val owner = b.ownerOf(card)
                        CardFace(
                            card = card, dir = dir, selected = pending?.id == card.id,
                            dimmed = !b.canPay(card, owner), glow = card.id == b.glowCard,
                            onClick = { vm.selectCard(card) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (b.hand.isEmpty()) "No cards in hand." else "Tap a card, then its target.",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = vm::endTurn, enabled = !b.finished) { Text("End turn") }
                }
            }
        }
    }

    if (b.epiphanyCard != null) EpiphanyDialog(vm, b)
    if (b.finished) ResultDialog(vm, b)
    if (ui.showLog) {
        AlertDialog(
            onDismissRequest = vm::toggleLog,
            title = { Text("Battle log") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { b.log.takeLast(40).reversed().forEach { Text(it, fontSize = 12.sp, color = tokens.primaryText) } } },
            confirmButton = { TextButton(onClick = vm::toggleLog) { Text("Close") } },
        )
    }
    if (ui.confirmLeave) {
        AlertDialog(
            onDismissRequest = { vm.askLeave(false) },
            title = { Text("Withdraw from the battle?") },
            text = { Text("The run ends here and you go to the draft with what you've earned.") },
            confirmButton = { TextButton(onClick = { vm.askLeave(false); vm.leaveFight() }) { Text("Withdraw") } },
            dismissButton = { TextButton(onClick = { vm.askLeave(false) }) { Text("Keep fighting") } },
        )
    }
}

@Composable
private fun ReserveStrip(unit: BattleUnit, dir: java.io.File, partnerOf: BattleUnit?, focused: Boolean, popups: List<Popup>, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))) {
            CardArt.unit(unit, dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            popups.forEach { FloatingPopup(it, Modifier.align(Alignment.Center)) }
        }
        Column(Modifier.weight(1f)) {
            Text(unit.name.substringBefore(' '), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Bar(unit.hp / unit.maxHp.toFloat(), HpColor, Modifier.fillMaxWidth(), 4)
            Text(if (unit.alive) (if (unit.partnerUsed) "assisted" else "bench") + (partnerOf?.let { " · ${it.name.substringBefore(' ')}" } ?: "") else "down", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SummonerBar(b: BattleState, ui: CardBattleUi, vm: CardBattleViewModel) {
    val jd = b.summoner ?: return
    val pending = ui.pending
    val targetable = pending != null && b.isTarget(pending, b.ownerOf(pending), jd)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
            .border(if (targetable) 2.dp else 1.dp, if (targetable) GoldColor else SpColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { vm.tapUnitOrAssist(jd) }.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) {
            CardArt.portraitOf(jd.id, vm.dir)?.let { AsyncImage(model = it, contentDescription = "JD", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ui.popups.filter { it.unitId == jd.id }.forEach { FloatingPopup(it, Modifier.align(Alignment.Center)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("JD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Bar(jd.hp / jd.maxHp.toFloat(), HpColor, Modifier.weight(1f), 6)
                Text("${jd.hp}/${jd.maxHp}", color = Color.White, fontSize = 9.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CP", color = SpColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Pips(b.cp, 2, SpColor, 8)
                Text("SP", color = SpColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Bar(b.sp / BattleState.SP_MAX.toFloat(), SpColor, Modifier.weight(1f), 6)
                Text("${b.sp}", color = Color.White, fontSize = 9.sp)
            }
        }
        if (b.sp >= BattleState.SP_MAX) Button(onClick = vm::decree, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("Decree", fontSize = 12.sp) }
    }
}

/** The tapped fighter's own actions: basic attack, guard, ultimates, awakening, swap; assist for a reserve. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusActions(vm: CardBattleViewModel, b: BattleState, unit: BattleUnit) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${unit.name} · ATK ${unit.attack.toInt()} · MAG ${unit.magic.toInt()} · DEF ${unit.defense.toInt()} · RES ${unit.resistance.toInt()} · stress ${unit.stress}",
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val onField = b.allies.contains(unit)
            if (onField && unit.alive) {
                OutlinedButton(onClick = { vm.playBasic(unit) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Basic · 1 AP", fontSize = 11.sp) }
                OutlinedButton(onClick = { vm.playGuard(unit) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Guard · 1 AP", fontSize = 11.sp) }
                Catalog.ultimates(unit).forEachIndexed { i, ult ->
                    val ready = b.canPay(ult, unit)
                    OutlinedButton(
                        onClick = { vm.playUltimate(unit, i) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text("${if (ready) "★ " else ""}${ult.name} · ${b.ultCost(unit)} SP", fontSize = 11.sp, color = if (ready) GoldColor else Color.White.copy(alpha = 0.6f)) }
                }
                if (unit.awakeningReady && unit.collapseRounds == 0) {
                    OutlinedButton(onClick = { vm.playAwakening(unit) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Awakening", fontSize = 11.sp, color = StressColor) }
                }
                b.partnerOf(unit)?.takeIf { it.alive }?.let { partner ->
                    TextButton(onClick = { vm.swap(unit, partner) }) { Text("Swap with ${partner.name.substringBefore(' ')} (spends their round)", fontSize = 11.sp, color = Color.White) }
                }
            } else if (!onField && unit.alive) {
                val partnered = b.partneredBy(unit)
                OutlinedButton(
                    onClick = { vm.assist(unit) }, enabled = b.canPartnerAssist(unit),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Assist · ${BattleState.PARTNER_SP_COST} SP", fontSize = 11.sp) }
                partnered?.let { TextButton(onClick = { vm.swap(it, unit) }) { Text("Swap in for ${it.name.substringBefore(' ')}", fontSize = 11.sp, color = Color.White) } }
            }
        }
    }
}

@Composable
private fun EpiphanyDialog(vm: CardBattleViewModel, b: BattleState) {
    val card = b.epiphanyCard ?: return
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF14171D)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦ Epiphany", color = GoldColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            CardFace(card, vm.dir, glow = true)
            Text("Choose how ${card.name} grows for the rest of this run.", color = Color.White, fontSize = 12.sp)
            b.epiphanyOptions.forEachIndexed { i, mod ->
                OutlinedButton(onClick = { vm.chooseEpiphany(i) }, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                        Text(Catalog.epiphanyName(mod), fontWeight = FontWeight.Bold)
                        Text(Catalog.epiphanyText(mod), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultDialog(vm: CardBattleViewModel, b: BattleState) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF14171D)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (b.victory) "Victory!" else if (b.withdrawn) "Withdrawn" else "Defeat", color = if (b.victory) GoldColor else FoeColor, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text(b.lastMessage, color = Color.White, fontSize = 13.sp)
            Text("Round ${b.round} · ${b.interrupts} enemy interrupts", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Button(onClick = vm::finishFight) { Text("Continue") }
        }
    }
}
