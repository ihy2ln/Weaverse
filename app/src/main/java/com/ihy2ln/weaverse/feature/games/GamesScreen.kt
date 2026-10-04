package com.ihy2ln.weaverse.feature.games

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import kotlinx.coroutines.launch
import java.io.File

private const val HERO_ART = "file:///android_asset/images/adams_haven/battle/boss_arena/boss-arena-01-heartwood-throne.webp"

/**
 * Games mode: the Adams Haven Card Game (Unity, embedded; it opens in its own screen) and the bridge from the novels:
 * every Codex character can get a card set and join the game's roster.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(viewModel: GamesViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = inkTokens()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    fun play() {
        scope.launch {
            viewModel.prepareLaunch()
            context.startActivity(
                Intent(context, CardGameActivity::class.java).putExtra(CardGameActivity.EXTRA_DATA_DIR, viewModel.dataDir),
            )
        }
    }

    val novel = ui.characters.filter { it.builtin == null }
    val ready = novel.count { it.set?.inGame == true }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        val playLabel = if (ready > 0) "Play  ·  $ready from your novels" else "Play"
        // In its tab the Games banner becomes this billboard's heading and Play button.
        com.ihy2ln.weaverse.core.ui.components.ModePageHeader(
            title = "The Card Game",
            subtitle = "Lead JD's party through the Silverwood with your own cards. Characters from your novels join the roster.",
            actions = {
                Button(onClick = ::play) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
            },
        ) {
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd() * 1.5f))) {
                AsyncImage(model = HERO_ART, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, tokens.background.copy(alpha = 0.92f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(InkSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ADAMS HAVEN", style = MaterialTheme.typography.labelMedium, color = tokens.activePill, letterSpacing = 1.5.sp)
                    Text("The Card Game", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    Text(
                        "Lead JD's party through the Silverwood with your own cards. Characters from your novels join the roster.",
                        style = MaterialTheme.typography.bodyMedium, color = tokens.primaryText.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = ::play) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (ready > 0) "Play  ·  $ready from your novels" else "Play")
                    }
                }
            }
        }

        if (ui.message.isNotBlank()) Text(ui.message, color = tokens.activePill, style = MaterialTheme.typography.bodyMedium)

        Text("FROM YOUR NOVELS", style = MaterialTheme.typography.labelLarge, color = tokens.activePill, letterSpacing = 1.2.sp)
        Text(
            "Each character in your Codex's Characters category can become playable. The AI reads their Codex entry and writes " +
                "six cards and two ultimates in their own words; the numbers follow the same curve as the Adams Haven cast, and " +
                "you can rename or rework any card. Their Codex picture becomes their card art.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        when {
            ui.loading -> CircularProgressIndicator()
            novel.isEmpty() -> InkCard(Modifier.fillMaxWidth()) {
                Text(
                    "No characters yet. Add some to a novel's Codex (Characters category) and they will show up here.",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText, modifier = Modifier.padding(InkSpacing.md),
                )
            }
            else -> novel.forEach { row ->
                CharacterRow(
                    row = row,
                    building = row.entryId in ui.building,
                    error = ui.errors[row.entryId],
                    onBuild = { viewModel.build(row.entryId) },
                    onBuildPlain = { role -> viewModel.buildWithoutAi(row.entryId, role) },
                    onToggle = { viewModel.setInGame(row.entryId, it) },
                    onEdit = { viewModel.openEditor(row.entryId) },
                )
            }
        }

        Text("THE ADAMS HAVEN CAST", style = MaterialTheme.typography.labelLarge, color = tokens.activePill, letterSpacing = 1.2.sp)
        Text(
            "Already in the game with their finished cards. A Codex character with the same name uses these.",
            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            BuiltinCast.members.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
                    Box(Modifier.size(80.dp, 112.dp).clip(RoundedCornerShape(10.dp)).background(tokens.panel)) {
                        BuiltinCast.artUrl(m)?.let {
                            AsyncImage(model = it, contentDescription = m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(m.name.substringBefore(' '), style = MaterialTheme.typography.labelMedium, color = tokens.primaryText, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(InkSpacing.xl))
    }

    ui.editing?.let { id ->
        ui.characters.firstOrNull { it.entryId == id && it.set != null }?.let { row ->
            CardSetEditor(
                row = row,
                onDismiss = { viewModel.openEditor(null) },
                onEditCard = { cardId, name, text, effect, ep, status -> viewModel.editCard(row.entryId, cardId, name, text, effect, ep, status) },
                onRestyle = { role, element, magic -> viewModel.restyle(row.entryId, role, element, magic) },
                onRemake = { viewModel.openEditor(null); viewModel.build(row.entryId) },
                onPicture = { viewModel.refreshPicture(row.entryId) },
                onDelete = { viewModel.delete(row.entryId) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterRow(
    row: NovelCharacterRow,
    building: Boolean,
    error: String?,
    onBuild: () -> Unit,
    onBuildPlain: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val tokens = inkTokens()
    var plainRole by remember { mutableStateOf(false) }
    InkCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(InkSpacing.md), horizontalArrangement = Arrangement.spacedBy(InkSpacing.md)) {
            Box(Modifier.size(72.dp, 100.dp).clip(RoundedCornerShape(10.dp)).background(tokens.panel)) {
                row.picture?.let { AsyncImage(model = File(it), contentDescription = row.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                val set = row.set
                Text(
                    listOfNotNull(row.novel.takeIf { it.isNotBlank() }, set?.let { "${it.role} · ${it.element}" }, set?.title?.takeIf { it.isNotBlank() })
                        .joinToString("  ·  ").ifBlank { "No card set yet" },
                    style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (set != null) {
                    Text(
                        (set.cards + set.ultimates).joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall, color = tokens.primaryText.copy(alpha = .8f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { plainRole = true }) { Text("Make a starter set without AI") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm), verticalArrangement = Arrangement.Center) {
                    when {
                        building -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Writing their cards…", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                        }
                        set == null -> Button(onClick = onBuild) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Make their cards")
                        }
                        else -> {
                            OutlinedButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cards")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = set.inGame, onCheckedChange = onToggle)
                                Spacer(Modifier.width(6.dp))
                                Text(if (set.inGame) "In the game" else "Left out", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
    if (plainRole) {
        AlertDialog(
            onDismissRequest = { plainRole = false },
            title = { Text("Starter set for ${row.name}") },
            text = { Text("Pick how they fight. You can rename and rework every card afterwards.") },
            confirmButton = {
                Column {
                    CardSetBuilder.roles.forEach { role ->
                        TextButton(onClick = { plainRole = false; onBuildPlain(role) }) { Text(roleLabel(role)) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { plainRole = false }) { Text("Cancel") } },
        )
    }
}

private fun roleLabel(role: String) = when (role) {
    "Tank" -> "Tank — takes the hits, guards the party"
    "Dps" -> "Striker — deals the damage"
    "Ranger" -> "Ranger — strikes from behind the tank"
    else -> "Support — heals and strengthens"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardSetEditor(
    row: NovelCharacterRow,
    onDismiss: () -> Unit,
    onEditCard: (String, String, String, String, Int, String) -> Unit,
    onRestyle: (String, String, Boolean) -> Unit,
    onRemake: () -> Unit,
    onPicture: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = inkTokens()
    val set = row.set ?: return
    var editing by remember { mutableStateOf<CardGameCard?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(tokens.background).verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(set.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.primaryText, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            if (set.bio.isNotBlank()) Text(set.bio, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
            Text("HOW THEY FIGHT", style = MaterialTheme.typography.labelLarge, color = tokens.activePill)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardSetBuilder.roles.forEach { r ->
                    FilterChip(selected = set.role == r, onClick = { onRestyle(r, set.element, set.magicUser) }, label = { Text(if (r == "Dps") "Striker" else r) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardSetBuilder.elements.forEach { e ->
                    FilterChip(selected = set.element == e, onClick = { onRestyle(set.role, e, set.magicUser) }, label = { Text(e) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = set.magicUser, onCheckedChange = { onRestyle(set.role, set.element, it) })
                Spacer(Modifier.width(8.dp))
                Text(if (set.magicUser) "Fights with magic" else "Fights with weapons", color = tokens.primaryText)
            }
            Text("CARDS (tap to change)", style = MaterialTheme.typography.labelLarge, color = tokens.activePill)
            (set.cards + set.ultimates).forEach { card ->
                InkCard(Modifier.fillMaxWidth().clickable { editing = card }) {
                    Column(Modifier.padding(InkSpacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tokens.primaryText, modifier = Modifier.weight(1f))
                            Text(if (card.kind == "Ultimate") "ULTIMATE · 3 SP" else "${card.ep} EP" + if (card.ap > 0) " · ${card.ap} AP" else "",
                                style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
                        }
                        Text(rulesLine(card), style = MaterialTheme.typography.bodySmall, color = tokens.primaryText.copy(alpha = .85f))
                        if (card.text.isNotBlank()) Text(card.text, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    }
                }
            }
            Spacer(Modifier.height(InkSpacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                OutlinedButton(onClick = onRemake) { Text("Have the AI remake all") }
                OutlinedButton(onClick = onPicture) { Text("Use their current Codex picture") }
                TextButton(onClick = { confirmDelete = true }) { Text("Remove from the game", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    editing?.let { card ->
        CardEditDialog(card, onDismiss = { editing = null }) { name, text, effect, ep, status ->
            onEditCard(card.id, name, text, effect, ep, status)
            editing = null
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${set.name}'s cards?") },
            text = { Text("They leave the game's roster. Their Codex entry is not touched; you can make cards again any time.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardEditDialog(card: CardGameCard, onDismiss: () -> Unit, onSave: (String, String, String, Int, String) -> Unit) {
    val ultimate = card.kind == "Ultimate"
    var name by remember { mutableStateOf(card.name) }
    var text by remember { mutableStateOf(card.text) }
    var effect by remember { mutableStateOf(card.effect.ifBlank { if (ultimate) "nova" else "strike" }) }
    var ep by remember { mutableIntStateOf(card.ep.coerceIn(1, 3)) }
    var status by remember { mutableStateOf(card.status.ifBlank { "DefenseDown" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ultimate) "Ultimate" else "Card") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(28) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = text, onValueChange = { text = it.take(90) }, label = { Text("Flavour line") })
                Text("What it does", style = MaterialTheme.typography.labelLarge)
                val options = if (ultimate) CardSetBuilder.ultimateEffects else CardSetBuilder.effects
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (key, meaning) ->
                        FilterChip(selected = effect == key, onClick = { effect = key }, label = { Text(key) })
                    }
                }
                Text(options[effect].orEmpty(), style = MaterialTheme.typography.bodySmall)
                if (!ultimate) {
                    Text("Weight (EP)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { n -> FilterChip(selected = ep == n, onClick = { ep = n }, label = { Text("$n EP") }) }
                    }
                    if (effect == "hex") {
                        Text("Weakens with", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CardSetBuilder.hexStatuses.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) }) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, text, effect, ep, status) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The card's effect in plain words, from its numbers (the game shows the same on the card face). */
fun rulesLine(c: CardGameCard): String {
    val parts = mutableListOf<String>()
    if (c.power > 0) parts += "Deal ${(c.power * 100).toInt()}% ${if (c.magic) "MAG" else "ATK"}" + if (c.target == "AllEnemies") " to all enemies" else ""
    if (c.heal > 0) parts += "Heal ${c.heal.toInt()}" + if (c.target == "AllAllies") " to all allies" else ""
    if (c.status.isNotBlank()) parts += when (c.status) {
        "Poison", "Burn", "Regen" -> "${c.status} ${c.magnitude.toInt()} a round for ${c.duration}"
        "Shield" -> "Shield ${c.magnitude.toInt()}"
        "Stun" -> "Stun"
        else -> "${c.status} ${(c.magnitude * 100).toInt()}% for ${c.duration}"
    }
    if (c.draw > 0) parts += "Draw ${c.draw}"
    if (c.epGain > 0) parts += "+${c.epGain} EP to an ally"
    val keys = listOfNotNull("Opening card".takeIf { c.initiation }, "Retain".takeIf { c.retain }, "Exhaust".takeIf { c.exhaust })
    return (parts + keys).joinToString(" · ")
}
