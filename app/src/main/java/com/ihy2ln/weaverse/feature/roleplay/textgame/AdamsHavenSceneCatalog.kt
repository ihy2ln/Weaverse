package com.ihy2ln.weaverse.feature.roleplay.textgame

/**
 * Godot Adams Haven scene art exposed through WeaverVerse's Pictures library.
 *
 * Sourced from AdamsHavenCardGame `art/landscape`, `art/world`, `art/map`, and
 * the map tiles already under `images/adams_haven/maps/`.
 */
fun adamsHavenSceneCatalog(): List<TextGameSceneAsset> = buildList {
    // Crossroads / farm / town ground tiles (ModeStub placement screens).
    add(scene("crossroads-four-way", listOf("crossroads"), "maps/crossroads/four-way-road.png", 1254, 1254))
    add(scene("crossroads-four-way-textured", listOf("crossroads"), "maps/crossroads/four-way-road-textured.png", 1254, 1254))
    add(scene("farm-unmaintained", listOf("farm-tile"), "maps/farm/unmaintained-ground.png", 1254, 1254))
    add(scene("farm-board", listOf("farm"), "locations/landscape-farm.png", 1536, 1024, categoryFolder = "Farm Board"))
    add(scene("farm-ground", listOf("farm-tile"), "world/ground_farm.png", 1024, 1024, categoryFolder = "World"))
    add(scene("town-board", listOf("town"), "locations/town-lot-painterly.png", 1024, 1536, categoryFolder = "Town Board"))

    listOf(
        "road-straight" to "maps/town/road-straight.png",
        "road-curve-east" to "maps/town/road-curve-east.png",
        "road-curve-west" to "maps/town/road-curve-west.png",
        "road-curve-soft" to "maps/town/road-curve-soft.png",
    ).forEach { (id, path) -> add(scene("town-$id", listOf("town-tile"), path, 1254, 1254)) }

    // Full-bleed landscape backdrops from Farm.tscn / Town.tscn.
    add(
        scene(
            id = "landscape-farm",
            types = listOf("landscape"),
            path = "locations/landscape-farm.png",
            width = 1536,
            height = 1024,
            categoryFolder = "Landscape",
        ),
    )
    add(
        scene(
            id = "landscape-town",
            types = listOf("landscape"),
            path = "locations/landscape-town.png",
            width = 1536,
            height = 1024,
            categoryFolder = "Landscape",
        ),
    )

    // Playable dungeon map sheets (Dungeon.tscn / map UI).
    add(scene("map-blank", listOf("dungeon", "map"), "maps/playable/blank.webp", 1254, 1254, categoryFolder = "Map"))
    add(scene("map-floor", listOf("dungeon", "map"), "maps/playable/floor.webp", 2048, 2048, categoryFolder = "Map"))
    add(scene("map-fog", listOf("dungeon", "map"), "maps/playable/fog.webp", 2048, 2048, categoryFolder = "Map"))

    (1..6).forEach { index ->
        add(scene("silverwood-$index", listOf("battle", "dungeon"), "maps/battle/silverwood-$index.png", 1024, 1536))
    }

    // World placement props from Home / Town / Farm ModeStub scenes.
    worldBuildings().forEach { (id, file, w, h, types) ->
        add(
            scene(
                id = "world-$id",
                types = types,
                path = "world/$file",
                width = w,
                height = h,
                categoryFolder = "World",
            ),
        )
    }
}

private fun worldBuildings(): List<WorldBuilding> = listOf(
    WorldBuilding("deck-hall", "deck_hall.png", 512, 1024, listOf("deck_hall", "building")),
    WorldBuilding("guild-hall", "guild_hall.png", 512, 1024, listOf("guild_hall", "building")),
    WorldBuilding("frosted-mug", "frosted_mug.png", 512, 1024, listOf("inn", "building")),
    WorldBuilding("market", "market.png", 512, 1024, listOf("market", "building")),
    WorldBuilding("house", "house.png", 512, 1024, listOf("home", "building")),
    WorldBuilding("gate", "gate.png", 512, 768, listOf("dungeon", "building")),
    WorldBuilding("cottage", "cottage.png", 256, 512, listOf("home", "building")),
    WorldBuilding("painterly-cottage", "painterly-cottage.png", 857, 858, listOf("home", "building")),
    WorldBuilding("barn", "barn.png", 512, 768, listOf("barn", "building")),
    WorldBuilding("kitchen", "kitchen.png", 512, 768, listOf("home", "building")),
    WorldBuilding("silo", "silo.png", 256, 1024, listOf("barn", "building")),
    WorldBuilding("stall", "stall.png", 256, 512, listOf("market", "building")),
    WorldBuilding("well", "well.png", 256, 256, listOf("building")),
    WorldBuilding("tree", "tree.png", 256, 512, listOf("building")),
    WorldBuilding("door", "door.png", 256, 256, listOf("home", "building")),
    WorldBuilding("workbench", "workbench.png", 256, 256, listOf("home", "building")),
)

private data class WorldBuilding(
    val id: String,
    val file: String,
    val width: Int,
    val height: Int,
    val types: List<String>,
)

private fun scene(
    id: String,
    types: List<String>,
    path: String,
    width: Int,
    height: Int,
    categoryFolder: String = types.joinToString(" & ") { it.replaceFirstChar(Char::uppercase) },
) = TextGameSceneAsset(
    id = id,
    sceneTypes = types,
    mediaId = "adams-haven-map-$id",
    artAssetPath = "images/adams_haven/$path",
    width = width,
    height = height,
    displayName = id.split('-').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) },
    category = "Adams Haven / Scene / $categoryFolder",
    tags = listOf("adams-haven", "text-game", "scene", "map") +
        types.map { "scene:$it" } + id.split('-'),
)
