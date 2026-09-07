package com.ihy2ln.weaverse.feature.roleplay.rpg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.ExpandableSection
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun RpgAdventureScreen(
    campaignId: String,
    viewModel: RpgAdventureViewModel = hiltViewModel(),
) {
    LaunchedEffect(campaignId) { viewModel.bind(campaignId) }
    val ui by viewModel.uiState.collectAsState()
    RpgAdventurePane(
        ui = ui,
        onSummonerName = viewModel::onSummonerName,
        onBegin = viewModel::beginAdventure,
        onSelectProposal = viewModel::selectProposal,
        onCustomDraft = viewModel::onCustomDraft,
        onSubmitCustom = viewModel::submitCustomAction,
        onConfirm = viewModel::confirmPreview,
        onResolve = viewModel::resolveConfirmed,
        onDismissPreview = viewModel::dismissPreview,
        onRetryChoices = { viewModel.refreshChoices(forceAi = true) },
        onToggleDetails = viewModel::toggleDetails,
        onPlayCard = viewModel::playCard,
        onEndRound = viewModel::endRound,
        onRetryCombat = viewModel::retryCombat,
        onCollectSpoils = viewModel::collectSpoils,
        onCraft = viewModel::craftSalve,
        onFinish = viewModel::finishChapter,
    )
}

@Composable
fun RpgAdventurePane(
    ui: RpgAdventureUiState,
    onSummonerName: (String) -> Unit = {},
    onBegin: () -> Unit = {},
    onSelectProposal: (String) -> Unit = {},
    onCustomDraft: (String) -> Unit = {},
    onSubmitCustom: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onResolve: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onRetryChoices: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
    onPlayCard: (String) -> Unit = {},
    onEndRound: () -> Unit = {},
    onRetryCombat: () -> Unit = {},
    onCollectSpoils: () -> Unit = {},
    onCraft: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    val tokens = inkTokens()
    if (ui.loading) {
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = RpgUiLabels.Loading },
            contentAlignment = Alignment.Center,
        ) {
            Text(RpgUiLabels.Loading)
        }
        return
    }
    Box(Modifier.fillMaxSize().background(tokens.background).testTag("rpg-root")) {
        Column(Modifier.fillMaxSize()) {
            RpgStatusStrip(ui)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.game.scene.phase) {
                    RpgScenePhase.Onboarding -> RpgOnboardingPane(
                        ui = ui,
                        onName = onSummonerName,
                        onBegin = onBegin,
                    )
                    RpgScenePhase.Exploration -> RpgExplorationPane(
                        ui = ui,
                        onSelect = onSelectProposal,
                        onCustomDraft = onCustomDraft,
                        onSubmitCustom = onSubmitCustom,
                        onRetry = onRetryChoices,
                        onToggleDetails = onToggleDetails,
                    )
                    RpgScenePhase.Combat -> RpgCombatPane(
                        ui = ui,
                        onPlayCard = onPlayCard,
                        onEndRound = onEndRound,
                        onRetry = onRetryCombat,
                        onCollect = onCollectSpoils,
                    )
                    RpgScenePhase.Aftermath -> RpgAftermathPane(
                        ui = ui,
                        onCollect = onCollectSpoils,
                        onCraft = onCraft,
                        onFinish = onFinish,
                    )
                    RpgScenePhase.Recap -> RpgRecapPane(ui = ui)
                }
            }
        }
        if (ui.previewOpen && ui.game.pendingPreview != null) {
            RpgPreviewSheet(
                game = ui.game,
                onDismiss = onDismissPreview,
                onConfirm = onConfirm,
                onResolve = onResolve,
            )
        }
    }
}

@Composable
private fun RpgStatusStrip(ui: RpgAdventureUiState) {
    val tokens = inkTokens()
    val game = ui.game
    Column(
        Modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
    ) {
        Text(
            ui.campaignTitle.ifBlank { FirstLightChapter.TITLE },
            style = MaterialTheme.typography.labelMedium,
            color = tokens.secondaryText,
        )
        Text(
            game.scene.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            game.scene.objective,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText,
            modifier = Modifier.semantics { contentDescription = RpgUiLabels.Objective },
        )
        Text(
            game.scene.nextGuidance,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.activePill,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = InkSpacing.xs)
                .semantics { contentDescription = RpgUiLabels.NextGuidance },
        )
        if (ui.offline) {
            Text(RpgUiLabels.Offline, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
        }
        game.lastError?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        ui.saveError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RpgOnboardingPane(
    ui: RpgAdventureUiState,
    onName: (String) -> Unit,
    onBegin: () -> Unit,
) {
    val summoner = ui.game.party.firstOrNull { it.isSummoner }
    val mira = ui.game.party.firstOrNull { it.id == FirstLightChapter.MIRA_ID }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.lg)
            .testTag("rpg-onboarding"),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Text(ui.game.scene.introduction, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = summoner?.name.orEmpty(),
            onValueChange = onName,
            label = { Text(RpgUiLabels.SummonerName) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = RpgUiLabels.SummonerName }
                .testTag("rpg-summoner-name"),
        )
        InkCard(modifier = Modifier.fillMaxWidth().semantics { contentDescription = RpgUiLabels.PartyReady }) {
            Text("Party", fontWeight = FontWeight.Bold)
            Text("${summoner?.name ?: "Summoner"} · Summoner · ${summoner?.hp}/${summoner?.maxHp} HP")
            Text("${mira?.name ?: "Mira"} · Ranger companion · ${mira?.hp}/${mira?.maxHp} HP")
        }
        InkFilledButton(
            label = RpgUiLabels.Begin,
            onClick = onBegin,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InkSpacing.touchTarget)
                .semantics { contentDescription = RpgUiLabels.Begin }
                .testTag("rpg-begin"),
        )
    }
}

@Composable
private fun RpgExplorationPane(
    ui: RpgAdventureUiState,
    onSelect: (String) -> Unit,
    onCustomDraft: (String) -> Unit,
    onSubmitCustom: () -> Unit,
    onRetry: () -> Unit,
    onToggleDetails: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(ui.game.scene.introduction, style = MaterialTheme.typography.bodyLarge)
        if (ui.proposing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Loading choices" },
            )
        }
        val generated = ui.game.scene.proposals.filterNot { it.isCustom }
        generated.forEach { proposal ->
            RpgActionCard(proposal = proposal, onClick = { onSelect(proposal.id) })
        }
        ui.game.scene.proposals.firstOrNull { it.isCustom }?.let { custom ->
            InkCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rpg-custom-card")
                    .semantics { contentDescription = RpgUiLabels.WriteOwn },
            ) {
                Text(custom.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(custom.summary, color = tokens.secondaryText)
                OutlinedTextField(
                    value = ui.customDraft,
                    onValueChange = onCustomDraft,
                    label = { Text("Your action") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm)
                        .semantics { contentDescription = RpgUiLabels.CustomComposer }
                        .testTag("rpg-custom-input"),
                )
                InkFilledButton(
                    label = "Preview custom action",
                    onClick = onSubmitCustom,
                    modifier = Modifier
                        .padding(top = InkSpacing.sm)
                        .heightIn(min = InkSpacing.touchTarget)
                        .testTag("rpg-custom-submit"),
                )
            }
        }
        if (ui.game.lastError != null) {
            InkOutlinedButton(
                label = RpgUiLabels.Retry,
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = InkSpacing.touchTarget)
                    .semantics { contentDescription = RpgUiLabels.Retry }
                    .testTag("rpg-retry"),
            )
        }
        RpgCampaignDetails(ui = ui, onToggle = onToggleDetails)
    }
}

@Composable
private fun RpgActionCard(proposal: RpgActionProposal, onClick: () -> Unit) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd()))
            .clickable(onClickLabel = proposal.title, onClick = onClick)
            .padding(InkSpacing.lg)
            .testTag("rpg-choice-${proposal.id}")
            .semantics { contentDescription = proposal.title },
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text(proposal.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(proposal.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            "${proposal.skill} · ${proposal.ability} DC ${proposal.checkDc} · ${proposal.riskTier.name} risk",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.activePill,
        )
    }
}

@Composable
private fun RpgPreviewSheet(
    game: RpgCampaignState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onResolve: () -> Unit,
) {
    val preview = game.pendingPreview ?: return
    val tokens = inkTokens()
    Box(
        Modifier
            .fillMaxSize()
            .background(tokens.background.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)
            .testTag("rpg-preview-scrim"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
                .semantics { contentDescription = RpgUiLabels.Preview }
                .testTag("rpg-preview-sheet"),
            color = tokens.panel,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier
                    .padding(InkSpacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                Text(preview.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Intent: ${preview.intent}")
                Text("Check: ${preview.checkLabel} DC ${preview.checkDc}")
                Text("Risk: ${preview.riskTier.name}")
                preview.likelyConsequences.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    InkOutlinedButton(
                        label = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = InkSpacing.touchTarget),
                    )
                    if (game.confirmedProposalId == null) {
                        InkFilledButton(
                            label = RpgUiLabels.Confirm,
                            onClick = onConfirm,
                            modifier = Modifier
                                .heightIn(min = InkSpacing.touchTarget)
                                .semantics { contentDescription = RpgUiLabels.Confirm }
                                .testTag("rpg-confirm"),
                        )
                    } else {
                        InkFilledButton(
                            label = RpgUiLabels.Resolve,
                            onClick = onResolve,
                            modifier = Modifier
                                .heightIn(min = InkSpacing.touchTarget)
                                .semantics { contentDescription = RpgUiLabels.Resolve }
                                .testTag("rpg-resolve"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RpgCombatPane(
    ui: RpgAdventureUiState,
    onPlayCard: (String) -> Unit,
    onEndRound: () -> Unit,
    onRetry: () -> Unit,
    onCollect: () -> Unit = {},
) {
    val combat = ui.game.combat ?: return
    val tokens = inkTokens()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("rpg-combat")) {
        val landscape = maxWidth >= 640.dp
        val content = @Composable {
            Column(
                Modifier
                    .then(if (landscape) Modifier.widthIn(min = 280.dp).fillMaxHeight() else Modifier.fillMaxWidth())
                    .verticalScroll(rememberScrollState())
                    .padding(InkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                Text(ui.game.scene.introduction, style = MaterialTheme.typography.bodyMedium)
                combat.enemies.forEach { enemy ->
                    InkCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = RpgUiLabels.EnemyIntent },
                    ) {
                        Text("${enemy.name}  ${enemy.hp}/${enemy.maxHp} HP", fontWeight = FontWeight.Bold)
                        Text(
                            "Intent: ${enemy.intent.title} — ${enemy.intent.description}",
                            color = tokens.activePill,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    combat.party.forEach { actor ->
                        Column(
                            Modifier
                                .weight(1f)
                                .background(tokens.hover, RoundedCornerShape(inkRadiusMd()))
                                .padding(InkSpacing.sm)
                                .semantics { contentDescription = "${actor.name} AP ${actor.ap} EP ${actor.ep}" },
                        ) {
                            Text(actor.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("HP ${actor.hp}/${actor.maxHp}")
                            Text("AP ${actor.ap}/${actor.maxAp}  EP ${actor.ep}/${actor.maxEp}")
                            if (actor.isSummoner) Text("SP ${combat.summonerSp}/${combat.maxSummonerSp}")
                        }
                    }
                }
                Text(RpgUiLabels.CombatHand, fontWeight = FontWeight.Bold)
                val cards = if (landscape) {
                    combat.hand.chunked(2)
                } else {
                    combat.hand.map { listOf(it) }
                }
                cards.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    ) {
                        row.forEach { card ->
                            val owner = combat.party.firstOrNull { it.id == card.ownerId }
                            val enabled = combat.result == RpgCombatResult.Ongoing &&
                                owner != null &&
                                owner.ap >= card.apCost &&
                                owner.ep >= card.epCost &&
                                combat.summonerSp >= card.spCost
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 96.dp)
                                    .clip(RoundedCornerShape(inkRadiusMd()))
                                    .background(if (enabled) tokens.panel else tokens.hover)
                                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd()))
                                    .clickable(enabled = enabled, onClickLabel = card.title) { onPlayCard(card.id) }
                                    .padding(InkSpacing.md)
                                    .testTag("rpg-card-${card.id}")
                                    .semantics { contentDescription = "${RpgUiLabels.PlayCard}: ${card.title}" },
                            ) {
                                Text(card.title, fontWeight = FontWeight.Bold)
                                Text(card.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Text(
                                    "AP ${card.apCost}  EP ${card.epCost}" +
                                        if (card.spCost > 0) "  SP ${card.spCost}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText,
                                )
                            }
                        }
                    }
                }
                when (combat.result) {
                    RpgCombatResult.Ongoing -> InkOutlinedButton(
                        label = RpgUiLabels.EndRound,
                        onClick = onEndRound,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.touchTarget)
                            .semantics { contentDescription = RpgUiLabels.EndRound }
                            .testTag("rpg-end-round"),
                    )
                    RpgCombatResult.Victory -> InkFilledButton(
                        label = RpgUiLabels.CollectSpoils,
                        onClick = onCollect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.touchTarget)
                            .semantics { contentDescription = RpgUiLabels.CollectSpoils }
                            .testTag("rpg-collect"),
                    )
                    RpgCombatResult.Defeat -> InkFilledButton(
                        label = "Retry ambush",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = InkSpacing.touchTarget),
                    )
                }
                combat.log.takeLast(4).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            content()
        }
    }
}

@Composable
private fun RpgAftermathPane(
    ui: RpgAdventureUiState,
    onCollect: () -> Unit,
    onCraft: () -> Unit,
    onFinish: () -> Unit,
) {
    val game = ui.game
    val salveReady = game.crafting.recipes.any { it.id == FirstLightChapter.SALVE_RECIPE_ID && it.unlocked } &&
        (game.crafting.materials[FirstLightChapter.MOONROOT_ID] ?: 0) > 0 &&
        FirstLightChapter.SALVE_ITEM !in game.crafting.preparedItems
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.lg)
            .testTag("rpg-aftermath"),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Text(game.scene.introduction, style = MaterialTheme.typography.bodyLarge)
        RpgCampaignDetails(ui = ui, expanded = true, onToggle = {})
        if (game.scene.id != FirstLightChapter.SCENE_AFTERMATH) {
            InkFilledButton(
                label = RpgUiLabels.CollectSpoils,
                onClick = onCollect,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = InkSpacing.touchTarget)
                    .testTag("rpg-collect"),
            )
        }
        if (salveReady) {
            InkOutlinedButton(
                label = RpgUiLabels.CraftSalve,
                onClick = onCraft,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = InkSpacing.touchTarget)
                    .semantics { contentDescription = RpgUiLabels.CraftSalve }
                    .testTag("rpg-craft"),
            )
        }
        InkFilledButton(
            label = RpgUiLabels.ContinueRecap,
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InkSpacing.touchTarget)
                .semantics { contentDescription = RpgUiLabels.ContinueRecap }
                .testTag("rpg-finish"),
        )
    }
}

@Composable
private fun RpgRecapPane(ui: RpgAdventureUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.lg)
            .testTag("rpg-recap")
            .semantics { contentDescription = RpgUiLabels.Recap },
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text("Chapter recap", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(ui.game.scene.introduction)
        ui.game.progress.recapNotes.forEach { Text("· $it") }
        Text(
            "Next: ${ui.game.progress.nextObjective}",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = RpgUiLabels.Objective },
        )
        RpgCampaignDetails(ui = ui, expanded = true, onToggle = {})
    }
}

@Composable
private fun RpgCampaignDetails(
    ui: RpgAdventureUiState,
    expanded: Boolean = ui.detailsExpanded,
    onToggle: () -> Unit,
) {
    val game = ui.game
    ExpandableSection(
        title = "Campaign status",
        expanded = expanded,
        onToggle = onToggle,
        subtitle = "Party, conditions, consequences, bonds, crafting, factions",
    ) {
        StatusBlock(RpgUiLabels.PartyReady, game.party.joinToString { "${it.name} ${it.hp}/${it.maxHp} HP" })
        StatusBlock(
            RpgUiLabels.Conditions,
            (game.conditions.ifEmpty { listOf("None") } +
                "Moonroot ${game.crafting.materials[FirstLightChapter.MOONROOT_ID] ?: 0}").joinToString(" · "),
        )
        StatusBlock(
            RpgUiLabels.Consequences,
            game.recentConsequences.firstOrNull() ?: "No consequences yet.",
        )
        StatusBlock(
            RpgUiLabels.Relationships,
            game.companions.joinToString { "${it.name} bond ${it.level}" },
        )
        StatusBlock(
            RpgUiLabels.Crafting,
            buildString {
                append(game.crafting.materials.entries.joinToString { "${it.key}×${it.value}" }.ifBlank { "No materials" })
                if (game.crafting.preparedItems.isNotEmpty()) {
                    append(" · Prepared: ${game.crafting.preparedItems.joinToString()}")
                }
            },
        )
        StatusBlock(
            RpgUiLabels.Factions,
            game.factions.joinToString { "${it.name} ${it.reputation}" },
        )
    }
}

@Composable
private fun StatusBlock(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = InkSpacing.sm)
            .semantics { contentDescription = label },
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
