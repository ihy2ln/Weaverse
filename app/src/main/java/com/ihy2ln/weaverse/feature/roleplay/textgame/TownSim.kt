package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable

fun isTapWorldNode(nodeId: String): Boolean = false

fun isFarmWorldNode(nodeId: String): Boolean = nodeId in setOf("farm", "return_farm")

fun isTownWorldNode(nodeId: String): Boolean = nodeId in setOf("town", "return_town")

@Serializable
data class TownBuilding(
    val id: String,
    /** Battles-won when this lot was last collected. -1 = never collected (ready once built). */
    val lastCollectBattles: Int = -1,
    /** TownYard plot this building occupies. Blank until sync assigns a pad. */
    val plotId: String = "",
)

@Serializable
data class TownState(
    val buildings: List<TownBuilding> = emptyList(),
)

enum class TownLotStatus { Locked, Empty, Built }

enum class TownLotKind { Lot, Business, Landmark }

data class TownBuildingDef(
    val id: String,
    val name: String,
    val kind: TownLotKind = TownLotKind.Business,
    /** Flag that means the lot is constructed and generating. */
    val builtFlag: String? = null,
    /** Town level at which the lot becomes an empty buildable plot (or appears). */
    val unlockTownLevel: Int = 1,
    /** Choice that constructs this lot. */
    val buildChoiceId: String? = null,
    /** Choice that enters the interior — visual novel / services. */
    val visitChoiceId: String? = null,
    val coinIncome: Int = 2,
    val materialIncome: Int = 0,
    /** Appears already built once unlocked (the starting shack). */
    val autoBuiltOnUnlock: Boolean = false,
    /** Coin cost to raise this empty pad. */
    val coinCost: Int = 0,
    /** Ore cost to raise this empty pad. */
    val materialCost: Int = 0,
    /** Position along the Mafia City street (near crossroads = high depth). */
    val streetSlot: StreetSlot,
    val artAssetPath: String = "",
    /** Default TownYard pad when first raised. */
    val defaultPlotId: String = "",
)

data class FarmBuildingDef(
    val id: String,
    val name: String,
    val builtFlag: String,
    val buildChoiceId: String,
    val visitChoiceId: String,
    val unlockFarmLevel: Int,
    val footprint: GridRect,
    val artAssetPath: String,
)

data class TownCollectResult(
    val buildingId: String,
    val name: String,
    val coins: Int,
    val materials: Int,
)

object FarmBuildings {
    val LOTS: List<FarmBuildingDef> = listOf(
        FarmBuildingDef(
            id = "kitchen",
            name = "Kitchen",
            builtFlag = "farm_l2",
            buildChoiceId = "upgrade_farm_l2",
            visitChoiceId = "visit_kitchen",
            unlockFarmLevel = 1,
            footprint = FarmLayout.KITCHEN,
            artAssetPath = "images/adams_haven/world/kitchen.png",
        ),
        FarmBuildingDef(
            id = "barn",
            name = "Barn",
            builtFlag = "farm_l3",
            buildChoiceId = "upgrade_farm_l3",
            visitChoiceId = "visit_barn",
            unlockFarmLevel = 2,
            footprint = FarmLayout.BARN,
            artAssetPath = "images/adams_haven/world/barn.png",
        ),
    )

    /** @deprecated use [FarmLayout.PLOT_CELLS] — kept for tests counting plot slots. */
    val PLOT_LAYOUT: List<Pair<Float, Float>> = FarmLayout.PLOT_CELLS.map { it.col.toFloat() / IsoGrid.FARM_COLS to it.row.toFloat() / IsoGrid.FARM_ROWS }

    fun def(id: String): FarmBuildingDef? = LOTS.firstOrNull { it.id == id }

    fun status(def: FarmBuildingDef, flags: List<String>, farmLevel: Int, cleared: Boolean): TownLotStatus {
        if (def.builtFlag in flags) return TownLotStatus.Built
        if (!cleared) return TownLotStatus.Locked
        if (farmLevel >= def.unlockFarmLevel) return TownLotStatus.Empty
        return TownLotStatus.Locked
    }

    fun isVisible(def: FarmBuildingDef, flags: List<String>, farmLevel: Int, cleared: Boolean): Boolean {
        val status = status(def, flags, farmLevel, cleared)
        return status == TownLotStatus.Built || status == TownLotStatus.Empty
    }

    fun hotspot(def: FarmBuildingDef): TextGameHotspot = TextGameHotspot(
        id = def.id,
        label = def.name,
        choiceId = def.visitChoiceId,
        buildingId = def.id,
        kind = TextGameHotspotKind.Building,
    )
}

object TownRules {
    val BUILDINGS: List<TownBuildingDef> = listOf(
        TownBuildingDef(
            id = "house",
            name = "Shack",
            kind = TownLotKind.Landmark,
            unlockTownLevel = 1,
            visitChoiceId = "town_house",
            coinIncome = 0,
            autoBuiltOnUnlock = true,
            streetSlot = StreetSlot(slot = 0, side = StreetSide.Right, depth = 0.90f),
            artAssetPath = TownYard.HOUSE_ART,
            defaultPlotId = TownYard.defaultPlotId("house"),
        ),
        TownBuildingDef(
            id = "deck_hall",
            name = "Deck Hall",
            builtFlag = "deck_hall_built",
            unlockTownLevel = 1,
            buildChoiceId = "build_deck_hall",
            visitChoiceId = "visit_deck_hall",
            coinIncome = 3,
            coinCost = 5,
            materialCost = 2,
            streetSlot = StreetSlot(slot = 1, side = StreetSide.Left, depth = 0.78f),
            artAssetPath = "images/adams_haven/world/deck_hall.png",
            defaultPlotId = TownYard.defaultPlotId("deck_hall"),
        ),
        TownBuildingDef(
            id = "frosted_mug",
            name = "Frosted Mug",
            builtFlag = "mug_built",
            unlockTownLevel = 2,
            buildChoiceId = "build_inn",
            visitChoiceId = "visit_frosted_mug",
            coinIncome = 2,
            coinCost = 3,
            materialCost = 1,
            streetSlot = StreetSlot(slot = 2, side = StreetSide.Right, depth = 0.58f),
            artAssetPath = "images/adams_haven/world/frosted_mug.png",
            defaultPlotId = TownYard.defaultPlotId("frosted_mug"),
        ),
        TownBuildingDef(
            id = "market",
            name = "Market",
            builtFlag = "market_built",
            unlockTownLevel = 2,
            buildChoiceId = "build_market",
            visitChoiceId = "visit_market",
            coinIncome = 4,
            coinCost = 4,
            materialCost = 1,
            streetSlot = StreetSlot(slot = 3, side = StreetSide.Left, depth = 0.48f),
            artAssetPath = "images/adams_haven/world/market.png",
            defaultPlotId = TownYard.defaultPlotId("market"),
        ),
        TownBuildingDef(
            id = "guild_hall",
            name = "Guild Hall",
            builtFlag = "town_l3",
            unlockTownLevel = 2,
            buildChoiceId = "town_l3",
            visitChoiceId = "visit_guild_hall",
            coinIncome = 5,
            coinCost = 8,
            materialCost = 4,
            streetSlot = StreetSlot(slot = 4, side = StreetSide.Left, depth = 0.30f),
            artAssetPath = "images/adams_haven/world/guild_hall.png",
            defaultPlotId = TownYard.defaultPlotId("guild_hall"),
        ),
        TownBuildingDef(
            id = "workshop",
            name = "Workshop",
            builtFlag = "town_l4",
            unlockTownLevel = 3,
            buildChoiceId = "town_l4",
            coinIncome = 4,
            materialIncome = 1,
            coinCost = 12,
            materialCost = 6,
            streetSlot = StreetSlot(slot = 5, side = StreetSide.Right, depth = 0.38f),
            artAssetPath = "images/adams_haven/world/workbench.png",
            defaultPlotId = TownYard.defaultPlotId("workshop"),
        ),
        TownBuildingDef(
            id = "plaza",
            name = "Well",
            kind = TownLotKind.Landmark,
            builtFlag = "town_l5",
            unlockTownLevel = 4,
            buildChoiceId = "town_l5",
            visitChoiceId = "town_plaza",
            coinIncome = 2,
            coinCost = 18,
            materialCost = 9,
            streetSlot = StreetSlot(slot = 6, side = StreetSide.Center, depth = 0.20f),
            artAssetPath = "images/adams_haven/world/well.png",
            defaultPlotId = TownYard.defaultPlotId("plaza"),
        ),
        TownBuildingDef(
            id = "forest_gate",
            name = "Forest Gate",
            kind = TownLotKind.Landmark,
            builtFlag = "town_l2",
            unlockTownLevel = 2,
            visitChoiceId = "visit_forest_gate",
            coinIncome = 0,
            autoBuiltOnUnlock = true,
            streetSlot = StreetSlot(slot = 7, side = StreetSide.Center, depth = 0.06f),
            artAssetPath = "images/adams_haven/world/gate.png",
            defaultPlotId = TownYard.defaultPlotId("forest_gate"),
        ),
    )

    fun def(id: String): TownBuildingDef? = BUILDINGS.firstOrNull { it.id == id }

    fun spritePath(def: TownBuildingDef, homeLevel: Int): String =
        if (def.id == "house") TownYard.HOUSE_ART else def.artAssetPath

    fun displayName(def: TownBuildingDef, homeLevel: Int): String =
        if (def.id == "house") {
            if (homeLevel >= 4) "Manor" else if (homeLevel >= 2) "House" else "Shack"
        } else {
            def.name
        }

    fun status(def: TownBuildingDef, flags: List<String>, townLevel: Int): TownLotStatus {
        if (def.builtFlag != null && def.builtFlag in flags) return TownLotStatus.Built
        if (def.autoBuiltOnUnlock && townLevel >= def.unlockTownLevel) return TownLotStatus.Built
        if (def.builtFlag == null && def.autoBuiltOnUnlock) return TownLotStatus.Built
        if (townLevel >= def.unlockTownLevel && def.buildChoiceId != null) return TownLotStatus.Empty
        if (townLevel >= def.unlockTownLevel) return TownLotStatus.Built
        return TownLotStatus.Locked
    }

    fun isVisible(def: TownBuildingDef, flags: List<String>, townLevel: Int): Boolean {
        val status = status(def, flags, townLevel)
        return status == TownLotStatus.Built || status == TownLotStatus.Empty
    }

    fun isBuilt(def: TownBuildingDef, flags: List<String>, townLevel: Int): Boolean =
        status(def, flags, townLevel) == TownLotStatus.Built

    fun sync(town: TownState, flags: List<String>, townLevel: Int): TownState {
        val next = town.buildings.toMutableList()
        BUILDINGS.forEach { def ->
            if (!isBuilt(def, flags, townLevel)) return@forEach
            val idx = next.indexOfFirst { it.id == def.id }
            if (idx < 0) {
                val taken = next.map { it.plotId }.filter { it.isNotBlank() }.toSet()
                next += TownBuilding(
                    id = def.id,
                    plotId = TownYard.freePlotId(taken, def.defaultPlotId.ifBlank { TownYard.defaultPlotId(def.id) }),
                )
            } else if (next[idx].plotId.isBlank()) {
                val taken = next.mapIndexedNotNull { i, building ->
                    building.plotId.takeIf { it.isNotBlank() && i != idx }
                }.toSet()
                next[idx] = next[idx].copy(
                    plotId = TownYard.freePlotId(taken, def.defaultPlotId.ifBlank { TownYard.defaultPlotId(def.id) }),
                )
            }
        }
        return town.copy(buildings = next)
    }

    fun plotId(town: TownState, def: TownBuildingDef): String {
        val stored = town.buildings.firstOrNull { it.id == def.id }?.plotId
        if (!stored.isNullOrBlank() && TownYard.plot(stored) != null) return stored
        return def.defaultPlotId.ifBlank { TownYard.defaultPlotId(def.id) }
    }

    fun occupant(town: TownState, plotId: String, flags: List<String>, townLevel: Int): TownBuildingDef? =
        BUILDINGS.firstOrNull { isBuilt(it, flags, townLevel) && plotId(town, it) == plotId }

    fun emptyPadPlot(town: TownState, def: TownBuildingDef, flags: List<String>, townLevel: Int): String {
        val taken = BUILDINGS.filter { isBuilt(it, flags, townLevel) }.map { plotId(town, it) }.toSet()
        return TownYard.freePlotId(taken, plotId(town, def))
    }

    fun visualPlot(town: TownState, def: TownBuildingDef, flags: List<String>, townLevel: Int): String =
        if (isBuilt(def, flags, townLevel)) plotId(town, def) else emptyPadPlot(town, def, flags, townLevel)

    fun defOnPlot(town: TownState, plotId: String, flags: List<String>, townLevel: Int): TownBuildingDef? {
        val built = occupant(town, plotId, flags, townLevel)
        if (built != null) return built
        return BUILDINGS.firstOrNull {
            isVisible(it, flags, townLevel) &&
                !isBuilt(it, flags, townLevel) &&
                emptyPadPlot(town, it, flags, townLevel) == plotId
        }
    }

    fun move(town: TownState, buildingId: String, plotId: String, flags: List<String>, townLevel: Int): TownState? {
        if (TownYard.plot(plotId) == null) return null
        val def = def(buildingId) ?: return null
        if (!isBuilt(def, flags, townLevel)) return null
        val synced = sync(town, flags, townLevel)
        val from = plotId(synced, def)
        if (from == plotId) return synced
        val other = occupant(synced, plotId, flags, townLevel)
        val next = synced.buildings.map { building ->
            when (building.id) {
                buildingId -> building.copy(plotId = plotId)
                other?.id -> building.copy(plotId = from)
                else -> building
            }
        }
        return synced.copy(buildings = next)
    }

    fun live(persistent: TextGamePersistentState): TownState =
        sync(persistent.town, persistent.flags, persistent.townLevel)

    fun costLabel(def: TownBuildingDef): String {
        if (def.coinCost <= 0 && def.materialCost <= 0) return ""
        return buildString {
            if (def.coinCost > 0) append("${def.coinCost}c")
            if (def.materialCost > 0) {
                if (isNotEmpty()) append(" · ")
                append("${def.materialCost} ore")
            }
        }
    }

    fun canAfford(def: TownBuildingDef, coins: Int, materials: Int): Boolean =
        coins >= def.coinCost && materials >= def.materialCost

    fun hotspot(def: TownBuildingDef, homeLevel: Int): TextGameHotspot = TextGameHotspot(
        id = def.id,
        label = displayName(def, homeLevel),
        choiceId = def.visitChoiceId.orEmpty(),
        buildingId = def.id,
        kind = TextGameHotspotKind.Building,
    )

    fun canCollect(
        town: TownState,
        def: TownBuildingDef,
        flags: List<String>,
        townLevel: Int,
        battlesWon: Int,
    ): Boolean {
        if (!isBuilt(def, flags, townLevel)) return false
        if (def.coinIncome <= 0 && def.materialIncome <= 0) return false
        val building = town.buildings.firstOrNull { it.id == def.id } ?: return true
        return building.lastCollectBattles < 0 || building.lastCollectBattles < battlesWon
    }

    fun collect(
        town: TownState,
        def: TownBuildingDef,
        flags: List<String>,
        townLevel: Int,
        battlesWon: Int,
    ): Pair<TownState, TownCollectResult>? {
        if (!canCollect(town, def, flags, townLevel, battlesWon)) return null
        val coins = def.coinIncome + (townLevel - 1).coerceAtLeast(0)
        val mats = def.materialIncome
        val synced = sync(town, flags, townLevel)
        val nextBuildings = synced.buildings.toMutableList()
        val idx = nextBuildings.indexOfFirst { it.id == def.id }
        val previous = if (idx >= 0) nextBuildings[idx] else TownBuilding(def.id)
        val updated = previous.copy(lastCollectBattles = battlesWon)
        if (idx >= 0) nextBuildings[idx] = updated else nextBuildings += updated
        return synced.copy(buildings = nextBuildings) to TownCollectResult(def.id, def.name, coins, mats)
    }

    fun summaryLine(town: TownState, flags: List<String>, townLevel: Int, battlesWon: Int): String {
        val built = BUILDINGS.count { isBuilt(it, flags, townLevel) }
        val ready = BUILDINGS.count { canCollect(town, it, flags, townLevel, battlesWon) }
        return "Lots $built · Collect $ready · Town Lv $townLevel"
    }
}

fun TextGameHotspot.resolvedKind(): TextGameHotspotKind = when {
    farmPlotId != null -> TextGameHotspotKind.Plot
    kind != TextGameHotspotKind.Location -> kind
    npcName.isNotBlank() || talkProse.isNotBlank() -> TextGameHotspotKind.Npc
    else -> kind
}
