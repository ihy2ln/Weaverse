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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.CharacterDef
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import java.io.File

/**
 * Games → Card Battle: the Adams Haven card game played inside the app, using only the cards (no 3D, no Unity). Hub,
 * party and deck, the five-node run, the fight and the end-of-run draft.
 */
@Composable
fun CardBattleScreen(onLeave: () -> Unit = {}, viewModel: CardBattleViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    when (ui.screen) {
        CardScreen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        CardScreen.Hub -> HubScreen(viewModel, ui, onLeave)
        CardScreen.Party -> PartyScreen(viewModel, ui)
        CardScreen.Run -> RunScreen(viewModel, ui)
        CardScreen.Battle -> BattleScreen(viewModel, ui)
        CardScreen.Draft -> DraftScreen(viewModel, ui)
    }
}

private fun cardOf(vm: CardBattleViewModel, id: String): BattleCard? = GameLogic.played(vm.save, id)

@Composable
private fun HubScreen(vm: CardBattleViewModel, ui: CardBattleUi, onLeave: () -> Unit) {
    val tokens = inkTokens()
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val save = vm.save
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(inkRadiusMd() * 1.5f))) {
            AsyncImage(model = CardArt.battleBackground, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, tokens.background.copy(alpha = 0.92f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(InkSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ADAMS HAVEN", style = MaterialTheme.typography.labelMedium, color = tokens.activePill, letterSpacing = 1.5.sp)
                Text("Card Battle", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    "The card game, played right here: build a party from your collection, take JD through the Silverwood and draft new cards.",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.primaryText.copy(alpha = 0.85f),
                )
            }
        }

        val node = GameLogic.current(save)
        if (save.run.active) {
            InkCard(Modifier.fillMaxWidth()) {
                Text("Run in progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    "Node ${save.run.index + 1} of ${save.run.nodes.size}" + (node?.let { " · ${it.title}" } ?: "") + " · ${save.run.victories} won",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.show(CardScreen.Run) }) { Text("Continue run") }
                    OutlinedButton(onClick = vm::abandonRun) { Text("Abandon") }
                }
            }
        } else {
            Button(onClick = vm::startRun, modifier = Modifier.fillMaxWidth()) { Text("Start a run") }
        }

        SectionLabel("YOUR PARTY")
        Text(
            "The first three start on the field; the rest wait on the bench and partner the fighter in the same slot.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            save.party.forEachIndexed { i, id ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(8.dp)).background(tokens.panel).border(if (i < GameLogic.FIELD_SIZE) 2.dp else 1.dp, if (i < GameLogic.FIELD_SIZE) tokens.activePill else tokens.hairline, RoundedCornerShape(8.dp))) {
                        CardArt.portraitOf(id, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(Catalog.get(id)?.name?.substringBefore(' ').orEmpty(), style = MaterialTheme.typography.labelSmall, color = tokens.primaryText, maxLines = 1)
                }
            }
        }
        OutlinedButton(onClick = { vm.show(CardScreen.Party) }, modifier = Modifier.fillMaxWidth()) { Text("Party & deck") }

        SectionLabel("PROGRESS")
        Text(
            "${save.collection.size} cards collected · ${save.deck.size} in your deck · ${save.runsPlayed} runs, ${save.runsWon} won",
            style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText,
        )
        Text(
            "Cards you pick up during a run are lost when it ends. Win or lose, the end-of-run draft is how cards join your collection for good (or level up).",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        TextButton(onClick = onLeave) { Text("Back to Adams Haven") }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyScreen(vm: CardBattleViewModel, ui: CardBattleUi) {
    val tokens = inkTokens()
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val save = vm.save
    BackHandler { vm.show(CardScreen.Hub) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Party & deck", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.primaryText, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.show(CardScreen.Hub) }) { Text("Done") }
        }
        if (save.run.active) Text("The party is locked while a run is in progress. You can still change your deck.", color = tokens.activePill, style = MaterialTheme.typography.bodySmall)
        SectionLabel("PARTY (${save.party.size}/${GameLogic.PARTY_SIZE}) · order matters")
        save.party.forEachIndexed { i, id ->
            val c = Catalog.get(id) ?: return@forEachIndexed
            InkCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp, 56.dp).clip(RoundedCornerShape(6.dp)).background(tokens.panel)) {
                        CardArt.portraitOf(id, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        Text(
                            (if (i < GameLogic.FIELD_SIZE) "Field" else "Bench · partners slot ${i - GameLogic.FIELD_SIZE + 1}") + " · ${c.role} · ${c.element}",
                            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
                        )
                    }
                    TextButton(onClick = { vm.moveParty(id, -1) }, enabled = i > 0 && !save.run.active) { Text("▲") }
                    TextButton(onClick = { vm.moveParty(id, 1) }, enabled = i < save.party.size - 1 && !save.run.active) { Text("▼") }
                    TextButton(onClick = { vm.toggleParty(id) }, enabled = save.party.size > 1 && !save.run.active) { Text("Remove") }
                }
            }
        }

        SectionLabel("ROSTER & CARDS")
        Text(
            "Switch cards on to put them in your deck. Cards you haven't drafted yet are locked. Only your party's cards (and JD's) are shuffled into a fight.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        Catalog.characters.forEach { c -> RosterCard(vm, c, c.id in save.party) }
        JdCard(vm)
        Spacer(Modifier.height(InkSpacing.xl))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterCard(vm: CardBattleViewModel, c: CharacterDef, inParty: Boolean) {
    val tokens = inkTokens()
    val save = vm.save
    var open by remember { mutableStateOf(false) }
    InkCard(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp, 56.dp).clip(RoundedCornerShape(6.dp)).background(tokens.panel)) {
                CardArt.portraitOf(c.id, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    listOf(c.title, "${c.role} · ${c.element}", if (c.source == "novel") c.novel.ifBlank { "Your novel" } else "").filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val owned = c.cards.count { it.id in save.collection }
                Text("$owned/${c.cards.size} cards unlocked", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
            }
            if (!inParty) TextButton(onClick = { vm.toggleParty(c.id) }, enabled = !save.run.active && save.party.size < GameLogic.PARTY_SIZE) { Text("Add") }
            else Text("In party", style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            c.cards.forEach { d -> DeckRow(vm, d.id) }
            c.ultimates.forEach { d -> UltRow(vm, d.id) }
        }
    }
}

@Composable
private fun JdCard(vm: CardBattleViewModel) {
    val tokens = inkTokens()
    var open by remember { mutableStateOf(false) }
    InkCard(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp, 56.dp).clip(RoundedCornerShape(6.dp)).background(tokens.panel)) {
                CardArt.portraitOf(Catalog.SUMMONER_ID, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f)) {
                Text("JD · the Summoner", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text("Always in the fight, never on the field. Support cards cost CP; full SP fires the decrees.", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            BuiltinContent.summonerKit().forEach { d -> DeckRow(vm, d.id) }
        }
    }
}

@Composable
private fun DeckRow(vm: CardBattleViewModel, id: String) {
    val tokens = inkTokens()
    val save = vm.save
    val card = cardOf(vm, id) ?: return
    val owned = GameLogic.owned(save, id)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardFace(card, vm.dir, dimmed = !owned, badge = if (GameLogic.level(save, id) > 1) "Lv ${GameLogic.level(save, id)}" else null, modifier = Modifier)
        Column(Modifier.weight(1f)) {
            Text(card.name, fontWeight = FontWeight.Bold, color = tokens.primaryText, fontSize = 13.sp)
            Text(com.ihy2ln.weaverse.feature.games.battle.engine.CardText.describe(card), style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
            if (card.description.isNotBlank()) Text(card.description, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText.copy(alpha = 0.8f), maxLines = 2)
        }
        if (owned) Switch(checked = id in save.deck, onCheckedChange = { vm.toggleDeck(id) })
        else Text("Locked", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
    }
}

@Composable
private fun UltRow(vm: CardBattleViewModel, id: String) {
    val tokens = inkTokens()
    val card = Catalog.card(id) ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardFace(card.also { it.kind = BattleCardKind.Ultimate }, vm.dir)
        Column(Modifier.weight(1f)) {
            Text(card.name, fontWeight = FontWeight.Bold, color = tokens.primaryText, fontSize = 13.sp)
            Text("Ultimate · fires from a full gauge for ${3} SP", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
            Text(com.ihy2ln.weaverse.feature.games.battle.engine.CardText.describe(card), style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunScreen(vm: CardBattleViewModel, ui: CardBattleUi) {
    val tokens = inkTokens()
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val save = vm.save
    val run = save.run
    BackHandler { vm.show(CardScreen.Hub) }
    val node = GameLogic.current(save)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("The Silverwood", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.primaryText, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.show(CardScreen.Hub) }) { Text("Menu") }
        }
        // The route.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            run.nodes.forEachIndexed { i, n ->
                val done = i < run.index
                val current = i == run.index
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (current) tokens.activePill.copy(alpha = 0.25f) else tokens.panel)
                        .border(if (current) 2.dp else 1.dp, if (current) tokens.activePill else tokens.hairline, RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when { done -> "✓"; n.kind == "rest" -> "♨"; n.kind == "elite" -> "☠"; n.kind == "boss" -> "♛"; else -> "⚔" },
                        fontSize = 20.sp, color = if (done) tokens.secondaryText else tokens.primaryText,
                    )
                    Text(n.title, style = MaterialTheme.typography.labelSmall, color = tokens.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (run.reward.isNotEmpty()) {
            SectionLabel("CHOOSE A CARD FOR THIS RUN")
            Text("It joins your deck until the run ends.", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                run.reward.forEach { id ->
                    cardOf(vm, id)?.let { CardFace(it, vm.dir, onClick = { vm.takeReward(id) }) }
                }
            }
            OutlinedButton(onClick = vm::skipReward) { Text("Skip") }
        } else if (node != null) {
            InkCard(Modifier.fillMaxWidth()) {
                Text(node.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    when (node.kind) {
                        "rest" -> "A quiet campfire. Everyone heals 40% (the fallen get up with 40%) and JD recovers 80."
                        "elite" -> "An elite hunt: an evolved monster with an affix, and a commander from depth 3."
                        "boss" -> "The lair boss waits with two minions. It has two phases."
                        else -> "A pack of monsters. Enemy intents are shown on each tile."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText,
                )
                Spacer(Modifier.height(8.dp))
                if (node.kind == "rest") Button(onClick = vm::rest) { Text("Rest") } else Button(onClick = vm::fight) { Text("Fight") }
            }
        }

        SectionLabel("PARTY HEALTH")
        save.party.forEach { id ->
            val u = Catalog.unit(id) ?: return@forEach
            val hp = run.hp.firstOrNull { it.id == id }?.hp ?: u.maxHp
            HealthRow(vm, id, u.name, hp, u.maxHp)
        }
        val jd = BuiltinContent.summoner()
        HealthRow(vm, jd.id, "JD", if (run.summonerHp >= 0) run.summonerHp else jd.maxHp, jd.maxHp)
        if (run.runCards.isNotEmpty()) Text("Run cards: " + run.runCards.joinToString(", ") { Catalog.card(it)?.name ?: it }, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        if (run.epiphanies.isNotEmpty()) Text("Epiphanies kept: " + run.epiphanies.joinToString(", ") { (Catalog.card(it.id)?.name ?: it.id) + " (" + it.mods.split(',').joinToString("+") { m -> Catalog.epiphanyName(m) } + ")" }, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        OutlinedButton(onClick = vm::abandonRun) { Text("Abandon run") }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}

@Composable
private fun HealthRow(vm: CardBattleViewModel, id: String, name: String, hp: Int, maxHp: Int) {
    val tokens = inkTokens()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(tokens.panel)) {
            CardArt.portraitOf(id, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(name.substringBefore(' '), color = tokens.primaryText, modifier = Modifier.width(70.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall)
        Bar(hp / maxHp.toFloat(), if (hp > 0) HpColor else FoeColor, Modifier.weight(1f), 8)
        Text(if (hp > 0) "$hp/$maxHp" else "down", color = tokens.secondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DraftScreen(vm: CardBattleViewModel, ui: CardBattleUi) {
    val tokens = inkTokens()
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val save = vm.save
    val won = save.run.won
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Text(if (won) "The Lair is cleared!" else "The run is over", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (won) tokens.activePill else tokens.primaryText)
        Text(
            "Draft your reward: pick one card per fighter. A new card joins your collection and deck for good; one you already own levels up (+10% power each, up to level ${GameLogic.MAX_CARD_LEVEL}).",
            style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText,
        )
        save.draft.forEachIndexed { rowIndex, row ->
            val name = if (row.owner == Catalog.SUMMONER_ID) "JD" else Catalog.get(row.owner)?.name ?: row.owner
            InkCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(tokens.panel)) {
                        CardArt.portraitOf(row.owner, vm.dir)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(name, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    if (row.picked.isNotEmpty()) Text("✓ picked", color = tokens.activePill, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.options.forEach { id ->
                        val card = cardOf(vm, id) ?: return@forEach
                        val badge = when {
                            row.picked == id -> "TAKEN"
                            id in save.collection -> "LV ${GameLogic.level(save, id)} → ${GameLogic.level(save, id) + 1}"
                            else -> "NEW"
                        }
                        CardFace(card, vm.dir, selected = row.picked == id, dimmed = row.picked.isNotEmpty() && row.picked != id, badge = badge, onClick = { vm.draft(rowIndex, id) })
                    }
                }
            }
        }
        val done = GameLogic.draftDone(save)
        Button(onClick = vm::closeDraft, enabled = done) { Text(if (done) "Done" else "Pick a card from every row") }
        TextButton(onClick = vm::closeDraft) { Text("Skip the rest", color = tokens.secondaryText) }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}
