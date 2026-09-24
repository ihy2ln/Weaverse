package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.random.Random

/** Soil stages for a Clearing plot — mirrors Godot `FarmSim.Soil`. */
@Serializable
enum class FarmSoil { Wild, Tilled, Planted, Ready }

@Serializable
data class FarmPlot(
    val id: Int,
    val soil: FarmSoil = FarmSoil.Wild,
    val cropId: String = "",
    val plantedAtBattles: Int = 0,
    val watered: Boolean = false,
    /** 1..5 from the plant minigame / dice tier. */
    val quality: Int = 1,
)

@Serializable
data class FarmState(
    val battlesFought: Int = 0,
    val plots: List<FarmPlot> = emptyList(),
    /** Cooked dishes waiting in the kitchen pantry. */
    val pantry: Map<String, Int> = emptyMap(),
    /** Dish packed for the next battle (consumed on encounter entry). */
    val packedDish: String? = null,
)

data class FarmCropDef(
    val id: String,
    val name: String,
    val battles: Int,
    val yield: Int,
    val dish: String,
)

data class FarmDishDef(
    val id: String,
    val status: String,
    val magnitude: Float,
    val duration: Int,
    /** Opening guard granted when this dish is packed into a fight. */
    val openingGuard: Int = 0,
    /** Flat attack bonus applied to damaging cards for [duration] rounds. */
    val attackBonus: Int = 0,
    /** Heal applied once when the fight starts. */
    val openingHeal: Int = 0,
)

data class FarmDiceRoll(
    val raw: Int,
    val total: Int,
    val minigameMod: Int,
    val tier: Int,
    val tierName: String,
    val text: String,
)

data class FarmHarvestResult(
    val cropId: String,
    val cropName: String,
    val amount: Int,
    val quality: Int,
    val dish: String,
)

object FarmRules {
    val CROPS: List<FarmCropDef> = listOf(
        FarmCropDef("emberroot", "Emberroot", battles = 2, yield = 2, dish = "Ember Stew"),
        FarmCropDef("frostcap", "Frostcap", battles = 3, yield = 3, dish = "Frostcap Broth"),
        FarmCropDef("valegrain", "Valegrain", battles = 1, yield = 4, dish = "Vale Loaf"),
    )

    val DISHES: Map<String, FarmDishDef> = mapOf(
        "Ember Stew" to FarmDishDef("Ember Stew", "AttackUp", 0.20f, 3, attackBonus = 2),
        "Frostcap Broth" to FarmDishDef("Frostcap Broth", "DefenseUp", 0.25f, 3, openingGuard = 4),
        "Vale Loaf" to FarmDishDef("Vale Loaf", "Regen", 8f, 4, openingHeal = 6),
    )

    private val TIER_NAMES = listOf("Critical Failure", "Failure", "Success", "Great", "Perfect")

    fun crop(id: String): FarmCropDef? = CROPS.firstOrNull { it.id == id }

    fun plotCapacity(farmLevel: Int, cleared: Boolean): Int = when {
        !cleared -> 0
        else -> farmLevel.coerceIn(1, 4)
    }

    fun ensureCapacity(farm: FarmState, capacity: Int): FarmState {
        if (capacity <= 0) return farm.copy(plots = emptyList())
        if (farm.plots.size == capacity) return farm
        if (farm.plots.size > capacity) return farm.copy(plots = farm.plots.take(capacity))
        val nextId = (farm.plots.maxOfOrNull { it.id } ?: -1) + 1
        val added = (0 until (capacity - farm.plots.size)).map { offset ->
            FarmPlot(id = nextId + offset)
        }
        return farm.copy(plots = farm.plots + added)
    }

    fun till(farm: FarmState, plotId: Int): FarmState? {
        val index = farm.plots.indexOfFirst { it.id == plotId }
        if (index < 0) return null
        val plot = farm.plots[index]
        if (plot.soil != FarmSoil.Wild) return null
        return farm.copy(plots = farm.plots.toMutableList().also {
            it[index] = plot.copy(soil = FarmSoil.Tilled)
        })
    }

    fun plant(farm: FarmState, plotId: Int, cropId: String, quality: Int): FarmState? {
        val crop = crop(cropId) ?: return null
        val index = farm.plots.indexOfFirst { it.id == plotId }
        if (index < 0) return null
        val plot = farm.plots[index]
        if (plot.soil != FarmSoil.Tilled) return null
        return farm.copy(
            plots = farm.plots.toMutableList().also {
                it[index] = plot.copy(
                    soil = FarmSoil.Planted,
                    cropId = crop.id,
                    plantedAtBattles = farm.battlesFought,
                    watered = false,
                    quality = quality.coerceIn(1, 5),
                )
            },
        )
    }

    fun water(farm: FarmState, plotId: Int): FarmState? {
        val index = farm.plots.indexOfFirst { it.id == plotId }
        if (index < 0) return null
        val plot = farm.plots[index]
        if (plot.soil != FarmSoil.Planted || plot.watered) return null
        val watered = farm.copy(
            plots = farm.plots.toMutableList().also {
                it[index] = plot.copy(watered = true)
            },
        )
        return refreshReady(watered)
    }

    fun harvest(farm: FarmState, plotId: Int): Pair<FarmState, FarmHarvestResult>? {
        val index = farm.plots.indexOfFirst { it.id == plotId }
        if (index < 0) return null
        val plot = farm.plots[index]
        if (plot.soil != FarmSoil.Ready) return null
        val crop = crop(plot.cropId) ?: return null
        val amount = crop.yield + (plot.quality - 1)
        val result = FarmHarvestResult(
            cropId = crop.id,
            cropName = crop.name,
            amount = amount,
            quality = plot.quality,
            dish = crop.dish,
        )
        val cleared = farm.copy(
            plots = farm.plots.toMutableList().also {
                it[index] = FarmPlot(id = plot.id)
            },
        )
        return cleared to result
    }

    fun addDish(farm: FarmState, dish: String, count: Int): FarmState {
        if (count <= 0 || dish !in DISHES) return farm
        val next = farm.pantry.toMutableMap()
        next[dish] = (next[dish] ?: 0) + count
        return farm.copy(pantry = next)
    }

    fun packDish(farm: FarmState, dish: String): FarmState? {
        val held = farm.pantry[dish] ?: return null
        if (held <= 0) return null
        val next = farm.pantry.toMutableMap()
        if (held <= 1) next.remove(dish) else next[dish] = held - 1
        return farm.copy(pantry = next, packedDish = dish)
    }

    fun battlesNeeded(plot: FarmPlot): Int {
        val crop = crop(plot.cropId) ?: return 0
        return max(1, crop.battles - if (plot.watered) 1 else 0)
    }

    fun battlesRemaining(farm: FarmState, plot: FarmPlot): Int =
        max(0, battlesNeeded(plot) - (farm.battlesFought - plot.plantedAtBattles))

    fun refreshReady(farm: FarmState): FarmState = farm.copy(
        plots = farm.plots.map { plot ->
            if (plot.soil == FarmSoil.Planted && battlesRemaining(farm, plot) <= 0) {
                plot.copy(soil = FarmSoil.Ready)
            } else {
                plot
            }
        },
    )

    fun recordBattle(farm: FarmState): FarmState =
        refreshReady(farm.copy(battlesFought = farm.battlesFought + 1))

    fun syncBattlesFought(farm: FarmState, battlesWon: Int): FarmState {
        if (battlesWon <= farm.battlesFought) return refreshReady(farm)
        return refreshReady(farm.copy(battlesFought = battlesWon))
    }

    fun minigameModifier(score01: Float): Int = when {
        score01 >= 0.90f -> 6
        score01 >= 0.65f -> 4
        score01 >= 0.40f -> 2
        else -> 0
    }

    fun roll(score01: Float, base: Int = 2, rng: Random): FarmDiceRoll {
        val mg = minigameModifier(score01)
        val raw = rng.nextInt(1, 21)
        val total = raw + base + mg
        val tier = tierFor(total)
        val parts = buildList {
            add("d20=$raw")
            if (base != 0) add("base %+$base")
            if (mg != 0) add("minigame %+$mg")
        }
        return FarmDiceRoll(
            raw = raw,
            total = total,
            minigameMod = mg,
            tier = tier,
            tierName = TIER_NAMES[tier],
            text = "${parts.joinToString(" ")} = $total",
        )
    }

    fun tierFor(total: Int): Int = when {
        total <= 3 -> 0
        total <= 9 -> 1
        total <= 15 -> 2
        total <= 21 -> 3
        else -> 4
    }

    fun qualityForTier(tier: Int): Int = tier.coerceIn(1, 5)

    fun pickCrop(rng: Random): FarmCropDef = CROPS[rng.nextInt(CROPS.size)]

    fun live(persistent: TextGamePersistentState): FarmState {
        val cleared = "farm_cleared" in persistent.flags || persistent.farm.plots.isNotEmpty()
        val capacity = plotCapacity(persistent.farmLevel, cleared)
        return refreshReady(
            syncBattlesFought(ensureCapacity(persistent.farm, capacity), persistent.battlesWon),
        )
    }

    fun summaryLine(farm: FarmState): String {
        val growing = farm.plots.count { it.soil == FarmSoil.Planted }
        val ready = farm.plots.count { it.soil == FarmSoil.Ready }
        val pantryBits = farm.pantry.entries.joinToString(", ") { "${it.key} x${it.value}" }
        return buildString {
            append("Battles fought: ${farm.battlesFought} · Growing: $growing · Ready: $ready")
            if (pantryBits.isNotBlank()) append(" · Pantry: $pantryBits")
            farm.packedDish?.let { append(" · Packed: $it") }
        }
    }
}
