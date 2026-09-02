package com.ihy2ln.weaverse.feature.roleplay.textgame

/** Canonical Godot map art exposed through WeaverVerse's shared Pictures library. */
fun adamsHavenSceneCatalog(): List<TextGameSceneAsset> = buildList {
    add(scene("crossroads-four-way", listOf("crossroads"), "crossroads/four-way-road.png", 1254, 1254))
    add(scene("crossroads-four-way-textured", listOf("crossroads"), "crossroads/four-way-road-textured.png", 1254, 1254))
    add(scene("farm-unmaintained", listOf("farm"), "farm/unmaintained-ground.png", 1254, 1254))

    listOf(
        "road-straight" to "town/road-straight.png",
        "road-curve-east" to "town/road-curve-east.png",
        "road-curve-west" to "town/road-curve-west.png",
        "road-curve-soft" to "town/road-curve-soft.png",
    ).forEach { (id, path) -> add(scene("town-$id", listOf("town"), path, 1254, 1254)) }

    (1..6).forEach { index ->
        add(scene("silverwood-$index", listOf("battle", "dungeon"), "battle/silverwood-$index.png", 1024, 1536))
    }
}

private fun scene(id: String, types: List<String>, path: String, width: Int, height: Int) = TextGameSceneAsset(
    id = id,
    sceneTypes = types,
    mediaId = "adams-haven-map-$id",
    artAssetPath = "images/adams_haven/maps/$path",
    width = width,
    height = height,
    displayName = id.split('-').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) },
    category = "Adams Haven / Scene / " + types.joinToString(" & ") { it.replaceFirstChar(Char::uppercase) },
    tags = listOf("adams-haven", "text-game", "scene", "map") +
        types.map { "scene:$it" } + id.split('-'),
)
