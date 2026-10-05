package com.ihy2ln.weaverse.feature.games.adventure

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.CardText
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.ui.BattleScreen
import com.ihy2ln.weaverse.feature.games.battle.ui.Bar
import com.ihy2ln.weaverse.feature.games.battle.ui.CardArt
import com.ihy2ln.weaverse.feature.games.battle.ui.CardBattleViewModel
import com.ihy2ln.weaverse.feature.games.battle.ui.CardFace
import com.ihy2ln.weaverse.feature.games.battle.ui.CardScreen
import com.ihy2ln.weaverse.feature.games.battle.ui.GoldColor
import com.ihy2ln.weaverse.feature.games.battle.ui.HpColor
import com.ihy2ln.weaverse.feature.games.battle.ui.FoeColor
import com.ihy2ln.weaverse.feature.games.battle.ui.SectionLabel

private fun kindMark(kind: String) = when (kind) {
    ChoiceKind.FIGHT -> "⚔"
    ChoiceKind.ELITE, ChoiceKind.BOSS -> "☠"
    ChoiceKind.TRADE -> "⚖"
    ChoiceKind.REST -> "♨"
    ChoiceKind.LOOT -> "✦"
    ChoiceKind.EXPLORE -> "➶"
    else -> "➤"
}

/**
 * Games -> Adventure: the text adventure whose fights are played as Card Battles. A hub, the scene loop with choices
 * and free-text actions, the barter screen and the ending. Fights borrow the Card Battle screen.
 */
@Composable
fun AdventureScreen(
    onLeave: () -> Unit = {},
    viewModel: AdventureViewModel = hiltViewModel(),
    cardViewModel: CardBattleViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val cardUi by cardViewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (ui.fighting == null) viewModel.refresh() }
    // A fight the adventure started is played on the Card Battle screen.
    if (ui.fighting != null && cardUi.screen == CardScreen.Battle && cardViewModel.battle != null) {
        BattleScreen(cardViewModel, cardUi)
        return
    }
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    when (ui.screen) {
        AdvScreen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AdvScreen.Hub -> Hub(viewModel, ui, onLeave)
        AdvScreen.Scene -> SceneScreen(viewModel, cardViewModel, ui)
        AdvScreen.Shop -> ShopScreen(viewModel, ui)
        AdvScreen.End -> EndScreen(viewModel, onLeave)
    }
}

@Composable
private fun Hub(vm: AdventureViewModel, ui: AdventureUi, onLeave: () -> Unit) {
    val tokens = inkTokens()
    var premise by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(inkRadiusMd() * 1.5f))) {
            AsyncImage(model = CardArt.battleBackground, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, tokens.background.copy(alpha = 0.92f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(InkSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ADAMS HAVEN", style = MaterialTheme.typography.labelMedium, color = tokens.activePill, letterSpacing = 1.5.sp)
                Text("Adventure", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    "A story told scene by scene. Your choices lead to card fights, merchants to barter with, camps and treasure.",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.primaryText.copy(alpha = 0.85f),
                )
            }
        }
        if (vm.hasSave) {
            InkCard(Modifier.fillMaxWidth()) {
                Text(if (vm.adv.over) (if (vm.adv.won) "Adventure completed" else "The party fell") else "Adventure in progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text("Scene ${vm.adv.depth + 1} of ${AdventureRules.LENGTH} · ${vm.adv.gold} gold · ${vm.adv.battlesWon} fights won", style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::resume) { Text(if (vm.adv.over) "See the ending" else "Continue") }
                    OutlinedButton(onClick = vm::quit) { Text("Abandon") }
                }
            }
        }
        SectionLabel("A NEW ADVENTURE")
        Text(
            "Your party: " + vm.partyNames().joinToString(", ").ifBlank { "none yet" } + ". Change it in Card Battle → Party & deck.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        OutlinedTextField(
            value = premise, onValueChange = { premise = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            label = { Text("Premise (optional)") },
            placeholder = { Text("e.g. The party hunts a thief through the haunted Silverwood") },
        )
        Text(
            "With a premise, the AI writes every scene around it (set up a model in Settings). Without one, or with no AI, " +
                "a built-in storyteller runs the Silverwood.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        Button(onClick = { vm.begin(premise) }, modifier = Modifier.fillMaxWidth(), enabled = vm.partyNames().isNotEmpty()) {
            Text(if (vm.hasSave) "Start over with a new adventure" else "Begin the adventure")
        }
        if (ui.message.isNotBlank()) Text(ui.message, color = tokens.activePill, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onLeave) { Text("Back to Adams Haven") }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}

@Composable
private fun PartyStrip(vm: AdventureViewModel, onPick: ((String) -> Unit)? = null) {
    val tokens = inkTokens()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        vm.base.party.forEach { id ->
            val u = Catalog.unit(id) ?: return@forEach
            val hp = AdventureRules.hpOf(vm.adv, id)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(tokens.panel.copy(alpha = .6f))
                    .then(if (onPick != null && hp > 0 && hp < u.maxHp) Modifier.border(2.dp, GoldColor, RoundedCornerShape(8.dp)) else Modifier)
                    .then(if (onPick != null) Modifier.clickableNoRipple { onPick(id) } else Modifier)
                    .padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp))) {
                    CardArt.portraitOf(id, vm.dir)?.let { AsyncImage(model = it, contentDescription = u.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Bar(hp / u.maxHp.toFloat(), if (hp > 0) HpColor else FoeColor, Modifier.fillMaxWidth().padding(top = 2.dp), 4)
                Text(if (hp > 0) "$hp" else "down", color = tokens.secondaryText, fontSize = 9.sp)
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
)

@Composable
private fun SceneScreen(vm: AdventureViewModel, cardVm: CardBattleViewModel, ui: AdventureUi) {
    val tokens = inkTokens()
    val adv = vm.adv
    var action by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.toHub() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(adv.scene.place.ifBlank { "The Silverwood" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text("Scene ${adv.depth + 1} of ${AdventureRules.LENGTH}", style = MaterialTheme.typography.labelMedium, color = tokens.secondaryText)
            }
            Text("${adv.gold} gold", color = GoldColor, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = vm::toggleItems, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text("Pack · ${adv.items.values.sum()}")
            }
            TextButton(onClick = vm::toHub) { Text("Menu") }
        }
        PartyStrip(vm)
        if (adv.outcome.isNotBlank()) {
            Text(adv.outcome, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(tokens.activePill.copy(alpha = .35f), RoundedCornerShape(8.dp)).padding(8.dp))
        }
        InkCard(Modifier.fillMaxWidth()) {
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("The storyteller is writing…", color = tokens.secondaryText)
                }
            } else {
                Text(adv.scene.narration, style = MaterialTheme.typography.bodyLarge, color = tokens.primaryText)
            }
        }
        if (ui.message.isNotBlank()) Text(ui.message, color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall)
        if (!ui.busy) {
            adv.scene.choices.forEach { choice ->
                OutlinedButton(
                    onClick = { vm.pick(choice) { state, kind -> cardVm.startExternal(state) { done -> vm.battleDone(done, kind) } } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(kindMark(choice.kind) + "  ", fontSize = 16.sp)
                    Text(choice.text, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = action, onValueChange = { action = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Or do something else…") })
                Button(onClick = { vm.act(action); action = "" }, enabled = action.isNotBlank()) { Text("Do it") }
            }
        }
        Spacer(Modifier.height(InkSpacing.xl))
    }
    if (ui.showItems) ItemsDialog(vm, ui)
}

@Composable
private fun ItemsDialog(vm: AdventureViewModel, ui: AdventureUi) {
    val adv = vm.adv
    AlertDialog(
        onDismissRequest = vm::toggleItems,
        title = { Text(if (ui.drinking) "Who drinks it?" else "Your pack") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.drinking) {
                    Text("Tap a fighter who is hurt.", fontSize = 12.sp)
                    PartyStrip(vm) { id -> vm.useItem("potion", id) }
                } else if (adv.items.isEmpty()) {
                    Text("Nothing but dust.")
                } else {
                    AdventureRules.items.filter { AdventureRules.count(adv, it.id) > 0 }.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.name} ×${AdventureRules.count(adv, item.id)}", fontWeight = FontWeight.Bold)
                                Text(item.text, fontSize = 11.sp)
                            }
                            if (item.id != "charm") OutlinedButton(onClick = { vm.useItem(item.id) }) { Text("Use") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::toggleItems) { Text("Close") } },
    )
}

@Composable
private fun ShopScreen(vm: AdventureViewModel, ui: AdventureUi) {
    val tokens = inkTokens()
    val adv = vm.adv
    val shop = adv.shop ?: run { vm.toHub(); return }
    BackHandler { vm.toHub() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Barter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(shop.keeper, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
            }
            Text("${adv.gold} gold", color = GoldColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Text(ui.message.ifBlank { shop.mood }, color = tokens.activePill, style = MaterialTheme.typography.bodyMedium)
        SectionLabel("FOR SALE")
        Text("Haggle once per ware: a win takes 25% off, a miss adds 10%. A Haggler's Charm helps.", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        shop.stock.forEachIndexed { index, ware ->
            InkCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ware.kind == "card") {
                        val card: BattleCard? = Catalog.card(ware.id)
                        if (card != null) CardFace(card, vm.dir, modifier = Modifier)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val item = AdventureRules.item(ware.id)
                        Text(if (ware.kind == "card") (Catalog.card(ware.id)?.name ?: ware.id) else (item?.name ?: ware.id), fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        Text(
                            if (ware.kind == "card") Catalog.card(ware.id)?.let { CardText.describe(it) }.orEmpty() else item?.text.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
                        )
                        Text("${ware.price} gold", color = GoldColor, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.buy(index) }, enabled = adv.gold >= ware.price, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text("Buy") }
                            OutlinedButton(onClick = { vm.haggle(index) }, enabled = !ware.haggled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text(if (ware.haggled) "Haggled" else "Haggle") }
                        }
                    }
                }
            }
        }
        if (shop.stock.isEmpty()) Text("The cart is empty.", color = tokens.secondaryText)
        SectionLabel("SELL")
        val sellable = AdventureRules.items.filter { AdventureRules.count(adv, it.id) > 0 }
        if (sellable.isEmpty()) Text("Nothing in your pack to sell.", color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall)
        sellable.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.name} ×${AdventureRules.count(adv, item.id)}", modifier = Modifier.weight(1f), color = tokens.primaryText)
                OutlinedButton(onClick = { vm.sell(item.id) }) { Text("Sell · ${AdventureRules.sellPrice(item.id)}") }
            }
        }
        Button(onClick = vm::leaveShop, modifier = Modifier.fillMaxWidth()) { Text("Leave and move on") }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}

@Composable
private fun EndScreen(vm: AdventureViewModel, onLeave: () -> Unit) {
    val tokens = inkTokens()
    val adv = vm.adv
    BackHandler { vm.toHub() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Text(if (adv.won) "The adventure is won!" else "The party has fallen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (adv.won) GoldColor else FoeColor)
        Text(adv.outcome, style = MaterialTheme.typography.bodyLarge, color = tokens.primaryText)
        Text("${adv.depth} scenes · ${adv.battlesWon} fights won · ${adv.gold} gold left", color = tokens.secondaryText)
        if (adv.history.isNotEmpty()) {
            SectionLabel("THE STORY")
            adv.history.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText) }
        }
        Button(onClick = vm::quit, modifier = Modifier.fillMaxWidth()) { Text("New adventure") }
        TextButton(onClick = onLeave) { Text("Back to Adams Haven") }
        Spacer(Modifier.height(InkSpacing.xl))
    }
}
