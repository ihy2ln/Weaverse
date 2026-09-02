package com.ihy2ln.weaverse.feature.roleplay.town

/** What a building does when you walk into it. */
enum class TownLocationKind {
    Commerce,
    Blacksmith,
    Chief,
    Tavern,
    Apothecary,
    Stables,
}

/**
 * A building on the town strip.
 *
 * [xPercent] is the doorway's position along the whole map (0–100), so the same
 * layout works at any screen size and against any background art of any width.
 */
data class TownLocation(
    val id: String,
    val name: String,
    val kind: TownLocationKind,
    val xPercent: Float,
    val blurb: String,
    val actions: List<String>,
)

/** One item in the game-style shop catalog. */
data class ShopGood(val name: String, val note: String, val priceGold: Int = 1)

object TownMap {
    /**
     * Positions follow the reference art: two rows of buildings either side of a
     * road that runs up the middle, so the doorways cluster left and right of ~50%.
     */
    val locations: List<TownLocation> = listOf(
        TownLocation(
            id = "store",
            name = "General Store",
            kind = TownLocationKind.Commerce,
            xPercent = 12f,
            blurb = "Barrels out front and a counter worn smooth by decades of trade.",
            actions = listOf("Browse goods", "Ask about the road"),
        ),
        TownLocation(
            id = "blacksmith",
            name = "Blacksmith",
            kind = TownLocationKind.Blacksmith,
            xPercent = 28f,
            blurb = "The forge is banked but still ticking with heat.",
            actions = listOf("Buy gear", "Ask for repairs"),
        ),
        TownLocation(
            id = "tavern",
            name = "The Long Rest",
            kind = TownLocationKind.Tavern,
            xPercent = 41f,
            blurb = "Low voices, a fire, and someone who knows something.",
            actions = listOf("Buy a round", "Listen for rumours"),
        ),
        TownLocation(
            id = "chief",
            name = "Chief's House",
            kind = TownLocationKind.Chief,
            xPercent = 62f,
            blurb = "The tallest roof in town, and the only door with a guard.",
            actions = listOf("Request an audience", "Ask about the trouble"),
        ),
        TownLocation(
            id = "apothecary",
            name = "Apothecary",
            kind = TownLocationKind.Apothecary,
            xPercent = 78f,
            blurb = "Jars, dried bundles, and a smell that clears your head.",
            actions = listOf("Buy remedies", "Ask about the sickness"),
        ),
        TownLocation(
            id = "stables",
            name = "Stables",
            kind = TownLocationKind.Stables,
            xPercent = 92f,
            blurb = "Straw, leather, and the road out of town just beyond.",
            actions = listOf("Buy supplies", "Leave town"),
        ),
    )

    /** How close the player must stand before a door can be entered, in percent. */
    const val REACH_PERCENT = 6f

    fun nearest(playerPercent: Float): TownLocation? =
        locations.minByOrNull { kotlin.math.abs(it.xPercent - playerPercent) }
            ?.takeIf { kotlin.math.abs(it.xPercent - playerPercent) <= REACH_PERCENT }

    fun goodsFor(kind: TownLocationKind): List<ShopGood> = when (kind) {
        TownLocationKind.Commerce -> listOf(
            ShopGood("Rope", "50ft, hemp", 1),
            ShopGood("Lantern", "Burns about six hours", 5),
            ShopGood("Rations", "A week, dried", 4),
            ShopGood("Bedroll", "Waxed against damp", 2),
        )
        TownLocationKind.Blacksmith -> listOf(
            ShopGood("Iron helm", "Dented, honest", 12),
            ShopGood("Short sword", "Well balanced", 10),
            ShopGood("Buckler", "Strapped, not held", 8),
            ShopGood("Chain shirt", "Heavy but quiet", 50),
        )
        TownLocationKind.Apothecary -> listOf(
            ShopGood("Healing draught", "Bitter, works", 25),
            ShopGood("Antivenom", "For the marsh road", 18),
            ShopGood("Bandages", "Boiled linen", 2),
        )
        TownLocationKind.Stables -> listOf(
            ShopGood("Feed sack", "Two days", 2),
            ShopGood("Saddlebags", "Waxed canvas", 8),
        )
        TownLocationKind.Tavern -> listOf(
            ShopGood("Hot meal", "Whatever is in the pot", 1),
            ShopGood("Room for the night", "Straw mattress", 2),
        )
        TownLocationKind.Chief -> emptyList()
    }
}
