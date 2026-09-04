package com.ihy2ln.weaverse.sync.adams

/**
 * Adams Haven RPG pack — world, gacha cards, and playable scenes lifted from
 * the Adams Haven lane-tactics game (farming, town building, gacha) into
 * Weaverse Roleplay.
 *
 * Source: https://github.com/ihy2ln/AdamsHaven (the public game repo;
 * `AdamsHavenCardGame` is not a separate repository).
 *
 * IDs are stable so seeders can insert-if-missing without clobbering user edits.
 */
object AdamsHavenRpgCatalog {
    const val PERSONA_ID = "ah-rpg-persona-jd"
    const val PERSONA_NAME = "JD"
    const val SCENE_ID_PREFIX = "ah-rpg-scene-"
    const val CHAR_ID_PREFIX = "ah-rpg-char-"
    const val LORE_ID_PREFIX = "ah-rpg-lore-"
    const val SNIPPET_ID = "ah-rpg-snippet-gm"
    const val TAG_CARDS = "Adams Haven Cards"
    const val TAG_WORLD = "Adams Haven"
    const val TAG_GACHA = "gacha"
    const val TAG_RPG = "rpg"

    const val COLOR_CHARACTER = "#4A90D9"
    const val COLOR_LOCATION = "#3FA66A"
    const val COLOR_OBJECT = "#8B6FD1"
    const val COLOR_LORE = "#D98A3F"
    const val COLOR_MAGIC = "#7E6BD1"
    const val COLOR_EVENT = "#C98BB0"
    const val COLOR_FACTION = "#C4574B"

    const val WORLD_SCENARIO =
        "Adams Haven / Elysium Vale — an archipelago where each island is a different Age, " +
            "and summoned cards fight in three-lane battles between farm chores and town work."

    fun isRpgSceneId(id: String): Boolean = id.startsWith(SCENE_ID_PREFIX)

    fun isRpgCharacterId(id: String): Boolean = id.startsWith(CHAR_ID_PREFIX)

    val persona = RpgPersona(
        id = PERSONA_ID,
        name = PERSONA_NAME,
        description = "Player persona for the Adams Haven RPG — John / JD, transferred in and bound to the Isekai Incubus System.",
    )

    val gmBrief = RpgSnippet(
        id = SNIPPET_ID,
        title = "Adams Haven RPG — GM brief",
        body = """
            Adams Haven is a lane-tactics RPG with farming, town building, and gacha.

            Camera: Home is a tight 2.5D diorama (farm plot, farmhouse). Overworld is a steeper forest-path view that leads to the farm gate. Yaw never changes.

            Combat: 3 lanes × 8 columns. Height capped at 3. Facing is two-state (left/right). Jump is a legality check, not a cost. Movement stats never roll.

            Cards: one standard skill (fixed), one class skill (rolled), one element skill (rolled). Tiers F–A vary ±15%. S–SSS take a stat multiplier. Fusion is +10% additive, cap 10. Rolls freeze at acquisition and store their seed. No starter gear.

            Farm: Stardew × Rune Factory clearing. Starter plot is a 2×2 aesthetic sandbox — oak tree (axe, Lv.3), rock (pickaxe, Lv.2), weed (scythe, Lv.1), one tilled tile. Crops grow on real time and/or battle count. Dungeon plots use the same rules.

            Town: buildings grant verbs (recruit, enhance, cook, sell, idle slots, craft, seeds, storage), not flat stat bonuses. Districts: Residential, Industrial, Agricultural, Military, Arcane, Commercial.

            Economy: materials sit on four axes — tier, age, island, rarity. Each game mode owns at least one material category. Boss stages (every 10th) unlock materials retroactively onto earlier drop tables.

            Ages / islands: Primitive, Medieval, Industrial, Modern, Futuristic, Mythic.
        """.trimIndent(),
    )

    val cards: List<RpgCard> = listOf(
        RpgCard(
            id = "${CHAR_ID_PREFIX}iis",
            name = "Isekai Incubus System",
            description = "The wristband system that transferred JD to Adams Haven. It is the gacha interface, the tutorial, and the voice that explains rolls, fusion, and stage unlocks. Not a combat card — a companion UI with a personality.",
            personality = "Crisp, pleased with itself, slightly hungry for more summons. Speaks in system notices first, then in a low aside. Never apologizes for a bad roll; it explains the seed instead.",
            scenario = WORLD_SCENARIO,
            firstMes = "You have successfully transferred to Adams Haven. Congratulations. I am your Isekai Incubus System. Tutorial begins now.",
            tags = listOf(TAG_WORLD, TAG_GACHA, TAG_RPG, "system"),
            colorHex = COLOR_MAGIC,
            creatorNotes = "Adams Haven RPG — system / gacha UI as a character card.",
            classType = null,
            element = "Neutral",
            age = "Mythic",
        ),
        RpgCard(
            id = "${CHAR_ID_PREFIX}guild",
            name = "Guild Hall",
            description = "Town building that grants the Recruitment verb. This is where gacha summons resolve: a definition is rolled into an instance, tier weighted, skills picked, seed stored. The hall itself talks like a clerk who has seen every F and every SSS.",
            personality = "Brisk, ledger-minded, unimpressed by drama. Cares about flags, costs, and whether you have an empty roster slot.",
            scenario = "Inside the Guild Hall of Adams Haven's first town — recruitment counter, summon circle in the floor, empty weapon racks because no starter gear is ever issued.",
            firstMes = "Name on the ledger. Then we roll. Standard skill is yours. Class and element — that's the circle's business.",
            tags = listOf(TAG_WORLD, TAG_GACHA, "town"),
            colorHex = COLOR_FACTION,
            creatorNotes = "Adams Haven RPG — Recruitment building as an NPC.",
            classType = null,
            age = "Medieval",
        ),
        RpgCard(
            id = "${CHAR_ID_PREFIX}kitchen",
            name = "Kitchen",
            description = "Town building that grants Cooking. Dishes apply a party buff strong enough that cooking is the correct play before hard stages; selling produce is the surplus fallback. Only one exclusive dish buff at a time.",
            personality = "Warm, practical, slightly bossy about ingredients. Talks in recipes and timers. Will send you back to the farm if the pantry is empty.",
            scenario = "The Kitchen off the farmhouse — hanging herbs, a pot already going, farm produce on the board.",
            firstMes = "Sit. If you're marching a lane today, you eat first. Surplus we sell. Don't make me say it twice.",
            tags = listOf(TAG_WORLD, "town", "farm"),
            colorHex = COLOR_LORE,
            creatorNotes = "Adams Haven RPG — Cooking building as an NPC.",
            age = "Medieval",
        ),
        classCard(
            classType = "Warrior",
            element = "Fire",
            age = "Medieval",
            movePoints = 4,
            jump = 1,
            description = "Front-lane striker. Standard skill is a straight column smash. Class pool leans melee, high attack, modest defense. Fire element skills are ranged enough to take the downward elevation bonus when the lane steps down.",
            personality = "Loud in the lane, quiet at the farm gate. Measures distance in move points. Hates illegal jumps more than losing HP.",
            firstMes = "Point me at a lane. I walk it. I don't climb what I can't jump.",
            colorHex = "#C45C3A",
        ),
        classCard(
            classType = "Guardian",
            element = "Earth",
            age = "Primitive",
            movePoints = 3,
            jump = 1,
            description = "Anchor unit. Low move, high defense. Holds a column so back-lane mages aren't occluded by height-3 terrain. Earth skills are mostly melee — they take the upward penalty, so the Guardian wants the high tile, not the charge.",
            personality = "Few words. Plants feet. Talks about stone, roots, and who is allowed past.",
            firstMes = "This tile is mine. The lane behind me stays empty of corpses.",
            colorHex = "#6B5A3C",
        ),
        classCard(
            classType = "Ranger",
            element = "Wind",
            age = "Medieval",
            movePoints = 5,
            jump = 2,
            costLateral = 1,
            description = "Skirmisher. Lateral lane-change is cheaper than the default 2 MP, so this card actually uses the three-lane grid. Jump 2 lets it take ruin steps the Warrior can't. Wind skills are ranged — downward bonus on cliff maps.",
            personality = "Restless, counts columns out loud, teases tanks for standing still. Notices streams, ruins, and canopy gaps because those are overworld landmarks too.",
            firstMes = "Three lanes. I don't live in one. Keep the back column open.",
            colorHex = "#3FA66A",
        ),
        classCard(
            classType = "Mage",
            element = "Light",
            age = "Mythic",
            movePoints = 3,
            jump = 1,
            description = "Back-lane artillery. Magic stat, not attack. Light skills are ranged with wide area offsets once aimed. Height 3 in front of a Mage is a design bug — the camera can't rotate, so the Mage never sees the shot. Keep front-lane terrain low.",
            personality = "Precise, a little vain about clip timing. Cares when the impact frame lands because that's when the damage number is allowed to appear.",
            firstMes = "Aim first. Area second. If you stack a wall in front of me I will simply not cast.",
            colorHex = "#D4C36A",
        ),
        classCard(
            classType = "Healer",
            element = "Water",
            age = "Medieval",
            movePoints = 4,
            jump = 1,
            description = "Support card. Water skills reach sideways across lanes more than they punch forward. Cooking buffs stack with a Healer the way the Kitchen intended — cook before the boss ten.",
            personality = "Calm under clip noise. Scolds people who skip the Kitchen. Speaks in cooldowns and who is actually in pattern.",
            firstMes = "Who is in my pattern. Don't make me waste a cooldown on an empty tile.",
            colorHex = "#4A90D9",
        ),
        classCard(
            classType = "Assassin",
            element = "Dark",
            age = "Modern",
            movePoints = 5,
            jump = 2,
            costLateral = 1,
            description = "Flanker. Dark element, modern island. Wants the side lane and a height drop onto the target. Standard skill is a single-tile execute. No starter gear — this card comes in empty-handed and steals the first drop.",
            personality = "Dry, impatient with deployment chatter. Counts facing because patterns mirror on the column axis only.",
            firstMes = "Face them. I only have two facings. Make the first one count.",
            colorHex = "#5A4A6A",
        ),
        classCard(
            classType = "Summoner",
            element = "Neutral",
            age = "Futuristic",
            movePoints = 4,
            jump = 1,
            description = "The gacha's joke and its thesis: a card that summons more cards. Neutral element so it can roll from any island's element pool if the definition allows. Standard skill plants a token on a tile; class skills pull a ghost of another roster member for one clip.",
            personality = "Curious, overlay-brained, talks about seeds and pity as if they were weather. Treats the Guild Hall as a temple.",
            firstMes = "The roll already happened. I'm what's left. Want to see the seed?",
            colorHex = "#8B6FD1",
        ),
    )

    val lore: List<RpgLoreEntry> = listOf(
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}haven",
            category = "Locations",
            name = "Adams Haven",
            aliases = listOf("Haven", "Elysium Vale"),
            keys = listOf("Adams Haven", "Elysium Vale", "Haven"),
            alwaysInclude = true,
            isConstant = true,
            insertionOrder = 10,
            colorHex = COLOR_LOCATION,
            body = """
                Adams Haven sits on Elysium Vale, the hub of an archipelago where each island is locked to an Age: Primitive, Medieval, Industrial, Modern, Futuristic, Mythic. The town, the starter farm, and the forest path that leads to the farm gate are the Home/overworld pair — close diorama for chores, wider steep camera for travel. JD arrives by transfer, not by ferry. The Isekai Incubus System confirms the landing.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}forest",
            category = "Locations",
            name = "Forest Path",
            aliases = listOf("overworld", "canopy path"),
            keys = listOf("forest path", "stream", "ruins", "overworld"),
            insertionOrder = 20,
            colorHex = COLOR_LOCATION,
            body = """
                Overworld approach to the farm. A winding dirt path, a clear stream with stepping stones as a guide line (not a hard wall), moss-covered ruins and stone pillars at junctions as waypoints. Canopy light shafts, deep teal-green, gold accents. Camera is steeper and wider than Home — region scale, not plot scale. The path ends at the farm gate.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}farm",
            category = "Locations",
            name = "Starter Farm Plot",
            aliases = listOf("Aesthetic Sandbox", "farm plot", "Home farm"),
            keys = listOf("farm", "plot", "weed", "oak", "tilled"),
            insertionOrder = 21,
            colorHex = COLOR_LOCATION,
            body = """
                Home camera. 2×2 aesthetic sandbox (roadmap still calls the larger field a 4×4). North-west: Oak Tree, axe, farm Lv.3, 20 XP, blocks movement and planting. North-east: Weed, scythe, Lv.1, 6 XP, blocks planting only. South-west: Rock, pickaxe, Lv.2, 12 XP, blocks both. South-east: empty, player start, already tilled. Other tiles untilled. Level curve 15 / 40 / 80 / 140 / 220 XP. Clearing the last obstacle is the cue to expand the grid. Brown Dust 2–inspired dusk-purple plate, warm spotlight, chibi billboard player.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}farmhouse",
            category = "Locations",
            name = "Farmhouse",
            aliases = listOf("home", "night farmhouse"),
            keys = listOf("farmhouse", "lantern", "night"),
            insertionOrder = 22,
            colorHex = COLOR_LOCATION,
            body = """
                Day/night anchor of Home. Lit windows, hanging lantern, indigo night. Same close pitch as the farm plot, night palette instead of dusk. This is where rest belongs — Kitchen, storage, the door JD stands at before a stage. Not a combat map.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}town",
            category = "Locations",
            name = "Town Districts",
            aliases = listOf("town", "districts"),
            keys = listOf("district", "town", "building slots"),
            insertionOrder = 23,
            colorHex = COLOR_LOCATION,
            body = """
                Districts gate which buildings may be placed and how many. Types: Residential, Industrial, Agricultural, Military, Arcane, Commercial. Each has an unlock cost, required flags, and a small queue-speed bonus. Buildings must grant a verb the player did not have: Recruitment (Guild Hall), Gear Enhancement (Blacksmith), Cooking (Kitchen), Sell Produce (Market), Idle Team Slots (Barracks/Dorm), Crafting, Seed Production, Storage. Higher levels unlock better products, not a flat stat stick.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}lanes",
            category = "Locations",
            name = "Lane Field",
            aliases = listOf("battlefield", "grid"),
            keys = listOf("lane", "column", "battlefield", "deployment"),
            insertionOrder = 24,
            colorHex = COLOR_LOCATION,
            body = """
                Default combat map: 3 lanes (rows) × 8 columns. Height capped at 3 — a fixed camera cannot peek around tall front-lane terrain. Tiles: Plain, Grass, Stone, Water, Sand, Ruins. Player deploy tiles are a free placement phase. Enemies are authored placements with a definition, a tier, a cell, and a level. Brown Dust 2–style square targeting: range is where a skill may be aimed; area is what it hits relative to that tile.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}gacha",
            category = "Magic/Tech Systems",
            name = "Gacha Cards",
            aliases = listOf("summon", "character card", "pull"),
            keys = listOf("gacha", "summon", "card", "roll", "seed"),
            alwaysInclude = true,
            insertionOrder = 30,
            colorHex = COLOR_MAGIC,
            body = """
                Units are cards. A CharacterDefinition is the authored template. A CharacterInstance is the rolled save: tier, rollSeed, rolledStats, classSkillId, elementSkillId. All randomness flows through one seeded RNG so Android and PC saves resolve identically. Three skills at recruitment: 1 standard (fixed on the definition), 1 class (rolled from the class pool), 1 element (rolled from the element pool). Movement (movePoints, jump, lane-change cost) never rolls. No starter gear — weapon, armor, accessory start empty. Classes: Warrior, Guardian, Ranger, Mage, Healer, Assassin, Summoner. Elements: Neutral, Fire, Water, Wind, Earth, Light, Dark.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}tiers",
            category = "Lore",
            name = "Tiers F–SSS",
            aliases = listOf("tier", "rank"),
            keys = listOf("tier", "SSS", "variance", "summon weight"),
            insertionOrder = 31,
            colorHex = COLOR_LORE,
            body = """
                F E D C B A S SS SSS. F–A: variance 0.15 (±15%) at multiplier 1.0. S–SSS: a statMultiplier above 1.0; variance is independently tunable. Summon weight on each TierDefinition drives gacha rarity. The roll happens once, then freezes.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}fusion",
            category = "Lore",
            name = "Fusion",
            aliases = listOf("duplicate", "limit break"),
            keys = listOf("fusion", "duplicate", "fuse"),
            insertionOrder = 32,
            colorHex = COLOR_LORE,
            body = """
                Duplicate copies fuse into an owned instance. Each fusion +10%, additive, cap 10 (so +100% at max). Multiplicative (~2.59× at 10) exists as a one-line switch and is not the default — it compounds too hard on top of tier variance.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}tactics",
            category = "Magic/Tech Systems",
            name = "Lane Tactics",
            aliases = listOf("combat", "pathfinding"),
            keys = listOf("jump", "move points", "facing", "elevation"),
            insertionOrder = 33,
            colorHex = COLOR_MAGIC,
            body = """
                Jump is a legality check, not a cost: a height difference above jump makes the edge illegal and must be rejected inside pathfinding, never post-filtered. Default costs: forward 1 MP, lateral lane-change 2 MP, plus costPerHeightLevel. Facing is two-state; skill patterns mirror on the column axis only (2 facings of art, not 8). Ranged skills gain the downward elevation bonus; melee takes the upward penalty. Clips carry impact frames so pre-rendered video can time damage numbers.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}ages",
            category = "Lore",
            name = "Archipelago Ages",
            aliases = listOf("islands", "era"),
            keys = listOf("Primitive", "Medieval", "Industrial", "Modern", "Futuristic", "Mythic", "island"),
            insertionOrder = 34,
            colorHex = COLOR_LORE,
            body = """
                Six islands, six Ages: Primitive, Medieval, Industrial, Modern, Futuristic, Mythic. Cards, materials, and gear all carry an Age. Crops can be native to an islandId or grow anywhere. This is the genre-per-island structure — a medieval farm and a futuristic summoner can meet in Adams Haven because the hub sits between them.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}farming",
            category = "Lore",
            name = "Farming",
            aliases = listOf("crops", "plots"),
            keys = listOf("crop", "water", "fertiliser", "harvest", "dungeon plot"),
            insertionOrder = 35,
            colorHex = COLOR_LORE,
            body = """
                Stardew × Rune Factory. Seed in, water and fertiliser modify growth, produce out. Growth completes on whichever comes first: growthSeconds or growthBattles (endless-mode fights count). Clock manipulation is allowed — no server check. Plot types: Town, Dungeon, Both. Water speed bonus default 25%; dry plots stall if requiresWater. Harvest min/max yield; regrowSeconds 0 means single harvest. Dungeon farming and town farming share rules, different dirt.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}materials",
            category = "Objects/Items",
            name = "Materials",
            aliases = listOf("drops", "catalogue"),
            keys = listOf("material", "rarity", "drop table", "boss"),
            insertionOrder = 36,
            colorHex = COLOR_OBJECT,
            body = """
                Four independent axes: tier (1–10, power band), age, island, rarity (Common through Legendary). Categories are mode-owned: BuildingMaterial (Battle), GearUpgrade (Gear stages), PremiumCurrency (Endless), StandardCurrency (Idle), FarmInput, Produce, CraftComponent, Consumable. Drop tables are fixed, not rotating — players plan routes. Guaranteed + chance + unlockGatedEntries. A boss clear (every 10th stage) can add materials to earlier tables so old stages stay relevant. Gear: drop, summon, or craft. Exclusive pieces never appear on drop tables. Enhance cap 15, +8% main stats per level.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}stages",
            category = "Events/Timeline",
            name = "Stages",
            aliases = listOf("story stages", "boss ten"),
            keys = listOf("stage", "boss", "first clear", "endless"),
            insertionOrder = 37,
            colorHex = COLOR_EVENT,
            body = """
                A stage has an island, a number, a mode (Story, Battle, GearStage, Endless, Idle), a map, a drop table, required flags, and first-clear rewards. Every 10th is a boss and sets unlockFlagOnClear. Endless fights also tick crop growthBattles. Idle mode spends Barracks slots. Gear stages are where enhance materials come from.
            """.trimIndent(),
        ),
        RpgLoreEntry(
            id = "${LORE_ID_PREFIX}stats",
            category = "Lore",
            name = "Stat Block",
            aliases = listOf("stats"),
            keys = listOf("HP", "attack", "defense", "magic", "resistance", "speed"),
            insertionOrder = 38,
            colorHex = COLOR_LORE,
            body = """
                Rolled combat stats: HP, attack, defense, magic, resistance, speed. Effective stats = rolledStats × (1 + growthPerLevel × (level−1)) × fusionMultiplier. growthPerLevel default 0.06. Movement is not in this block.
            """.trimIndent(),
        ),
    )

    val scenes: List<RpgScene> = listOf(
        RpgScene(
            id = "${SCENE_ID_PREFIX}void",
            title = "RPG · Pulled through the void",
            location = "Between worlds",
            blurb = "Transfer cutscene — white, then the dark between worlds.",
            displayMode = "roleplay",
            characterId = "${CHAR_ID_PREFIX}iis",
            sortOrder = 0,
            opening = "White. Then the dark between worlds. Someone — or something — is pulling you through. A band of heat closes around your wrist like a clasp finding skin.",
            authorsNote = "Arrival beat only. Do not start the farm tutorial until JD is on dirt. IIS may speak as system text. Manga 6×6: keep the void large; do not fill every cell.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}forest",
            title = "RPG · Forest path",
            location = "Overworld — canopy path",
            blurb = "Stream, stepping stones, moss ruins. The farm gate is ahead.",
            displayMode = "dungeonMaster",
            characterId = "${CHAR_ID_PREFIX}iis",
            sortOrder = 1,
            opening = "Sun cuts through the canopy in long gold shafts. A dirt path crosses a clear stream on stepping stones. Moss takes the old stone pillars at the bend. Somewhere ahead, past the ruin waypoint, a farm gate waits.",
            authorsNote = "Overworld camera: wide, steep, region-scale. Stream is a guide, not a wall. Landmarks: stepping stones, moss ruins, farm gate. IIS can ping but the path should feel like a place, not a loading screen. 3×3 DM canvas.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}farm",
            title = "RPG · Starter farm plot",
            location = "Home — 2×2 aesthetic sandbox",
            blurb = "Weed, rock, oak, one tilled tile. Clear the plot.",
            displayMode = "dungeonMaster",
            characterId = "${CHAR_ID_PREFIX}iis",
            sortOrder = 2,
            opening = "The plot is smaller than the stories. Two tiles by two. An oak roots the far-left corner. Weeds brush the far-right. A rock squats beside you. The square under your boots is already tilled, dark, waiting. The system hums against your wrist: scythe for grass, pickaxe for stone, axe for the oak — if your farm level will allow it.",
            authorsNote = "Exact layout: NW oak (axe Lv.3), NE weed (scythe Lv.1), SW rock (pickaxe Lv.2), SE player + tilled. Home camera, close diorama. Let JD clear, fail a level gate, and hear XP. When the last obstacle goes: 'Starter plot cleared. Expand the grid next.'",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}farmhouse",
            title = "RPG · Farmhouse night",
            location = "Home — farmhouse door",
            blurb = "Lantern, indigo night, Kitchen through the door.",
            displayMode = "dungeonMaster",
            characterId = "${CHAR_ID_PREFIX}kitchen",
            sortOrder = 3,
            opening = "Night belongs to the farmhouse. Windows hold a warm square of light. A lantern hangs by the door and throws a small gold coin on the step. Indigo sits in the trees behind you. Inside, something is already on the boil.",
            authorsNote = "Rest scene. Kitchen may speak. No combat. Offer food buffs before tomorrow's stage. Home camera, night palette.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}guild",
            title = "RPG · Guild Hall summon",
            location = "Town — Guild Hall",
            blurb = "Roll a card. Seed freezes. No starter gear.",
            displayMode = "messenger",
            characterId = "${CHAR_ID_PREFIX}guild",
            sortOrder = 4,
            opening = "Counter. Circle in the floor. Empty racks on the wall — they do not issue a practice sword here. The clerk does not look up. 'Name on the ledger. Then we roll.'",
            authorsNote = "This is the gacha. Narrate a summon: pick a class card, a tier (F–SSS), freeze the seed, assign standard + class + element skills, leave gear slots empty. Messenger mode. IIS may overlay a system notice.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}lanes",
            title = "RPG · First lane battle",
            location = "3×8 field",
            blurb = "Deploy, face them, don't jump what you can't.",
            displayMode = "dungeonMaster",
            characterId = "${CHAR_ID_PREFIX}iis",
            sortOrder = 5,
            opening = "Three lanes. Eight columns. The ground is not flat — a ruin step in the center lane sits one height up, legal for a jump of 1. Enemy placements already hold the far columns. Deployment is still free. Your cards have no gear. The system, mild: 'Facing is two-state. Patterns mirror. Try not to occlude your mage.'",
            authorsNote = "Teach lane tactics in scene, not as a manual. Height cap 3. Jump is illegal/legal, never a cost. Ranged wants high-to-low. Keep the front lane from blocking the camera. 3×3 DM canvas can show a crop of the grid, not all 24 tiles.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}town",
            title = "RPG · Town square",
            location = "First town — district board",
            blurb = "Unlock a district. Buildings are verbs.",
            displayMode = "dungeonMaster",
            characterId = "${CHAR_ID_PREFIX}guild",
            sortOrder = 6,
            opening = "The square is a board more than a plaza. Stakes mark empty lots. A painted legend lists districts: Residential, Industrial, Agricultural, Military, Arcane, Commercial. The Guild Hall already claims one slot. The rest wait on flags and materials you do not have yet.",
            authorsNote = "Town-building scene. Stress verbs over bonuses. Agricultural wants the farm's produce. Arcane wants the Mage. Do not unlock everything in one beat.",
        ),
        RpgScene(
            id = "${SCENE_ID_PREFIX}kitchen",
            title = "RPG · Cook before the ten",
            location = "Kitchen",
            blurb = "Dish buff before a boss stage. Surplus we sell.",
            displayMode = "messenger",
            characterId = "${CHAR_ID_PREFIX}kitchen",
            sortOrder = 7,
            opening = "The Kitchen does not ask if you are hungry. It asks which stage is next. 'Every tenth is a boss. You cook. If the pantry is all surplus, we sell. Sit.'",
            authorsNote = "Cooking as the correct play before hard content. One exclusive dish buff. Ingredients come from farm produce. Messenger, short, practical.",
        ),
    )

    fun cardById(id: String): RpgCard? = cards.find { it.id == id }

    fun sceneById(id: String): RpgScene? = scenes.find { it.id == id }

    fun systemPromptFor(card: RpgCard): String = buildString {
        append("You are ")
        append(card.name)
        append(". Stay fully in character for the whole reply.\n\n")
        append("You are a Pantser style Dungeon Master that will create scenes in a movie show not tell way. ")
        append("Write in scene, not as a summary. Do not recap. Do not speak for the other person.\n")
        append("\nWho you are:\n")
        append(card.description.trim())
        append("\n\nHow you come across:\n")
        append(card.personality.trim())
        append("\n\nThe scene you are in:\n")
        append(card.scenario.trim())
        card.classType?.let { cls ->
            append("\n\nCard data: class ")
            append(cls)
            card.element?.let { append(", element $it") }
            card.age?.let { append(", age $it") }
            card.movePoints?.let { append(", move $it") }
            card.jump?.let { append(", jump $it") }
            append(". Lateral lane-change costs ${card.costLateral} MP, forward ${card.costForward}.")
        }
        append("\n\nWrite the next beat in prose.")
    }

    fun extensionsJsonFor(card: RpgCard): String {
        val fields = buildList {
            card.classType?.let { add("\"classType\":\"$it\"") }
            card.element?.let { add("\"element\":\"$it\"") }
            card.age?.let { add("\"age\":\"$it\"") }
            card.movePoints?.let { add("\"movePoints\":$it") }
            card.jump?.let { add("\"jump\":$it") }
            add("\"costLateral\":${card.costLateral}")
            add("\"costForward\":${card.costForward}")
        }
        return """{"adamsHaven":{${fields.joinToString(",")}}}"""
    }

    fun tagsJsonFor(card: RpgCard): String =
        card.tags.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }

    fun aliasesJsonFor(entry: RpgLoreEntry): String =
        entry.aliases.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }

    fun keysJsonFor(entry: RpgLoreEntry): String =
        entry.keys.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }

    private fun classCard(
        classType: String,
        element: String,
        age: String,
        movePoints: Int,
        jump: Int,
        costLateral: Int = 2,
        description: String,
        personality: String,
        firstMes: String,
        colorHex: String,
    ): RpgCard = RpgCard(
        id = "${CHAR_ID_PREFIX}${classType.lowercase()}",
        name = classType,
        description = description,
        personality = personality,
        scenario = WORLD_SCENARIO,
        firstMes = firstMes,
        tags = listOf(TAG_CARDS, TAG_GACHA, TAG_RPG, classType.lowercase()),
        colorHex = colorHex,
        creatorNotes = "Adams Haven RPG — $classType class card (gacha definition).",
        classType = classType,
        element = element,
        age = age,
        movePoints = movePoints,
        jump = jump,
        costLateral = costLateral,
        costForward = 1,
    )
}

data class RpgPersona(
    val id: String,
    val name: String,
    val description: String,
)

data class RpgSnippet(
    val id: String,
    val title: String,
    val body: String,
)

data class RpgCard(
    val id: String,
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val tags: List<String>,
    val colorHex: String,
    val creatorNotes: String,
    val classType: String? = null,
    val element: String? = null,
    val age: String? = null,
    val movePoints: Int? = null,
    val jump: Int? = null,
    val costLateral: Int = 2,
    val costForward: Int = 1,
)

data class RpgLoreEntry(
    val id: String,
    val category: String,
    val name: String,
    val aliases: List<String>,
    val keys: List<String>,
    val body: String,
    val alwaysInclude: Boolean = false,
    val isConstant: Boolean = false,
    val insertionOrder: Int = 100,
    val colorHex: String,
)

data class RpgScene(
    val id: String,
    val title: String,
    val location: String,
    val blurb: String,
    val displayMode: String,
    val characterId: String,
    val opening: String,
    val authorsNote: String,
    val sortOrder: Int,
)
