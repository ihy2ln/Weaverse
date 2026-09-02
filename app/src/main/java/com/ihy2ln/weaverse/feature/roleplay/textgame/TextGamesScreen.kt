package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.components.LoopingVideoBackground
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TextGamesScreen(
    campaignId: String,
    onOpenPrompt: () -> Unit = {},
    onBackToSessions: () -> Unit = {},
    onBattleFocus: (Boolean) -> Unit = {},
    viewModel: TextGameViewModel = hiltViewModel(),
) {
    LaunchedEffect(campaignId) { viewModel.bind(campaignId) }
    val ui by viewModel.uiState.collectAsState()
    var playing by rememberSaveable(campaignId) { mutableStateOf(false) }
    var showingCards by rememberSaveable(campaignId) { mutableStateOf(false) }
    var showingMissionLog by rememberSaveable(campaignId) { mutableStateOf(false) }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Preparing Text Games…") }
        return
    }
    if (showingCards) {
        TextGameCardLibrary(
            definition = ui.definition,
            collectedIds = ui.game.persistent.collection,
            imagePaths = ui.cardImagePaths,
            onBack = { showingCards = false },
        )
    } else if (!playing) {
        TextGameShelf(
            campaignTitle = ui.campaignTitle,
            definition = ui.definition,
            playStyle = ui.playStyle,
            difficulty = ui.game.persistent.difficulty,
            hasProgress = ui.game.run.nodeId != ui.definition.startNodeId || ui.game.persistent.flags.isNotEmpty(),
            onPlay = { playing = true },
            onCards = { showingCards = true },
            onStyle = viewModel::selectPlayStyle,
            onSessions = onBackToSessions,
        )
    } else {
        TextGamePlayer(
            ui = ui,
            isChoiceEnabled = viewModel::isChoiceEnabled,
            canPlay = viewModel::canPlay,
            canSelectCard = viewModel::canSelectCard,
            dispatch = viewModel::dispatch,
            onShelf = { playing = false },
            onCards = { showingCards = true },
            onOpenPrompt = onOpenPrompt,
            onSessions = onBackToSessions,
            onMissionLog = { showingMissionLog = true },
            onGenerateMissions = viewModel::generateMissions,
            onBattleFocus = onBattleFocus,
        )
    }
    if (showingMissionLog) {
        MissionLogDialog(ui.game.persistent.missionLog) { showingMissionLog = false }
    }
}

@Composable
private fun TextGameShelf(
    campaignTitle: String,
    definition: TextGameDefinition,
    playStyle: TextGamePlayStyle,
    difficulty: TextGameDifficulty,
    hasProgress: Boolean,
    onPlay: () -> Unit,
    onCards: () -> Unit,
    onStyle: (TextGamePlayStyle) -> Unit,
    onSessions: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TEXT GAMES", style = MaterialTheme.typography.labelLarge, color = tokens.activePill, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onSessions) { Text("Sessions") }
        }
        Text("Playable stories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Campaign: $campaignTitle", color = tokens.secondaryText)
        Text(
            "Difficulty: ${difficulty.label} · ${difficulty.description}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )
        Text("PLAY STYLE", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
        Text("Campaign is the main story. Endless Battles and Haven Simulation are optional side modes with separate saves.", color = tokens.secondaryText)
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            TextGamePlayStyle.entries.forEach { style ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onStyle(style) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (style == playStyle) tokens.hover else tokens.panel,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(InkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(style.label, fontWeight = FontWeight.Bold)
                            Text(style.description, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                        }
                        Text(if (style == playStyle) "SELECTED" else "OPEN", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = tokens.panel),
        ) {
            Column(Modifier.padding(InkSpacing.lg), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                Text("BUILT-IN · OFFLINE", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                Text(definition.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(definition.subtitle, color = tokens.secondaryText)
                Text("Branching story · Card battle · Reward draft · Farm · Town · Home")
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    Button(onClick = onPlay) { Text(if (hasProgress) "Continue ${playStyle.label}" else "Start ${playStyle.label}") }
                    OutlinedButton(onClick = onCards) { Text("Browse 69 cards") }
                }
            }
        }
        Text("More Text Games can use this same definition and reducer format in a later release.", color = tokens.secondaryText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextGamePlayer(
    ui: TextGameUiState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    canPlay: (TextGameCard) -> Boolean,
    canSelectCard: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onShelf: () -> Unit,
    onCards: () -> Unit,
    onOpenPrompt: () -> Unit,
    onSessions: () -> Unit,
    onMissionLog: () -> Unit,
    onGenerateMissions: () -> Unit,
    onBattleFocus: (Boolean) -> Unit = {},
) {
    val node = ui.definition.node(ui.game.run.nodeId)
    val tokens = inkTokens()
    val battleFocus = node?.type == TextGameNodeType.Battle
    LaunchedEffect(node?.id, battleFocus) { onBattleFocus(battleFocus) }
    if (node == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { dispatch(TextGameAction.Reset) }) { Text("Repair save and restart") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        if (battleFocus) {
            // Battle focus: one thin header line — every pixel goes to the fight.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                Text(
                    ui.definition.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("✦", modifier = Modifier.clickable(onClick = onOpenPrompt).padding(horizontal = InkSpacing.xs))
                Text("Cards", modifier = Modifier.clickable(onClick = onCards).padding(horizontal = InkSpacing.xs))
                Text("Modes", modifier = Modifier.clickable(onClick = onShelf).padding(horizontal = InkSpacing.xs))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("TEXT GAME", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                    Text(
                        ui.definition.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    OutlinedButton(onClick = onOpenPrompt) { Text("✦ Ask AI") }
                    OutlinedButton(onClick = { dispatch(TextGameAction.EnterDungeon) }) { Text("⚔ Dungeon") }
                    OutlinedButton(onClick = onCards) { Text("Cards") }
                    OutlinedButton(onClick = onMissionLog) { Text("Mission Log (${ui.game.persistent.missionLog.size})") }
                    OutlinedButton(onClick = onShelf) { Text("Modes") }
                    OutlinedButton(onClick = onSessions) { Text("Sessions") }
                    OutlinedButton(onClick = { dispatch(TextGameAction.Reset) }) { Text("Restart") }
                }
            }
        }

        val dungeon = ui.game.persistent.dungeon
        val inDungeonDelve = dungeon?.inDelve() == true
        val inDungeonFight = inDungeonDelve && node.type == TextGameNodeType.Battle && ui.game.run.dungeonFight
        if (inDungeonDelve && !inDungeonFight) {
            DungeonExploreView(
                ui = ui,
                dungeon = dungeon,
                node = node,
                onShelf = onShelf,
                onCards = onCards,
                onOpenPrompt = onOpenPrompt,
                dispatch = dispatch,
            )
        } else if (node.type == TextGameNodeType.Battle) {
            BattleGameBoard(ui, node, canPlay, canSelectCard, dispatch)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.md)) {
                        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                            ScenePicture(node, ui.sceneImagePath, ui.sceneMotionPath, ui.game, isChoiceEnabled) { choiceId ->
                                dispatch(TextGameAction.Choose(choiceId))
                            }
                            StatusStrip(ui.game)
                        }
                        Column(Modifier.weight(.85f), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                            StoryAndControls(ui, node, isChoiceEnabled, canPlay, dispatch, onGenerateMissions, onOpenPrompt)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                        ScenePicture(node, ui.sceneImagePath, ui.sceneMotionPath, ui.game, isChoiceEnabled) { choiceId ->
                            dispatch(TextGameAction.Choose(choiceId))
                        }
                        StatusStrip(ui.game)
                        StoryAndControls(ui, node, isChoiceEnabled, canPlay, dispatch, onGenerateMissions, onOpenPrompt)
                    }
                }
            }
        }
        ui.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Autosaved · ${ui.game.persistent.flags.size} flags · ${ui.game.persistent.collection.size} reward cards",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )
    }
}

@Composable
private fun StoryAndControls(
    ui: TextGameUiState,
    node: TextGameNode,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    canPlay: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onGenerateMissions: () -> Unit,
    onOpenPrompt: () -> Unit,
) {
    val tokens = inkTokens()
    Text(node.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val sceneProse = if (node.type == TextGameNodeType.MissionBoard) {
        ui.game.run.missionBoardIntro.ifBlank { node.proseFor(ui.game.persistent.rngSeed) }
    } else {
        node.proseFor(ui.game.persistent.rngSeed)
    }
    Text(sceneProse, style = MaterialTheme.typography.bodyLarge)
    if (ui.game.run.lastLog.isNotBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = tokens.hover), modifier = Modifier.fillMaxWidth()) {
            Text(ui.game.run.lastLog, Modifier.padding(InkSpacing.sm))
        }
    }
    if (ui.generatedNarration.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = tokens.panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Text("GENERATED NARRATION", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                ui.generatedNarration.takeLast(4).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    ui.game.run.pendingStoryProposal?.let { proposal ->
        StoryProposalControls(proposal, dispatch, onOpenPrompt)
    }
    val missionPending = node.type == TextGameNodeType.MissionBoard &&
        ui.game.run.missionOffer.isNotEmpty() && ui.game.persistent.missionId == null
    when {
        missionPending -> {
            MissionOfferControls(ui, dispatch)
            ChoiceControls(ui.game, node, isChoiceEnabled, dispatch, onOpenPrompt)
        }
        node.type == TextGameNodeType.MissionBoard -> {
            Text(
                "The keeper is refreshing the contracts. I can request another board or return to the Crossroads.",
                color = tokens.secondaryText,
            )
            OutlinedButton(onClick = onGenerateMissions, modifier = Modifier.fillMaxWidth()) {
                Text("Generate 1–6 missions")
            }
            ChoiceControls(ui.game, node, isChoiceEnabled, dispatch, onOpenPrompt)
        }
        else -> when (node.type) {
            TextGameNodeType.Battle -> BattleControls(ui, canPlay, dispatch)
            TextGameNodeType.Reward -> RewardControls(ui, dispatch)
            TextGameNodeType.Gacha -> GachaControls(ui, node, isChoiceEnabled, dispatch)
            TextGameNodeType.Ending -> EndingSummary(ui, dispatch)
            else -> ChoiceControls(ui.game, node, isChoiceEnabled, dispatch, onOpenPrompt)
        }
    }
}

/** A persistent AI/offline dungeon contract board containing one to six missions. */
@Composable
private fun MissionOfferControls(ui: TextGameUiState, dispatch: (TextGameAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Text(
            "DUNGEON MISSION BOARD · ${ui.game.run.missionOffer.size} AVAILABLE",
            style = MaterialTheme.typography.labelSmall,
            color = inkTokens().activePill,
            fontWeight = FontWeight.Bold,
        )
        ui.game.run.missionOffer.forEach { mission ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
                Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mission.title, fontWeight = FontWeight.Bold)
                    if (mission.description.isNotBlank()) {
                        Text(mission.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        missionEffectText(mission),
                        style = MaterialTheme.typography.labelSmall,
                        color = inkTokens().activePill,
                    )
                    Button(
                        onClick = { dispatch(TextGameAction.BeginMission(mission)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Accept mission") }
                }
            }
        }
    }
}

@Composable
private fun MissionLogDialog(
    entries: List<TextGameMissionLogEntry>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = inkTokens().panel),
        ) {
            Column(
                Modifier.padding(InkSpacing.md).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                Text("MISSION LOG", style = MaterialTheme.typography.labelSmall, color = inkTokens().activePill)
                Text("Dungeon contracts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (entries.isEmpty()) {
                    Text("No missions have appeared on the board yet.", color = inkTokens().secondaryText)
                } else {
                    val completed = entries.count { it.status == TextGameMissionStatus.Completed }
                    val active = entries.count { it.status == TextGameMissionStatus.Active }
                    Text(
                        "${entries.size} recorded · $active active · $completed completed",
                        color = inkTokens().secondaryText,
                    )
                    entries.asReversed().forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = inkTokens().hover),
                        ) {
                            Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.mission.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(
                                        entry.status.name.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (entry.status) {
                                            TextGameMissionStatus.Active -> inkTokens().activePill
                                            TextGameMissionStatus.Completed -> Color(0xFF4E9F63)
                                            TextGameMissionStatus.Failed -> MaterialTheme.colorScheme.error
                                            TextGameMissionStatus.Available -> inkTokens().secondaryText
                                        },
                                    )
                                }
                                if (entry.mission.description.isNotBlank()) {
                                    Text(entry.mission.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    missionEffectText(entry.mission),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = inkTokens().secondaryText,
                                )
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

private fun missionEffectText(mission: TextGameMission): String {
    val effect = mission.effects.firstOrNull() ?: return "No modifiers — pure story"
    val parts = buildList {
        if (effect.coinsDelta != 0) add("%+d coin".format(effect.coinsDelta))
        if (effect.seedsDelta != 0) add("%+d seed".format(effect.seedsDelta))
        if (effect.materialsDelta != 0) add("%+d material".format(effect.materialsDelta))
        if (effect.summonerSpDelta != 0) add("%+d SP".format(effect.summonerSpDelta))
        if (effect.maxHealthDelta != 0) add("%+d max health".format(effect.maxHealthDelta))
        if (effect.healthDelta != 0) add("%+d health".format(effect.healthDelta))
        if (effect.preparedGuardDelta != 0) add("%+d starting guard".format(effect.preparedGuardDelta))
        if (effect.harvestDelta != 0) add("%+d harvest".format(effect.harvestDelta))
    }
    return parts.joinToString(" · ").ifBlank { "No modifiers — pure story" }
}

@Composable
private fun StoryProposalControls(
    proposal: TextGameStoryProposal,
    dispatch: (TextGameAction) -> Unit,
    onOpenPrompt: () -> Unit,
) {
    val tokens = inkTokens()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tokens.hover)) {
        Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text("PROPOSED STORY DIRECTION", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
            Text(proposal.prose, style = MaterialTheme.typography.bodyMedium)
            proposal.options.take(3).forEachIndexed { index, option ->
                Button(
                    onClick = { dispatch(TextGameAction.ConfirmStoryOption(option.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("${index + 1}  ${option.label}") }
            }
            OutlinedButton(onClick = onOpenPrompt, modifier = Modifier.fillMaxWidth()) {
                Text("✦  Ask AI / write my own action")
            }
            OutlinedButton(
                onClick = { dispatch(TextGameAction.DismissStoryProposal) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dismiss proposal") }
        }
    }
}

@Composable
private fun GachaControls(
    ui: TextGameUiState,
    node: TextGameNode,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val state = ui.game
    val tokens = inkTokens()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tokens.panel)) {
        Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text("LOCAL SEEDED SUMMON", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
            Text(
                if (state.persistent.gachaTutorialComplete) "The tutorial draw is complete." else "Two offline draws add allies to my roster.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.persistent.recentGachaIds.isNotEmpty()) {
                Text(
                    "Recruited: " + state.persistent.recentGachaIds.mapNotNull { id -> ui.definition.roster.firstOrNull { it.id == id }?.name }
                        .joinToString(" · "),
                    color = tokens.secondaryText,
                )
            }
            if (!state.persistent.gachaTutorialComplete) {
                Button(onClick = { dispatch(TextGameAction.RunGachaTutorial) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Perform two seeded summons")
                }
            }
            node.choices.forEach { choice ->
                Button(
                    onClick = { dispatch(TextGameAction.Choose(choice.id)) },
                    enabled = isChoiceEnabled(state, choice),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(choice.label) }
            }
        }
    }
}

@Composable
private fun ScenePicture(
    node: TextGameNode,
    imagePath: String?,
    motionPath: String?,
    state: TextGameState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    onHotspot: (String) -> Unit,
) {
    val colors = when (node.type) {
        TextGameNodeType.Battle -> listOf(Color(0xFF201A39), Color(0xFF7B3D50))
        TextGameNodeType.Reward -> listOf(Color(0xFF202A42), Color(0xFF3C6A75))
        TextGameNodeType.Hub -> listOf(Color(0xFF35253D), Color(0xFFB36E5F))
        TextGameNodeType.Ending -> listOf(Color(0xFF182A2C), Color(0xFFB58C5D))
        else -> listOf(Color(0xFF111A2A), Color(0xFF9B5C5B))
    }
    BoxWithConstraints(
        // Half the full-card aspect: the scene picture takes half the vertical
        // space it used to, while ContentScale.Fit keeps the art fully visible.
        Modifier.fillMaxWidth().aspectRatio((941f / 1672f) * 2f).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val model = imagePath?.let(::textGameImageModel) ?: node.bundledSceneAssetPath?.let { "file:///android_asset/$it" }
        if (motionPath != null) {
            LoopingVideoBackground(path = motionPath, modifier = Modifier.fillMaxSize(), fitInside = true)
            Text(
                "MOTION SCENE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = node.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦  ◈  ✦", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFE8B0))
                Text("ADAMS HAVEN", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2C18E))
                Text("Picture slot: ${node.sceneMediaId ?: "none"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
            }
        }
        node.hotspots.forEach { hotspot ->
            val choice = node.choices.firstOrNull { it.id == hotspot.choiceId } ?: return@forEach
            Button(
                onClick = { onHotspot(hotspot.choiceId) },
                enabled = isChoiceEnabled(state, choice),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (maxWidth * hotspot.x.coerceIn(0f, 1f) - 48.dp).coerceAtLeast(0.dp),
                        y = (maxHeight * hotspot.y.coerceIn(0f, 1f) - 20.dp).coerceAtLeast(0.dp),
                    ),
            ) { Text(hotspot.label) }
        }
    }
}

@Composable
private fun DungeonExploreView(
    ui: TextGameUiState,
    dungeon: DungeonState,
    node: TextGameNode,
    onShelf: () -> Unit,
    onCards: () -> Unit,
    onOpenPrompt: () -> Unit,
    dispatch: (TextGameAction) -> Unit,
) {
    val floor = dungeon.currentFloor()
    val exits = DungeonRules.exits(dungeon)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text(
                dungeon.floorName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE8C87A),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onOpenPrompt) { Text("✦ Ask AI") }
            OutlinedButton(onClick = onCards) { Text("Cards") }
            OutlinedButton(onClick = onShelf) { Text("Modes") }
        }
        DungeonMapPanel(dungeon, Modifier.fillMaxWidth())
        StatusStrip(ui.game)
        if (ui.game.run.lastLog.isNotBlank()) {
            Text(ui.game.run.lastLog, color = Color(0xFFFFD479), style = MaterialTheme.typography.bodySmall)
        }
        Text("DOORS", style = MaterialTheme.typography.labelSmall, color = inkTokens().secondaryText)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            exits.forEach { exit ->
                val direction = when {
                    exit.y < dungeon.atY -> "N"
                    exit.y > dungeon.atY -> "S"
                    exit.x > dungeon.atX -> "E"
                    else -> "W"
                }
                val kind = DungeonKind.fromIndex(exit.kind)
                val revealed = DungeonRules.sight(dungeon, exit.x, exit.y) != DungeonSight.Hidden
                FilterChip(
                    selected = false,
                    onClick = { dispatch(TextGameAction.DungeonStep(exit.x, exit.y)) },
                    label = {
                        Text(
                            (if (revealed) "${kind.glyph} ${kind.label}" else "? ? ?") + " · $direction",
                        )
                    },
                )
            }
            if (exits.isEmpty()) {
                Text(
                    "No way out while the fight is unresolved.",
                    style = MaterialTheme.typography.labelMedium,
                    color = inkTokens().secondaryText,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            if (DungeonRules.canDescend(dungeon)) {
                Button(onClick = { dispatch(TextGameAction.DungeonStep(dungeon.atX, dungeon.atY)) }) {
                    Text("▼ Take the stairs down")
                }
            }
            if (DungeonRules.canRetreat(dungeon)) {
                OutlinedButton(onClick = { dispatch(TextGameAction.LeaveDungeon) }) { Text("Leave the dungeon") }
            }
        }
    }
}

/** The battle map grid: parchment floor, visible grid, fog of war over rooms. */
@Composable
private fun DungeonMapPanel(dungeon: DungeonState, modifier: Modifier = Modifier) {
    val floor = dungeon.currentFloor()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF6B5326)),
    ) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF241C10), Color(0xFF54421F), Color(0xFF241C10))))
        val cols = floor?.sizeX ?: 6
        val rows = floor?.sizeY ?: 5
        val cell = min(size.width / cols, size.height / rows)
        val ox = (size.width - cell * cols) / 2f
        val oy = (size.height - cell * rows) / 2f
        val gridColor = Color(0xFF171208).copy(alpha = 0.85f)
        for (i in 0..cols) {
            drawLine(gridColor, Offset(ox + i * cell, oy), Offset(ox + i * cell, oy + rows * cell), 1f)
        }
        for (j in 0..rows) {
            drawLine(gridColor, Offset(ox, oy + j * cell), Offset(ox + cols * cell, oy + j * cell), 1f)
        }
        floor?.rooms?.forEach { room ->
            val x = ox + room.x * cell
            val y = oy + room.y * cell
            val rect = Rect(Offset(x + 1.5f, y + 1.5f), Size(cell - 3f, cell - 3f))
            val sight = DungeonRules.sight(dungeon, room.x, room.y)
            when (sight) {
                DungeonSight.Known -> drawRect(
                    color = Color(0xFF8A6E3A).copy(alpha = 0.9f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                DungeonSight.Peeked -> drawRect(
                    color = Color(0xFF5C4A28).copy(alpha = 0.85f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                DungeonSight.Hidden -> drawRect(
                    color = Color(0xFF100C07).copy(alpha = 0.88f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
            }
            val isHere = room.x == dungeon.atX && room.y == dungeon.atY
            if (sight != DungeonSight.Hidden) {
                val kind = DungeonKind.fromIndex(room.kind)
                if (room.cleared && kind.isFightKind) {
                    drawRect(
                        color = Color(0xFF2E3A2A).copy(alpha = 0.7f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                    )
                }
                val glyph = when {
                    isHere -> "✦"
                    else -> kind.glyph
                }
                val paint = android.graphics.Paint().apply {
                    color = if (isHere) 0xFFFFE8B0.toInt() else 0xFFE8D8B0.toInt()
                    textSize = cell * 0.5f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    glyph,
                    x + cell / 2f,
                    y + cell / 2f + paint.textSize * 0.35f,
                    paint,
                )
                if (isHere) {
                    drawCircle(
                        Color(0xFFFFE8B0),
                        radius = cell * 0.46f,
                        center = Offset(x + cell / 2f, y + cell / 2f),
                        style = Stroke(width = 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(state: TextGameState) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        StatusChip("♥ HEALTH", "${state.run.playerHealth}/${state.persistent.maxHealth}", Color(0xFFE26B67))
        if (state.persistent.missionTitle.isNotBlank()) {
            StatusChip("◎ MISSION", state.persistent.missionTitle, Color(0xFFC9A0F0))
        }
        if (state.run.guard > 0) StatusChip("⬟ GUARD", state.run.guard.toString(), Color(0xFF9B8EE8))
        state.run.resources.forEach { StatusChip("⚔ ${it.actorName.uppercase()}", "${it.ap} AP · ${it.ep} EP", Color(0xFF76CFC0)) }
        StatusChip("✦ SUMMONER", "${state.persistent.summonerSp} SP", Color(0xFFD5A85A))
        StatusChip("● POUCH", "${state.persistent.coins} coin · ${state.persistent.seeds} seed · ${state.persistent.materials} material", Color(0xFF86BEEA))
        if (state.persistent.harvest > 0 || state.persistent.dishes > 0) {
            StatusChip("♣ FARM", "${state.persistent.harvest} produce · ${state.persistent.dishes} dish", Color(0xFF77B982))
        }
        if (state.persistent.farmLevel > 1 || state.persistent.townLevel > 0 || state.persistent.homeLevel > 1) {
            StatusChip(
                "⌂ HAVEN",
                "Home Lv ${state.persistent.homeLevel} · Farm Lv ${state.persistent.farmLevel} · Town Lv ${state.persistent.townLevel}",
                Color(0xFFE0B24E),
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String, color: Color) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .16f)).padding(9.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ChoiceControls(
    state: TextGameState,
    node: TextGameNode,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onOpenPrompt: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        val choices = if (node.type == TextGameNodeType.Narrative) node.choices.take(3) else node.choices
        choices.forEachIndexed { index, choice ->
            Button(
                onClick = { dispatch(TextGameAction.Choose(choice.id)) },
                enabled = isChoiceEnabled(state, choice),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (node.type == TextGameNodeType.Narrative) "${index + 1}  ${choice.label}" else choice.label)
            }
        }
        if (node.type == TextGameNodeType.Narrative) {
            OutlinedButton(onClick = onOpenPrompt, modifier = Modifier.fillMaxWidth()) {
                Text("✦  Ask AI / write my own action")
            }
        }
    }
}

private enum class BattleViewMode(val label: String) {
    Standard("Standard"),
    Card("Cards"),
    Classic("Classic"),
}

private fun acronymOf(name: String): String = name
    .split(Regex("[^A-Za-z0-9]+"))
    .filter(String::isNotBlank)
    .take(3)
    .joinToString("") { it.first().uppercaseChar().toString() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BattleGameBoard(
    ui: TextGameUiState,
    node: TextGameNode,
    canPlay: (TextGameCard) -> Boolean,
    canSelectCard: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val battleBackground = Color(0xFF15121A)
    val panel = Color(0xFF241C1A)
    val line = Color(0xFF79552E)
    val pale = Color(0xFFF4EBDD)
    val secondary = Color(0xFFC8B9A5)
    val encounter = node.encounterId?.let(ui.definition::encounter) ?: return

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var viewMode by rememberSaveable {
            mutableStateOf(if (maxWidth >= 600.dp) BattleViewMode.Standard else BattleViewMode.Card)
        }
        Card(
            modifier = Modifier.fillMaxWidth().border(2.dp, Color(0xFF9B6A31), RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = battleBackground),
            shape = RoundedCornerShape(18.dp),
        ) {
            Box {
                ui.sceneImagePath?.let { imagePath ->
                    AsyncImage(
                        model = textGameImageModel(imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(.22f),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(Color(0xA0443428), battleBackground.copy(alpha = .90f), Color(0xF00A0A10)),
                        ),
                    ),
                )
                Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(node.title.uppercase(), color = pale, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "R${ui.game.run.turn} · ${ui.game.persistent.summonerSp} SP",
                            color = pale,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    // View switcher, stacked: one control, press-and-hold (or tap)
                    // pops the choice menu. Cards sits on top as the default.
                    var viewMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            "▤ ${viewMode.label} ▾",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF22262F))
                                .combinedClickable(
                                    onClick = { viewMenuOpen = true },
                                    onLongClick = { viewMenuOpen = true },
                                    onLongClickLabel = "Choose battle view",
                                )
                                .padding(horizontal = InkSpacing.sm, vertical = 3.dp),
                            color = pale,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        DropdownMenu(
                            expanded = viewMenuOpen,
                            onDismissRequest = { viewMenuOpen = false },
                        ) {
                            // Cards first: the portrait default.
                            listOf(BattleViewMode.Card, BattleViewMode.Standard, BattleViewMode.Classic).forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            mode.label,
                                            fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        viewMode = mode
                                        viewMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    when (viewMode) {
                        BattleViewMode.Standard -> {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 430.dp),
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                            ) {
                                BattlePartyPanel(ui, panel, line, pale, secondary, Modifier.weight(.28f).fillMaxSize())
                                BattleLogPanel(ui, node, panel, line, pale, secondary, Modifier.weight(.44f).fillMaxSize())
                                BattleEnemyPanel(ui, encounter, dispatch, panel, line, pale, secondary, Modifier.weight(.28f).fillMaxSize())
                            }
                            BattleHand(ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary)
                        }
                        BattleViewMode.Card -> {
                            BattleCompactEnemyPanel(ui, encounter, dispatch, panel, line, pale, secondary)
                            BattleCompactPartyPanel(ui, panel, line, pale, secondary)
                            BattleHandCompact(ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary)
                        }
                        BattleViewMode.Classic -> {
                            // Enemies → allies → action cards, each capped at half
                            // the screen so the whole fight fits with minimal scroll.
                            BattleEnemyPanel(
                                ui, encounter, dispatch, panel, line, pale, secondary,
                                Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                            )
                            BattlePartyPanel(
                                ui, panel, line, pale, secondary,
                                Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState()),
                            )
                            BattleLogCondensed(ui, node, pale, secondary)
                            BattleHandCompact(ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary)
                        }
                    }
                }
            }
        }
    }
}

/** Enemies as a tight two-per-row tile grid: acronym, numbers, intent damage. */
@Composable
private fun BattleCompactEnemyPanel(
    ui: TextGameUiState,
    encounter: TextGameEncounter,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
) {
    Text("GKOM CORRUPTED", color = Color(0xFFFF6F78), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    // The wall condenses as more monsters join: 2 per row, then 3, then 4 with
    // bars dropped for raw numbers.
    val enemyCount = encounter.enemies.size
    val perRow = when {
        enemyCount <= 2 -> 2
        enemyCount <= 4 -> 3
        else -> 4
    }
    val showBars = enemyCount <= 4
    val showIntent = enemyCount <= 6
    encounter.enemies.chunked(perRow).forEach { rowEnemies ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            rowEnemies.forEach { enemy ->
                val enemyState = ui.game.run.enemies.firstOrNull { it.id == enemy.id }
                val health = enemyState?.health ?: 0
                val maxHealth = enemyState?.maxHealth ?: enemy.maxHealth
                val selected = ui.game.run.selectedTargetId == enemy.id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = health > 0) { dispatch(TextGameAction.SelectTarget(enemy.id)) }
                        .border(1.dp, if (selected) Color(0xFFFFC857) else line)
                        .background(if (selected) Color(0xFF342D20) else panel)
                        .padding(horizontal = InkSpacing.xs, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(acronymOf(enemy.name), color = pale, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("$health/$maxHealth", color = secondary, style = MaterialTheme.typography.labelSmall)
                    }
                    if (showBars) {
                        LinearProgressIndicator(
                            progress = { health.toFloat() / maxHealth.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = if (health > 0) Color(0xFFB5B8BE) else Color(0xFF5D626B),
                            trackColor = Color(0xFF343A45),
                        )
                    }
                    if (showIntent) {
                        Text("~${enemy.intentDamage} dmg", color = Color(0xFFFFC857), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            repeat(perRow - rowEnemies.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Allies as a single strip of compact chips: acronym plus AP/EP numbers. */
@Composable
private fun BattleCompactPartyPanel(
    ui: TextGameUiState,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
) {
    Column(
        Modifier.fillMaxWidth().background(panel).border(1.dp, line).padding(horizontal = InkSpacing.xs, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SUMMONER", color = Color(0xFFFFD86A), style = MaterialTheme.typography.labelSmall)
            Text(
                "${ui.game.run.playerHealth}/${ui.game.persistent.maxHealth} HP · ${ui.game.run.guard} G · ${ui.game.persistent.summonerSp} SP",
                color = secondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        // Summoner ultimate gauge.
        val ult = ui.game.persistent.ultimate
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text("ULT", color = if (ult >= 100) Color(0xFFFFE8B0) else secondary, style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(
                progress = { ult / 100f },
                modifier = Modifier.weight(1f).height(5.dp),
                color = if (ult >= 100) Color(0xFFFFE8B0) else Color(0xFF8ED9F7),
                trackColor = Color(0xFF343A45),
            )
            Text("$ult%", color = secondary, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            ui.game.run.resources.forEach { resource ->
                Column(
                    Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, line).padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(acronymOf(resource.actorName), color = pale, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${resource.ap}/${resource.maxAp}AP", color = Color(0xFF8ED9F7), style = MaterialTheme.typography.labelSmall)
                    Text("${resource.ep}/${resource.maxEp}EP", color = secondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Condensed hand: small art, acronym title, numeric costs, one-line log. */
@Composable
private fun BattleHandCompact(
    ui: TextGameUiState,
    canPlay: (TextGameCard) -> Boolean,
    canSelectCard: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
) {
    val cards = ui.game.run.hand.mapNotNull(ui.definition::card)
    val selected = ui.game.run.selectedCardId?.let(ui.definition::card)
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        when {
            selected == null -> "Select a card."
            (selected.damage > 0 || selected.markBonus > 0) && ui.game.run.selectedTargetId == null ->
                "${acronymOf(selected.title)} — pick target."
            else -> "${acronymOf(selected.title)} — ready."
        },
        color = pale,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        cards.forEach { card ->
            val played = card.id in ui.game.run.playedCards
            val active = ui.game.run.selectedCardId == card.id
            val available = canSelectCard(card) && !played
            BattleActionCard(
                ui, card, active, played, available, compact = true,
                modifier = Modifier.width(104.dp),
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
                onToggleExpanded = { expandedCardId = if (expandedCardId == card.id) null else card.id },
            )
        }
    }
    // Tight action bar — End round on the left (bare white text), commit on the
    // right, with the ultimate unleash appearing once the gauge is full.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        TextButton(
            onClick = { dispatch(TextGameAction.EndTurn) },
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = InkSpacing.xs, vertical = 2.dp),
        ) {
            Text(
                "End round",
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (ui.game.persistent.ultimate >= 100) {
            TextButton(
                onClick = { dispatch(TextGameAction.CastUltimate) },
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = InkSpacing.xs, vertical = 2.dp),
            ) {
                Text(
                    "⚡ Ultimate",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFE8B0),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Button(
            onClick = { dispatch(TextGameAction.PlaySelectedCard) },
            enabled = selected != null && canPlay(selected),
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = InkSpacing.xs, vertical = 2.dp),
        ) { Text("Play card", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
    }
    expandedCardId?.let { id ->
        cards.firstOrNull { it.id == id }?.let { card ->
            val played = card.id in ui.game.run.playedCards
            ExpandedBattleCard(
                ui, card, ui.game.run.selectedCardId == card.id, played, canSelectCard(card) && !played,
                onDismiss = { expandedCardId = null },
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
            )
        }
    }
}

/** Classic mode's one-line log: no prose block, just the live feed. */
@Composable
private fun BattleLogCondensed(
    ui: TextGameUiState,
    node: TextGameNode,
    pale: Color,
    secondary: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "R${ui.game.run.turn} · hand ${ui.game.run.hand.size} · allies ${ui.game.run.resources.size}",
            color = secondary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (ui.game.run.lastLog.isNotBlank()) {
            Text(ui.game.run.lastLog, color = Color(0xFFFFD479), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
        node.title.takeIf { it.isNotBlank() }?.let {
            Text(it, color = pale, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BattlePartyPanel(
    ui: TextGameUiState,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    modifier: Modifier,
) {
    Column(
        modifier.background(panel).border(1.dp, line).padding(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text("PARTY", color = Color(0xFF8ED9F7), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("SUMMONER — off field", color = Color(0xFFFFD86A), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { ui.game.run.playerHealth.toFloat() / ui.game.persistent.maxHealth.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF77C887),
            trackColor = Color(0xFF343A45),
        )
        Text(
            "HP ${ui.game.run.playerHealth}/${ui.game.persistent.maxHealth}   Guard ${ui.game.run.guard}   SP ${ui.game.persistent.summonerSp}",
            color = secondary,
            style = MaterialTheme.typography.labelSmall,
        )
        ui.game.run.resources.forEach { resource ->
            val rosterMember = ui.definition.roster.firstOrNull { it.id == resource.actorId }
            val characterCard = rosterMember?.collectibleCardId?.let(ui.definition::collectible)
            Row(
                Modifier.fillMaxWidth().border(1.dp, line).padding(InkSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                characterCard?.let { card ->
                    AsyncImage(
                        model = cardImageModel(card, ui.cardImagePaths),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(44.dp).height(68.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        resource.actorName,
                        color = pale,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${rosterMember?.role ?: "ALLY"}  ·  AP ${resource.ap}/${resource.maxAp}  ·  EP ${resource.ep}/${resource.maxEp}",
                        color = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
        if (ui.game.persistent.subUnitIds.isNotEmpty()) {
            Text("SUB UNITS", color = secondary, style = MaterialTheme.typography.labelSmall)
            Text(
                ui.game.persistent.subUnitIds.mapNotNull { id -> ui.definition.roster.firstOrNull { it.id == id }?.name }.joinToString(" · "),
                color = pale,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BattleLogPanel(
    ui: TextGameUiState,
    node: TextGameNode,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    modifier: Modifier,
) {
    Column(
        modifier.background(panel).border(1.dp, line).padding(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text("=== ${node.title} ===", color = pale, fontWeight = FontWeight.Bold)
        Text(node.proseFor(ui.game.persistent.rngSeed), color = secondary, style = MaterialTheme.typography.bodySmall)
        Text("--- Round ${ui.game.run.turn} ---", color = pale, style = MaterialTheme.typography.labelMedium)
        Text(
            "Shared hand: ${ui.game.run.hand.size} cards   Active allies: ${ui.game.run.resources.size}",
            color = secondary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (ui.game.run.lastLog.isNotBlank()) {
            Text(ui.game.run.lastLog, color = Color(0xFFFFD479), style = MaterialTheme.typography.bodySmall)
        }
        ui.generatedNarration.takeLast(2).forEach {
            Text(it, color = secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BattleEnemyPanel(
    ui: TextGameUiState,
    encounter: TextGameEncounter,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    modifier: Modifier,
) {
    Column(
        modifier.background(panel).border(1.dp, line).padding(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text("GKOM CORRUPTED", color = Color(0xFFFF6F78), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        encounter.enemies.forEach { enemy ->
            val enemyState = ui.game.run.enemies.firstOrNull { it.id == enemy.id }
            val health = enemyState?.health ?: 0
            val maxHealth = enemyState?.maxHealth ?: enemy.maxHealth
            val selected = ui.game.run.selectedTargetId == enemy.id
            Column(
                Modifier.fillMaxWidth()
                    .clickable(enabled = health > 0) { dispatch(TextGameAction.SelectTarget(enemy.id)) }
                    .border(2.dp, if (selected) Color(0xFFFFC857) else line)
                    .background(if (selected) Color(0xFF342D20) else panel)
                    .padding(InkSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(enemy.name, color = pale, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { health.toFloat() / maxHealth.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (health > 0) Color(0xFFB5B8BE) else Color(0xFF5D626B),
                    trackColor = Color(0xFF343A45),
                )
                Text("HP $health/$maxHealth", color = secondary, style = MaterialTheme.typography.labelSmall)
                Text("▶ ${enemy.intent} · ~${enemy.intentDamage} dmg", color = Color(0xFFFFC857), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ResourceGem(symbol: String, value: Int, color: Color, compact: Boolean) {
    Box(
        Modifier
            .width(if (compact) 25.dp else 31.dp)
            .height(if (compact) 25.dp else 31.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color.copy(alpha = .96f), color.copy(alpha = .48f), Color(0xFF17121B))))
            .border(1.dp, Color(0xFFFFE18A), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$symbol$value", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Adams Haven's tactile battle card. Artwork owns the front; holding reveals
 * rules on the back, and double-tapping delegates expansion to the hand.
 */
@Composable
private fun BattleActionCard(
    ui: TextGameUiState,
    card: TextGameCard,
    selected: Boolean,
    played: Boolean,
    available: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    var showingBack by remember(card.id) { mutableStateOf(false) }
    val gold = if (selected) Color(0xFFFFD55F) else Color(0xFF9B6A31)
    val art = ui.definition.roster.firstOrNull { it.id == card.ownerId }
        ?.collectibleCardId
        ?.let(ui.definition::collectible)
    val gestureModifier = Modifier.pointerInput(card.id, available) {
        detectTapGestures(
            onTap = { if (available) onSelect() },
            onDoubleTap = { onToggleExpanded() },
            onPress = {
                coroutineScope {
                    var revealed = false
                    val reveal = launch {
                        delay(430)
                        revealed = true
                        showingBack = true
                    }
                    tryAwaitRelease()
                    reveal.cancel()
                    if (revealed) showingBack = false
                }
            },
        )
    }
    Box(
        modifier
            .aspectRatio(.68f)
            .then(gestureModifier)
            .alpha(if (played) .48f else 1f)
            .clip(RoundedCornerShape(if (compact) 12.dp else 18.dp))
            .background(Color(0xFF24170F))
            .border(if (selected) 4.dp else 2.dp, gold, RoundedCornerShape(if (compact) 12.dp else 18.dp)),
    ) {
        if (showingBack) {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF46311F), Color(0xFF17121B))))
                    .padding(if (compact) 7.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✦", color = Color(0xFFFFD479), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium)
                Text(card.title, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2)
                if (!compact) {
                    Spacer(Modifier.height(8.dp))
                    Text(card.description, color = Color(0xFFF1E5D1), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(card.ownerName, color = Color(0xFFFFD479), style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(card.description, color = Color(0xFFF1E5D1), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Text(costText(card), color = Color(0xFF9DDCF6), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            if (art != null) {
                AsyncImage(
                    model = cardImageModel(art, ui.cardImagePaths),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (compact) 4.dp else 6.dp).clip(RoundedCornerShape(if (compact) 9.dp else 14.dp)),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF25435B), Color(0xFF22182F)))),
                    contentAlignment = Alignment.Center,
                ) { Text("✦", color = Color(0xFFFFD479), style = MaterialTheme.typography.headlineLarge) }
            }
            Box(
                Modifier.fillMaxWidth().height(if (compact) 52.dp else 72.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2181110))))
                    .padding(horizontal = if (compact) 5.dp else 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.title, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    if (!compact) Text(card.ownerName, color = Color(0xFFFFD479), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (card.apCost > 0) ResourceGem("⚔", card.apCost, Color(0xFF277BC0), compact)
                if (card.epCost > 0) ResourceGem("◆", card.epCost, Color(0xFF7555B7), compact)
                if (card.spCost > 0) ResourceGem("✦", card.spCost, Color(0xFFC79524), compact)
            }
            if (selected) {
                Text("◆", color = Color(0xFFFFE27A), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), fontWeight = FontWeight.Black)
            }
            if (!available && !played) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
                    Text("⊘", color = Color(0xFFFF7D72), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ExpandedBattleCard(
    ui: TextGameUiState,
    card: TextGameCard,
    selected: Boolean,
    played: Boolean,
    available: Boolean,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BattleActionCard(
                ui = ui,
                card = card,
                selected = selected,
                played = played,
                available = available,
                compact = false,
                modifier = Modifier.width(310.dp),
                onSelect = onSelect,
                onToggleExpanded = onDismiss,
            )
            Text("Double-tap to shrink · Hold to read the back", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BattleHand(
    ui: TextGameUiState,
    canPlay: (TextGameCard) -> Boolean,
    canSelectCard: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
) {
    val cards = ui.game.run.hand.mapNotNull(ui.definition::card)
    val selected = ui.game.run.selectedCardId?.let(ui.definition::card)
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        when {
            selected == null -> "Select a card."
            (selected.damage > 0 || selected.markBonus > 0) && ui.game.run.selectedTargetId == null -> "${selected.title} — choose a target."
            else -> "${selected.title} — ready to commit."
        },
        color = pale,
        style = MaterialTheme.typography.labelMedium,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        cards.forEach { card ->
            val played = card.id in ui.game.run.playedCards
            val active = ui.game.run.selectedCardId == card.id
            val available = canSelectCard(card) && !played
            BattleActionCard(
                ui, card, active, played, available, compact = false,
                modifier = Modifier.width(154.dp),
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
                onToggleExpanded = { expandedCardId = if (expandedCardId == card.id) null else card.id },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Button(
            onClick = { dispatch(TextGameAction.PlaySelectedCard) },
            enabled = selected != null && canPlay(selected),
            modifier = Modifier.weight(1f),
        ) { Text("Play selected card") }
        OutlinedButton(
            onClick = { dispatch(TextGameAction.EndTurn) },
            modifier = Modifier.weight(1f),
        ) { Text("End round → enemies act") }
    }
    expandedCardId?.let { id ->
        cards.firstOrNull { it.id == id }?.let { card ->
            val played = card.id in ui.game.run.playedCards
            ExpandedBattleCard(
                ui, card, ui.game.run.selectedCardId == card.id, played, canSelectCard(card) && !played,
                onDismiss = { expandedCardId = null },
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
            )
        }
    }
}

@Composable
private fun BattleControls(
    ui: TextGameUiState,
    canPlay: (TextGameCard) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val encounter = ui.definition.node(ui.game.run.nodeId)?.encounterId?.let(ui.definition::encounter) ?: return
    Text("ENEMIES · TURN ${ui.game.run.turn}", style = MaterialTheme.typography.labelSmall, color = inkTokens().secondaryText)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        encounter.enemies.forEach { enemy ->
            val enemyState = ui.game.run.enemies.firstOrNull { it.id == enemy.id }
            val health = enemyState?.health ?: 0
            val maxHealth = enemyState?.maxHealth ?: enemy.maxHealth
            FilterChip(
                selected = ui.game.run.selectedTargetId == enemy.id,
                enabled = health > 0,
                onClick = { dispatch(TextGameAction.SelectTarget(enemy.id)) },
                label = { Text("${enemy.name} $health/$maxHealth · ${enemy.intent}") },
            )
        }
    }
    Text("SHARED HAND", style = MaterialTheme.typography.labelSmall, color = inkTokens().secondaryText)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        ui.game.run.hand.mapNotNull(ui.definition::card).forEach { card ->
            val played = card.id in ui.game.run.playedCards
            Card(Modifier.width(190.dp), colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
                Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(card.title, fontWeight = FontWeight.Bold)
                    Text("${card.ownerName} · ${costText(card)}", style = MaterialTheme.typography.labelSmall, color = inkTokens().activePill)
                    Text(card.description, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { dispatch(TextGameAction.PlayCard(card.id)) },
                        enabled = !played && canPlay(card),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (played) "Played" else "Play") }
                }
            }
        }
    }
    OutlinedButton(onClick = { dispatch(TextGameAction.EndTurn) }, modifier = Modifier.fillMaxWidth()) { Text("End turn") }
}

/** Spoils of the fight that just ended, shown on reward and ending screens. */
@Composable
private fun BattleSpoilsCard(gains: TextGameBattleGains?) {
    if (gains == null) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2418))) {
        Column(Modifier.padding(InkSpacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "BATTLE SPOILS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFE8B0),
            )
            Text(
                if (gains.isEmpty()) {
                    "No spoils — the case was content to keep you breathing."
                } else {
                    buildList {
                        if (gains.coins != 0) add("%+d coin".format(gains.coins))
                        if (gains.sp != 0) add("%+d SP".format(gains.sp))
                        if (gains.materials != 0) add("%+d material".format(gains.materials))
                        if (gains.seeds != 0) add("%+d seed".format(gains.seeds))
                        if (gains.cropGrowth != 0) add("%+d crop growth".format(gains.cropGrowth))
                        if (gains.ultimate != 0) add("%+d ultimate".format(gains.ultimate))
                    }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RewardControls(ui: TextGameUiState, dispatch: (TextGameAction) -> Unit) {
    BattleSpoilsCard(ui.game.run.lastBattleGains)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        ui.game.run.rewardOptions.mapNotNull(ui.definition::collectible).forEach { card ->
            Card(
                modifier = Modifier.width(180.dp).clickable { dispatch(TextGameAction.ClaimReward(card.id)) },
                colors = CardDefaults.cardColors(containerColor = inkTokens().panel),
            ) {
                Column {
                    AsyncImage(
                        model = cardImageModel(card, ui.cardImagePaths),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(941f / 1672f),
                    )
                    Column(Modifier.padding(InkSpacing.sm)) {
                        Text(card.title, fontWeight = FontWeight.Bold)
                        Text("Choose ${card.category.dropLast(1)} card", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EndingSummary(ui: TextGameUiState, dispatch: (TextGameAction) -> Unit) {
    val state = ui.game
    val node = ui.definition.node(state.run.nodeId)
    val defeat = node?.id == "defeat" || node?.id?.endsWith("_defeat") == true
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
        Column(Modifier.padding(InkSpacing.md), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text(
                if (defeat) "RUN ENDED" else "RUN COMPLETE",
                fontWeight = FontWeight.Bold,
            )
            node?.title?.takeIf { it.isNotBlank() }?.let { Text(it, color = inkTokens().secondaryText) }
            Text("Companion: ${state.persistent.companionId ?: "none"}")
            Text("Coins ${state.persistent.coins} · Seeds ${state.persistent.seeds} · Harvest ${state.persistent.harvest}")
            Text("Materials ${state.persistent.materials} · Dishes ${state.persistent.dishes} · Battles ${state.persistent.battlesWon}")
            Text("Reward cards: ${state.persistent.collection.mapNotNull(ui.definition::collectible).joinToString { it.title }.ifBlank { "none" }}")
            Text("Max health: ${state.persistent.maxHealth}")
            Text("Difficulty: ${state.persistent.difficulty.label}")
        }
        BattleSpoilsCard(state.run.lastBattleGains)
    }
    Button(onClick = { dispatch(TextGameAction.Reset) }, modifier = Modifier.fillMaxWidth()) {
        Text("Restart ${ui.playStyle.label}")
    }
}

private fun costText(card: TextGameCard): String = buildList {
    if (card.apCost > 0) add("${card.apCost} AP")
    if (card.epCost > 0) add("${card.epCost} EP")
    if (card.spCost > 0) add("${card.spCost} SP")
}.joinToString(" · ").ifBlank { "Free" }

@Composable
private fun TextGameCardLibrary(
    definition: TextGameDefinition,
    collectedIds: List<String>,
    imagePaths: Map<String, String>,
    onBack: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf("all") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val cards = definition.collectibleCards.filter { category == "all" || it.category == category }
    Column(Modifier.fillMaxSize().padding(InkSpacing.md), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CARD LIBRARY", style = MaterialTheme.typography.labelSmall, color = inkTokens().activePill)
                Text("Adams Haven V2 · ${definition.collectibleCards.size} cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            listOf("all" to "All", "characters" to "Characters", "locations" to "Locations", "objects" to "Objects").forEach { (id, label) ->
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            items(cards, key = { it.id }) { card ->
                val collected = card.id in collectedIds
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedId = card.id },
                    colors = CardDefaults.cardColors(containerColor = inkTokens().panel),
                ) {
                    Column {
                        AsyncImage(
                            model = cardImageModel(card, imagePaths),
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(941f / 1672f),
                        )
                        Column(Modifier.padding(InkSpacing.xs)) {
                            Text(card.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (collected) "COLLECTED" else card.category.dropLast(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (collected) inkTokens().activePill else inkTokens().secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
    selectedId?.let { id ->
        definition.collectible(id)?.let { card ->
            Dialog(onDismissRequest = { selectedId = null }) {
                Card(colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
                    Column(Modifier.padding(InkSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = cardImageModel(card, imagePaths),
                            contentDescription = card.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(520.dp),
                        )
                        Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(card.category.dropLast(1).uppercase(), color = inkTokens().secondaryText)
                        Button(onClick = { selectedId = null }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                    }
                }
            }
        }
    }
}

private fun cardImageModel(card: TextGameCollectibleCard, imagePaths: Map<String, String>): Any =
    imagePaths[card.id]?.let(::File) ?: "file:///android_asset/${card.artAssetPath}"

private fun textGameImageModel(path: String): Any =
    if (path.startsWith("file:") || path.startsWith("content:") || path.startsWith("asset:")) path else File(path)
