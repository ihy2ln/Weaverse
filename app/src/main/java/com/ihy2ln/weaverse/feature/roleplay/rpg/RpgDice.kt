package com.ihy2ln.weaverse.feature.roleplay.rpg

/** Deterministic d20 / d6 stream owned by campaign state. */
object RpgDice {
    fun rollD20(state: RpgCampaignState): Pair<Int, RpgCampaignState> {
        val value = 1 + (mixed(state) % 20L).toInt()
        return value to state.copy(rngCounter = state.rngCounter + 1)
    }

    fun rollD6(state: RpgCampaignState): Pair<Int, RpgCampaignState> {
        val value = 1 + (mixed(state) % 6L).toInt()
        return value to state.copy(rngCounter = state.rngCounter + 1)
    }

    fun pick(state: RpgCampaignState, bound: Int): Pair<Int, RpgCampaignState> {
        val safe = bound.coerceAtLeast(1).toLong()
        val value = (mixed(state) % safe).toInt()
        return value to state.copy(rngCounter = state.rngCounter + 1)
    }

    private fun mixed(state: RpgCampaignState): Long {
        var x = state.rngSeed xor (state.rngCounter.toLong() * -7046029254386353131L)
        x = (x xor (x ushr 30)) * -4658895280553007687L
        x = (x xor (x ushr 27)) * -7723592293111394305L
        return (x xor (x ushr 31)) and Long.MAX_VALUE
    }
}
