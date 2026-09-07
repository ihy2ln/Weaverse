package com.ihy2ln.weaverse.feature.roleplay.rpg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgReducerTest {
    private val reducer = RpgReducer()

    @Test
    fun explorationOffersThreeAiChoicesPlusCustomAction() {
        val state = onboarded()
        val generated = state.scene.proposals.filterNot { it.isCustom }
        val custom = state.scene.proposals.filter { it.isCustom }
        assertEquals(3, generated.size)
        assertEquals(1, custom.size)
        assertEquals(RPG_CUSTOM_ACTION_ID, custom.single().id)
        assertEquals("Write your own action", custom.single().title)
    }

    @Test
    fun malformedProposalDoesNotMutateWorld() {
        val state = onboarded()
        val before = state.worldFingerprint()
        val result = reducer.proposeActions(state, RpgProposalParseResult.Failure())
        assertFalse(result.accepted)
        assertEquals(RPG_RETRY_MESSAGE, result.message)
        assertEquals(before, result.state.worldFingerprint())
        assertEquals(state.scene.proposals, result.state.scene.proposals)
    }

    @Test
    fun previewDoesNotMutateInventoryHealthRelationshipsOrFactions() {
        val state = onboarded()
        val before = state.worldFingerprint()
        val result = reducer.previewAction(state, FirstLightChapter.CHOICE_CART)
        assertTrue(result.accepted)
        assertNotNull(result.state.pendingPreview)
        assertEquals(before, result.state.worldFingerprint())
        assertEquals(state.crafting, result.state.crafting)
        assertEquals(state.companions, result.state.companions)
        assertEquals(state.factions, result.state.factions)
        assertEquals(state.party, result.state.party)
    }

    @Test
    fun confirmedActionMutatesWithDeterministicRolls() {
        val first = resolveChoice(onboarded(seed = 42L), FirstLightChapter.CHOICE_CART)
        val second = resolveChoice(onboarded(seed = 42L), FirstLightChapter.CHOICE_CART)
        assertEquals(first.lastOutcome?.roll, second.lastOutcome?.roll)
        assertEquals(first.lastOutcome?.success, second.lastOutcome?.success)
        assertNotEquals(onboarded(seed = 42L).worldFingerprint(), first.worldFingerprint())
        assertNotNull(first.combat)
        assertEquals(RpgScenePhase.Combat, first.scene.phase)
    }

    @Test
    fun customActionUsesTheSamePreviewConfirmResolvePipeline() {
        val drafted = reducer.writeCustomAction(onboarded(), "I search the moss for tracks")
        assertTrue(drafted.accepted)
        assertEquals(RPG_CUSTOM_ACTION_ID, drafted.state.pendingPreview?.proposalId)
        val confirmed = reducer.confirmAction(drafted.state, RPG_CUSTOM_ACTION_ID)
        assertTrue(confirmed.accepted)
        val resolved = reducer.resolveAction(confirmed.state)
        assertTrue(resolved.accepted)
        assertNotNull(resolved.state.lastOutcome)
        assertEquals(RpgScenePhase.Combat, resolved.state.scene.phase)
    }

    @Test
    fun companionBondAndFactionStandingChangeOnRewards() {
        var state = resolveChoice(onboarded(seed = 7L), FirstLightChapter.CHOICE_SCOUT_WOUNDED)
        if (state.combat != null) {
            state = winCombat(state)
        }
        val mira = state.companions.single { it.id == FirstLightChapter.MIRA_ID }
        val wardens = state.factions.single { it.id == FirstLightChapter.WARDENS_ID }
        assertTrue(mira.level >= 2 || mira.flags.isNotEmpty())
        assertTrue(wardens.reputation > 0 || wardens.flags.isNotEmpty())
    }

    @Test
    fun craftingConsumesMaterialsAndCreatesPreparedItem() {
        val rewarded = reducer.applyReward(
            onboarded(),
            RpgReward(
                id = "fiber",
                category = "crafting",
                label = "fiber",
                materialId = FirstLightChapter.MOONROOT_ID,
                materialQty = 1,
                recipeId = FirstLightChapter.SALVE_RECIPE_ID,
            ),
        ).state
        assertEquals(1, rewarded.crafting.materials[FirstLightChapter.MOONROOT_ID])
        assertTrue(rewarded.crafting.recipes.single { it.id == FirstLightChapter.SALVE_RECIPE_ID }.unlocked)
        val crafted = reducer.craftRecipe(rewarded, FirstLightChapter.SALVE_RECIPE_ID)
        assertTrue(crafted.accepted)
        assertEquals(0, crafted.state.crafting.materials[FirstLightChapter.MOONROOT_ID] ?: 0)
        assertTrue(FirstLightChapter.SALVE_ITEM in crafted.state.crafting.preparedItems)
        val again = reducer.craftRecipe(crafted.state, FirstLightChapter.SALVE_RECIPE_ID)
        assertFalse(again.accepted)
    }

    @Test
    fun resumeAfterRestartRestoresSceneCombatAndLedgers() {
        var state = resolveChoice(onboarded(seed = 99L), FirstLightChapter.CHOICE_CART)
        state = winCombat(state)
        state = reducer.collectAftermath(state).state
        state = reducer.finishChapter(state).state
        val restored = RpgCampaignCodec.decode(RpgCampaignCodec.encode(state))
        assertEquals(state.scene.id, restored.scene.id)
        assertEquals(state.scene.phase, restored.scene.phase)
        assertEquals(state.companions, restored.companions)
        assertEquals(state.factions, restored.factions)
        assertEquals(state.crafting, restored.crafting)
        assertEquals(FirstLightChapter.SCENE_RECAP, restored.scene.id)
        assertTrue(restored.progress.recapNotes.isNotEmpty())
    }

    @Test
    fun firstLightChapterCoversTheVerticalSlice() {
        var state = FirstLightChapter.newCampaign(3L)
        assertEquals(RpgScenePhase.Onboarding, state.scene.phase)
        state = reducer.completeOnboarding(state, "Ryn").state
        assertEquals("Ryn", state.party.first { it.isSummoner }.name)
        assertEquals(FirstLightChapter.SCENE_FOREST, state.scene.id)
        state = resolveChoice(state, FirstLightChapter.CHOICE_SCOUT)
        assertEquals(RpgScenePhase.Combat, state.scene.phase)
        assertNotNull(state.combat?.enemies?.single()?.intent?.title)
        state = winCombat(state)
        assertEquals(RpgCombatResult.Victory, state.combat?.result)
        state = reducer.collectAftermath(state).state
        assertEquals(RpgScenePhase.Aftermath, state.scene.phase)
        if ((state.crafting.materials[FirstLightChapter.MOONROOT_ID] ?: 0) > 0) {
            state = reducer.craftRecipe(state, FirstLightChapter.SALVE_RECIPE_ID).state
        }
        state = reducer.finishChapter(state).state
        assertEquals(RpgScenePhase.Recap, state.scene.phase)
        assertEquals(FirstLightChapter.NEXT_OBJECTIVE, state.progress.nextObjective)
    }

    private fun onboarded(seed: Long = 1L): RpgCampaignState =
        reducer.completeOnboarding(FirstLightChapter.newCampaign(seed), "Adam").state

    private fun resolveChoice(state: RpgCampaignState, id: String): RpgCampaignState {
        val preview = reducer.previewAction(state, id)
        assertTrue(preview.accepted)
        val confirm = reducer.confirmAction(preview.state, id)
        assertTrue(confirm.accepted)
        return reducer.resolveAction(confirm.state).state
    }

    private fun winCombat(state: RpgCampaignState): RpgCampaignState {
        var current = state
        var guard = 0
        while (current.combat?.result == RpgCombatResult.Ongoing && guard++ < 12) {
            val played = reducer.playCombatCard(current, "thorn_cut", "wisp")
            current = if (played.accepted) played.state else current
            if (current.combat?.result != RpgCombatResult.Ongoing) break
            val spark = reducer.playCombatCard(current, "bind_spark", "wisp")
            current = if (spark.accepted) spark.state else current
            if (current.combat?.result != RpgCombatResult.Ongoing) break
            val burst = reducer.playCombatCard(current, "ember_burst", "wisp")
            current = if (burst.accepted) burst.state else current
            if (current.combat?.result != RpgCombatResult.Ongoing) break
            current = reducer.endCombatRound(current).state
        }
        assertEquals(RpgCombatResult.Victory, current.combat?.result)
        return current
    }
}
