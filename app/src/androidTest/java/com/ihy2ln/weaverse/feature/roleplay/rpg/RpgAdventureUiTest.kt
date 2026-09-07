package com.ihy2ln.weaverse.feature.roleplay.rpg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ihy2ln.weaverse.core.ui.theme.WeaverseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RpgAdventureUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val reducer = RpgReducer()

    @Test
    fun firstRunOnboardingShowsNameAndBegin() {
        val ui = RpgAdventureUiState(
            loading = false,
            campaignTitle = "First Light",
            game = FirstLightChapter.newCampaign(1L),
        )
        compose.setContent { WeaverseTheme { RpgAdventurePane(ui) } }
        compose.onNodeWithTag("rpg-onboarding").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.SummonerName)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Begin)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.PartyReady)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.NextGuidance)).assertIsDisplayed()
    }

    @Test
    fun choiceCardsAndCustomComposerAreShown() {
        val game = reducer.completeOnboarding(FirstLightChapter.newCampaign(1L), "Ada").state
        val ui = RpgAdventureUiState(loading = false, game = game)
        compose.setContent { WeaverseTheme { RpgAdventurePane(ui) } }
        compose.onNodeWithTag("rpg-choice-${FirstLightChapter.CHOICE_SCOUT}").assertIsDisplayed()
        compose.onNodeWithTag("rpg-choice-${FirstLightChapter.CHOICE_SCOUT_WOUNDED}").assertIsDisplayed()
        compose.onNodeWithTag("rpg-choice-${FirstLightChapter.CHOICE_CART}").assertIsDisplayed()
        compose.onNodeWithTag("rpg-custom-card").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.WriteOwn)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.CustomComposer)).assertIsDisplayed()
    }

    @Test
    fun previewConfirmResolveFlow() {
        var game = reducer.completeOnboarding(FirstLightChapter.newCampaign(5L), "Ada").state
        var ui by mutableStateOf(
            RpgAdventureUiState(loading = false, game = game, previewOpen = false),
        )
        compose.setContent {
            WeaverseTheme {
                RpgAdventurePane(
                    ui = ui,
                    onSelectProposal = { id ->
                        game = reducer.previewAction(game, id).state
                        ui = ui.copy(game = game, previewOpen = true)
                    },
                    onConfirm = {
                        game = reducer.confirmAction(game, game.pendingPreview!!.proposalId).state
                        ui = ui.copy(game = game, previewOpen = true)
                    },
                    onResolve = {
                        game = reducer.resolveAction(game).state
                        ui = ui.copy(game = game, previewOpen = false)
                    },
                )
            }
        }
        compose.onNodeWithTag("rpg-choice-${FirstLightChapter.CHOICE_SCOUT}").performClick()
        compose.onNodeWithTag("rpg-preview-sheet").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Confirm)).assertIsDisplayed()
        compose.onNodeWithTag("rpg-confirm").performClick()
        compose.onNode(hasContentDescription(RpgUiLabels.Resolve)).assertIsDisplayed()
        compose.onNodeWithTag("rpg-resolve").performClick()
        compose.onNodeWithTag("rpg-combat").assertIsDisplayed()
    }

    @Test
    fun customComposerSubmitsIntoPreview() {
        var game = reducer.completeOnboarding(FirstLightChapter.newCampaign(1L), "Ada").state
        var draft = ""
        var ui by mutableStateOf(RpgAdventureUiState(loading = false, game = game, customDraft = draft))
        compose.setContent {
            WeaverseTheme {
                RpgAdventurePane(
                    ui = ui,
                    onCustomDraft = {
                        draft = it
                        ui = ui.copy(customDraft = it)
                    },
                    onSubmitCustom = {
                        game = reducer.writeCustomAction(game, draft).state
                        ui = ui.copy(game = game, previewOpen = true)
                    },
                )
            }
        }
        compose.onNodeWithTag("rpg-custom-input").performTextInput("I scout the treeline carefully")
        compose.onNodeWithTag("rpg-custom-submit").performClick()
        compose.onNodeWithTag("rpg-preview-sheet").assertIsDisplayed()
    }

    @Test
    fun combatCardsOnLandscapePhoneSize() {
        val game = reducer.startCombat(
            reducer.completeOnboarding(FirstLightChapter.newCampaign(1L), "Ada").state,
            FirstLightChapter.ENCOUNTER_WISP,
        ).state
        compose.setContent {
            WeaverseTheme {
                BoxWithConstraints(Modifier.requiredSize(DpSize(800.dp, 360.dp))) {
                    RpgAdventurePane(RpgAdventureUiState(loading = false, game = game))
                }
            }
        }
        compose.onNodeWithTag("rpg-combat").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.EnemyIntent)).assertIsDisplayed()
        compose.onNodeWithTag("rpg-card-thorn_cut").assertIsDisplayed()
        compose.onNodeWithTag("rpg-card-ember_burst").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.EndRound)).assertIsDisplayed()
    }

    @Test
    fun objectivePartyConsequenceAndRecapSurfaces() {
        var game = reducer.completeOnboarding(FirstLightChapter.newCampaign(2L), "Ada").state
        game = reducer.previewAction(game, FirstLightChapter.CHOICE_CART).state
        game = reducer.confirmAction(game, FirstLightChapter.CHOICE_CART).state
        game = reducer.resolveAction(game).state
        game = reducer.playCombatCard(game, "thorn_cut", "wisp").state
        game = reducer.playCombatCard(game, "ember_burst", "wisp").state
        if (game.combat?.result == RpgCombatResult.Ongoing) {
            game = reducer.endCombatRound(game).state
            game = reducer.playCombatCard(game, "thorn_cut", "wisp").state
        }
        game = reducer.collectAftermath(game).state
        game = reducer.finishChapter(game).state
        compose.setContent {
            WeaverseTheme {
                RpgAdventurePane(
                    RpgAdventureUiState(loading = false, game = game, detailsExpanded = true),
                )
            }
        }
        compose.onNodeWithTag("rpg-recap").assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Recap)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Objective)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.PartyReady)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Consequences)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Relationships)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Crafting)).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Factions)).assertIsDisplayed()
    }

    @Test
    fun loadingRetryAndOfflineStates() {
        compose.setContent {
            WeaverseTheme {
                RpgAdventurePane(RpgAdventureUiState(loading = true))
            }
        }
        compose.onNode(hasContentDescription(RpgUiLabels.Loading)).assertIsDisplayed()

        val exploring = reducer.completeOnboarding(FirstLightChapter.newCampaign(1L), "Ada").state
            .copy(lastError = RPG_RETRY_MESSAGE)
        compose.setContent {
            WeaverseTheme {
                RpgAdventurePane(
                    RpgAdventureUiState(loading = false, game = exploring, offline = true),
                )
            }
        }
        compose.onNodeWithText(RpgUiLabels.Offline).assertIsDisplayed()
        compose.onNode(hasContentDescription(RpgUiLabels.Retry)).assertIsDisplayed()
        compose.onNodeWithTag("rpg-retry").assertIsDisplayed()
    }
}
