package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable

/** Town or Farm plot picture used as the card board. */
enum class HavenBoardKind(val id: String) {
    Town("town"),
    Farm("farm"),
    ;

    companion object {
        fun fromId(id: String): HavenBoardKind? = entries.firstOrNull { it.id == id }
    }
}

enum class HavenCardKind { Building, Upgrade }

data class HavenCardDef(
    val id: String,
    val name: String,
    val kind: HavenCardKind,
    val board: HavenBoardKind?,
    /** TownRules / Farm building id for flags and visit routing. */
    val buildingId: String,
    /** Building card id this upgrade stacks onto. */
    val stacksOn: String? = null,
    val builtFlag: String? = null,
    val cardArtPath: String,
    val roomArtPath: String,
    val visitNodeId: String? = null,
    val defaultX: Float = 0.5f,
    val defaultY: Float = 0.5f,
)

@Serializable
data class PlacedHavenCard(
    val cardId: String,
    val board: String,
    val x: Float,
    val y: Float,
    val upgradeIds: List<String> = emptyList(),
)

@Serializable
data class HavenBoardState(
    val placed: List<PlacedHavenCard> = emptyList(),
    val hand: List<String> = emptyList(),
)

object HavenBoardRules {
    const val TOWN_BACKDROP = "images/adams_haven/haven/boards/town.png"
    const val FARM_BACKDROP = "images/adams_haven/haven/boards/farm.png"

    /** Shared card back and plot marker from the Adams Haven settlement card set. */
    const val CARD_BACK = "images/adams_haven/haven/tables/settlement_card_back.webp"
    const val LOT_MARKER = "images/adams_haven/haven/tables/lot_marker.webp"

    /**
     * Building and upgrade cards, faced with the Adams Haven `art/build` and `art/room`
     * sets. The card picture is the building seen from outside; the room picture is what
     * you see on stepping inside, so an interior always matches the card that was tapped
     * even where several buildings share one conversation node.
     */
    val CARDS: List<HavenCardDef> = buildList {
        // Story buildings, auto-placed by their built flag as the campaign unlocks them.
        add(
            building(
                id = "shack", name = "Shack", board = HavenBoardKind.Town,
                buildingId = "house", art = "cottage", room = "hearth",
                visit = "home", builtFlag = null, x = 0.72f, y = 0.48f,
            ),
        )
        add(
            building(
                id = "deck_hall", name = "Deck Hall", board = HavenBoardKind.Town,
                buildingId = "deck_hall", art = "deck_hall", room = "contract_board",
                visit = "deck_hall", builtFlag = "deck_hall_built", x = 0.28f, y = 0.52f,
            ),
        )
        add(
            building(
                id = "frosted_mug", name = "Frosted Mug", board = HavenBoardKind.Town,
                buildingId = "frosted_mug", art = "frosted_mug", room = "taproom",
                visit = "frosted_mug", builtFlag = "mug_built", x = 0.68f, y = 0.62f,
            ),
        )
        add(
            building(
                id = "market", name = "Market", board = HavenBoardKind.Town,
                buildingId = "market", art = "market", room = "stalls",
                visit = "market", builtFlag = "market_built", x = 0.32f, y = 0.68f,
            ),
        )
        add(
            building(
                id = "guild_hall", name = "Guild Hall", board = HavenBoardKind.Town,
                buildingId = "guild_hall", art = "guild_hall", room = "trophy_hall",
                visit = "guild_hall", builtFlag = "town_l3", x = 0.24f, y = 0.34f,
            ),
        )
        add(
            building(
                id = "workshop", name = "Workshop", board = HavenBoardKind.Town,
                buildingId = "workshop", art = "forge", room = "anvil",
                visit = null, builtFlag = "town_l4", x = 0.52f, y = 0.42f,
            ),
        )
        add(
            building(
                id = "plaza", name = "Well", board = HavenBoardKind.Town,
                buildingId = "plaza", art = "well", room = "shared_yard",
                visit = "town", builtFlag = "town_l5", x = 0.48f, y = 0.58f,
            ),
        )
        add(
            building(
                id = "kitchen", name = "Kitchen", board = HavenBoardKind.Farm,
                buildingId = "kitchen", art = "kitchen", room = "stove",
                visit = "kitchen", builtFlag = "farm_l2", x = 0.62f, y = 0.38f,
            ),
        )
        add(
            building(
                id = "barn", name = "Barn", board = HavenBoardKind.Farm,
                buildingId = "barn", art = "barn", room = "byre",
                visit = "barn", builtFlag = "farm_l3", x = 0.34f, y = 0.36f,
            ),
        )

        // Settlement buildings, earned from battle spoils once the story set is complete.
        add(
            building(
                id = "manor", name = "Manor", board = HavenBoardKind.Town,
                art = "manor", room = "solar", visit = "home", x = 0.8f, y = 0.34f,
            ),
        )
        add(
            building(
                id = "terrace_row", name = "Terrace Row", board = HavenBoardKind.Town,
                art = "terrace_row", room = "guest_room", visit = "home", x = 0.86f, y = 0.56f,
            ),
        )
        add(
            building(
                id = "boarding_house", name = "Boarding House", board = HavenBoardKind.Town,
                art = "boarding_house", room = "dormitory", visit = "frosted_mug", x = 0.78f, y = 0.72f,
            ),
        )
        add(
            building(
                id = "moonwell_baths", name = "Moonwell Baths", board = HavenBoardKind.Town,
                art = "moonwell_baths", room = "bathing_hall", visit = "frosted_mug", x = 0.6f, y = 0.76f,
            ),
        )
        add(
            building(
                id = "trading_post", name = "Trading Post", board = HavenBoardKind.Town,
                art = "trading_post", room = "bonded_store", visit = "market", x = 0.2f, y = 0.6f,
            ),
        )
        add(
            building(
                id = "auction_house", name = "Auction House", board = HavenBoardKind.Town,
                art = "auction_house", room = "auction_floor", visit = "market", x = 0.14f, y = 0.46f,
            ),
        )
        add(
            building(
                id = "weavers_exchange", name = "Weavers Exchange", board = HavenBoardKind.Town,
                art = "weavers_exchange", room = "loom_vault", visit = "market", x = 0.4f, y = 0.78f,
            ),
        )
        add(
            building(
                id = "windmill", name = "Windmill", board = HavenBoardKind.Town,
                art = "windmill", room = "millstone", visit = "market", x = 0.12f, y = 0.22f,
            ),
        )
        add(
            building(
                id = "town_hall", name = "Town Hall", board = HavenBoardKind.Town,
                art = "town_hall", room = "council_chamber", visit = "guild_hall", x = 0.44f, y = 0.24f,
            ),
        )
        add(
            building(
                id = "council_archive", name = "Council Archive", board = HavenBoardKind.Town,
                art = "council_archive", room = "records_vault", visit = "guild_hall", x = 0.34f, y = 0.18f,
            ),
        )
        add(
            building(
                id = "watch_barracks", name = "Watch Barracks", board = HavenBoardKind.Town,
                art = "watch_barracks", room = "training_floor", visit = "guild_hall", x = 0.58f, y = 0.2f,
            ),
        )
        add(
            building(
                id = "courier_post", name = "Courier Post", board = HavenBoardKind.Town,
                art = "courier_post", room = "ledger_desk", visit = "deck_hall", x = 0.18f, y = 0.72f,
            ),
        )
        add(
            building(
                id = "caravan_depot", name = "Caravan Depot", board = HavenBoardKind.Town,
                art = "caravan_depot", room = "loading_dock", visit = "deck_hall", x = 0.26f, y = 0.84f,
            ),
        )
        add(
            building(
                id = "waystation", name = "Waystation", board = HavenBoardKind.Town,
                art = "waystation", room = "back_room", visit = "deck_hall", x = 0.72f, y = 0.86f,
            ),
        )
        add(
            building(
                id = "warehouse", name = "Warehouse", board = HavenBoardKind.Town,
                art = "warehouse", room = "dry_store", visit = "deck_hall", x = 0.86f, y = 0.8f,
            ),
        )
        add(
            building(
                id = "lumber_mill", name = "Lumber Mill", board = HavenBoardKind.Town,
                art = "lumber_mill", room = "saw_floor", visit = "deck_hall", x = 0.14f, y = 0.86f,
            ),
        )
        add(
            building(
                id = "quarry", name = "Quarry", board = HavenBoardKind.Town,
                art = "quarry", room = "cutting_yard", visit = "deck_hall", x = 0.88f, y = 0.2f,
            ),
        )
        add(
            building(
                id = "foundry", name = "Foundry", board = HavenBoardKind.Town,
                art = "foundry", room = "smelting_floor", visit = "deck_hall", x = 0.66f, y = 0.3f,
            ),
        )
        add(
            building(
                id = "glassworks", name = "Glassworks", board = HavenBoardKind.Town,
                art = "glassworks", room = "glass_furnace", visit = "deck_hall", x = 0.54f, y = 0.86f,
            ),
        )
        add(
            building(
                id = "builders_guild", name = "Builders Guild", board = HavenBoardKind.Town,
                art = "builders_guild", room = "hoist", visit = "deck_hall", x = 0.4f, y = 0.34f,
            ),
        )
        add(
            building(
                id = "silo", name = "Silo", board = HavenBoardKind.Farm,
                art = "silo", room = "larder", visit = "barn", x = 0.18f, y = 0.3f,
            ),
        )
        add(
            building(
                id = "stable", name = "Stable", board = HavenBoardKind.Farm,
                art = "stable", room = "hayloft", visit = "barn", x = 0.8f, y = 0.3f,
            ),
        )
        add(
            building(
                id = "pasture", name = "Pasture", board = HavenBoardKind.Farm,
                art = "pasture", room = "awning", visit = "barn", x = 0.86f, y = 0.62f,
            ),
        )
        add(
            building(
                id = "glasshouse", name = "Glasshouse", board = HavenBoardKind.Farm,
                art = "glasshouse", room = "seed_vault", visit = "barn", x = 0.14f, y = 0.62f,
            ),
        )
        add(
            building(
                id = "irrigation_tower", name = "Irrigation Tower", board = HavenBoardKind.Farm,
                art = "irrigation_tower", room = "press_shed", visit = "barn", x = 0.5f, y = 0.22f,
            ),
        )
        add(
            building(
                id = "orchard", name = "Orchard", board = HavenBoardKind.Farm,
                art = "orchard", room = "drying_rack", visit = "kitchen", x = 0.46f, y = 0.76f,
            ),
        )
        add(
            building(
                id = "apiary", name = "Apiary", board = HavenBoardKind.Farm,
                art = "apiary", room = "racking", visit = "kitchen", x = 0.72f, y = 0.8f,
            ),
        )
        add(
            building(
                id = "fish_pond", name = "Fish Pond", board = HavenBoardKind.Farm,
                art = "fish_pond", room = "scales", visit = "kitchen", x = 0.26f, y = 0.8f,
            ),
        )

        // Upgrades stack onto a placed building and take over its interior picture.
        add(upgrade(id = "upgrade-house-kitchen", name = "House Kitchen", buildingId = "house", stacksOn = "shack", room = "stove"))
        add(upgrade(id = "upgrade-house-workshop", name = "House Workshop", buildingId = "house", stacksOn = "shack", room = "anvil"))
        add(upgrade(id = "upgrade-inn-hearth", name = "Inn Hearth", buildingId = "frosted_mug", stacksOn = "frosted_mug", room = "hearth"))
        add(upgrade(id = "upgrade-market-stall", name = "Market Stall", buildingId = "market", stacksOn = "market", room = "stalls"))
        add(upgrade(id = "upgrade-guild-board", name = "Guild Board", buildingId = "guild_hall", stacksOn = "guild_hall", room = "contract_board"))
    }

    private fun building(
        id: String,
        name: String,
        board: HavenBoardKind,
        art: String,
        room: String,
        buildingId: String = id,
        visit: String? = null,
        builtFlag: String? = null,
        x: Float = 0.5f,
        y: Float = 0.5f,
    ) = HavenCardDef(
        id = "haven/$id",
        name = name,
        kind = HavenCardKind.Building,
        board = board,
        buildingId = buildingId,
        builtFlag = builtFlag,
        cardArtPath = buildArt(art),
        roomArtPath = roomArt(room),
        visitNodeId = visit,
        defaultX = x,
        defaultY = y,
    )

    private fun upgrade(
        id: String,
        name: String,
        buildingId: String,
        stacksOn: String,
        room: String,
    ) = HavenCardDef(
        id = "haven/$id",
        name = name,
        kind = HavenCardKind.Upgrade,
        board = null,
        buildingId = buildingId,
        stacksOn = "haven/$stacksOn",
        cardArtPath = roomArt(room),
        roomArtPath = roomArt(room),
    )

    private fun buildArt(stem: String) = "images/adams_haven/haven/build/$stem.webp"

    private fun roomArt(stem: String) = "images/adams_haven/haven/room/rm_$stem.webp"

    /** Grant order for battle spoils, first unowned card goes to hand. */
    val REWARD_ORDER: List<String> = listOf(
        "haven/deck_hall",
        "haven/kitchen",
        "haven/frosted_mug",
        "haven/market",
        "haven/barn",
        "haven/guild_hall",
        "haven/workshop",
        "haven/plaza",
        "haven/upgrade-house-kitchen",
        "haven/upgrade-inn-hearth",
        "haven/upgrade-market-stall",
        "haven/upgrade-guild-board",
        "haven/upgrade-house-workshop",
        "haven/manor",
        "haven/terrace_row",
        "haven/boarding_house",
        "haven/moonwell_baths",
        "haven/trading_post",
        "haven/auction_house",
        "haven/weavers_exchange",
        "haven/windmill",
        "haven/town_hall",
        "haven/council_archive",
        "haven/watch_barracks",
        "haven/courier_post",
        "haven/caravan_depot",
        "haven/waystation",
        "haven/warehouse",
        "haven/lumber_mill",
        "haven/quarry",
        "haven/foundry",
        "haven/glassworks",
        "haven/builders_guild",
        "haven/silo",
        "haven/stable",
        "haven/pasture",
        "haven/glasshouse",
        "haven/irrigation_tower",
        "haven/orchard",
        "haven/apiary",
        "haven/fish_pond",
    )
    fun def(cardId: String): HavenCardDef? = CARDS.firstOrNull { it.id == cardId }

    fun initial(): HavenBoardState = HavenBoardState(
        placed = listOf(
            PlacedHavenCard(
                cardId = "haven/shack",
                board = HavenBoardKind.Town.id,
                x = def("haven/shack")!!.defaultX,
                y = def("haven/shack")!!.defaultY,
            ),
        ),
        hand = emptyList(),
    )

    fun boardKind(nodeId: String): HavenBoardKind? = when (nodeId) {
        "town", "return_town" -> HavenBoardKind.Town
        "farm", "return_farm" -> HavenBoardKind.Farm
        else -> null
    }

    fun placedOn(board: HavenBoardState, kind: HavenBoardKind): List<PlacedHavenCard> =
        board.placed.filter { it.board == kind.id }

    fun handForBoard(board: HavenBoardState, kind: HavenBoardKind): List<String> =
        board.hand.filter { id -> def(id)?.board == kind }

    fun ownsCard(board: HavenBoardState, cardId: String): Boolean =
        board.placed.any { it.cardId == cardId } ||
            board.hand.contains(cardId) ||
            board.placed.any { cardId in it.upgradeIds }

    fun nextRewardCard(board: HavenBoardState): String? =
        REWARD_ORDER.firstOrNull { !ownsCard(board, it) }

    fun grantReward(board: HavenBoardState): HavenBoardState {
        val next = nextRewardCard(board) ?: return board
        if (board.hand.contains(next)) return board
        return board.copy(hand = board.hand + next)
    }

    fun place(
        board: HavenBoardState,
        cardId: String,
        kind: HavenBoardKind,
        x: Float,
        y: Float,
    ): HavenBoardState? {
        val def = def(cardId) ?: return null
        if (def.kind != HavenCardKind.Building || def.board != kind) return null
        if (!board.hand.contains(cardId)) return null
        if (board.placed.any { it.cardId == cardId }) return null
        return board.copy(
            hand = board.hand - cardId,
            placed = board.placed + PlacedHavenCard(
                cardId = cardId,
                board = kind.id,
                x = x.coerceIn(0.08f, 0.92f),
                y = y.coerceIn(0.12f, 0.88f),
            ),
        )
    }

    fun stack(
        board: HavenBoardState,
        buildingCardId: String,
        upgradeId: String,
    ): HavenBoardState? {
        val upgrade = def(upgradeId) ?: return null
        if (upgrade.kind != HavenCardKind.Upgrade || upgrade.stacksOn != buildingCardId) return null
        if (!board.hand.contains(upgradeId)) return null
        val idx = board.placed.indexOfFirst { it.cardId == buildingCardId }
        if (idx < 0) return null
        val placed = board.placed[idx]
        if (upgradeId in placed.upgradeIds) return null
        val nextPlaced = board.placed.toMutableList()
        nextPlaced[idx] = placed.copy(upgradeIds = placed.upgradeIds + upgradeId)
        return board.copy(hand = board.hand - upgradeId, placed = nextPlaced)
    }

    fun move(
        board: HavenBoardState,
        cardId: String,
        x: Float,
        y: Float,
    ): HavenBoardState? {
        val idx = board.placed.indexOfFirst { it.cardId == cardId }
        if (idx < 0) return null
        val next = board.placed.toMutableList()
        next[idx] = next[idx].copy(
            x = x.coerceIn(0.08f, 0.92f),
            y = y.coerceIn(0.12f, 0.88f),
        )
        return board.copy(placed = next)
    }

    fun roomArt(placed: PlacedHavenCard): String {
        val topUpgrade = placed.upgradeIds.lastOrNull()?.let(::def)
        if (topUpgrade != null) return topUpgrade.roomArtPath
        return def(placed.cardId)?.roomArtPath.orEmpty()
    }

    fun visitNode(placed: PlacedHavenCard): String? = def(placed.cardId)?.visitNodeId

    /** When a story choice sets a built flag, auto-place the matching card if it is not on the board yet. */
    fun autoPlaceFromFlags(board: HavenBoardState, flags: List<String>): HavenBoardState {
        var next = board
        CARDS.filter { it.kind == HavenCardKind.Building && it.builtFlag != null }.forEach { card ->
            if (card.builtFlag !in flags) return@forEach
            if (next.placed.any { it.cardId == card.id }) return@forEach
            val kind = card.board ?: return@forEach
            next = if (next.hand.contains(card.id)) {
                place(next, card.id, kind, card.defaultX, card.defaultY) ?: next
            } else {
                next.copy(
                    placed = next.placed + PlacedHavenCard(
                        cardId = card.id,
                        board = kind.id,
                        x = card.defaultX,
                        y = card.defaultY,
                    ),
                )
            }
        }
        return next
    }

    fun backdrop(kind: HavenBoardKind): String = when (kind) {
        HavenBoardKind.Town -> TOWN_BACKDROP
        HavenBoardKind.Farm -> FARM_BACKDROP
    }
}

fun isHavenBoardNode(nodeId: String): Boolean = HavenBoardRules.boardKind(nodeId) != null
