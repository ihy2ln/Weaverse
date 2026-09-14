package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.components.LoopingVideoBackground
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
            motionPaths = ui.cardMotionPaths,
            defeatedMonsterIds = ui.game.persistent.defeatedMonsters,
            gkomImagePaths = ui.gkomImagePaths,
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
    val tapWorld = node != null && isTapWorldNode(node.id)
    val compactChrome = battleFocus || tapWorld
    LaunchedEffect(node?.id, compactChrome) { onBattleFocus(compactChrome) }
    if (node == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { dispatch(TextGameAction.Reset) }) { Text("Repair save and restart") }
        }
        return
    }

    if (tapWorld) {
        Column(
            Modifier.fillMaxSize().padding(InkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                Text(
                    node.title,
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
            TapWorldPlay(
                ui = ui,
                node = node,
                isChoiceEnabled = isChoiceEnabled,
                dispatch = dispatch,
                modifier = Modifier.weight(1f),
            )
            ui.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            var farmMinigame by remember { mutableStateOf<FarmMinigameRequest?>(null) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                val onPlotTap: (Int) -> Unit = { plotId ->
                    handleFarmPlotTap(ui, plotId, isChoiceEnabled, dispatch) { farmMinigame = it }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.md)) {
                        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                            ScenePicture(
                                node = node,
                                imagePath = ui.sceneImagePath,
                                motionPath = ui.sceneMotionPath,
                                state = ui.game,
                                isChoiceEnabled = isChoiceEnabled,
                                onHotspot = { choiceId -> dispatch(TextGameAction.Choose(choiceId)) },
                                onPlotTap = onPlotTap,
                                dispatch = dispatch,
                            )
                            StatusStrip(ui.game)
                        }
                        Column(Modifier.weight(.85f), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                            StoryAndControls(ui, node, isChoiceEnabled, canPlay, dispatch, onGenerateMissions, onOpenPrompt) {
                                farmMinigame = it
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                        ScenePicture(
                            node = node,
                            imagePath = ui.sceneImagePath,
                            motionPath = ui.sceneMotionPath,
                            state = ui.game,
                            isChoiceEnabled = isChoiceEnabled,
                            onHotspot = { choiceId -> dispatch(TextGameAction.Choose(choiceId)) },
                            onPlotTap = onPlotTap,
                            dispatch = dispatch,
                        )
                        StatusStrip(ui.game)
                        StoryAndControls(ui, node, isChoiceEnabled, canPlay, dispatch, onGenerateMissions, onOpenPrompt) {
                            farmMinigame = it
                        }
                    }
                }
            }
            farmMinigame?.let { request ->
                FarmTimingMinigame(
                    label = request.label,
                    onSkip = {
                        when (request) {
                            is FarmMinigameRequest.Plant -> dispatch(TextGameAction.FarmPlant(request.plotId, 0f))
                            is FarmMinigameRequest.Harvest -> dispatch(TextGameAction.FarmHarvest(request.plotId, 0f))
                        }
                        farmMinigame = null
                    },
                    onStrike = { score ->
                        when (request) {
                            is FarmMinigameRequest.Plant -> dispatch(TextGameAction.FarmPlant(request.plotId, score))
                            is FarmMinigameRequest.Harvest -> dispatch(TextGameAction.FarmHarvest(request.plotId, score))
                        }
                        farmMinigame = null
                    },
                )
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
    onFarmMinigame: (FarmMinigameRequest) -> Unit = {},
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
            else -> {
                if (node.id == "kitchen") {
                    FarmBoard(ui, dispatch, onFarmMinigame)
                }
                ChoiceControls(ui.game, node, isChoiceEnabled, dispatch, onOpenPrompt)
            }
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
    onPlotTap: (Int) -> Unit = {},
    dispatch: (TextGameAction) -> Unit = {},
) {
    val colors = when (node.type) {
        TextGameNodeType.Battle -> listOf(Color(0xFF201A39), Color(0xFF7B3D50))
        TextGameNodeType.Reward -> listOf(Color(0xFF202A42), Color(0xFF3C6A75))
        TextGameNodeType.Hub -> listOf(Color(0xFF35253D), Color(0xFFB36E5F))
        TextGameNodeType.Ending -> listOf(Color(0xFF182A2C), Color(0xFFB58C5D))
        else -> listOf(Color(0xFF111A2A), Color(0xFF9B5C5B))
    }
    val boardKind = HavenBoardRules.boardKind(node.id)
    val havenBoardScene = boardKind != null
    val farm = remember(state.persistent) { liveFarm(state.persistent) }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .aspectRatio(
                when (boardKind) {
                    HavenBoardKind.Town -> 2f / 3f
                    HavenBoardKind.Farm -> 3f / 2f
                    null -> if (node.id in setOf("farm", "return_farm", "town", "return_town")) 3f / 2f else (941f / 1672f) * 2f
                },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val model = imagePath?.let(::textGameImageModel) ?: node.bundledSceneAssetPath?.let { "file:///android_asset/$it" }
        val showMotion = motionPath != null && !havenBoardScene
        if (showMotion) {
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
        if (boardKind != null) {
            HavenBoardOverlay(
                boardKind = boardKind,
                havenBoard = state.persistent.havenBoard,
                dispatch = dispatch,
                modifier = Modifier.fillMaxSize(),
            )
        }
        node.hotspots.forEach { hotspot ->
            val plotId = hotspot.farmPlotId
            val choice = node.choices.firstOrNull { it.id == hotspot.choiceId }
            val enabled = when {
                plotId != null && !havenBoardScene -> plotChipEnabled(farm, plotId, node, state, isChoiceEnabled)
                choice != null -> isChoiceEnabled(state, choice)
                hotspot.talkProse.isNotBlank() || hotspot.npcName.isNotBlank() -> true
                else -> false
            }
            if (plotId != null && havenBoardScene) return@forEach
            if (plotId == null && choice == null && hotspot.talkProse.isBlank() && hotspot.npcName.isBlank()) return@forEach
            val label = if (plotId != null) plotChipLabel(farm, plotId) else hotspot.label
            val fill = if (plotId != null) plotChipFill(farm, plotId) else Color(0xE6281C12)
            MapHotspotChip(
                label = label,
                enabled = enabled,
                fill = fill,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (maxWidth * hotspot.x.coerceIn(0f, 1f) - 44.dp).coerceAtLeast(0.dp),
                        y = (maxHeight * hotspot.y.coerceIn(0f, 1f) - 16.dp).coerceAtLeast(0.dp),
                    ),
                onClick = {
                    if (plotId != null) onPlotTap(plotId) else if (choice != null) onHotspot(hotspot.choiceId) else Unit
                },
            )
        }
    }
}

@Composable
private fun MapHotspotChip(
    label: String,
    enabled: Boolean,
    fill: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (enabled) Color(0xFFFFF4DC) else Color(0x99E8D8B0),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) fill else fill.copy(alpha = 0.45f))
            .border(1.dp, Color(0xFFFFD86A).copy(alpha = if (enabled) 0.9f else 0.3f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun liveFarm(persistent: TextGamePersistentState): FarmState {
    val cleared = "farm_cleared" in persistent.flags || persistent.farm.plots.isNotEmpty()
    val capacity = FarmRules.plotCapacity(persistent.farmLevel, cleared)
    return FarmRules.refreshReady(
        FarmRules.syncBattlesFought(
            FarmRules.ensureCapacity(persistent.farm, capacity),
            persistent.battlesWon,
        ),
    )
}

private fun plotChipLabel(farm: FarmState, plotId: Int): String {
    val plot = farm.plots.firstOrNull { it.id == plotId }
    return when {
        plot == null -> if (plotId == 0) "Clear plot" else "Locked"
        plot.soil == FarmSoil.Wild -> "Till"
        plot.soil == FarmSoil.Tilled -> "Plant"
        plot.soil == FarmSoil.Planted && !plot.watered -> "Water"
        plot.soil == FarmSoil.Planted -> "Growing"
        plot.soil == FarmSoil.Ready -> "Harvest"
        else -> "Plot"
    }
}

private fun plotChipFill(farm: FarmState, plotId: Int): Color {
    val plot = farm.plots.firstOrNull { it.id == plotId }
    return when (plot?.soil) {
        FarmSoil.Wild -> Color(0xE65C3A1E)
        FarmSoil.Tilled -> Color(0xE67A4E24)
        FarmSoil.Planted -> Color(0xE82E6A32)
        FarmSoil.Ready -> Color(0xF0C49218)
        null -> Color(0xE6281C12)
    }
}

private fun plotChipEnabled(
    farm: FarmState,
    plotId: Int,
    node: TextGameNode,
    state: TextGameState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
): Boolean {
    val plot = farm.plots.firstOrNull { it.id == plotId }
    if (plot != null) return plot.soil != FarmSoil.Planted || !plot.watered
    val clear = node.choices.firstOrNull { it.id == "clear_plot" }
    return plotId == 0 && clear != null && isChoiceEnabled(state, clear)
}

private fun handleFarmPlotTap(
    ui: TextGameUiState,
    plotId: Int,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onMinigame: (FarmMinigameRequest) -> Unit,
) {
    val node = ui.definition.node(ui.game.run.nodeId) ?: return
    val farm = liveFarm(ui.game.persistent)
    val plot = farm.plots.firstOrNull { it.id == plotId }
    if (plot == null) {
        val clear = node.choices.firstOrNull { it.id == "clear_plot" }
        if (plotId == 0 && clear != null && isChoiceEnabled(ui.game, clear)) {
            dispatch(TextGameAction.Choose("clear_plot"))
        }
        return
    }
    when (plot.soil) {
        FarmSoil.Wild -> dispatch(TextGameAction.FarmTill(plot.id))
        FarmSoil.Tilled -> if (ui.game.persistent.seeds > 0) onMinigame(FarmMinigameRequest.Plant(plot.id))
        FarmSoil.Planted -> if (!plot.watered) dispatch(TextGameAction.FarmWater(plot.id))
        FarmSoil.Ready -> onMinigame(FarmMinigameRequest.Harvest(plot.id))
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
    val here = dungeon.currentRoom()
    val hereKind = here?.let { DungeonKind.fromIndex(it.kind) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text(
                "SILVERWOOD — ${dungeon.floorName().uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE8C87A),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onOpenPrompt) { Text("✦ Ask AI") }
            OutlinedButton(onClick = onCards) { Text("Cards") }
            OutlinedButton(onClick = onShelf) { Text("Modes") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.md)) {
                    DungeonMapPanel(
                        dungeon = dungeon,
                        onCellTap = { x, y -> dispatch(TextGameAction.DungeonStep(x, y)) },
                        modifier = Modifier.weight(1.7f),
                    )
                    DungeonSidePanel(ui, dungeon, hereKind, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    DungeonMapPanel(
                        dungeon = dungeon,
                        onCellTap = { x, y -> dispatch(TextGameAction.DungeonStep(x, y)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DungeonSidePanel(ui, dungeon, hereKind, Modifier.fillMaxWidth())
                }
            }
        }
        StatusStrip(ui.game)
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
                Button(onClick = { dispatch(TextGameAction.DescendDungeon) }) {
                    Text("▼ Take the stairs down")
                }
            }
            if (DungeonRules.canRetreat(dungeon)) {
                OutlinedButton(onClick = { dispatch(TextGameAction.LeaveDungeon) }) { Text("Leave the dungeon") }
            }
        }
    }
}

@Composable
private fun DungeonSidePanel(
    ui: TextGameUiState,
    dungeon: DungeonState,
    hereKind: DungeonKind?,
    modifier: Modifier = Modifier,
) {
    val floor = dungeon.currentFloor()
    val fights = floor?.fights() ?: 0
    val cleared = floor?.fightsCleared() ?: 0
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF6B5326))
            .background(Color(0xFF1A140C))
            .padding(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(
            hereKind?.let { "${it.glyph} ${it.label}" } ?: "Dungeon",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE8C87A),
        )
        if (ui.game.persistent.missionTitle.isNotBlank()) {
            Text(
                "Contract: ${ui.game.persistent.missionTitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC9A0F0),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Clear the floor boss to complete the contract.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
        }
        Text(
            "Floor progress · $cleared / $fights fights cleared",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB8A078),
        )
        if (ui.game.run.lastLog.isNotBlank()) {
            Text(ui.game.run.lastLog, color = Color(0xFFFFD479), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Tap an adjacent door on the map, or a cleared room further in.",
            style = MaterialTheme.typography.labelSmall,
            color = inkTokens().secondaryText,
        )
    }
}

/** Godot-style battlemat: full grid fog, walls, door gaps, reach marks, tap-to-move. */
@Composable
private fun DungeonMapPanel(
    dungeon: DungeonState,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val floor = dungeon.currentFloor()
    val exits = remember(dungeon) { DungeonRules.exits(dungeon) }
    val cols = floor?.sizeX ?: 6
    val rows = floor?.sizeY ?: 5
    Box(
        modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF6B5326))
            .pointerInput(dungeon.atX, dungeon.atY, cols, rows, exits) {
                detectTapGestures { offset ->
                    val fl = dungeon.currentFloor() ?: return@detectTapGestures
                    val cell = min(size.width / fl.sizeX, size.height / fl.sizeY)
                    val ox = (size.width - cell * fl.sizeX) / 2f
                    val oy = (size.height - cell * fl.sizeY) / 2f
                    val gx = ((offset.x - ox) / cell).toInt()
                    val gy = ((offset.y - oy) / cell).toInt()
                    if (gx !in 0 until fl.sizeX || gy !in 0 until fl.sizeY) return@detectTapGestures
                    if (fl.room(gx, gy) == null) return@detectTapGestures
                    onCellTap(gx, gy)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF241C10), Color(0xFF54421F), Color(0xFF241C10))))
            val cell = min(size.width / cols, size.height / rows)
            val ox = (size.width - cell * cols) / 2f
            val oy = (size.height - cell * rows) / 2f
            val wallColor = Color(0xFF120E08).copy(alpha = 0.92f)
            val wallW = cell * 0.055f
            val doorGap = 0.44f

            // Fog every grid cell so unexplored shape is not readable.
            for (gx in 0 until cols) {
                for (gy in 0 until rows) {
                    val x = ox + gx * cell
                    val y = oy + gy * cell
                    val room = floor?.room(gx, gy)
                    val sight = if (room == null) DungeonSight.Hidden else DungeonRules.sight(dungeon, gx, gy)
                    val fill = when {
                        room == null || sight == DungeonSight.Hidden -> Color(0xFF100C07).copy(alpha = 0.92f)
                        sight == DungeonSight.Peeked -> Color(0xFF5C4A28).copy(alpha = 0.85f)
                        else -> Color(0xFF8A6E3A).copy(alpha = 0.9f)
                    }
                    drawRect(fill, topLeft = Offset(x + 1f, y + 1f), size = Size(cell - 2f, cell - 2f))
                }
            }

            floor?.rooms?.forEach { room ->
                val sight = DungeonRules.sight(dungeon, room.x, room.y)
                if (sight == DungeonSight.Hidden) return@forEach
                val x = ox + room.x * cell
                val y = oy + room.y * cell
                val rect = Rect(Offset(x, y), Size(cell, cell))
                val isHere = room.x == dungeon.atX && room.y == dungeon.atY
                val isReach = exits.any { it.x == room.x && it.y == room.y }

                // Walls + door gaps on each edge.
                DOOR_OFFSETS.forEach { (door, _) ->
                    drawDoorEdge(rect, door, room.hasDoor(door), wallColor, wallW, doorGap)
                }

                if (isHere) {
                    drawRect(
                        Color(0xFF4CCFE0).copy(alpha = 0.35f),
                        topLeft = Offset(x + cell * 0.12f, y + cell * 0.12f),
                        size = Size(cell * 0.76f, cell * 0.76f),
                    )
                } else if (isReach) {
                    drawRect(
                        Color(0xFFFFD14F).copy(alpha = 0.22f),
                        topLeft = Offset(x + cell * 0.12f, y + cell * 0.12f),
                        size = Size(cell * 0.76f, cell * 0.76f),
                    )
                }

                val kind = DungeonKind.fromIndex(room.kind)
                if (room.cleared && kind.isFightKind) {
                    drawRect(
                        Color(0xFF2E3A2A).copy(alpha = 0.55f),
                        topLeft = Offset(x + 2f, y + 2f),
                        size = Size(cell - 4f, cell - 4f),
                    )
                }
                if (kind.isFightKind && !room.cleared) {
                    drawCircle(
                        Color(0xFFEB4D48),
                        radius = cell * 0.055f,
                        center = Offset(x + cell - cell * 0.14f, y + cell * 0.14f),
                    )
                }
                val glyph = when {
                    isHere -> "✦"
                    else -> kind.glyph
                }
                val paint = android.graphics.Paint().apply {
                    color = if (isHere) 0xFFFFE8B0.toInt() else if (sight == DungeonSight.Peeked) 0xFFB8A078.toInt() else 0xFFE8D8B0.toInt()
                    textSize = cell * 0.42f
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
                        Color(0xFF4CCFE0),
                        radius = cell * 0.42f,
                        center = Offset(x + cell / 2f, y + cell / 2f),
                        style = Stroke(width = 2.5f),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoorEdge(
    rect: Rect,
    door: Int,
    hasDoor: Boolean,
    color: Color,
    wallW: Float,
    doorGap: Float,
) {
    val a: Offset
    val b: Offset
    when (door) {
        DOOR_N -> {
            a = rect.topLeft
            b = Offset(rect.right, rect.top)
        }
        DOOR_S -> {
            a = Offset(rect.left, rect.bottom)
            b = Offset(rect.right, rect.bottom)
        }
        DOOR_E -> {
            a = Offset(rect.right, rect.top)
            b = Offset(rect.right, rect.bottom)
        }
        else -> {
            a = rect.topLeft
            b = Offset(rect.left, rect.bottom)
        }
    }
    if (!hasDoor) {
        drawLine(color, a, b, wallW, StrokeCap.Butt)
        return
    }
    val gap = doorGap.coerceIn(0.1f, 0.8f)
    val along = Offset(b.x - a.x, b.y - a.y)
    val g0 = Offset(a.x + along.x * ((1f - gap) / 2f), a.y + along.y * ((1f - gap) / 2f))
    val g1 = Offset(a.x + along.x * ((1f + gap) / 2f), a.y + along.y * ((1f + gap) / 2f))
    drawLine(color, a, g0, wallW, StrokeCap.Butt)
    drawLine(color, g1, b, wallW, StrokeCap.Butt)
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
        if (state.persistent.harvest > 0 || state.persistent.dishes > 0 || state.persistent.farm.pantry.isNotEmpty()) {
            val packed = state.persistent.farm.packedDish?.let { " · packed $it" } ?: ""
            StatusChip(
                "♣ FARM",
                "${state.persistent.harvest} produce · ${state.persistent.dishes} dish$packed",
                Color(0xFF77B982),
            )
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

/** Live drag of a hand card across the battle board toward an enemy drop target. */
private data class BattleCardDrag(
    val cardId: String,
    val pointerRoot: Offset,
    val grabInCard: Offset,
    val cardSize: Size,
)

private fun TextGameCard.needsEnemyTarget(): Boolean = damage > 0 || markBonus > 0

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
    val enemyDropBounds = remember { mutableStateMapOf<String, Rect>() }
    var drag by remember { mutableStateOf<BattleCardDrag?>(null) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    val hoverEnemyId = drag?.let { current ->
        enemyDropBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(current.pointerRoot)
        }?.key
    }

    fun playDraggedCard(cardId: String, targetId: String?) {
        val card = ui.definition.card(cardId) ?: return
        if (!canSelectCard(card)) return
        if (card.needsEnemyTarget()) {
            val living = targetId?.takeIf { id -> ui.game.run.enemies.any { it.id == id && it.health > 0 } }
                ?: ui.game.run.selectedTargetId?.takeIf { id -> ui.game.run.enemies.any { it.id == id && it.health > 0 } }
                ?: return
            dispatch(TextGameAction.SelectTarget(living))
            dispatch(TextGameAction.PlayCard(cardId))
        } else {
            dispatch(TextGameAction.PlayCard(cardId))
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var viewMode by rememberSaveable {
            mutableStateOf(if (maxWidth >= 600.dp) BattleViewMode.Standard else BattleViewMode.Card)
        }
        Card(
            modifier = Modifier.fillMaxWidth().border(2.dp, Color(0xFF9B6A31), RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = battleBackground),
            shape = RoundedCornerShape(18.dp),
        ) {
            Box(
                Modifier.onGloballyPositioned { coords ->
                    boardOrigin = coords.boundsInRoot().topLeft
                },
            ) {
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
                    Text(
                        when {
                            drag != null && hoverEnemyId != null -> "Drop on the GKOM to strike."
                            drag != null -> "Drag onto a living enemy — or release to cancel."
                            else -> "Drag an attack card onto an enemy to play it."
                        },
                        color = if (drag != null) Color(0xFFFFD479) else secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    when (viewMode) {
                        BattleViewMode.Standard -> {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 430.dp),
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                            ) {
                                BattlePartyPanel(ui, panel, line, pale, secondary, Modifier.weight(.28f).fillMaxSize())
                                BattleLogPanel(ui, node, panel, line, pale, secondary, Modifier.weight(.44f).fillMaxSize())
                                BattleEnemyPanel(
                                    ui, encounter, dispatch, panel, line, pale, secondary,
                                    Modifier.weight(.28f).fillMaxSize(),
                                    hoverEnemyId = hoverEnemyId,
                                    onEnemyBounds = { id, bounds ->
                                        if (bounds == Rect.Zero) enemyDropBounds.remove(id) else enemyDropBounds[id] = bounds
                                    },
                                )
                            }
                            BattleHand(
                                ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary,
                                drag = drag,
                                onDragChanged = { drag = it },
                                onDragPlay = { cardId, pointer ->
                                    val target = enemyDropBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                    playDraggedCard(cardId, target)
                                },
                            )
                        }
                        BattleViewMode.Card -> {
                            BattleCompactEnemyPanel(
                                ui, encounter, dispatch, panel, line, pale, secondary,
                                hoverEnemyId = hoverEnemyId,
                                onEnemyBounds = { id, bounds ->
                                    if (bounds == Rect.Zero) enemyDropBounds.remove(id) else enemyDropBounds[id] = bounds
                                },
                            )
                            BattleHandCompact(
                                ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary,
                                drag = drag,
                                onDragChanged = { drag = it },
                                onDragPlay = { cardId, pointer ->
                                    val target = enemyDropBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                    playDraggedCard(cardId, target)
                                },
                                // Summoner strip sits under the hand cards to free enemy space.
                                belowHand = {
                                    BattleCompactPartyPanel(ui, panel, line, pale, secondary)
                                },
                            )
                        }
                        BattleViewMode.Classic -> {
                            BattleEnemyPanel(
                                ui, encounter, dispatch, panel, line, pale, secondary,
                                Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                                hoverEnemyId = hoverEnemyId,
                                onEnemyBounds = { id, bounds ->
                                    if (bounds == Rect.Zero) enemyDropBounds.remove(id) else enemyDropBounds[id] = bounds
                                },
                            )
                            BattlePartyPanel(
                                ui, panel, line, pale, secondary,
                                Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState()),
                            )
                            BattleLogCondensed(ui, node, pale, secondary)
                            BattleHandCompact(
                                ui, canPlay, canSelectCard, dispatch, panel, line, pale, secondary,
                                drag = drag,
                                onDragChanged = { drag = it },
                                onDragPlay = { cardId, pointer ->
                                    val target = enemyDropBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                    playDraggedCard(cardId, target)
                                },
                            )
                        }
                    }
                }

                // Floating ghost of the dragged attack card.
                drag?.let { current ->
                    val card = ui.definition.card(current.cardId) ?: return@let
                    val density = LocalDensity.current
                    val widthDp = with(density) { current.cardSize.width.toDp() }
                    Box(
                        Modifier
                            .zIndex(8f)
                            .offset {
                                IntOffset(
                                    (current.pointerRoot.x - current.grabInCard.x - boardOrigin.x).roundToInt(),
                                    (current.pointerRoot.y - current.grabInCard.y - boardOrigin.y).roundToInt(),
                                )
                            }
                            .width(widthDp)
                            .alpha(0.92f),
                    ) {
                        BattleActionCard(
                            ui = ui,
                            card = card,
                            selected = true,
                            played = false,
                            available = true,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                            onSelect = {},
                            onToggleExpanded = {},
                            draggable = false,
                        )
                    }
                }
            }
        }
    }
}

/** Compact monster-card wall; five or more enemies keep the dense fallback sizing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BattleCompactEnemyPanel(
    ui: TextGameUiState,
    encounter: TextGameEncounter,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    hoverEnemyId: String? = null,
    onEnemyBounds: (String, Rect) -> Unit = { _, _ -> },
) {
    Text("GKOM CORRUPTED", color = Color(0xFFFF6F78), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    val enemyCount = encounter.enemies.size
    val cardWidth = when {
        enemyCount <= 2 -> 132.dp
        enemyCount <= 4 -> 96.dp
        else -> 78.dp
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        maxItemsInEachRow = if (enemyCount <= 2) 2 else if (enemyCount <= 4) 4 else 5,
    ) {
        encounter.enemies.forEach { enemy ->
            MonsterCard(
                ui = ui,
                enemy = enemy,
                selected = ui.game.run.selectedTargetId == enemy.id,
                dropHover = hoverEnemyId == enemy.id,
                compact = enemyCount >= 5,
                panel = panel,
                line = line,
                pale = pale,
                secondary = secondary,
                modifier = Modifier.width(cardWidth),
                onClick = { dispatch(TextGameAction.SelectTarget(enemy.id)) },
                onBounds = { rect -> onEnemyBounds(enemy.id, rect) },
            )
        }
    }
}

/** Slim summoner strip: HP / guard / SP + ultimate gauge only (no ally chips). */
@Composable
private fun BattleCompactPartyPanel(
    ui: TextGameUiState,
    panel: Color,
    line: Color,
    @Suppress("UNUSED_PARAMETER") pale: Color,
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
    drag: BattleCardDrag? = null,
    onDragChanged: (BattleCardDrag?) -> Unit = {},
    onDragPlay: (String, Offset) -> Unit = { _, _ -> },
    belowHand: (@Composable () -> Unit)? = null,
) {
    val cards = ui.game.run.hand.mapNotNull(ui.definition::card)
    val selected = ui.game.run.selectedCardId?.let(ui.definition::card)
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val handScroll = rememberScrollState()
    Text(
        when {
            drag != null -> "Dragging — drop on a GKOM to attack."
            selected == null -> "Select or drag a card."
            (selected.damage > 0 || selected.markBonus > 0) && ui.game.run.selectedTargetId == null ->
                "${acronymOf(selected.title)} — pick target or drag onto it."
            else -> "${acronymOf(selected.title)} — ready."
        },
        color = pale,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(handScroll, enabled = drag == null),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        cards.forEach { card ->
            val played = card.id in ui.game.run.playedCards
            val active = ui.game.run.selectedCardId == card.id
            val available = canSelectCard(card) && !played
            val lifted = drag?.cardId == card.id
            BattleActionCard(
                ui, card, active, played, available, compact = true,
                modifier = Modifier
                    .width(104.dp)
                    // Hide the home-slot art entirely while the floating card moves.
                    .alpha(if (lifted) 0f else 1f),
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
                onToggleExpanded = { expandedCardId = if (expandedCardId == card.id) null else card.id },
                onDragChanged = onDragChanged,
                onDragPlay = onDragPlay,
            )
        }
    }
    belowHand?.invoke()
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
                    CollectibleCardFace(
                        card = card,
                        imagePaths = ui.cardImagePaths,
                        motionPaths = ui.cardMotionPaths,
                        preferMotion = false,
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
@OptIn(ExperimentalLayoutApi::class)
private fun BattleEnemyPanel(
    ui: TextGameUiState,
    encounter: TextGameEncounter,
    dispatch: (TextGameAction) -> Unit,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    modifier: Modifier,
    hoverEnemyId: String? = null,
    onEnemyBounds: (String, Rect) -> Unit = { _, _ -> },
) {
    Column(
        modifier.background(panel).border(1.dp, line).padding(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text("GKOM CORRUPTED", color = Color(0xFFFF6F78), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        val count = encounter.enemies.size
        val cardWidth = if (count <= 2) 132.dp else if (count <= 4) 96.dp else 78.dp
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            maxItemsInEachRow = if (count <= 2) 2 else if (count <= 4) 4 else 5,
        ) {
            encounter.enemies.forEach { enemy ->
                MonsterCard(
                    ui = ui,
                    enemy = enemy,
                    selected = ui.game.run.selectedTargetId == enemy.id,
                    dropHover = hoverEnemyId == enemy.id,
                    compact = count >= 5,
                    panel = panel,
                    line = line,
                    pale = pale,
                    secondary = secondary,
                    modifier = Modifier.width(cardWidth),
                    onClick = { dispatch(TextGameAction.SelectTarget(enemy.id)) },
                    onBounds = { rect -> onEnemyBounds(enemy.id, rect) },
                )
            }
        }
    }
}

@Composable
private fun MonsterCard(
    ui: TextGameUiState,
    enemy: TextGameEnemy,
    selected: Boolean,
    dropHover: Boolean,
    compact: Boolean,
    panel: Color,
    line: Color,
    pale: Color,
    secondary: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val enemyState = ui.game.run.enemies.firstOrNull { it.id == enemy.id }
    val health = enemyState?.health ?: 0
    val maxHealth = enemyState?.maxHealth ?: enemy.maxHealth
    val alive = health > 0
    val variant = gkomVariant(ui, enemy.id)
    val portrait = variant?.let { ui.gkomImagePaths[it.id] }
    val borderColor = when {
        dropHover && alive -> Color(0xFFFF6F78)
        selected -> Color(0xFFFFC857)
        else -> Color(0xCCB89A62)
    }
    val tierIcon = when (variant?.tier) {
        GkomTier.Elite -> "elite"
        GkomTier.Boss -> "boss"
        else -> "enemy"
    }
    Box(
        modifier
            .aspectRatio(0.68f)
            .onGloballyPositioned { coords -> onBounds(if (alive) coords.boundsInRoot() else Rect.Zero) }
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = alive, onClick = onClick)
            .background(panel)
            .border(if (selected || dropHover) 3.dp else 2.dp, borderColor, RoundedCornerShape(8.dp))
            .alpha(if (alive) 1f else 0.45f),
    ) {
        if (portrait != null) {
            val deadFilter = remember(alive) {
                if (alive) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            }
            AsyncImage(
                model = textGameImageModel(portrait),
                contentDescription = variant.displayName,
                contentScale = ContentScale.Crop,
                colorFilter = deadFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            "▶ ${enemy.intent} · ~${enemy.intentDamage} dmg",
            color = Color(0xFFFFE39A),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(Color(0xCC21151A)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        AsyncImage(
            model = "file:///android_asset/images/adams_haven/ui/$tierIcon.png",
            contentDescription = variant?.tier?.name ?: "Enemy",
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(if (compact) 18.dp else 24.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE120D10))))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(enemy.name, color = pale, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!compact && variant != null) {
                Text(variant.displayName, color = Color(0xFFFF9D9D), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            LinearProgressIndicator(
                progress = { health.toFloat() / maxHealth.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (alive) Color(0xFFFF6F78) else Color(0xFF5D626B),
                trackColor = Color(0x99343A45),
            )
            Text("HP $health/$maxHealth", color = secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun gkomVariant(ui: TextGameUiState, enemyId: String): AdamsHavenGkomMonster? {
    val kind = ui.game.run.dungeonRoomKind?.let(DungeonKind::fromIndex)
    return pickGkomVariant(enemyId, ui.game.persistent.rngSeed, gkomTierFor(kind))
}

private fun gkomPortraitPath(ui: TextGameUiState, enemyId: String): String? {
    val variant = gkomVariant(ui, enemyId) ?: return null
    return ui.gkomImagePaths[variant.id]
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
 * Adams Haven's tactile battle card. Artwork owns the front; long-press flips
 * rules; dragging plays an attack onto an enemy; double-tap expands.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    draggable: Boolean = true,
    onDragChanged: (BattleCardDrag?) -> Unit = {},
    onDragPlay: (cardId: String, pointerRoot: Offset) -> Unit = { _, _ -> },
) {
    var showingBack by remember(card.id) { mutableStateOf(false) }
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }
    var grabInCard by remember { mutableStateOf(Offset.Zero) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val gold = if (selected) Color(0xFFFFD55F) else Color(0xFF9B6A31)
    val art = ui.definition.roster.firstOrNull { it.id == card.ownerId }
        ?.collectibleCardId
        ?.let(ui.definition::collectible)
    val gestureModifier = if (!draggable) {
        Modifier
    } else {
        Modifier
            .combinedClickable(
                onClick = { if (available && !played) onSelect() },
                onDoubleClick = onToggleExpanded,
                onLongClick = { showingBack = !showingBack },
                onLongClickLabel = "Flip card",
            )
            .pointerInput(card.id, available, played) {
                if (!available || played) return@pointerInput
                detectDragGestures(
                    onDragStart = { startInCard ->
                        showingBack = false
                        dragging = true
                        // Freeze grab so only the floating ghost follows the finger;
                        // the hand slot stays where it was.
                        grabInCard = startInCard
                        dragPointerRoot = cardOrigin + startInCard
                        onSelect()
                        onDragChanged(
                            BattleCardDrag(
                                cardId = card.id,
                                pointerRoot = dragPointerRoot,
                                grabInCard = startInCard,
                                cardSize = cardSize,
                            ),
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragPointerRoot += dragAmount
                        onDragChanged(
                            BattleCardDrag(
                                cardId = card.id,
                                pointerRoot = dragPointerRoot,
                                grabInCard = grabInCard,
                                cardSize = cardSize,
                            ),
                        )
                    },
                    onDragEnd = {
                        val dropAt = dragPointerRoot
                        dragging = false
                        onDragChanged(null)
                        onDragPlay(card.id, dropAt)
                    },
                    onDragCancel = {
                        dragging = false
                        onDragChanged(null)
                    },
                )
            }
    }
    Box(
        modifier
            .aspectRatio(.68f)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                cardSize = Size(bounds.width, bounds.height)
                // Keep the home-slot origin frozen for the whole drag so scroll
                // jitter cannot yank the stationary card.
                if (!dragging) {
                    cardOrigin = bounds.topLeft
                }
            }
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
                CollectibleCardFace(
                    card = art,
                    imagePaths = ui.cardImagePaths,
                    motionPaths = ui.cardMotionPaths,
                    preferMotion = selected || !compact,
                    contentDescription = card.title,
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
            Text("Double-tap to shrink · Long-press to flip · Drag onto an enemy to play", color = Color.White, style = MaterialTheme.typography.labelMedium)
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
    drag: BattleCardDrag? = null,
    onDragChanged: (BattleCardDrag?) -> Unit = {},
    onDragPlay: (String, Offset) -> Unit = { _, _ -> },
) {
    val cards = ui.game.run.hand.mapNotNull(ui.definition::card)
    val selected = ui.game.run.selectedCardId?.let(ui.definition::card)
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val handScroll = rememberScrollState()
    Text(
        when {
            drag != null -> "Dragging — drop on a living enemy to commit the attack."
            selected == null -> "Select or drag a card."
            (selected.damage > 0 || selected.markBonus > 0) && ui.game.run.selectedTargetId == null ->
                "${selected.title} — choose a target, or drag onto one."
            else -> "${selected.title} — ready to commit."
        },
        color = pale,
        style = MaterialTheme.typography.labelMedium,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(handScroll, enabled = drag == null),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        cards.forEach { card ->
            val played = card.id in ui.game.run.playedCards
            val active = ui.game.run.selectedCardId == card.id
            val available = canSelectCard(card) && !played
            val lifted = drag?.cardId == card.id
            BattleActionCard(
                ui, card, active, played, available, compact = false,
                modifier = Modifier
                    .width(154.dp)
                    .alpha(if (lifted) 0f else 1f),
                onSelect = { dispatch(TextGameAction.SelectCard(card.id)) },
                onToggleExpanded = { expandedCardId = if (expandedCardId == card.id) null else card.id },
                onDragChanged = onDragChanged,
                onDragPlay = onDragPlay,
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
                    CollectibleCardFace(
                        card = card,
                        imagePaths = ui.cardImagePaths,
                        motionPaths = ui.cardMotionPaths,
                        preferMotion = true,
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
    motionPaths: Map<String, String> = emptyMap(),
    defeatedMonsterIds: Set<String> = emptySet(),
    gkomImagePaths: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf("all") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMonsterId by rememberSaveable { mutableStateOf<String?>(null) }
    val cards = definition.collectibleCards.filter { category == "all" || it.category == category }
    Column(Modifier.fillMaxSize().padding(InkSpacing.md), verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CARD LIBRARY", style = MaterialTheme.typography.labelSmall, color = inkTokens().activePill)
                Text("Adams Haven V2 · ${definition.collectibleCards.size} cards · ${adamsHavenGkomMonsters().size} monsters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            listOf("all" to "All", "characters" to "Characters", "locations" to "Locations", "objects" to "Objects", "monsters" to "Monsters").forEach { (id, label) ->
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label) })
            }
        }
        if (category == "monsters") {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                items(adamsHavenGkomMonsters(), key = { it.id }) { monster ->
                    val revealed = monster.id in defeatedMonsterIds
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedMonsterId = monster.id },
                        colors = CardDefaults.cardColors(containerColor = inkTokens().panel),
                    ) {
                        AsyncImage(
                            model = gkomImagePaths[monster.id]?.let(::File) ?: "file:///android_asset/${monster.artAssetPath}",
                            contentDescription = if (revealed) monster.displayName else "Unknown monster",
                            contentScale = ContentScale.Crop,
                            colorFilter = if (revealed) null else ColorFilter.tint(Color.Black),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
                        )
                        Column(Modifier.padding(InkSpacing.xs)) {
                            Text(if (revealed) monster.displayName else "Unknown GKOM", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(monster.tier.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (revealed) inkTokens().activePill else inkTokens().secondaryText)
                        }
                    }
                }
            }
        } else {
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
                            CollectibleCardFace(
                                card = card,
                                imagePaths = imagePaths,
                                motionPaths = motionPaths,
                                preferMotion = false,
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
    }
    selectedId?.let { id ->
        definition.collectible(id)?.let { card ->
            Dialog(onDismissRequest = { selectedId = null }) {
                Card(colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
                    Column(Modifier.padding(InkSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                        CollectibleCardFace(
                            card = card,
                            imagePaths = imagePaths,
                            motionPaths = motionPaths,
                            preferMotion = true,
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
    selectedMonsterId?.let { id ->
        adamsHavenGkomMonsters().firstOrNull { it.id == id }?.let { monster ->
            val revealed = monster.id in defeatedMonsterIds
            val authoredIds = authoredEnemyIdsForGkom(monster.id)
            val enemy = definition.encounters.asSequence().flatMap { it.enemies.asSequence() }
                .firstOrNull { it.id in authoredIds }
            Dialog(onDismissRequest = { selectedMonsterId = null }) {
                Card(colors = CardDefaults.cardColors(containerColor = inkTokens().panel)) {
                    Column(Modifier.padding(InkSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = gkomImagePaths[monster.id]?.let(::File) ?: "file:///android_asset/${monster.artAssetPath}",
                            contentDescription = if (revealed) monster.displayName else "Unknown monster",
                            contentScale = ContentScale.Fit,
                            colorFilter = if (revealed) null else ColorFilter.tint(Color.Black),
                            modifier = Modifier.fillMaxWidth().height(480.dp),
                        )
                        Text(if (revealed) monster.displayName else "Unknown GKOM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(monster.tier.name.uppercase(), color = inkTokens().secondaryText)
                        if (revealed && enemy != null) {
                            Text("${enemy.name} · HP ${enemy.maxHealth} · ${enemy.intent}", color = inkTokens().secondaryText)
                        }
                        Button(onClick = { selectedMonsterId = null }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                    }
                }
            }
        }
    }
}

/**
 * Card face: looping motion MP4 when [preferMotion] and a motion path exist,
 * otherwise the still PNG (asset or installed library file).
 */
@Composable
private fun CollectibleCardFace(
    card: TextGameCollectibleCard,
    imagePaths: Map<String, String>,
    motionPaths: Map<String, String>,
    preferMotion: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = card.title,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val motion = motionPaths[card.id]?.takeIf { preferMotion && File(it).isFile }
    if (motion != null) {
        Box(modifier) {
            LoopingVideoBackground(
                path = motion,
                modifier = Modifier.fillMaxSize(),
                fitInside = contentScale == ContentScale.Fit,
            )
        }
    } else {
        AsyncImage(
            model = cardImageModel(card, imagePaths),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private fun cardImageModel(card: TextGameCollectibleCard, imagePaths: Map<String, String>): Any =
    imagePaths[card.id]?.let(::File) ?: "file:///android_asset/${card.artAssetPath}"

private fun textGameImageModel(path: String): Any =
    if (path.startsWith("file:") || path.startsWith("content:") || path.startsWith("asset:")) path else File(path)

private sealed class FarmMinigameRequest {
    abstract val plotId: Int
    abstract val label: String
    data class Plant(override val plotId: Int) : FarmMinigameRequest() {
        override val label: String get() = "Planting"
    }
    data class Harvest(override val plotId: Int) : FarmMinigameRequest() {
        override val label: String get() = "Harvest"
    }
}

/** Godot Farm.tscn plot strip + kitchen pantry — crops grow by battles fought. */
@Composable
private fun FarmBoard(
    ui: TextGameUiState,
    dispatch: (TextGameAction) -> Unit,
    onMinigame: (FarmMinigameRequest) -> Unit,
) {
    val tokens = inkTokens()
    val persistent = ui.game.persistent
    val cleared = "farm_cleared" in persistent.flags || persistent.farm.plots.isNotEmpty()
    val capacity = FarmRules.plotCapacity(persistent.farmLevel, cleared)
    val farm = remember(persistent.farm, persistent.battlesWon, capacity) {
        FarmRules.refreshReady(
            FarmRules.syncBattlesFought(
                FarmRules.ensureCapacity(persistent.farm, capacity),
                persistent.battlesWon,
            ),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C2418))
            .border(1.dp, Color(0xFF6A8F4E), RoundedCornerShape(12.dp))
            .padding(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text("THE CLEARING", color = Color(0xFFB7E08A), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            "Crops grow by battles fought, not real time.",
            color = tokens.secondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(FarmRules.summaryLine(farm), color = Color(0xFFD7E6C8), style = MaterialTheme.typography.labelSmall)
        Text("Seeds ${persistent.seeds} · Produce ${persistent.harvest}", color = tokens.secondaryText, style = MaterialTheme.typography.labelSmall)

        if (farm.plots.isEmpty()) {
            Text(
                "Clear a plot to open the first bed.",
                color = tokens.secondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                farm.plots.forEach { plot ->
                    FarmPlotCard(
                        farm = farm,
                        plot = plot,
                        seeds = persistent.seeds,
                        onTill = { dispatch(TextGameAction.FarmTill(plot.id)) },
                        onPlant = { onMinigame(FarmMinigameRequest.Plant(plot.id)) },
                        onWater = { dispatch(TextGameAction.FarmWater(plot.id)) },
                        onHarvest = { onMinigame(FarmMinigameRequest.Harvest(plot.id)) },
                    )
                }
            }
        }

        if (farm.pantry.isNotEmpty() || farm.packedDish != null) {
            Text("PANTRY", color = Color(0xFFFFD86A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            farm.packedDish?.let {
                Text("Packed for next run: $it", color = Color(0xFF9FE6A0), style = MaterialTheme.typography.labelSmall)
            }
            farm.pantry.forEach { (dish, count) ->
                val def = FarmRules.DISHES[dish]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$dish ×$count" + (def?.let { " · ${it.status}" } ?: ""),
                        color = Color(0xFFF1E5D1),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { dispatch(TextGameAction.FarmPackDish(dish)) }) {
                        Text("Pack", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmPlotCard(
    farm: FarmState,
    plot: FarmPlot,
    seeds: Int,
    onTill: () -> Unit,
    onPlant: () -> Unit,
    onWater: () -> Unit,
    onHarvest: () -> Unit,
) {
    val fill = when (plot.soil) {
        FarmSoil.Wild -> Color(0x8C332B21)
        FarmSoil.Tilled -> Color(0xCC755230)
        FarmSoil.Planted -> Color(0xD9386633)
        FarmSoil.Ready -> Color(0xEBE0B83D)
    }
    val rim = when (plot.soil) {
        FarmSoil.Wild -> Color(0xFF5C4F3D)
        FarmSoil.Tilled -> Color(0xFFA87A4A)
        FarmSoil.Planted -> Color(0xFF70B861)
        FarmSoil.Ready -> Color(0xFFFFE06B)
    }
    val crop = FarmRules.crop(plot.cropId)
    Column(
        Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .border(2.dp, rim, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Plot ${plot.id + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (crop != null && (plot.soil == FarmSoil.Planted || plot.soil == FarmSoil.Ready)) {
            val elapsed = (farm.battlesFought - plot.plantedAtBattles).coerceAtLeast(0)
            val needed = FarmRules.battlesNeeded(plot).coerceAtLeast(1)
            val stage = if (plot.soil == FarmSoil.Ready) 3 else ((elapsed * 3) / needed).coerceIn(0, 2)
            AsyncImage(
                model = "file:///android_asset/images/adams_haven/haven/crop/${crop.id}_stage_$stage.webp",
                contentDescription = "${crop.name} growth stage ${stage + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
        Text(
            when (plot.soil) {
                FarmSoil.Wild -> "Wild ground"
                FarmSoil.Tilled -> "Tilled"
                FarmSoil.Planted -> "${crop?.name ?: "Crop"} · ${FarmRules.battlesRemaining(farm, plot)} left" +
                    if (plot.watered) " · watered" else ""
                FarmSoil.Ready -> "${crop?.name ?: "Crop"} READY"
            },
            color = Color(0xFFF4EBDD),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3,
        )
        when (plot.soil) {
            FarmSoil.Wild -> TextButton(onClick = onTill, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Till", style = MaterialTheme.typography.labelSmall)
            }
            FarmSoil.Tilled -> TextButton(
                onClick = onPlant,
                enabled = seeds > 0,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Plant", style = MaterialTheme.typography.labelSmall)
            }
            FarmSoil.Planted -> if (!plot.watered) {
                TextButton(onClick = onWater, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Water", style = MaterialTheme.typography.labelSmall)
                }
            }
            FarmSoil.Ready -> TextButton(onClick = onHarvest, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Harvest", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Godot Minigame.gd — bouncing marker, tap the sweet band, ESC/Skip = score 0.
 */
@Composable
private fun FarmTimingMinigame(
    label: String,
    onSkip: () -> Unit,
    onStrike: (Float) -> Unit,
) {
    var marker by remember { mutableStateOf(0f) }
    var dir by remember { mutableStateOf(1f) }
    val speed = 1.35f
    val sweetCentre = remember { 0.3f + kotlin.random.Random.nextFloat() * 0.4f }
    val sweetWidth = 0.16f

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { frame ->
                if (last != 0L) {
                    val dt = ((frame - last) / 1000f).coerceIn(0f, 0.05f)
                    var next = marker + dir * speed * dt
                    var nextDir = dir
                    if (next >= 1f) {
                        next = 1f
                        nextDir = -1f
                    } else if (next <= 0f) {
                        next = 0f
                        nextDir = 1f
                    }
                    marker = next
                    dir = nextDir
                }
                last = frame
            }
        }
    }

    fun scoreAt(pos: Float): Float {
        val dist = kotlin.math.abs(pos - sweetCentre)
        val half = sweetWidth * 0.5f
        return when {
            dist <= half -> (1f - (dist / half) * 0.35f).coerceIn(0f, 1f)
            else -> (0.45f - (dist - half)).coerceAtLeast(0f)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xEE12141A))
            .border(1.dp, Color(0xFF6A8F4E), RoundedCornerShape(12.dp))
            .padding(InkSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            Text("$label — tap in the band", color = Color(0xFFB7E08A), fontWeight = FontWeight.Bold)
            Text("Tap to strike · Skip for base roll only", color = Color(0xFF9AA3B2), style = MaterialTheme.typography.labelSmall)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onStrike(scoreAt(marker)) },
            ) {
                drawRect(Color(0xFF24272F))
                val sx = (sweetCentre - sweetWidth * 0.5f) * size.width
                drawRect(Color(0xD94CBF73), topLeft = Offset(sx, 0f), size = Size(sweetWidth * size.width, size.height))
                drawRect(
                    Color(0xFFB3FFCC),
                    topLeft = Offset(sweetCentre * size.width - 2f, 0f),
                    size = Size(4f, size.height),
                )
                drawRect(
                    Color(0xFFFFE66B),
                    topLeft = Offset(marker * size.width - 3f, -4f),
                    size = Size(6f, size.height + 8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Button(onClick = { onStrike(scoreAt(marker)) }, modifier = Modifier.weight(1f)) {
                    Text("Strike")
                }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
            }
        }
    }
}
