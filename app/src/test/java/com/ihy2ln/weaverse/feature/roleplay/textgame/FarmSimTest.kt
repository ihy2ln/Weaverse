package com.ihy2ln.weaverse.feature.roleplay.textgame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FarmSimTest {
    @Test
    fun tillPlantWaterHarvestAndPantryFlow() {
        var farm = FarmRules.ensureCapacity(FarmState(), capacity = 1)
        farm = FarmRules.till(farm, 0)!!
        farm = FarmRules.plant(farm, 0, "valegrain", quality = 4)!!
        assertEquals(FarmSoil.Planted, farm.plots.first().soil)
        assertEquals(1, FarmRules.battlesRemaining(farm, farm.plots.first()))
        farm = FarmRules.water(farm, 0)!!
        // Valegrain needs 1 battle; watering shortens to 1 still (max 1 min) — after 1 battle ready.
        farm = FarmRules.recordBattle(farm)
        assertEquals(FarmSoil.Ready, farm.plots.first().soil)
        val (after, result) = FarmRules.harvest(farm, 0)!!
        assertEquals("valegrain", result.cropId)
        assertEquals(4 + 3, result.amount) // yield 4 + quality-1
        farm = FarmRules.addDish(after, result.dish, 2)
        assertEquals(2, farm.pantry[result.dish])
        farm = FarmRules.packDish(farm, result.dish)!!
        assertEquals(result.dish, farm.packedDish)
        assertEquals(1, farm.pantry[result.dish])
    }

    @Test
    fun minigameScoreRaisesTierBand() {
        val weak = FarmRules.roll(0f, base = 2, rng = Random(1))
        val strong = FarmRules.roll(1f, base = 2, rng = Random(1))
        assertTrue(strong.minigameMod >= weak.minigameMod)
        assertEquals(6, FarmRules.minigameModifier(0.95f))
        assertEquals(0, FarmRules.minigameModifier(0.1f))
        assertNotNull(FarmRules.DISHES["Ember Stew"])
    }

    @Test
    fun enginePlantAndHarvestUseMinigameScore() {
        val engine = TextGameEngine(adamsHavenTutorial())
        var state = engine.initialState()
        state = engine.reduce(state, TextGameAction.Choose("to_farm")).state
        state = engine.reduce(state, TextGameAction.Choose("clear_plot")).state
        assertTrue(state.persistent.farm.plots.isNotEmpty())
        assertEquals(FarmSoil.Tilled, state.persistent.farm.plots.first().soil)
        state = engine.reduce(state, TextGameAction.FarmPlant(0, 1f)).state
        assertEquals(FarmSoil.Planted, state.persistent.farm.plots.first().soil)
        assertTrue("crop_planted" in state.persistent.flags)
        // Force maturity via battle sync.
        state = state.copy(
            persistent = state.persistent.copy(
                battlesWon = state.persistent.farm.battlesFought + 5,
                farm = FarmRules.syncBattlesFought(
                    state.persistent.farm,
                    state.persistent.farm.battlesFought + 5,
                ),
            ),
        )
        assertEquals(FarmSoil.Ready, state.persistent.farm.plots.first().soil)
        state = engine.reduce(state, TextGameAction.FarmHarvest(0, 0.9f)).state
        assertTrue(state.persistent.harvest > 0)
        assertTrue(state.persistent.farm.pantry.isNotEmpty())
    }
}
