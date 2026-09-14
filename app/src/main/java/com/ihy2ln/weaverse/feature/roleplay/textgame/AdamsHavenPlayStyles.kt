package com.ihy2ln.weaverse.feature.roleplay.textgame

enum class TextGamePlayStyle(val label: String, val description: String) {
    Campaign("Campaign", "Branching story, exploration, battles, and lasting choices."),
    Endless("Endless Battles", "Fight, recover, and immediately enter the next encounter."),
    Simulation("Haven Simulation", "Farm, shop, improve Home, and take optional patrol battles."),
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
        subtitle = "Town, Farm, and Home simulation",
        startNodeId = "sim_home",
        nodes = listOf(
            TextGameNode(
                id = "sim_home",
                type = TextGameNodeType.Hub,
                title = "Home",
                prose = "I review my supplies beneath the card case and decide where I spend the day.",
                sceneMediaId = "adams-haven-locations-adams-haven",
                bundledSceneAssetPath = "images/adams_haven/locations/adams-haven.png",
                sceneMotionMediaId = "adams-haven-motion-home",
                bundledSceneMotionAssetPath = "videos/adams_haven/home.mp4",
                choices = listOf(
                    TextGameChoice("home_rest", "I rest and recover", "sim_home", effects = listOf(TextGameEffect(healthDelta = 99))),
                    TextGameChoice("home_farm", "I walk to the Farm", "sim_farm"),
                    TextGameChoice("home_town", "I visit Town", "sim_town"),
                    TextGameChoice("home_patrol", "I take an optional patrol battle", "sim_battle"),
                ),
            ),
            TextGameNode(
                id = "sim_farm",
                type = TextGameNodeType.Hub,
                title = "Farm",
                prose = "Lanternroot beds glow beside my shack. In this simulation-focused side mode, I can linger over Farm work and its generous economy.",
                sceneMediaId = "adams-haven-locations-fixer-upper",
                bundledSceneAssetPath = "images/adams_haven/locations/fixer-upper.png",
                sceneMotionMediaId = "adams-haven-motion-farm",
                bundledSceneMotionAssetPath = "videos/adams_haven/farm.mp4",
                choices = listOf(
                    TextGameChoice("farm_tend", "I tend lanternroot (+1 harvest, +2 coins)", "sim_farm", effects = listOf(TextGameEffect(harvestDelta = 1, coinsDelta = 2))),
                    TextGameChoice("farm_plant", "I plant one seed (+2 harvest)", "sim_farm", condition = TextGameCondition(minimumSeeds = 1), effects = listOf(TextGameEffect(seedsDelta = -1, harvestDelta = 2))),
                    TextGameChoice("farm_home", "I return Home", "sim_home"),
                    TextGameChoice("farm_town", "I carry produce to Town", "sim_town"),
                ),
            ),
            TextGameNode(
                id = "sim_town",
                type = TextGameNodeType.Hub,
                title = "Town",
                prose = "I trade at Silverbrook's market for seeds, supplies, and permanent improvements.",
                sceneMediaId = "adams-haven-locations-silverbrook-city",
                bundledSceneAssetPath = "images/adams_haven/locations/silverbrook-city.png",
                sceneMotionMediaId = "adams-haven-motion-town",
                bundledSceneMotionAssetPath = "videos/adams_haven/town.mp4",
                choices = listOf(
                    TextGameChoice("town_seed", "I buy one seed — 2 coins", "sim_town", condition = TextGameCondition(minimumCoins = 2), effects = listOf(TextGameEffect(coinsDelta = -2, seedsDelta = 1))),
                    TextGameChoice("town_upgrade", "I improve Home — 5 coins (+2 max health)", "sim_town", condition = TextGameCondition(minimumCoins = 5), effects = listOf(TextGameEffect(coinsDelta = -5, maxHealthDelta = 2, homeLevelDelta = 1))),
                    TextGameChoice("town_home", "I return Home", "sim_home"),
                    TextGameChoice("town_farm", "I visit the Farm", "sim_farm"),
                ),
            ),
            TextGameNode(
                id = "sim_battle",
                type = TextGameNodeType.Battle,
                title = "Road Patrol",
                prose = "Battles remain optional here. I take patrols only when I want coin, material, and Summoner SP for my Haven.",
                sceneMediaId = "adams-haven-locations-silverwood-forest",
                bundledSceneAssetPath = "images/adams_haven/locations/silverwood-forest.png",
                encounterId = "simulation_patrol",
                victoryNodeId = "sim_home",
                defeatNodeId = "sim_defeat",
                victoryCoins = 4,
                victorySp = 1,
                victoryEffects = listOf(TextGameEffect(materialsDelta = 1, cropGrowthDelta = 1)),
            ),
            TextGameNode(
                id = "sim_defeat",
                type = TextGameNodeType.Ending,
                title = "Carried Home",
                prose = "The patrol finds me first. I restart the simulation, rebuild my supplies, and try again.",
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
