package com.ihy2ln.weaverse.feature.roleplay.textgame

import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextGameEngineTest {
    private val definition = adamsHavenTutorial()
    private val engine = TextGameEngine(definition)

    @Test
    fun campaignStartsAtCrossroadsAndKeepsHubNavigationAvailable() {
        val start = engine.initialState()
        assertEquals("void_arrival", start.run.nodeId)
        val forest = engine.reduce(start, TextGameAction.Choose("arrive_dirt")).state
        assertEquals("forest_path", forest.run.nodeId)
        val state = engine.reduce(forest, TextGameAction.Choose("follow_path")).state
        assertEquals("crossroads", state.run.nodeId)
        assertTrue(setOf("to_dungeon", "to_tycoon", "to_farm", "to_town", "to_house").all { id -> definition.node("crossroads")!!.choices.any { it.id == id } })
        assertTrue(definition.node("crossroads")!!.hotspots.size >= 4)
        assertEquals(1, state.persistent.farmLevel)
        assertEquals(1, state.persistent.townLevel)
        assertEquals(1, state.persistent.homeLevel)
        listOf("void_arrival", "forest_path", "tycoon", "kitchen", "farmhouse_night", "guild_summon").forEach {
            assertNotNull(definition.node(it), "missing scene $it")
        }
        assertNull(definition.node("farm"))
        assertNull(definition.node("town"))
        assertNull(definition.node("home"))
        assertEquals(TextGameNodeType.Tycoon, definition.node("tycoon")?.type)
        assertEquals(5, state.persistent.tycoon.width)
        assertEquals(5, state.persistent.tycoon.height)
        assertEquals(25, state.persistent.tycoon.width * state.persistent.tycoon.height)
        assertEquals(6, definition.schemaVersion)
    }

    @Test
    fun narrativeChoicesAndConditionsAreValidated() {
        var state = toCrossroads(engine.initialState())
        state = engine.reduce(state, TextGameAction.Choose("to_dungeon")).state
        assertEquals(TextGameNodeType.MissionBoard, definition.node(state.run.nodeId)?.type)
        val mission = testMission(state.persistent.rngSeed)
        val forged = engine.reduce(state, TextGameAction.BeginMission(mission.copy(id = "not_offered")))
        assertFalse(forged.accepted)
        state = offerMission(state, mission)
        state = engine.reduce(state, TextGameAction.BeginMission(mission)).state
        assertEquals("summoning", state.run.nodeId)
        assertEquals(mission.id, state.persistent.missionId)
        assertEquals(TextGameMissionStatus.Active, state.persistent.missionLog.single().status)
        val invalid = engine.reduce(state, TextGameAction.Choose("to_town"))
        assertFalse(invalid.accepted)
        assertEquals("summoning", invalid.state.run.nodeId)
    }

    @Test
    fun cardCostsTargetsAndEnemyTurnsAreDeterministic() {
        var state = battleState()
        state = engine.reduce(state, TextGameAction.SelectTarget("warden")).state
        state = engine.reduce(state, TextGameAction.PlayCard("gale_mark")).state
        val strike = engine.reduce(state, TextGameAction.PlayCard("flame_cut"))
        assertTrue(strike.accepted)
        assertEquals(0, strike.state.run.enemies.first { it.id == "warden" }.health)
        assertEquals(0, strike.state.run.resources.first { it.actorId == "kestrel" }.ap)
        assertEquals(0, strike.state.run.resources.first { it.actorId == "kestrel" }.ep)
        assertFalse(engine.reduce(strike.state, TextGameAction.PlayCard("haven_guard")).accepted)
        val ended = engine.reduce(strike.state, TextGameAction.EndTurn)
        assertEquals(16, ended.state.run.playerHealth)
        assertEquals(2, ended.state.run.turn)
        assertEquals(1, ended.state.run.resources.first { it.actorId == "kestrel" }.ap)
    }

    @Test
    fun battleCardsCanBeSelectedThenCommittedLikeTheGameTable() {
        var state = battleState()
        val selected = engine.reduce(state, TextGameAction.SelectCard("gale_mark"))
        assertTrue(selected.accepted)
        assertEquals("gale_mark", selected.state.run.selectedCardId)

        state = engine.reduce(selected.state, TextGameAction.SelectTarget("warden")).state
        val played = engine.reduce(state, TextGameAction.PlaySelectedCard)
        assertTrue(played.accepted)
        assertNull(played.state.run.selectedCardId)
        assertEquals("warden", played.state.run.markedTargetId)
        assertTrue("gale_mark" in played.state.run.playedCards)
    }

    @Test
    fun transferMovesEnergyWithoutCreatingResources() {
        var state = battleState()
        val beforeTotal = state.run.resources.sumOf { it.ep }
        state = engine.reduce(state, TextGameAction.PlayCard("transfer")).state
        assertEquals(beforeTotal, state.run.resources.sumOf { it.ep })
        assertEquals(5, state.run.resources.first { it.actorId == "kestrel" }.ep)
        assertEquals(1, state.run.resources.first { it.actorId == "sable" }.ep)
    }

    @Test
    fun victoryProducesSeededRewardThenLocalGachaAddsTwoAllies() {
        val first = winBattle(battleState())
        val second = winBattle(battleState())
        assertEquals("reward", first.run.nodeId)
        assertEquals(first.run.rewardOptions, second.run.rewardOptions)
        assertEquals(3, first.run.rewardOptions.size)
        assertEquals(9, first.persistent.coins)
        assertEquals(1, first.persistent.summonerSp)
        assertEquals(2, first.persistent.materials)
        assertEquals(2, first.persistent.seeds)
        assertEquals(1, first.persistent.battlesWon)
        assertNull(first.persistent.missionId)
        assertEquals(TextGameMissionStatus.Completed, first.persistent.missionLog.single().status)
        val claimed = engine.reduce(first, TextGameAction.ClaimReward(first.run.rewardOptions.first())).state
        assertEquals("gacha", claimed.run.nodeId)
        val summoned = engine.reduce(claimed, TextGameAction.RunGachaTutorial)
        assertTrue(summoned.accepted)
        assertEquals(4, summoned.state.persistent.rosterIds.size)
        assertEquals(4, summoned.state.persistent.activePartyIds.size)
        assertEquals(2, summoned.state.persistent.gachaDrawCount)
        assertEquals(2, summoned.state.persistent.recentGachaIds.size)
        assertEquals(2, summoned.state.persistent.seeds)
        assertEquals("crossroads", engine.reduce(summoned.state, TextGameAction.Choose("continue_after_gacha")).state.run.nodeId)
    }

    @Test
    fun farmRequiresClearThenPlantAndBattleAdvancesGrowth() {
        var state = afterGacha()
        state = settleLots(state)
        assertEquals(1, state.persistent.farmLevel)
        assertEquals(1, state.persistent.seeds)
        assertTrue("crop_planted" in state.persistent.flags)
        assertTrue("deck_hall_built" in state.persistent.flags)
        assertFalse(engine.reduce(state, TextGameAction.Choose("harvest")).accepted)
        state = engine.reduce(state, TextGameAction.Choose("prepare_home")).state
        state = engine.reduce(state, TextGameAction.Choose("take_contract")).state
        state = engine.reduce(state, TextGameAction.Choose("protect_the_road")).state
        state = winContract(state)
        assertEquals(1, state.persistent.cropGrowth)
        assertEquals(4, state.run.resources.size)
        state = engine.reduce(state, TextGameAction.ClaimReward(state.run.rewardOptions.first())).state
        assertEquals("tycoon", state.run.nodeId)
        state = engine.reduce(state, TextGameAction.Choose("harvest")).state
        assertEquals(0, state.persistent.cropGrowth)
        assertEquals(2, state.persistent.harvest)
        assertEquals(2, state.persistent.farmLevel)
    }

    @Test
    fun townAndHouseHaveSeparateProgression() {
        val tycoon = definition.node("tycoon")!!
        assertEquals(TextGameNodeType.Tycoon, tycoon.type)
        assertTrue(tycoon.choices.any { it.id == "prepare_home" })
        assertTrue(tycoonBuilding("deck_hall") != null)
        assertTrue(tycoonBuilding("the_house") != null)
        var state = afterGacha()
        state = engine.reduce(state, TextGameAction.Choose("to_tycoon")).state
        val before = state.persistent.coins
        state = engine.reduce(state, TextGameAction.PlaceTycoonBuilding("cottage", 0, 0)).state
        state = engine.reduce(state, TextGameAction.TakeTycoonCard("deck_hall")).state
        assertEquals(before - 5, state.persistent.coins)
        state = engine.reduce(state, TextGameAction.PlaceTycoonBuilding("deck_hall", 3, 3)).state
        assertTrue(state.persistent.townLevel > 1)
        assertTrue("deck_hall_built" in state.persistent.flags)
        state = state.copy(persistent = state.persistent.copy(coins = 10, materials = 2))
        state = engine.reduce(state, TextGameAction.TakeTycoonCard("the_house")).state
        state = engine.reduce(state, TextGameAction.PlaceTycoonBuilding("the_house", 0, 0)).state
        assertTrue(state.persistent.homeLevel > 1)
        assertTrue("house_l2" in state.persistent.flags)
    }

    @Test
    fun completeCampaignLinksFarmTownHouseAndFinalBattle() {
        var state = afterGacha()
        state = settleLots(state)
        listOf("prepare_home", "take_contract", "protect_the_road").forEach {
            state = engine.reduce(state, TextGameAction.Choose(it)).state
        }
        state = winContract(state)
        state = engine.reduce(state, TextGameAction.ClaimReward(state.run.rewardOptions.first())).state
        listOf("harvest", "cook_dish", "sell_produce", "buy_coat", "serve_meal", "final_patrol").forEach {
            state = engine.reduce(state, TextGameAction.Choose(it)).state
        }
        assertEquals("final_battle", state.run.nodeId)
        assertEquals(4, state.run.guard)
        assertEquals(22, state.persistent.maxHealth)
        assertEquals(0, state.persistent.dishes)

        state = engine.reduce(state, TextGameAction.SelectTarget("gatebreaker")).state
        state = engine.reduce(state, TextGameAction.PlayCard("gale_mark")).state
        state = engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
        state = engine.reduce(state, TextGameAction.PlayCard("haven_guard")).state
        state = engine.reduce(state, TextGameAction.EndTurn).state
        state = engine.reduce(state, TextGameAction.PlayCard("transfer")).state
        state = engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
        assertEquals("final_reward", state.run.nodeId)
        state = engine.reduce(state, TextGameAction.ClaimReward(state.run.rewardOptions.first())).state
        assertEquals("chapter_complete", state.run.nodeId)
        assertTrue("haven_gate_won" in state.persistent.flags)
        assertEquals(3, state.persistent.battlesWon)
    }

    @Test
    fun defeatAndResetPreserveDifficultyButClearCampaignState() {
        var state = battleState(TextGameDifficulty.Veteran)
        repeat(4) { state = engine.reduce(state, TextGameAction.EndTurn).state }
        assertEquals("defeat", state.run.nodeId)
        assertEquals(0, state.run.playerHealth)
        val reset = engine.reduce(state, TextGameAction.Reset).state
        assertEquals("void_arrival", reset.run.nodeId)
        assertEquals(TextGameDifficulty.Veteran, reset.persistent.difficulty)
        assertTrue(reset.persistent.flags.isEmpty())
    }

    @Test
    fun aiStoryProposalDoesNotChangeStateUntilConfirmed() {
        val state = toCrossroads(engine.initialState())
        val proposal = parseTextGameStoryProposal(
            "I watch the road breathe beneath the roots.\nSTORY_OPTIONS:\n1. Walk toward the lantern.\n2. Search the shack. [ACTION: to_house]\n3. Wait for a sign.\n4. Custom prompt: tell the narrator what I attempt.",
            "message-1",
        )!!
        assertEquals(3, proposal.options.size)
        assertTrue(definition.nodes.filter { it.type == TextGameNodeType.Narrative }.all { it.choices.size == 3 })
        val queued = engine.reduce(state, TextGameAction.QueueStoryProposal(proposal)).state
        assertEquals(state.persistent, queued.persistent)
        assertEquals("crossroads", queued.run.nodeId)
        assertEquals("crossroads", engine.reduce(queued, TextGameAction.ConfirmStoryOption("message-1-1")).state.run.nodeId)
        assertEquals("tycoon", engine.reduce(queued, TextGameAction.ConfirmStoryOption("message-1-2")).state.run.nodeId)
    }

    @Test
    fun difficultyScalesHealthAndDamageDeterministically() {
        val story = battleState(TextGameDifficulty.Story)
        val storyAgain = battleState(TextGameDifficulty.Story)
        val nightmare = battleState(TextGameDifficulty.Nightmare)
        assertEquals(story.run.enemies, storyAgain.run.enemies)
        assertTrue(
            nightmare.run.enemies.first { it.id == "warden" }.maxHealth >
                story.run.enemies.first { it.id == "warden" }.maxHealth,
        )
        val storyAfterTurn = engine.reduce(story, TextGameAction.EndTurn).state
        val nightmareAfterTurn = engine.reduce(nightmare, TextGameAction.EndTurn).state
        assertTrue(story.run.playerHealth - storyAfterTurn.run.playerHealth < nightmare.run.playerHealth - nightmareAfterTurn.run.playerHealth)
        assertEquals(TextGameDifficulty.Veteran, parseTextGameDifficulty("Text Game difficulty: veteran"))
    }

    @Test
    fun persistentAndRunStateRoundTripIndependently() {
        val codec = Json { encodeDefaults = true }
        val state = afterGacha()
        val restored = TextGameState(
            persistent = codec.decodeFromString(codec.encodeToString(state.persistent)),
            run = codec.decodeFromString(codec.encodeToString(state.run)),
        )
        assertEquals(state, restored)
    }

    @Test
    fun playStylesHaveIndependentDefinitions() {
        val campaign = adamsHavenDefinition(TextGamePlayStyle.Campaign)
        val endless = adamsHavenDefinition(TextGamePlayStyle.Endless)
        val simulation = adamsHavenDefinition(TextGamePlayStyle.Simulation)
        assertEquals(3, setOf(campaign.id, endless.id, simulation.id).size)
        assertEquals(TextGameNodeType.Battle, endless.node(endless.startNodeId)?.type)
        assertEquals("sim_tycoon", simulation.startNodeId)
        assertEquals(TextGameNodeType.Tycoon, simulation.node("sim_tycoon")?.type)
    }

    @Test
    fun missingOrEmptyPictureFallsBackCleanly() {
        val directory = Files.createTempDirectory("text-game-media").toFile()
        assertNull(resolveSceneMediaPath("missing.png", directory))
        val empty = directory.resolve("empty.png").apply { writeBytes(byteArrayOf()) }
        assertNull(resolveSceneMediaPath(empty.name, directory))
        val image = directory.resolve("scene.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNotNull(resolveSceneMediaPath(image.name, directory))
    }

    @Test
    fun bundledCampaignUsesSummonerFirstPersonAndCatalog() {
        assertTrue(definition.nodes.all { it.prose.contains(Regex("\\b(I|my|me)\\b", RegexOption.IGNORE_CASE)) })
        assertEquals(76, definition.collectibleCards.size)
        assertEquals(76, definition.collectibleCards.map { it.id }.distinct().size)
        assertTrue(definition.collectibleCards.any { it.id == "characters/class-warrior" })
        assertTrue(definition.collectibleCards.all { it.artAssetPath.endsWith(".png") })
        val tycoon = definition.node("tycoon")!!
        assertEquals(TextGameNodeType.Tycoon, tycoon.type)
        assertNotNull(tycoon.bundledSceneAssetPath)
        assertTrue(tycoon.prose.contains("5×5") || tycoon.prose.contains("5x5") || tycoon.prose.contains("25"))
    }

    @Test
    fun sceneCatalogKeepsFourWayRoadsAtCrossroadsAndCardsMatchRoster() {
        val crossroads = definition.sceneAssets.filter { "crossroads" in it.sceneTypes }
        assertTrue(crossroads.isNotEmpty())
        assertTrue(crossroads.all { "four-way-road" in it.artAssetPath })
        assertTrue(crossroads.all { it.category == "Adams Haven / Scene / Crossroads" })
        assertTrue(crossroads.all { "scene:crossroads" in it.tags })
        assertTrue(definition.sceneAssets.filter { "town" in it.sceneTypes }.none { "four-way" in it.artAssetPath })
        assertEquals("crossroads", definition.node("crossroads")?.sceneAssetType)
        assertEquals("battle", definition.node("battle")?.sceneAssetType)
        definition.roster.forEach { member ->
            assertNotNull(member.collectibleCardId)
            assertNotNull(definition.collectible(member.collectibleCardId!!))
        }
        assertEquals("Adams Haven / Cards / Characters", adamsHavenCardMediaCategory("characters"))
        assertTrue("card:character" in adamsHavenCardMediaTags(definition.collectibleCards.first()))
    }

    @Test
    fun campaignOpeningsAreSeededAndRestartAdvancesTheOpeningSeed() {
        val crossroads = definition.node("crossroads")!!
        val first = engine.initialState(rngSeed = 101L)
        val same = engine.initialState(rngSeed = 101L)
        assertEquals(crossroads.proseFor(first.persistent.rngSeed), crossroads.proseFor(same.persistent.rngSeed))
        val reset = engine.reduce(first, TextGameAction.Reset).state
        assertEquals(8_020L, reset.persistent.rngSeed)
        assertTrue(crossroads.proseVariants.size >= 6)
        assertTrue((1L..40L).map(crossroads::proseFor).distinct().size > 1)
        assertTrue(freshCampaignSeed("campaign-a", definition.id) != freshCampaignSeed("campaign-b", definition.id))
    }

    @Test
    fun missionBoardVariesOneToSixOffersAndNeverUsesTheOldFixedIntro() {
        val counts = (1L..120L).map(::missionOfferCount).toSet()
        assertEquals((1..6).toSet(), counts)
        assertTrue((1L..40L).map(::fallbackMissionBoardIntro).distinct().size > 1)
        assertTrue((1L..40L).none { fallbackMissionBoardIntro(it).contains("The Shack at Dawn", ignoreCase = true) })
        assertTrue(definition.nodes.none { it.title.contains("The Shack at Dawn", ignoreCase = true) })
    }

    @Test
    fun dungeonPictureMetadataCreatesDungeonCategoryAndLimitsBattleBackgrounds() {
        val combat = adamsHavenDungeonMediaMetadata(
            "forest_dungeon_set/standard_combat/standard-combat-01-mossgate-arena.png",
        )
        val loot = adamsHavenDungeonMediaMetadata("cave-loot-rooms-v1/loot-01-coin-hoard-alcove.png")
        assertEquals("Adams Haven / Scene / Dungeons", combat.category)
        assertTrue("scene:dungeon" in combat.tags)
        assertTrue("scene:battle" in combat.tags)
        assertTrue("standard-combat" in combat.tags)
        assertTrue("scene:dungeon" in loot.tags)
        assertFalse("scene:battle" in loot.tags)
        assertEquals(combat.id, adamsHavenDungeonMediaMetadata(
            "forest_dungeon_set/standard_combat/standard-combat-01-mossgate-arena.png",
        ).id)
    }

    @Test
    fun tycoonBoardStartsAtTwentyFiveTilesAndExpandsSeveralWays() {
        assertEquals(25, TYCOON_START_TILES)
        assertEquals(5, tycoonBuildings().map { it.district }.distinct().size)
        TycoonDistrict.entries.forEach { district ->
            val tiles = (0 until 5).flatMap { y -> (0 until 5).map { x -> tycoonDistrictAt(x, y) } }
            assertEquals(5, tiles.count { it == district }, "$district should own 5 starter tiles")
        }
        var state = afterGacha()
        state = engine.reduce(state, TextGameAction.Choose("to_tycoon")).state
        assertEquals(5, state.persistent.tycoon.width)
        assertEquals(5, state.persistent.tycoon.height)
        val gold = engine.reduce(state, TextGameAction.ExpandTycoon(TycoonExpandWay.Gold))
        assertTrue(gold.accepted)
        assertEquals(6, gold.state.persistent.tycoon.width)
        assertEquals(5, gold.state.persistent.tycoon.height)
        val dungeon = engine.reduce(gold.state, TextGameAction.ExpandTycoon(TycoonExpandWay.Dungeon))
        assertTrue(dungeon.accepted)
        assertTrue(dungeon.state.persistent.tycoon.width * dungeon.state.persistent.tycoon.height > 30)
        assertFalse(engine.reduce(state, TextGameAction.PlaceTycoonBuilding("cottage", 4, 0)).accepted)
        val placed = engine.reduce(state, TextGameAction.PlaceTycoonBuilding("cottage", 0, 0))
        assertTrue(placed.accepted)
        assertEquals("cottage", placed.state.persistent.tycoon.placements.single().buildingId)
    }

    @Test
    fun tycoonCellSizeStaysFiniteWhenComposeReportsInfiniteConstraints() {
        val fromInfinite = tycoonCellSizeDp(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 5, 5, 1f)
        assertTrue(fromInfinite.isFinite() && fromInfinite in 28f..96f)
        val fromNaN = tycoonCellSizeDp(Float.NaN, Float.NaN, 5, 5, 1f)
        assertTrue(fromNaN.isFinite() && fromNaN in 28f..96f)
        val fitted = tycoonCellSizeDp(360f, 360f, 5, 5, 1f)
        assertEquals(72f, fitted)
        val largeBoard = tycoonCellSizeDp(360f, 520f, 20, 12, 1.4f)
        assertTrue(largeBoard.isFinite() && largeBoard in 28f..96f)
        assertEquals(360f, tycoonFiniteDp(Float.NEGATIVE_INFINITY, 360f))
        assertEquals(360f, tycoonFiniteDp(0f, 360f))
    }

    @Test
    fun enteringTycoonFromCrossroadsAndSimulationStartDoesNotReject() {
        var campaign = afterGacha()
        val entered = engine.reduce(campaign, TextGameAction.Choose("to_tycoon"))
        assertTrue(entered.accepted)
        assertEquals("tycoon", entered.state.run.nodeId)
        assertEquals(TextGameNodeType.Tycoon, definition.node(entered.state.run.nodeId)?.type)
        val simulation = TextGameEngine(adamsHavenDefinition(TextGamePlayStyle.Simulation)).initialState()
        assertEquals("sim_tycoon", simulation.run.nodeId)
        assertEquals(TextGameNodeType.Tycoon, adamsHavenDefinition(TextGamePlayStyle.Simulation).node(simulation.run.nodeId)?.type)
        assertEquals(5, simulation.persistent.tycoon.width)
        assertEquals(listOf("cottage"), simulation.persistent.tycoon.hand)
    }

    private fun settleLots(state: TextGameState): TextGameState {
        var next = engine.reduce(state, TextGameAction.Choose("to_tycoon")).state
        next = engine.reduce(next, TextGameAction.PlaceTycoonBuilding("cottage", 0, 0)).state
        next = engine.reduce(next, TextGameAction.TakeTycoonCard("crop")).state
        next = engine.reduce(next, TextGameAction.PlaceTycoonBuilding("crop", 2, 2)).state
        next = engine.reduce(next, TextGameAction.TakeTycoonCard("deck_hall")).state
        return engine.reduce(next, TextGameAction.PlaceTycoonBuilding("deck_hall", 3, 3)).state
    }

    private fun toCrossroads(state: TextGameState): TextGameState {
        var next = state
        if (next.run.nodeId == "void_arrival") {
            next = engine.reduce(next, TextGameAction.Choose("arrive_dirt")).state
        }
        if (next.run.nodeId == "forest_path") {
            next = engine.reduce(next, TextGameAction.Choose("follow_path")).state
        }
        return next
    }

    private fun battleState(difficulty: TextGameDifficulty = TextGameDifficulty.Standard): TextGameState {
        var state = toCrossroads(engine.initialState(difficulty))
        state = engine.reduce(state, TextGameAction.Choose("to_dungeon")).state
        val mission = testMission(state.persistent.rngSeed)
        state = offerMission(state, mission)
        state = engine.reduce(state, TextGameAction.BeginMission(mission)).state
        listOf("choose_kestrel", "burst_plan").forEach { choiceId ->
            state = engine.reduce(state, TextGameAction.Choose(choiceId)).state
        }
        return state
    }

    private fun testMission(seed: Long): TextGameMission = TextGameMission(
        id = missionInstanceId("test_descent", seed, 0),
        title = "Test Descent",
        description = "I take a measured route into the dungeon.",
        effects = emptyList(),
    )

    private fun offerMission(state: TextGameState, mission: TextGameMission): TextGameState = state.copy(
        persistent = state.persistent.copy(
            missionLog = listOf(TextGameMissionLogEntry(mission, offeredSeed = state.persistent.rngSeed)),
        ),
        run = state.run.copy(
            missionOffer = listOf(mission),
            missionBoardIntro = fallbackMissionBoardIntro(state.persistent.rngSeed),
        ),
    )

    private fun afterGacha(): TextGameState {
        var state = winBattle(battleState())
        state = engine.reduce(state, TextGameAction.ClaimReward(state.run.rewardOptions.first())).state
        state = engine.reduce(state, TextGameAction.RunGachaTutorial).state
        return engine.reduce(state, TextGameAction.Choose("continue_after_gacha")).state
    }

    private fun winBattle(start: TextGameState): TextGameState {
        var state = engine.reduce(start, TextGameAction.SelectTarget("warden")).state
        state = engine.reduce(state, TextGameAction.PlayCard("gale_mark")).state
        state = engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
        state = engine.reduce(state, TextGameAction.EndTurn).state
        state = engine.reduce(state, TextGameAction.SelectTarget("stinger")).state
        state = engine.reduce(state, TextGameAction.PlayCard("transfer")).state
        return engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
    }

    private fun winContract(start: TextGameState): TextGameState {
        var state = engine.reduce(start, TextGameAction.SelectTarget("glassroot_sentinel")).state
        state = engine.reduce(state, TextGameAction.PlayCard("gale_mark")).state
        state = engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
        state = engine.reduce(state, TextGameAction.SelectTarget("maw_spawn")).state
        state = engine.reduce(state, TextGameAction.PlayCard("haven_guard")).state
        state = engine.reduce(state, TextGameAction.EndTurn).state
        state = engine.reduce(state, TextGameAction.PlayCard("transfer")).state
        state = engine.reduce(state, TextGameAction.PlayCard("flame_cut")).state
        state = engine.reduce(state, TextGameAction.SelectTarget("glassroot_sentinel")).state
        return engine.reduce(state, TextGameAction.PlayCard("linnet_wave")).state
    }
}
