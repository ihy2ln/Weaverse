package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable

@Serializable
enum class TycoonDistrict {
    Residential,
    Farm,
    Commercial,
    Industrial,
    Governates;

    val shortLabel: String
        get() = when (this) {
            Residential -> "RES"
            Farm -> "FARM"
            Commercial -> "COM"
            Industrial -> "IND"
            Governates -> "GOV"
        }

    val displayName: String
        get() = when (this) {
            Residential -> "Residential"
            Farm -> "Farmland"
            Commercial -> "Commercial"
            Industrial -> "Industrial"
            Governates -> "Governates"
        }
}

@Serializable
data class TycoonPlacement(
    val id: String,
    val buildingId: String,
    val x: Int,
    val y: Int,
)

@Serializable
data class TycoonBoardState(
    val width: Int = TYCOON_START_WIDTH,
    val height: Int = TYCOON_START_HEIGHT,
    val placements: List<TycoonPlacement> = emptyList(),
    val hand: List<String> = listOf("cottage"),
    val selectedBuildingId: String? = "cottage",
    val selectedPlacementId: String? = null,
    val expansionsBought: Int = 0,
    val dungeonExpansionsClaimed: Int = 0,
    val farmExpansionsClaimed: Int = 0,
    val annexExpansionsClaimed: Int = 0,
)

data class TycoonBuildingDef(
    val id: String,
    val title: String,
    val district: TycoonDistrict,
    val width: Int,
    val height: Int,
    val artAssetPath: String,
    val coinCost: Int = 0,
    val materialCost: Int = 0,
    val seedCost: Int = 0,
    val description: String,
    val starter: Boolean = false,
    val placeFlag: String? = null,
    val placeEffects: List<TextGameEffect> = emptyList(),
    val verbs: List<TycoonVerb> = emptyList(),
)

data class TycoonVerb(
    val id: String,
    val label: String,
    val destinationNodeId: String? = null,
    val condition: TextGameCondition = TextGameCondition(),
    val effects: List<TextGameEffect> = emptyList(),
)

data class TycoonBonuses(
    val yieldPercent: Int,
    val goldPercent: Int,
    val comfortPercent: Int,
)

const val TYCOON_START_WIDTH = 5
const val TYCOON_START_HEIGHT = 5
const val TYCOON_START_TILES = TYCOON_START_WIDTH * TYCOON_START_HEIGHT
const val TYCOON_MAX_WIDTH = 20
const val TYCOON_MAX_HEIGHT = 12

/**
 * Starter 5×5 district map (25 lots, five of each):
 *
 * ```
 * R R F F C
 * R R F F C
 * R I F C C
 * I I I G G
 * I I G G G
 * ```
 *
 * Larger boards scale this same top-down partition so Residential stays
 * north-west, Farmland north, Commercial north-east, Industrial south-west,
 * and Governates south-east.
 */
private val TYCOON_CORE: Array<Array<TycoonDistrict>> = arrayOf(
    arrayOf(TycoonDistrict.Residential, TycoonDistrict.Residential, TycoonDistrict.Farm, TycoonDistrict.Farm, TycoonDistrict.Commercial),
    arrayOf(TycoonDistrict.Residential, TycoonDistrict.Residential, TycoonDistrict.Farm, TycoonDistrict.Farm, TycoonDistrict.Commercial),
    arrayOf(TycoonDistrict.Residential, TycoonDistrict.Industrial, TycoonDistrict.Farm, TycoonDistrict.Commercial, TycoonDistrict.Commercial),
    arrayOf(TycoonDistrict.Industrial, TycoonDistrict.Industrial, TycoonDistrict.Commercial, TycoonDistrict.Governates, TycoonDistrict.Governates),
    arrayOf(TycoonDistrict.Industrial, TycoonDistrict.Industrial, TycoonDistrict.Governates, TycoonDistrict.Governates, TycoonDistrict.Governates),
)

fun tycoonDistrictAt(x: Int, y: Int, width: Int = TYCOON_START_WIDTH, height: Int = TYCOON_START_HEIGHT): TycoonDistrict {
    val col = ((x + 0.5f) * 5f / width.coerceAtLeast(1)).toInt().coerceIn(0, 4)
    val row = ((y + 0.5f) * 5f / height.coerceAtLeast(1)).toInt().coerceIn(0, 4)
    return TYCOON_CORE[row][col]
}

fun tycoonBuilding(id: String): TycoonBuildingDef? = tycoonBuildings().firstOrNull { it.id == id }

fun tycoonBuildings(): List<TycoonBuildingDef> = listOf(
    TycoonBuildingDef(
        id = "cottage",
        title = "Cottage",
        district = TycoonDistrict.Residential,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/locations/fixer-upper.png",
        description = "A 1×1 shack card. Homes like neighbours.",
        starter = true,
        placeFlag = "cottage_placed",
        placeEffects = listOf(TextGameEffect(setFlag = "cottage_placed")),
        verbs = listOf(
            TycoonVerb("rest", "I rest until morning (restore health)", effects = listOf(TextGameEffect(healthDelta = 99))),
            TycoonVerb(
                id = "secure",
                label = "I secure the cottage (+3 guard for the next battle)",
                condition = TextGameCondition(requiredFlag = "deck_hall_built", forbiddenFlag = "home_secured"),
                effects = listOf(TextGameEffect(preparedGuardDelta = 3, homeLevelDelta = 1, setFlag = "home_secured")),
            ),
            TycoonVerb("night", "I step outside at night", destinationNodeId = "farmhouse_night"),
        ),
    ),
    TycoonBuildingDef(
        id = "the_house",
        title = "The House",
        district = TycoonDistrict.Residential,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/adams-haven.png",
        coinCost = 4,
        materialCost = 1,
        description = "A 2×2 house card. Replaces the cottage footprint when space allows.",
        placeFlag = "house_l2",
        placeEffects = listOf(TextGameEffect(homeLevelDelta = 1, setFlag = "house_l2")),
        verbs = listOf(
            TycoonVerb("rest", "I rest until morning (restore health)", effects = listOf(TextGameEffect(healthDelta = 99))),
            TycoonVerb(
                id = "secure",
                label = "I secure the house (+3 guard for the next battle)",
                condition = TextGameCondition(requiredFlag = "deck_hall_built", forbiddenFlag = "home_secured"),
                effects = listOf(TextGameEffect(preparedGuardDelta = 3, homeLevelDelta = 1, setFlag = "home_secured")),
            ),
            TycoonVerb(
                id = "meal",
                label = "I share lantern stew (+4 guard, +1 SP for the next battle)",
                condition = TextGameCondition(forbiddenFlag = "meal_shared", minimumDishes = 1),
                effects = listOf(TextGameEffect(dishesDelta = -1, preparedGuardDelta = 4, summonerSpDelta = 1, setFlag = "meal_shared")),
            ),
            TycoonVerb("night", "I step outside at night", destinationNodeId = "farmhouse_night"),
        ),
    ),
    TycoonBuildingDef(
        id = "crop",
        title = "Crop",
        district = TycoonDistrict.Farm,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/maps/farm/unmaintained-ground.png",
        seedCost = 1,
        description = "A 1×1 lanternroot bed. Clearing the plot and planting are the same placement.",
        placeFlag = "crop_planted",
        placeEffects = listOf(
            TextGameEffect(setFlag = "farm_cleared"),
            TextGameEffect(setFlag = "crop_planted"),
        ),
        verbs = listOf(
            TycoonVerb(
                id = "harvest",
                label = "I harvest the mature lanternroot (+2 produce)",
                condition = TextGameCondition(
                    requiredFlags = listOf("silverwood_won", "crop_planted"),
                    forbiddenFlag = "crop_harvested",
                    minimumCropGrowth = 1,
                ),
                effects = listOf(TextGameEffect(harvestDelta = 2, cropGrowthDelta = -1, farmLevelDelta = 1, setFlag = "crop_harvested")),
            ),
            TycoonVerb(
                id = "tend",
                label = "I tend the bed (+1 produce)",
                condition = TextGameCondition(requiredFlag = "crop_planted"),
                effects = listOf(TextGameEffect(harvestDelta = 1)),
            ),
        ),
    ),
    TycoonBuildingDef(
        id = "barn",
        title = "Barn",
        district = TycoonDistrict.Farm,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/starter-base.png",
        coinCost = 8,
        materialCost = 3,
        description = "A 2×2 barn. Unlocks a second bed's worth of yield.",
        placeFlag = "farm_l3",
        placeEffects = listOf(TextGameEffect(farmLevelDelta = 1, setFlag = "farm_l3")),
        verbs = listOf(
            TycoonVerb("tend", "I tend the improved beds (+1 produce)", effects = listOf(TextGameEffect(harvestDelta = 1))),
        ),
    ),
    TycoonBuildingDef(
        id = "kitchen",
        title = "Kitchen",
        district = TycoonDistrict.Farm,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/pacific-bastion-coastal-residence.png",
        coinCost = 7,
        materialCost = 3,
        description = "A 2×2 kitchen card. Surplus we sell. Only one dish buff at a time.",
        placeFlag = "house_l3",
        placeEffects = listOf(TextGameEffect(homeLevelDelta = 1, setFlag = "house_l3")),
        verbs = listOf(
            TycoonVerb(
                id = "cook",
                label = "I cook a party dish — 1 produce",
                condition = TextGameCondition(minimumHarvest = 1),
                effects = listOf(TextGameEffect(harvestDelta = -1, dishesDelta = 1, setFlag = "dish_cooked")),
            ),
            TycoonVerb("enter", "I enter the Kitchen", destinationNodeId = "kitchen"),
        ),
    ),
    TycoonBuildingDef(
        id = "supply",
        title = "Supply",
        district = TycoonDistrict.Farm,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/objects/life-technology-e-xperience-ltx.png",
        coinCost = 2,
        description = "A 1×1 supply crate. Buys field stock for the lots.",
        placeFlag = "bought_supplies",
        placeEffects = listOf(TextGameEffect(seedsDelta = 1, setFlag = "bought_supplies")),
        verbs = listOf(
            TycoonVerb(
                id = "restock",
                label = "I buy field supplies — 2 coins (+1 seed)",
                condition = TextGameCondition(minimumCoins = 2),
                effects = listOf(TextGameEffect(coinsDelta = -2, seedsDelta = 1)),
            ),
        ),
    ),
    TycoonBuildingDef(
        id = "stone",
        title = "Stone",
        district = TycoonDistrict.Farm,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/objects/celestium.png",
        materialCost = 0,
        description = "A 1×1 salvage stone. Farm lots remember what the pickaxe cleared.",
        placeFlag = "farm_l2",
        placeEffects = listOf(TextGameEffect(farmLevelDelta = 1, materialsDelta = 1, setFlag = "farm_l2")),
    ),
    TycoonBuildingDef(
        id = "frosted_mug",
        title = "Frosted Mug",
        district = TycoonDistrict.Commercial,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/the-frosted-mug-inn.png",
        coinCost = 6,
        materialCost = 2,
        description = "A 2×2 inn card. Trade likes company.",
        placeFlag = "frosted_mug_placed",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "frosted_mug_placed")),
        verbs = listOf(
            TycoonVerb("rest", "I take a room and recover", effects = listOf(TextGameEffect(healthDelta = 99))),
        ),
    ),
    TycoonBuildingDef(
        id = "argent_market",
        title = "Argent Market",
        district = TycoonDistrict.Commercial,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/the-argent-bourse.png",
        coinCost = 8,
        materialCost = 2,
        description = "A 2×2 market card. Sells surplus and buys a stronger coat.",
        placeFlag = "town_l2",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "market_placed")),
        verbs = listOf(
            TycoonVerb(
                id = "sell",
                label = "I sell one produce (+3 coins)",
                condition = TextGameCondition(forbiddenFlag = "produce_sold", minimumHarvest = 1),
                effects = listOf(TextGameEffect(harvestDelta = -1, coinsDelta = 3, setFlag = "produce_sold")),
            ),
            TycoonVerb(
                id = "coat",
                label = "I reinforce my Summoner coat — 5 coins, 2 materials (+4 health)",
                condition = TextGameCondition(requiredFlag = "produce_sold", minimumCoins = 5, minimumMaterials = 2),
                effects = listOf(TextGameEffect(coinsDelta = -5, materialsDelta = -2, maxHealthDelta = 4, townLevelDelta = 1, setFlag = "bought_coat")),
            ),
            TycoonVerb(
                id = "seed",
                label = "I buy one loose seed — 2 coins",
                condition = TextGameCondition(minimumCoins = 2),
                effects = listOf(TextGameEffect(coinsDelta = -2, seedsDelta = 1)),
            ),
        ),
    ),
    TycoonBuildingDef(
        id = "scales",
        title = "Scales",
        district = TycoonDistrict.Commercial,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/objects/celestium-clone-bodies.png",
        coinCost = 4,
        materialCost = 1,
        description = "A 1×1 merchant scale. Improves gold from neighbouring trade.",
        placeFlag = "scales_placed",
        placeEffects = listOf(TextGameEffect(setFlag = "scales_placed")),
    ),
    TycoonBuildingDef(
        id = "trading_post",
        title = "Trading Post",
        district = TycoonDistrict.Industrial,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/the-sandcastle.png",
        coinCost = 8,
        materialCost = 3,
        description = "A 2×2 trading post. Moves dungeon ore onto the lots.",
        placeFlag = "trading_post_placed",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "trading_post_placed")),
    ),
    TycoonBuildingDef(
        id = "warehouse",
        title = "Warehouse",
        district = TycoonDistrict.Industrial,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/afm-bunkers-rooms.png",
        coinCost = 12,
        materialCost = 6,
        description = "A 2×2 warehouse. Raises storage and the lot cap.",
        placeFlag = "town_l4",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "town_l4")),
    ),
    TycoonBuildingDef(
        id = "deck_hall",
        title = "Deck Hall",
        district = TycoonDistrict.Governates,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/silverbrook-city.png",
        coinCost = 5,
        materialCost = 2,
        description = "A 2×2 Deck Hall. The first permanent service the lots need, and it unlocks bought expansions.",
        placeFlag = "deck_hall_built",
        placeEffects = listOf(
            TextGameEffect(townLevelDelta = 1, setFlag = "town_l2"),
            TextGameEffect(setFlag = "deck_hall_built"),
        ),
    ),
    TycoonBuildingDef(
        id = "adventure_guild",
        title = "Adventure Guild",
        district = TycoonDistrict.Governates,
        width = 2,
        height = 2,
        artAssetPath = "images/adams_haven/locations/silverbrook-adventure-guild.png",
        coinCost = 8,
        materialCost = 4,
        description = "A 2×2 guild card. Name on the ledger, then we roll.",
        placeFlag = "town_l3",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "town_l3")),
        verbs = listOf(
            TycoonVerb("enter", "I enter the Guild Hall", destinationNodeId = "guild_summon"),
        ),
    ),
    TycoonBuildingDef(
        id = "forest_gate",
        title = "Forest Gate",
        district = TycoonDistrict.Governates,
        width = 1,
        height = 1,
        artAssetPath = "images/adams_haven/locations/silverwood-forest.png",
        coinCost = 10,
        materialCost = 4,
        description = "A 1×1 forest gate. Annexes the largest lot map (20×12).",
        placeFlag = "town_l5",
        placeEffects = listOf(TextGameEffect(townLevelDelta = 1, setFlag = "town_l5")),
    ),
)

fun tycoonOccupiedCells(board: TycoonBoardState): Set<Pair<Int, Int>> = buildSet {
    board.placements.forEach { placement ->
        val def = tycoonBuilding(placement.buildingId) ?: return@forEach
        addAll(tycoonCells(placement.x, placement.y, def.width, def.height))
    }
}

fun tycoonCells(x: Int, y: Int, width: Int, height: Int): List<Pair<Int, Int>> =
    (0 until height).flatMap { dy -> (0 until width).map { dx -> x + dx to y + dy } }

fun tycoonCanPlace(board: TycoonBoardState, buildingId: String, x: Int, y: Int): Boolean {
    val def = tycoonBuilding(buildingId) ?: return false
    if (x < 0 || y < 0 || x + def.width > board.width || y + def.height > board.height) return false
    val occupied = tycoonOccupiedCells(board)
    val cells = tycoonCells(x, y, def.width, def.height)
    if (cells.any { it in occupied }) return false
    return cells.all { (cx, cy) -> tycoonDistrictAt(cx, cy, board.width, board.height) == def.district }
}

fun tycoonBoardCap(flags: List<String>): Pair<Int, Int> = when {
    "town_l5" in flags -> TYCOON_MAX_WIDTH to TYCOON_MAX_HEIGHT
    "town_l3" in flags -> 12 to 8
    "deck_hall_built" in flags -> 8 to 6
    else -> 6 to 6
}

fun tycoonNextSize(width: Int, height: Int, cap: Pair<Int, Int>): Pair<Int, Int>? {
    val (maxW, maxH) = cap
    if (width >= maxW && height >= maxH) return null
    return if (width <= height && width < maxW) {
        (width + 1).coerceAtMost(maxW) to height
    } else if (height < maxH) {
        width to (height + 1).coerceAtMost(maxH)
    } else if (width < maxW) {
        (width + 1).coerceAtMost(maxW) to height
    } else {
        null
    }
}

enum class TycoonExpandWay { Gold, Dungeon, Farm, Annex }

fun tycoonExpandGoldCost(expansionsBought: Int): Pair<Int, Int> =
    (6 + 3 * expansionsBought) to (1 + expansionsBought / 2)

fun tycoonBonuses(board: TycoonBoardState): TycoonBonuses {
    val byDistrict = TycoonDistrict.entries.associateWith { district ->
        board.placements.count { tycoonBuilding(it.buildingId)?.district == district }
    }
    val neighbourGold = board.placements.count { placement ->
        val def = tycoonBuilding(placement.buildingId) ?: return@count false
        def.district == TycoonDistrict.Commercial && hasNeighbour(board, placement, TycoonDistrict.Commercial)
    }
    val neighbourHomes = board.placements.count { placement ->
        val def = tycoonBuilding(placement.buildingId) ?: return@count false
        def.district == TycoonDistrict.Residential && hasNeighbour(board, placement, TycoonDistrict.Residential)
    }
    val farmTiles = byDistrict.getValue(TycoonDistrict.Farm)
    return TycoonBonuses(
        yieldPercent = (farmTiles * 12 + (byDistrict[TycoonDistrict.Industrial] ?: 0) * 5).coerceAtMost(99),
        goldPercent = ((byDistrict[TycoonDistrict.Commercial] ?: 0) * 15 + neighbourGold * 10).coerceAtMost(99),
        comfortPercent = ((byDistrict[TycoonDistrict.Residential] ?: 0) * 12 + neighbourHomes * 8).coerceAtMost(99),
    )
}

private fun hasNeighbour(board: TycoonBoardState, placement: TycoonPlacement, district: TycoonDistrict): Boolean {
    val def = tycoonBuilding(placement.buildingId) ?: return false
    val mine = tycoonCells(placement.x, placement.y, def.width, def.height).toSet()
    return board.placements.any { other ->
        if (other.id == placement.id) return@any false
        val otherDef = tycoonBuilding(other.buildingId) ?: return@any false
        if (otherDef.district != district) return@any false
        tycoonCells(other.x, other.y, otherDef.width, otherDef.height).any { (x, y) ->
            mine.any { (mx, my) -> kotlin.math.abs(mx - x) + kotlin.math.abs(my - y) == 1 }
        }
    }
}

fun tycoonDistrictCounts(board: TycoonBoardState): Map<TycoonDistrict, Int> =
    TycoonDistrict.entries.associateWith { district ->
        board.placements.count { tycoonBuilding(it.buildingId)?.district == district }
    }
