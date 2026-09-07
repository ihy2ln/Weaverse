package com.ihy2ln.weaverse.feature.roleplay.rpg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgCombatTest {
    private val reducer = RpgReducer()

    @Test
    fun apEpSpLimitsAndVisibleIntent() {
        val combat = reducer.startCombat(onboarded(), FirstLightChapter.ENCOUNTER_WISP).state
        val fight = combat.combat!!
        assertEquals("Lash", fight.enemies.single().intent.title)
        assertTrue(fight.hand.isNotEmpty())
        val mira = fight.party.single { it.id == FirstLightChapter.MIRA_ID }
        assertEquals(1, mira.ap)
        val first = reducer.playCombatCard(combat, "thorn_cut", "wisp")
        assertTrue(first.accepted)
        val second = reducer.playCombatCard(first.state, "mira_guard", "wisp")
        assertFalse(second.accepted)
        val burst = reducer.playCombatCard(first.state, "ember_burst", "wisp")
        assertTrue(burst.accepted)
        val again = reducer.playCombatCard(burst.state, "bind_spark", "wisp")
        assertFalse(again.accepted)
    }

    @Test
    fun cardResolutionIsDeterministicAndCanWin() {
        val start = reducer.startCombat(onboarded(seed = 11L), FirstLightChapter.ENCOUNTER_WISP).state
        val a = playKill(start)
        val b = playKill(reducer.startCombat(onboarded(seed = 11L), FirstLightChapter.ENCOUNTER_WISP).state)
        assertEquals(a.combat?.enemies?.single()?.hp, b.combat?.enemies?.single()?.hp)
        assertEquals(RpgCombatResult.Victory, a.combat?.result)
        assertTrue(a.companions.single { it.id == FirstLightChapter.MIRA_ID }.level >= 2)
        assertTrue(a.factions.single { it.id == FirstLightChapter.WARDENS_ID }.reputation >= 2)
        assertTrue((a.crafting.materials[FirstLightChapter.MOONROOT_ID] ?: 0) >= 1)
    }

    @Test
    fun defeatHappensWhenPartyHitPointsReachZero() {
        val started = reducer.startCombat(onboarded(), FirstLightChapter.ENCOUNTER_WISP).state
        val weakened = started.copy(
            combat = started.combat!!.copy(
                party = started.combat!!.party.map { it.copy(hp = 1, ap = 0) },
                pendingBlock = 0,
            ),
        )
        var current = weakened
        var guard = 0
        while (current.combat?.result == RpgCombatResult.Ongoing && guard++ < 6) {
            current = reducer.endCombatRound(current).state
        }
        assertEquals(RpgCombatResult.Defeat, current.combat?.result)
        val retry = reducer.retryCombat(current)
        assertEquals(RpgCombatResult.Ongoing, retry.state.combat?.result)
        assertTrue(retry.state.combat!!.party.all { it.hp == it.maxHp })
    }

    private fun onboarded(seed: Long = 1L): RpgCampaignState =
        reducer.completeOnboarding(FirstLightChapter.newCampaign(seed), "Adam").state

    private fun playKill(state: RpgCampaignState): RpgCampaignState {
        var current = state
        current = reducer.playCombatCard(current, "thorn_cut", "wisp").state
        current = reducer.playCombatCard(current, "ember_burst", "wisp").state
        if (current.combat?.result == RpgCombatResult.Ongoing) {
            current = reducer.endCombatRound(current).state
            current = reducer.playCombatCard(current, "thorn_cut", "wisp").state
        }
        return current
    }
}
