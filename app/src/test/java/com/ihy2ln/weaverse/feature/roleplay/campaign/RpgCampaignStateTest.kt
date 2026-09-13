package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatant
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgStatusEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgCampaignStateTest {
    @Test fun `new campaign defaults to d20 and first node`() {
        val state = createRpgCampaign("c1")
        assertEquals(RpgCombatRuleset.DndD20.id, state.modeId)
        assertEquals("chapter-1-arrival", state.map.currentNodeId)
        assertEquals(1, availableRpgSceneNodes(state).size)
        assertEquals(2, state.schemaVersion)
        assertEquals(RpgStartupStep.Cyoa, state.startup.step)
    }

    @Test fun `freeform exploration returns to chapter node`() {
        val state = enterFreeformExploration(createRpgCampaign("c1"), "The Old Road")
        assertTrue(state.exploration.active)
        assertEquals("chapter-1-arrival", returnToChapterNode(state).map.currentNodeId)
    }

    @Test fun `victory completes encounter node and awards milestone`() {
        val state = createRpgCampaign("c1")
        val outcome = RpgCombatOutcome("encounter-crossroads", RpgCombatOutcome.Result.Victory, RpgCombatRuleset.DndD20, rewards = listOf("Torch"), recap = "Won")
        val updated = applyCombatOutcome(state, outcome)
        assertTrue("chapter-1-crossroads" in updated.map.completedNodeIds)
        assertEquals(1, updated.progression.milestonePoints)
        assertEquals("Won", buildChapterRecap(updated))
    }

    @Test fun `companion consequences clamp and change stance`() {
        val state = createRpgCampaign("c1").copy(companions = listOf(RpgCompanionState("ally")))
        val updated = applyCompanionConsequence(state, "ally", 30, "Protected ally")
        assertEquals("Supportive", updated.companions.single().stance)
        assertEquals(30, updated.companions.single().relationship)
    }

    @Test fun `completing a node discovers branches and records objective recap`() {
        val updated = completeRpgSceneNode(createRpgCampaign("c1"), "chapter-1-arrival", "The party found a coded map.")
        assertTrue("chapter-1-arrival" in updated.map.completedNodeIds)
        assertTrue("chapter-1-crossroads" in updated.map.discoveredNodeIds)
        assertTrue(updated.chapterRecap.contains("coded map"))
        assertEquals(1, updated.progression.milestonePoints)
    }

    @Test fun `scene entry rejects locked nodes`() {
        val state = createRpgCampaign("c1")
        assertEquals(state, enterRpgSceneNode(state, "chapter-1-vault"))
    }

    @Test fun `combat victory discovers branches and is idempotent`() {
        val state = createRpgCampaign("c1")
        val outcome = RpgCombatOutcome("encounter-arrival", RpgCombatOutcome.Result.Victory, RpgCombatRuleset.CardBattle, rewards = listOf("Torch"), recap = "The waystone is safe.")
        val first = applyCombatOutcome(state, outcome)
        val second = applyCombatOutcome(first, outcome)
        assertTrue("chapter-1-crossroads" in first.map.discoveredNodeIds)
        assertEquals(1, first.progression.milestonePoints)
        assertEquals(first, second)
    }

    @Test fun `party hp and conditions are copied from resolved combat`() {
        val state = createRpgCampaign("c1")
        val updated = updateRpgPartyFromCombat(
            state,
            listOf(RpgCombatant("hero", "Hero", 12, hp = 7, statuses = setOf(RpgStatusEffect.Guarded))),
        )
        assertEquals(listOf("hero"), updated.party.memberIds)
        assertEquals(7, updated.party.hpByMember["hero"])
        assertEquals(setOf("Guarded"), updated.party.conditionsByMember["hero"])
    }

    @Test fun `stopping setup preserves answers and supplies local fallback`() {
        val state = createRpgCampaign("c1").copy(
            startup = RpgStartupState(
                step = RpgStartupStep.GeneratingChapterPlan,
                plan = RpgAdventurePlan(listOf(RpgPlanAnswer("premise", "Find the missing bell."))),
                generationStatus = RpgGenerationStatus.Generating,
                generationProgress = 1,
            ),
        )
        val stopped = stopRpgSetupGeneration(state)
        assertEquals(RpgStartupStep.GeneratingChapterPlan, stopped.startup.step)
        assertEquals("Find the missing bell.", stopped.startup.plan.answers.single().value)
        assertEquals(RpgGenerationStatus.Failed, stopped.startup.generationStatus)
        assertTrue(stopped.startup.generationError.contains("offline fallback"))
        assertEquals(0, stopped.startup.generationProgress)
    }
}
