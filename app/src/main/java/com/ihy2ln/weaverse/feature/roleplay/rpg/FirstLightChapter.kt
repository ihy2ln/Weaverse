package com.ihy2ln.weaverse.feature.roleplay.rpg

/**
 * First vertical slice: First Light — party setup, one exploration scene,
 * card combat, one bond/craft/faction beat, then recap.
 */
object FirstLightChapter {
    const val ID = "first_light"
    const val TITLE = "First Light"
    const val SUMMONER_ID = "summoner"
    const val MIRA_ID = "mira"
    const val WARDENS_ID = "haven_wardens"
    const val MOONROOT_ID = "moonroot_fiber"
    const val SALVE_RECIPE_ID = "lantern_salve"
    const val SALVE_ITEM = "Lantern Salve"

    const val SCENE_ONBOARDING = "onboarding"
    const val SCENE_FOREST = "forest_verge"
    const val SCENE_COMBAT = "thorn_ambush"
    const val SCENE_AFTERMATH = "shrine_clearing"
    const val SCENE_RECAP = "chapter_recap"

    const val ENCOUNTER_WISP = "thorn_wisp"
    const val CHOICE_SCOUT = "scout_treeline"
    const val CHOICE_SCOUT_WOUNDED = "approach_scout"
    const val CHOICE_CART = "search_cart"

    fun newCampaign(seed: Long, summonerName: String = "Adam"): RpgCampaignState {
        val mira = RpgCompanionBond(id = MIRA_ID, name = "Mira", level = 1)
        return RpgCampaignState(
            schemaVersion = RPG_SCHEMA_VERSION,
            scene = onboardingScene(),
            companions = listOf(mira),
            crafting = RpgCraftingLedger(
                recipes = listOf(
                    RpgRecipe(
                        id = SALVE_RECIPE_ID,
                        name = SALVE_ITEM,
                        description = "A warm salve that steadies lantern-light and mends thorn cuts.",
                        inputs = mapOf(MOONROOT_ID to 1),
                        output = SALVE_ITEM,
                        unlocked = false,
                    ),
                ),
            ),
            factions = listOf(
                RpgFactionStanding(id = WARDENS_ID, name = "Haven Wardens", reputation = 0),
            ),
            progress = RpgCampaignProgress(
                nextObjective = "Create your party and begin First Light.",
            ),
            party = listOf(
                RpgPartyMember(
                    id = SUMMONER_ID,
                    name = summonerName,
                    role = "Summoner",
                    hp = 16,
                    maxHp = 16,
                    isSummoner = true,
                ),
                RpgPartyMember(
                    id = MIRA_ID,
                    name = "Mira",
                    role = "Ranger companion",
                    hp = 18,
                    maxHp = 18,
                ),
            ),
            rngSeed = seed,
        )
    }

    fun onboardingScene() = RpgSceneState(
        id = SCENE_ONBOARDING,
        title = "Party setup",
        introduction = "You wake on the forest verge of Haven with lantern-moth light still clinging to your coat. " +
            "Mira, a ranger bound to your summoning contract, waits with a ready bow. Name yourself and confirm the party.",
        objective = "Confirm your Summoner and companion, then step onto the trail.",
        phase = RpgScenePhase.Onboarding,
        nextGuidance = "Name your Summoner, then begin First Light.",
    )

    fun forestScene(proposals: List<RpgActionProposal> = forestProposals()) = RpgSceneState(
        id = SCENE_FOREST,
        title = "Forest verge",
        introduction = "A ruined cart blocks the lantern shrine path. Thorn vines twitch at the treeline. " +
            "Someone in Warden colors lies against the wheel, breathing shallowly.",
        objective = "Reach the lantern shrine before night closes the path.",
        phase = RpgScenePhase.Exploration,
        proposals = withCustom(proposals),
        nextGuidance = "What can I do next? Choose a card or write your own action.",
    )

    fun combatScene() = RpgSceneState(
        id = SCENE_COMBAT,
        title = "Thorn ambush",
        introduction = "A Thornbound Wisp tears out of the vines, lantern-light fracturing across its barbs. " +
            "Its next lash is already gathering.",
        objective = "Win the card fight and keep Mira standing.",
        phase = RpgScenePhase.Combat,
        proposals = emptyList(),
        customActionEnabled = false,
        nextGuidance = "Play a card from the shared hand. Watch the Wisp's intent.",
    )

    fun aftermathScene() = RpgSceneState(
        id = SCENE_AFTERMATH,
        title = "Lantern shrine",
        introduction = "The shrine's glass is cracked but still warm. Mira lowers her bow. " +
            "Moonroot fiber clings to the Wisp's remains, and a Warden token lies in the moss.",
        objective = "Collect the spoils and hear what changed.",
        phase = RpgScenePhase.Aftermath,
        proposals = emptyList(),
        customActionEnabled = false,
        nextGuidance = "Review rewards, then continue to the chapter recap.",
    )

    fun recapScene(nextObjective: String) = RpgSceneState(
        id = SCENE_RECAP,
        title = "First Light recap",
        introduction = "The verge is quiet. Mira stands closer than before. Haven's Wardens will hear of this.",
        objective = nextObjective,
        phase = RpgScenePhase.Recap,
        proposals = emptyList(),
        customActionEnabled = false,
        nextGuidance = "Save is current. Next: present yourselves at the Guild Hall.",
    )

    fun forestProposals(): List<RpgActionProposal> = listOf(
        RpgActionProposal(
            id = CHOICE_SCOUT,
            title = "Scout the treeline",
            summary = "Mira covers you while you read the twitching vines for a safe lane.",
            intent = "Find a quiet path past the cart without waking the thorns.",
            skill = "Perception",
            ability = "Wisdom",
            checkDc = 11,
            involvedCharacters = listOf("You", "Mira"),
            riskTier = RpgRiskTier.Low,
            rewardCategories = listOf("exploration"),
        ),
        RpgActionProposal(
            id = CHOICE_SCOUT_WOUNDED,
            title = "Approach the wounded scout",
            summary = "Kneel beside the Warden and offer water before the vines close in.",
            intent = "Stabilize the scout and earn Mira's trust.",
            skill = "Persuasion",
            ability = "Charisma",
            checkDc = 12,
            involvedCharacters = listOf("You", "Mira", "Warden scout"),
            riskTier = RpgRiskTier.Medium,
            rewardCategories = listOf("relationship", "faction"),
        ),
        RpgActionProposal(
            id = CHOICE_CART,
            title = "Search the ruined cart",
            summary = "Pull salvage from the splintered crate while Mira watches the trees.",
            intent = "Recover moonroot fiber and recipe scraps from the wreck.",
            skill = "Investigation",
            ability = "Intelligence",
            checkDc = 12,
            involvedCharacters = listOf("You", "Mira"),
            riskTier = RpgRiskTier.Medium,
            rewardCategories = listOf("crafting"),
        ),
    )

    fun withCustom(proposals: List<RpgActionProposal>): List<RpgActionProposal> =
        proposals.filterNot { it.isCustom }.take(3) + customActionProposal()

    fun thornEncounter(party: List<RpgPartyMember>, seed: Long): RpgCombatState {
        val actors = party.map { member ->
            RpgCombatActor(
                id = member.id,
                name = member.name,
                hp = member.hp,
                maxHp = member.maxHp,
                ap = if (member.isSummoner) 1 else 1,
                maxAp = 1,
                ep = 3,
                maxEp = 3,
                isSummoner = member.isSummoner,
            )
        }
        val summoner = actors.firstOrNull { it.isSummoner } ?: actors.first()
        val mira = actors.firstOrNull { it.id == MIRA_ID } ?: actors.last()
        return RpgCombatState(
            encounterId = ENCOUNTER_WISP,
            round = 1,
            party = actors,
            enemies = listOf(
                RpgCombatEnemy(
                    id = "wisp",
                    name = "Thornbound Wisp",
                    hp = 16,
                    maxHp = 16,
                    intent = RpgEnemyIntent(
                        title = "Lash",
                        description = "Barbed light snaps at Mira.",
                        damage = 4,
                        targetId = mira.id,
                    ),
                ),
            ),
            hand = combatDeck(summoner.id, mira.id),
            selectedTargetId = "wisp",
            summonerSp = 2,
            maxSummonerSp = 4,
            log = listOf("The Wisp gathers a Lash. Shared hand is ready."),
        )
    }

    fun combatDeck(summonerId: String, miraId: String): List<RpgCombatCard> = listOf(
        RpgCombatCard(
            id = "thorn_cut",
            title = "Thorn Cut",
            description = "Mira's first arrow, cheap and sharp.",
            ownerId = miraId,
            apCost = 1,
            epCost = 1,
            damage = 6,
        ),
        RpgCombatCard(
            id = "mira_guard",
            title = "Ranger Guard",
            description = "Mira steps in and blunts the next lash.",
            ownerId = miraId,
            apCost = 1,
            epCost = 1,
            block = 4,
        ),
        RpgCombatCard(
            id = "bind_spark",
            title = "Bind Spark",
            description = "Summoner support: a tether of lantern-light.",
            ownerId = summonerId,
            apCost = 1,
            epCost = 1,
            spCost = 1,
            damage = 5,
        ),
        RpgCombatCard(
            id = "ember_burst",
            title = "Ember Burst",
            description = "Summoner ultimate. Spend SP to finish the Wisp.",
            ownerId = summonerId,
            apCost = 1,
            epCost = 2,
            spCost = 2,
            damage = 12,
            isUltimate = true,
        ),
    )

    fun explorationRewards(proposalId: String, success: Boolean): List<RpgReward> {
        val combat = RpgReward(
            id = "ambush",
            category = "combat",
            label = "The vines answer. Combat begins.",
        )
        if (!success) return listOf(combat)
        return when (proposalId) {
            CHOICE_SCOUT -> listOf(
                RpgReward(id = "path", category = "exploration", label = "You mark a quieter lane."),
                combat,
            )
            CHOICE_SCOUT_WOUNDED -> listOf(
                RpgReward(
                    id = "mira_bond",
                    category = "relationship",
                    label = "Mira's bond deepens.",
                    bondCompanionId = MIRA_ID,
                    bondDelta = 1,
                    unlockFlag = "mira_trust",
                ),
                RpgReward(
                    id = "warden_notice",
                    category = "faction",
                    label = "The Wardens will hear you helped their scout.",
                    factionId = WARDENS_ID,
                    factionDelta = 1,
                    unlockFlag = "warden_scout_saved",
                ),
                combat,
            )
            CHOICE_CART -> listOf(
                RpgReward(
                    id = "moonroot",
                    category = "crafting",
                    label = "Moonroot fiber recovered.",
                    materialId = MOONROOT_ID,
                    materialQty = 1,
                    recipeId = SALVE_RECIPE_ID,
                ),
                combat,
            )
            else -> listOf(combat)
        }
    }

    fun victoryRewards(): List<RpgReward> = listOf(
        RpgReward(
            id = "mira_after",
            category = "relationship",
            label = "Mira nods — you fought as a pair.",
            bondCompanionId = MIRA_ID,
            bondDelta = 1,
            unlockFlag = "mira_battle_sworn",
        ),
        RpgReward(
            id = "moonroot_victory",
            category = "crafting",
            label = "Moonroot fiber from the Wisp.",
            materialId = MOONROOT_ID,
            materialQty = 1,
            recipeId = SALVE_RECIPE_ID,
        ),
        RpgReward(
            id = "warden_token",
            category = "faction",
            label = "A Warden token. Silverbrook will take notice.",
            factionId = WARDENS_ID,
            factionDelta = 2,
            unlockFlag = "warden_token",
        ),
    )

    const val NEXT_OBJECTIVE = "Present yourselves at the Guild Hall and report the Thornbound Wisp."

    fun recapNotes(state: RpgCampaignState): List<String> {
        val mira = state.companions.firstOrNull { it.id == MIRA_ID }
        val wardens = state.factions.firstOrNull { it.id == WARDENS_ID }
        val fiber = state.crafting.materials[MOONROOT_ID] ?: 0
        return listOfNotNull(
            "Chapter complete: $TITLE.",
            mira?.let { "Mira bond ${it.level}." },
            wardens?.let { "Haven Wardens standing ${it.reputation}." },
            "Moonroot fiber: $fiber.",
            NEXT_OBJECTIVE,
        )
    }
}
