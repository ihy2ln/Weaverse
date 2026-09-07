package com.ihy2ln.weaverse.feature.roleplay.rpg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RpgAdventureViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
) : ViewModel() {
    private val reducer = RpgReducer()
    private val _uiState = MutableStateFlow(RpgAdventureUiState())
    val uiState: StateFlow<RpgAdventureUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null

    fun bind(campaignId: String) {
        if (_uiState.value.campaignId == campaignId && !_uiState.value.loading) return
        viewModelScope.launch {
            val campaign = db.roleplayDao().getChat(campaignId)
            val save = db.rpgCampaignSaveDao().get(campaignId)
            val restored = save?.let { RpgCampaignCodec.decode(it.stateJson) }
            val seed = freshSeed(campaignId)
            val game = restored ?: FirstLightChapter.newCampaign(seed)
            _uiState.value = RpgAdventureUiState(
                campaignId = campaignId,
                campaignTitle = campaign?.title.orEmpty().ifBlank { "Campaign" },
                game = game,
                loading = false,
            )
        }
    }

    fun onSummonerName(value: String) {
        val current = _uiState.value
        val party = current.game.party.map {
            if (it.isSummoner) it.copy(name = value) else it
        }
        _uiState.value = current.copy(game = current.game.copy(party = party))
    }

    fun beginAdventure() {
        val current = _uiState.value
        val name = current.game.party.firstOrNull { it.isSummoner }?.name.orEmpty()
        apply(reducer.completeOnboarding(current.game, name))
    }

    fun refreshChoices(forceAi: Boolean = false) {
        val current = _uiState.value
        if (current.game.scene.phase != RpgScenePhase.Exploration) return
        viewModelScope.launch {
            _uiState.value = current.copy(proposing = true, offline = false)
            val parsed = if (forceAi || aiGeneration.hasApiKey()) {
                requestAiProposals(current.game)
            } else {
                _uiState.value = _uiState.value.copy(offline = true)
                RpgProposalParseResult.Success(FirstLightChapter.forestProposals())
            }
            val result = reducer.proposeActions(_uiState.value.game, parsed)
            apply(result, proposing = false)
        }
    }

    fun selectProposal(proposalId: String) {
        val current = _uiState.value
        val result = reducer.previewAction(current.game, proposalId)
        apply(result, previewOpen = result.accepted)
    }

    fun onCustomDraft(value: String) {
        _uiState.value = _uiState.value.copy(customDraft = value)
    }

    fun submitCustomAction() {
        val current = _uiState.value
        val result = reducer.writeCustomAction(current.game, current.customDraft)
        apply(result, previewOpen = result.accepted)
    }

    fun confirmPreview() {
        val current = _uiState.value
        val id = current.game.pendingPreview?.proposalId ?: return
        apply(reducer.confirmAction(current.game, id), previewOpen = true)
    }

    fun resolveConfirmed() {
        val current = _uiState.value
        apply(reducer.resolveAction(current.game), previewOpen = false)
    }

    fun playCard(cardId: String) {
        val combat = _uiState.value.game.combat ?: return
        val target = combat.selectedTargetId ?: combat.enemies.firstOrNull { it.hp > 0 }?.id
        apply(reducer.playCombatCard(_uiState.value.game, cardId, target))
    }

    fun endRound() {
        apply(reducer.endCombatRound(_uiState.value.game))
    }

    fun retryCombat() {
        apply(reducer.retryCombat(_uiState.value.game))
    }

    fun collectSpoils() {
        apply(reducer.collectAftermath(_uiState.value.game))
    }

    fun craftSalve() {
        apply(reducer.craftRecipe(_uiState.value.game, FirstLightChapter.SALVE_RECIPE_ID))
    }

    fun finishChapter() {
        apply(reducer.finishChapter(_uiState.value.game))
    }

    fun toggleDetails() {
        _uiState.value = _uiState.value.copy(detailsExpanded = !_uiState.value.detailsExpanded)
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(previewOpen = false)
    }

    private fun apply(
        result: RpgReduction,
        previewOpen: Boolean = _uiState.value.previewOpen,
        proposing: Boolean = false,
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            game = result.state,
            proposing = proposing,
            previewOpen = previewOpen,
            saveError = null,
        )
        persist(current.campaignId, result.state)
    }

    private fun persist(campaignId: String, state: RpgCampaignState) {
        if (campaignId.isBlank()) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            runCatching {
                db.rpgCampaignSaveDao().upsert(
                    RpgCampaignSaveEntity(
                        campaignId = campaignId,
                        schemaVersion = state.schemaVersion,
                        stateJson = RpgCampaignCodec.encode(state),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(saveError = "Progress could not be saved.")
            }
        }
    }

    private suspend fun requestAiProposals(game: RpgCampaignState): RpgProposalParseResult {
        return try {
            val result = aiGeneration.complete(
                userMessage = narratorPrompt(game),
                maxTokens = 700,
                temperature = 0.6,
            )
            RpgProposalParser.parse(result.text)
        } catch (error: AIError.NoApiKey) {
            _uiState.value = _uiState.value.copy(offline = true)
            RpgProposalParseResult.Success(FirstLightChapter.forestProposals())
        } catch (_: Throwable) {
            RpgProposalParseResult.Failure()
        }
    }

    private fun narratorPrompt(game: RpgCampaignState): String = buildString {
        appendLine("You are a structured co-narrator for WeaverVerse RPG.")
        appendLine("Propose EXACTLY three actionable choices as JSON. Do not mutate inventory or stats.")
        appendLine("Scene: ${game.scene.title}")
        appendLine(game.scene.introduction)
        appendLine("Objective: ${game.scene.objective}")
        appendLine("Companions: ${game.companions.joinToString { "${it.name} bond ${it.level}" }}")
        appendLine("Factions: ${game.factions.joinToString { "${it.name} ${it.reputation}" }}")
        appendLine("Materials: ${game.crafting.materials}")
        appendLine("Reply with JSON only:")
        appendLine("""{"proposals":[{"id":"stable_id","title":"...","summary":"...","intent":"...","skill":"...","ability":"...","checkDc":12,"involvedCharacters":["You"],"riskTier":"Low","rewardCategories":["exploration"]}]}""")
        appendLine("riskTier must be Low, Medium, or High. Include three objects.")
    }

    private fun freshSeed(campaignId: String): Long =
        (campaignId.hashCode().toLong() and 0xffff_ffffL) + 17L
}
