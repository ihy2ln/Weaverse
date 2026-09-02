package com.ihy2ln.weaverse.feature.roleplay.textgame

import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.TextGameSaveEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TextGameUiState(
    val campaignId: String = "",
    val campaignTitle: String = "",
    val definition: TextGameDefinition = adamsHavenTutorial(),
    val game: TextGameState = TextGameEngine(adamsHavenTutorial()).initialState(),
    val sceneImagePath: String? = null,
    val sceneMotionPath: String? = null,
    val cardImagePaths: Map<String, String> = emptyMap(),
    val playStyle: TextGamePlayStyle = TextGamePlayStyle.Campaign,
    val generatedNarration: List<String> = emptyList(),
    val loading: Boolean = true,
    val saveError: String? = null,
)

@HiltViewModel
class TextGameViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val aiGeneration: AiGenerationService,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var definition = adamsHavenDefinition(TextGamePlayStyle.Campaign)
    private var engine = TextGameEngine(definition)
    private var campaignDifficulty = TextGameDifficulty.Standard
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val missionCodec = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _uiState = MutableStateFlow(TextGameUiState(definition = definition, game = engine.initialState()))
    val uiState: StateFlow<TextGameUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null
    private var narrationJob: Job? = null

    fun bind(campaignId: String) {
        if (_uiState.value.campaignId == campaignId && !_uiState.value.loading) return
        viewModelScope.launch {
            val cardImagePaths = installBundledImageLibrary()
            val campaign = db.roleplayDao().getChat(campaignId)
            campaignDifficulty = parseTextGameDifficulty(campaign?.authorsNote)
            definition = adamsHavenDefinition(TextGamePlayStyle.Campaign)
            engine = TextGameEngine(definition)
            observeNarration(campaignId, campaign?.displayMode ?: "textGame")
            val save = db.textGameSaveDao().get(campaignId, definition.id)
            val restored = if (save?.schemaVersion == definition.schemaVersion) decodeSave(save) else null
            val game = restored ?: engine.initialState(campaignDifficulty, freshCampaignSeed(campaignId, definition.id))
            _uiState.value = TextGameUiState(
                campaignId = campaignId,
                campaignTitle = campaign?.title.orEmpty().ifBlank { "Campaign" },
                definition = definition,
                game = game,
                sceneImagePath = loadSceneImage(game.run.nodeId, game.persistent.rngSeed),
                sceneMotionPath = loadSceneMotion(game.run.nodeId, game.persistent.rngSeed),
                cardImagePaths = cardImagePaths,
                playStyle = TextGamePlayStyle.Campaign,
                loading = false,
            )
            if (game.persistent.missionId == null && game.run.missionOffer.isEmpty()) generateMissions()
        }
    }

    fun selectPlayStyle(style: TextGamePlayStyle) {
        val current = _uiState.value
        if (current.loading || current.playStyle == style) return
        definition = adamsHavenDefinition(style)
        engine = TextGameEngine(definition)
        _uiState.value = current.copy(loading = true, playStyle = style, definition = definition)
        viewModelScope.launch {
            val save = db.textGameSaveDao().get(current.campaignId, definition.id)
            val restored = if (save?.schemaVersion == definition.schemaVersion) decodeSave(save) else null
            val game = restored ?: engine.initialState(campaignDifficulty, freshCampaignSeed(current.campaignId, definition.id))
            _uiState.value = _uiState.value.copy(
                definition = definition,
                game = game,
                sceneImagePath = loadSceneImage(game.run.nodeId, game.persistent.rngSeed),
                sceneMotionPath = loadSceneMotion(game.run.nodeId, game.persistent.rngSeed),
                loading = false,
            )
            if (game.persistent.missionId == null && game.run.missionOffer.isEmpty()) generateMissions()
        }
    }

    fun dispatch(action: TextGameAction) {
        val current = _uiState.value
        if (current.loading) return
        val result = engine.reduce(current.game, action)
        val next = result.state
        _uiState.value = current.copy(game = next, saveError = null)
        val enteredEmptyMissionBoard = definition.node(next.run.nodeId)?.type == TextGameNodeType.MissionBoard &&
            next.persistent.missionId == null && next.run.missionOffer.isEmpty()
        if (action is TextGameAction.Reset || enteredEmptyMissionBoard) generateMissions()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val sceneImagePath = loadSceneImage(next.run.nodeId, next.persistent.rngSeed)
            val sceneMotionPath = loadSceneMotion(next.run.nodeId, next.persistent.rngSeed)
            runCatching { persist(current.campaignId, next) }
                .onSuccess { _uiState.value = _uiState.value.copy(sceneImagePath = sceneImagePath, sceneMotionPath = sceneMotionPath, saveError = null) }
                .onFailure { _uiState.value = _uiState.value.copy(sceneImagePath = sceneImagePath, sceneMotionPath = sceneMotionPath, saveError = "Progress could not be saved.") }
        }
    }

    fun isChoiceEnabled(state: TextGameState, choice: TextGameChoice): Boolean = engine.isChoiceEnabled(state, choice)
    fun canPlay(card: TextGameCard): Boolean = engine.canPlay(_uiState.value.game, card)
    fun canSelectCard(card: TextGameCard): Boolean = engine.canSelectCard(_uiState.value.game, card)

    /** Builds and persists a 1-6 contract board. AI is optional; offline deals use the same shape. */
    fun generateMissions() {
        val current = _uiState.value
        if (current.loading ||
            current.game.persistent.missionId != null ||
            current.game.run.missionOffer.isNotEmpty()
        ) {
            return
        }
        viewModelScope.launch {
            val seed = current.game.persistent.rngSeed
            val count = missionOfferCount(seed)
            val deal = requestAiMissionDeal(seed, count) ?: fallbackMissionDeal(seed, count)
            val newEntries = deal.missions.map { mission ->
                TextGameMissionLogEntry(
                    mission = mission,
                    status = TextGameMissionStatus.Available,
                    offeredSeed = seed,
                )
            }
            val existingIds = _uiState.value.game.persistent.missionLog.map { it.mission.id }.toSet()
            val game = _uiState.value.game.copy(
                persistent = _uiState.value.game.persistent.copy(
                    missionLog = (_uiState.value.game.persistent.missionLog + newEntries.filterNot {
                        it.mission.id in existingIds
                    }).takeLast(60),
                ),
                run = _uiState.value.game.run.copy(
                    missionOffer = deal.missions,
                    missionBoardIntro = deal.intro,
                ),
            )
            _uiState.value = _uiState.value.copy(game = game)
            runCatching { persist(current.campaignId, game) }
        }
    }

    private suspend fun requestAiMissionDeal(seed: Long, count: Int): MissionBoardDeal? {
        if (!aiGeneration.hasApiKey()) return null
        val openingTitle = definition.node(_uiState.value.game.run.nodeId)?.title.orEmpty()
        val instruction = buildString {
            append("You are the roguelite mission dealer for the card-RPG \"${definition.title}\" ")
            append("(opening scene: ${openingTitle.ifBlank { definition.startNodeId }}, ")
            append("difficulty ${campaignDifficulty.label}).\n")
            append("Deal exactly $count distinctly different dungeon missions for the player to choose between. ")
            append("Reply with ONLY a JSON object, no prose:\n")
            append("{\"intro\":\"\",\"missions\":[{\"id\":\"\",\"title\":\"\",\"description\":\"\",\"coins\":0,\"seeds\":0,")
            append("\"materials\":0,\"bonusHealth\":0,\"bonusMaxHealth\":0,\"guard\":0,\"sp\":0}]}\n")
            append("Rules:\n")
            append("- intro is a fresh 2-3 sentence first-person, present-tense arrival at a fantasy dungeon mission board. Never reuse the former fixed shack opening.\n")
            append("- Each mission is a different expedition (hunt, rescue, explore, trade, fight, sneak, study, recover, escort, and so on).\n")
            append("- One mission may be riskier with bigger rewards; keep stat modifiers within -2 to +4.\n")
            append("- ids are snake_case; descriptions are one or two vivid sentences, first person, present tense.\n")
            append("- Variety seed: $seed. Make these missions different from anything dealt before.\n")
        }
        val raw = runCatching {
            aiGeneration.complete(userMessage = instruction, maxTokens = 1200, temperature = 1.0).text
        }.getOrNull() ?: return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val root = missionCodec.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
            val intro = root["intro"]?.jsonPrimitive?.content?.trim()?.take(420)
                ?.takeIf(String::isNotBlank) ?: return@runCatching null
            val array = root["missions"]?.jsonArray ?: return@runCatching null
            val missions = array.mapIndexedNotNull { index, element ->
                val obj = element as? JsonObject ?: return@mapIndexedNotNull null
                fun intField(name: String): Int =
                    (obj[name]?.jsonPrimitive?.content?.toIntOrNull() ?: 0).coerceIn(-2, 4)
                val id = obj["id"]?.jsonPrimitive?.content?.trim()
                    ?.takeIf { it.matches(Regex("[a-z][a-z_]+")) } ?: return@mapIndexedNotNull null
                val title = obj["title"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapIndexedNotNull null
                TextGameMission(
                    id = missionInstanceId(id, seed, index),
                    title = title.take(60),
                    description = obj["description"]?.jsonPrimitive?.content?.trim()?.take(240).orEmpty(),
                    effects = listOf(
                        TextGameEffect(
                            coinsDelta = intField("coins"),
                            seedsDelta = intField("seeds"),
                            materialsDelta = intField("materials"),
                            healthDelta = intField("bonusHealth"),
                            maxHealthDelta = intField("bonusMaxHealth"),
                            preparedGuardDelta = intField("guard"),
                            summonerSpDelta = intField("sp"),
                        ),
                    ),
                )
            }.distinctBy { it.id }.take(count)
            missions.takeIf { it.size == count }?.let { MissionBoardDeal(intro, it) }
        }.getOrNull()
    }

    /** Local-rotation fallback so mission boards also work fully offline. */
    private fun fallbackMissionDeal(seed: Long, count: Int): MissionBoardDeal {
        val templates = listOf(
            Triple("coin_run", "Coin Run", "I start by chasing Silverbrook's coin — every favor answered pays in full.") to
                TextGameEffect(coinsDelta = 4),
            Triple("seed_scholar", "Seed Scholar", "I barter for lanternroot stock and trust the Farm to feed the whole run.") to
                TextGameEffect(seedsDelta = 2, harvestDelta = 1),
            Triple("scrappers_start", "Scrapper's Start", "Salvage first, questions later — my pack begins heavy with ore.") to
                TextGameEffect(materialsDelta = 2),
            Triple("iron_constitution", "Iron Constitution", "I have survived worse mornings than this one, and I will survive this.") to
                TextGameEffect(maxHealthDelta = 3, healthDelta = 3),
            Triple("frail_but_bright", "Frail but Bright", "The case's light runs hard through me — stronger summons, thinner skin.") to
                TextGameEffect(maxHealthDelta = -2, summonerSpDelta = 2),
            Triple("prepared_wards", "Prepared Wards", "I pre-draw the guard that will hold the first line before any blade moves.") to
                TextGameEffect(preparedGuardDelta = 3),
            Triple("lost_cartographer", "The Lost Cartographer", "I follow a surveyor's unfinished map into a wing that changes whenever the lanterns dim.") to
                TextGameEffect(materialsDelta = 1, preparedGuardDelta = 1),
            Triple("bell_below", "The Bell Below", "I hunt the source of a buried bell before its next toll wakes everything in the lower halls.") to
                TextGameEffect(summonerSpDelta = 1, coinsDelta = 1),
            Triple("rootbound_rescue", "Rootbound Rescue", "I enter the overgrown cells to recover a missing patrol before the roots close over their trail.") to
                TextGameEffect(healthDelta = 2, seedsDelta = 1),
            Triple("quiet_reliquary", "The Quiet Reliquary", "I seek a sealed reliquary whose guardians only move when I speak above a whisper.") to
                TextGameEffect(coinsDelta = 2, materialsDelta = 1),
        )
        var value = seed
        val remaining = templates.toMutableList()
        val missions = buildList {
            repeat(count.coerceIn(1, 6)) { index ->
                value = value * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
                val (info, effects) = remaining.removeAt(((value ushr 1) % remaining.size).toInt())
                add(TextGameMission(
                    id = missionInstanceId(info.first, seed, index),
                    title = info.second,
                    description = info.third,
                    effects = listOf(effects),
                ))
            }
        }
        return MissionBoardDeal(fallbackMissionBoardIntro(seed), missions)
    }

    private suspend fun persist(campaignId: String, state: TextGameState) {
        db.textGameSaveDao().upsert(
            TextGameSaveEntity(
                campaignId = campaignId,
                gameId = definition.id,
                schemaVersion = definition.schemaVersion,
                persistentStateJson = codec.encodeToString(state.persistent),
                runStateJson = codec.encodeToString(state.run),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun decodeSave(save: TextGameSaveEntity): TextGameState? = runCatching {
        TextGameState(
            persistent = codec.decodeFromString<TextGamePersistentState>(save.persistentStateJson),
            run = codec.decodeFromString<TextGameRunState>(save.runStateJson),
        ).takeIf { definition.node(it.run.nodeId) != null }
    }.getOrNull()

    private suspend fun loadSceneImage(nodeId: String, seed: Long): String? {
        val node = definition.node(nodeId) ?: return null
        val typedAssets = node.sceneAssetType?.let { type ->
            definition.sceneAssets.filter { type in it.sceneTypes }
        }.orEmpty()
        if (typedAssets.isNotEmpty()) {
            val mixed = (seed xor nodeId.hashCode().toLong()) and Long.MAX_VALUE
            val categorizedPaths = db.mediaDao().getImagesByTag("scene:${node.sceneAssetType}")
                .mapNotNull { media ->
                    resolveSceneMediaPath(media.relativePath, context.filesDir)?.let { media.id to it }
                }
                .sortedBy { it.first }
            if (categorizedPaths.isNotEmpty()) {
                return categorizedPaths[(mixed % categorizedPaths.size).toInt()].second
            }
            val ordered = typedAssets.sortedBy(TextGameSceneAsset::id)
            val selected = ordered[(mixed % ordered.size).toInt()]
            db.mediaDao().getById(selected.mediaId)?.let { media ->
                resolveSceneMediaPath(media.relativePath, context.filesDir)?.let { return it }
            }
            return selected.artAssetPath.takeIf { context.assetExists(it) }?.let { "file:///android_asset/$it" }
        }
        val mediaId = node.sceneMediaId ?: return null
        val media = db.mediaDao().getById(mediaId) ?: return null
        return resolveSceneMediaPath(media.relativePath, context.filesDir)
    }

    private suspend fun loadSceneMotion(nodeId: String, seed: Long): String? {
        val node = definition.node(nodeId) ?: return null
        node.sceneMotionMediaId?.let { mediaId ->
            val media = db.mediaDao().getById(mediaId)
            if (media?.type == "video") {
                resolveSceneMediaPath(media.relativePath, context.filesDir)?.let { return it }
            }
        }
        node.sceneAssetType?.let { sceneType ->
            val categorized = db.mediaDao().getByTypeAndTag("video", "scene:$sceneType")
                .mapNotNull { media ->
                    resolveSceneMediaPath(media.relativePath, context.filesDir)?.let { media.id to it }
                }
                .sortedBy { it.first }
            if (categorized.isNotEmpty()) {
                val mixed = (seed xor nodeId.hashCode().toLong()) and Long.MAX_VALUE
                return categorized[(mixed % categorized.size).toInt()].second
            }
        }
        val bundled = node.bundledSceneMotionAssetPath ?: return null
        return bundled.takeIf { context.assetExists(it) }?.let { "asset:///$it" }
    }

    private suspend fun installBundledImageLibrary(): Map<String, String> = buildMap {
        definition.collectibleCards.forEach { card ->
            val relativePath = "images/adams_haven/${card.category}/${card.id.substringAfter('/')}.png"
            val media = mediaRepository.registerBundledImage(
                assetPath = card.artAssetPath,
                id = card.mediaId,
                relativePath = relativePath,
                width = 941,
                height = 1672,
                displayName = card.title,
                category = adamsHavenCardMediaCategory(card.category),
                tags = adamsHavenCardMediaTags(card),
            )
            put(card.id, mediaRepository.resolveFile(media).absolutePath)
        }
        definition.sceneAssets.distinctBy(TextGameSceneAsset::mediaId).forEach { scene ->
            mediaRepository.registerBundledImage(
                assetPath = scene.artAssetPath,
                id = scene.mediaId,
                relativePath = "images/adams_haven/maps/${scene.id}.png",
                width = scene.width,
                height = scene.height,
                displayName = scene.displayName,
                category = scene.category,
                tags = scene.tags.joinToString(","),
            )
        }
        installBundledDungeonImageLibrary()
    }

    private suspend fun installBundledDungeonImageLibrary() {
        val root = "images/adams_haven/maps/dungeon"
        context.assetFilesRecursively(root)
            .filter { it.endsWith(".png", ignoreCase = true) }
            .sorted()
            .forEach { assetPath ->
                val relative = assetPath.removePrefix("$root/")
                val metadata = adamsHavenDungeonMediaMetadata(relative)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(assetPath).use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                mediaRepository.registerBundledImage(
                    assetPath = assetPath,
                    id = metadata.id,
                    relativePath = "images/adams_haven/maps/dungeon/$relative",
                    width = options.outWidth.coerceAtLeast(1),
                    height = options.outHeight.coerceAtLeast(1),
                    displayName = metadata.displayName,
                    category = metadata.category,
                    tags = metadata.tags.joinToString(","),
                )
            }
    }

    private fun observeNarration(campaignId: String, displayMode: String) {
        narrationJob?.cancel()
        narrationJob = viewModelScope.launch {
            db.roleplayDao().observeMessages(campaignId, displayMode).collect { messages ->
                val narration = messages.takeLast(8).mapNotNull { message ->
                    val text = runCatching { documentFromJson(message.contentJson).plainText() }.getOrDefault("").trim()
                    text.takeIf(String::isNotBlank)?.let {
                        if (message.role == "user") "You: $it" else "Narrator: $it"
                    }
                }
                val current = _uiState.value
                _uiState.value = current.copy(generatedNarration = narration)
                val latest = messages.lastOrNull { it.role != "user" }
                val latestText = latest?.let {
                    runCatching { documentFromJson(it.contentJson).plainText() }.getOrDefault("").trim()
                }
                val proposal = latestText?.takeIf { it.isNotBlank() }?.let {
                    parseTextGameStoryProposal(it, latest?.id.orEmpty())
                }
                if (proposal != null && current.game.run.pendingStoryProposal?.id != proposal.id) {
                    dispatch(TextGameAction.QueueStoryProposal(proposal))
                }
            }
        }
    }
}

internal fun resolveSceneMediaPath(relativePath: String?, filesDir: File): String? {
    if (relativePath.isNullOrBlank()) return null
    val file = File(filesDir, relativePath)
    return file.takeIf { it.isFile && it.length() > 0L }?.absolutePath
}

internal fun parseTextGameDifficulty(authorsNote: String?): TextGameDifficulty {
    val value = authorsNote
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("Text Game difficulty:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
    return TextGameDifficulty.fromId(value)
}

internal fun freshCampaignSeed(campaignId: String, gameId: String): Long {
    val mixed = "$campaignId:$gameId".hashCode().toLong() and 0xffff_ffffL
    return 13_371L + mixed
}

internal fun adamsHavenCardMediaCategory(category: String): String =
    "Adams Haven / Cards / " + category.replaceFirstChar(Char::uppercase)

internal fun adamsHavenCardMediaTags(card: TextGameCollectibleCard): String = buildList {
    add("adams-haven")
    add("text-game")
    add("card")
    add("card:${card.category.removeSuffix("s")}")
    add(card.category)
    addAll(card.title.lowercase().split(Regex("[^a-z0-9]+" )).filter(String::isNotBlank))
}.distinct().joinToString(",")

internal data class MissionBoardDeal(
    val intro: String,
    val missions: List<TextGameMission>,
)

internal fun missionOfferCount(seed: Long): Int =
    ((((seed xor (seed ushr 32)) and Long.MAX_VALUE) % 6L) + 1L).toInt()

internal fun missionInstanceId(base: String, seed: Long, index: Int): String =
    "${base}_${(seed and Long.MAX_VALUE).toString(36)}_$index"

internal fun fallbackMissionBoardIntro(seed: Long): String {
    val intros = listOf(
        "I reach the dungeon board while rain whispers against its copper hood. New contracts wait beneath colored seals, and I study which danger deserves my party.",
        "I enter the torchlit contract shelter as a courier pins fresh maps over yesterday's warnings. The dungeon breathes beyond the gate while I weigh every offered route.",
        "I stop before a board crowded with monster sketches, rescue notices, and half-finished maps. I feel the case at my side warm as I choose where the next expedition begins.",
        "I arrive when the mission keeper opens the board for the day. Wax seals crack in the cold air, each one promising a different descent below Haven.",
        "I follow the dungeon road to a lantern-bright wall of contracts. Some notices promise coin, some answers, and every one asks me to risk something real.",
        "I brush road dust from my sleeves beneath the mission awning. Fresh ink marks several ways into the dark, and I decide which story I am willing to enter.",
        "I hear stone shift beneath the gate as I read the newest dungeon requests. The board offers no safe route, only different reasons to go below.",
        "I join the quiet line at the mission board and watch an old contract vanish beneath six new ones. I step forward when the keeper calls for a Summoner.",
    )
    val mixed = (seed xor (seed ushr 29)) and Long.MAX_VALUE
    return intros[(mixed % intros.size).toInt()]
}

internal data class AdamsHavenDungeonMediaMetadata(
    val id: String,
    val displayName: String,
    val category: String,
    val tags: List<String>,
)

internal fun adamsHavenDungeonMediaMetadata(relativePath: String): AdamsHavenDungeonMediaMetadata {
    val normalized = relativePath.replace('\\', '/').trimStart('/')
    val lower = normalized.lowercase()
    val subtype = when {
        "standard_combat" in lower || "standard-combat" in lower -> "standard-combat"
        "elite_combat" in lower || "elite-combat" in lower -> "elite-combat"
        "boss" in lower -> "boss"
        "safe_camp" in lower || "safe-camp" in lower || "/camp-" in lower -> "safe-camp"
        "playable map" in lower || "playable-map" in lower || "blank map" in lower || "grid-" in lower -> "playable-map"
        "/loot" in lower || "loot-" in lower -> "loot"
        "/exit" in lower || "exit-" in lower -> "exit"
        "/merchant" in lower || "merchant-" in lower -> "merchant"
        "/puzzle" in lower || "puzzle-" in lower -> "puzzle"
        "/secret" in lower || "secret-" in lower -> "secret"
        "/shrine" in lower || "shrine-" in lower -> "shrine"
        "/upgrade" in lower || "upgrade-" in lower -> "upgrade"
        else -> "room"
    }
    val battleEligible = subtype in setOf("standard-combat", "elite-combat", "boss")
    val stem = normalized.substringAfterLast('/').substringBeforeLast('.')
    val displayName = stem.split(Regex("[-_]+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
    val slug = normalized.substringBeforeLast('.').lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    val tags = buildList {
        addAll(listOf("adams-haven", "text-game", "scene", "map", "dungeon", "scene:dungeon"))
        add(subtype)
        add("dungeon:$subtype")
        if ("forest_dungeon_set" in lower) add("forest-dungeon")
        if ("cave" in lower) add("cave-dungeon")
        if (battleEligible) add("scene:battle")
    }.distinct()
    return AdamsHavenDungeonMediaMetadata(
        id = "adams-haven-dungeon-$slug",
        displayName = displayName,
        category = "Adams Haven / Scene / Dungeons",
        tags = tags,
    )
}

private fun Context.assetFilesRecursively(path: String): List<String> {
    val children = assets.list(path).orEmpty()
    if (children.isEmpty()) return listOf(path)
    return children.flatMap { child -> assetFilesRecursively("$path/$child") }
}

private fun Context.assetExists(path: String): Boolean = runCatching {
    assets.open(path).use { true }
}.getOrDefault(false)
