package com.ihy2ln.weaverse.feature.roleplay.textgame

enum class TextGamePlayStyle(val label: String, val description: String) {
    Campaign("Campaign", "Branching story, exploration, battles, and lasting choices."),
    Endless("Endless Battles", "Fight, recover, and immediately enter the next encounter."),
    Simulation("Haven Simulation", "Seat building cards on the Silverbrook lots and take optional patrol battles."),
}

fun adamsHavenDefinition(style: TextGamePlayStyle): TextGameDefinition = when (style) {
    TextGamePlayStyle.Campaign -> adamsHavenTutorial()
    TextGamePlayStyle.Endless -> adamsHavenEndless()
    TextGamePlayStyle.Simulation -> adamsHavenSimulation()
}

private fun adamsHavenEndless(): TextGameDefinition {
    val base = adamsHavenTutorial()
    return base.copy(
        id = "adams_haven_endless",
        title = "Adams Haven: Endless Draw",
        subtitle = "Battle-only challenge loop",
        startNodeId = "endless_battle",
        nodes = listOf(
            TextGameNode(
                id = "endless_battle",
                type = TextGameNodeType.Battle,
                title = "The Unending Lantern",
                prose = "Another pair of enemies steps from the dark. I choose targets, sequence our shared hand, and keep my formation alive.",
                sceneMediaId = "adams-haven-characters-ruinous-maw",
                bundledSceneAssetPath = "images/adams_haven/characters/ruinous-maw.png",
                encounterId = "endless_encounter",
                victoryNodeId = "endless_between",
                defeatNodeId = "endless_defeat",
                victoryCoins = 2,
                victorySp = 1,
                victoryFlag = "endless_victory",
            ),
            TextGameNode(
                id = "endless_between",
                type = TextGameNodeType.Hub,
                title = "Between Draws",
                prose = "My case restores the party. I feel the next fight forming before the last card cools.",
                sceneMediaId = "adams-haven-objects-celestium",
                bundledSceneAssetPath = "images/adams_haven/objects/celestium.png",
                choices = listOf(
                    TextGameChoice("next_fight", "I enter the next fight", "endless_battle"),
                ),
            ),
            TextGameNode(
                id = "endless_defeat",
                type = TextGameNodeType.Ending,
                title = "The Draw Ends",
                prose = "My battle streak ends here. I restart when I want a fresh endless run.",
                sceneMediaId = "adams-haven-locations-adams-haven",
                bundledSceneAssetPath = "images/adams_haven/locations/adams-haven.png",
                sceneMotionMediaId = "adams-haven-motion-home",
                bundledSceneMotionAssetPath = "videos/adams_haven/home.mp4",
            ),
        ),
        encounters = listOf(
            TextGameEncounter(
                id = "endless_encounter",
                enemies = listOf(
                    TextGameEnemy("endless_warden", "Hollow Warden", 12, "Lantern Crush — 3 damage", 3),
                    TextGameEnemy("endless_stinger", "Glass Stinger", 8, "Needle Rush — 3 damage", 3),
                ),
                openingHand = listOf("flame_cut", "gale_mark", "transfer", "haven_guard"),
                actorResources = defaultActors(),
            ),
        ),
    )
}

private fun adamsHavenSimulation(): TextGameDefinition {
    val base = adamsHavenTutorial()
    return base.copy(
        id = "adams_haven_simulation",
        title = "Adams Haven: Hearth & Harvest",
        subtitle = "Silverbrook lot simulation",
        startNodeId = "sim_tycoon",
        nodes = listOf(
            TextGameNode(
                id = "sim_tycoon",
                type = TextGameNodeType.Tycoon,
                title = "Silverbrook Settlement",
                prose = "I look straight down on the starter 5×5 lots. In this simulation I can linger over placement, expansions, and a generous economy.",
                sceneMediaId = "adams-haven-locations-silverbrook-city",
                bundledSceneAssetPath = "images/adams_haven/locations/silverbrook-city.png",
                choices = listOf(
                    TextGameChoice("tycoon_rest", "I rest and recover", "sim_tycoon", effects = listOf(TextGameEffect(healthDelta = 99))),
                    TextGameChoice("tycoon_patrol", "I take an optional patrol battle", "sim_battle"),
                ),
            ),
            TextGameNode(
                id = "sim_battle",
                type = TextGameNodeType.Battle,
                title = "Road Patrol",
                prose = "Battles remain optional here. I take patrols only when I want coin, material, and Summoner SP for the lots.",
                sceneMediaId = "adams-haven-locations-silverwood-forest",
                bundledSceneAssetPath = "images/adams_haven/locations/silverwood-forest.png",
                encounterId = "simulation_patrol",
                victoryNodeId = "sim_tycoon",
                defeatNodeId = "sim_defeat",
                victoryCoins = 4,
                victorySp = 1,
                victoryEffects = listOf(TextGameEffect(materialsDelta = 1, cropGrowthDelta = 1)),
            ),
            TextGameNode(
                id = "sim_defeat",
                type = TextGameNodeType.Ending,
                title = "Carried Home",
                prose = "The patrol finds me first. I restart the simulation, rebuild the lots, and try again.",
                sceneMediaId = "adams-haven-locations-adams-haven",
                bundledSceneAssetPath = "images/adams_haven/locations/adams-haven.png",
            ),
        ),
        encounters = listOf(
            TextGameEncounter(
                id = "simulation_patrol",
                enemies = listOf(TextGameEnemy("road_wisp", "Road Wisp", 10, "Cold Spark — 3 damage", 3)),
                openingHand = listOf("flame_cut", "gale_mark", "transfer", "haven_guard"),
                actorResources = defaultActors(),
            ),
        ),
    )
}

private fun defaultActors() = listOf(
    TextGameActorResource("kestrel", "Kaela Stormfang", ap = 1, ep = 3, maxAp = 1, maxEp = 5),
    TextGameActorResource("sable", "Ghislaine Dedoldia", ap = 1, ep = 3, maxAp = 1, maxEp = 5),
)
