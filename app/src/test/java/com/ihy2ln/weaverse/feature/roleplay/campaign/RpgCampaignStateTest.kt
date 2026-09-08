package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgCampaignStateTest {
    @Test fun `new campaign defaults to d20 and first node`() {
        val state = createRpgCampaign("c1")
        assertEquals(RpgCombatRuleset.DndD20.id, state.modeId)
        assertEquals("chapter-1-arrival", state.map.currentNodeId)
        assertEquals(1, availableRpgSceneNodes(state).size)
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
}
