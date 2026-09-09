package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val plannerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun RpgAdventurePlan.answer(id: String, fallback: String): String = answers
    .firstOrNull { it.questionId == id && !it.skipped }
    ?.value?.trim()?.takeIf { it.isNotBlank() } ?: fallback

private val cyoaQuestionIds = listOf("plot", "goal", "scene", "party", "tone", "complication")

fun cyoaSuggestionPrompt(setup: RpgCampaignSetupSnapshot): String = """
You are an RPG campaign planner. Return ONLY one JSON object with exactly these keys:
{"plot":["","",""],"goal":["","",""],"scene":["","",""],"party":["","",""],"tone":["","",""],"complication":["","",""]}
Give three concise, distinct, tappable answers per question. Tailor every suggestion to this campaign rather than repeating generic fantasy defaults.
Campaign title: ${setup.title}
Setting: ${setup.setting}
Mode: ${setup.modeId}
Rule system: ${setup.ruleSystem}
House rules: ${setup.houseRules}
Characters: ${setup.characters}
POV and tense: ${setup.pointOfView}; ${setup.tense}
Player role: ${setup.playerRole}
""".trimIndent()

fun parseCyoaSuggestions(text: String): Map<String, List<String>>? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        val objectValue = plannerJson.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        cyoaQuestionIds.associateWith { id ->
            objectValue[id]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                .filter { it.isNotBlank() }.distinct().take(3)
        }.takeIf { result -> result.values.all { it.size == 3 } }
    }.getOrNull()
}

fun fallbackCyoaSuggestions(setup: RpgCampaignSetupSnapshot): Map<String, List<String>> {
    val setting = setup.setting.ifBlank { "the campaign setting" }
    val party = setup.characters.ifBlank { "the protagonist" }
    return mapOf(
        "plot" to listOf("A secret buried within $setting resurfaces", "A rival faction threatens the fragile order", "$party become tied to an unexplained disaster"),
        "goal" to listOf("Protect an ally linked to the central threat", "Find the first reliable lead before the enemy", "Reach safety with proof of what happened"),
        "scene" to listOf("A familiar place in $setting turns dangerous", "A tense arrival at the campaign's frontier", "A quiet meeting interrupted by the first omen"),
        "party" to listOf("$party begin together", "$party meet a wary local guide", "$party are joined by an unlikely survivor"),
        "tone" to listOf("Character-driven ${setup.modeId} adventure", "Tense danger with moments of hope", "Mystery, discovery, and consequential choices"),
        "complication" to listOf("The apparent ally is hiding urgent information", "The party has only hours before the situation changes", "A second faction mistakes the party for the enemy"),
    )
}

private fun normalizedKey(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

private fun JsonObject.value(vararg aliases: String): JsonElement? {
    val wanted = aliases.map(::normalizedKey).toSet()
    return entries.firstOrNull { normalizedKey(it.key) in wanted }?.value
}

private fun JsonObject.objectValue(vararg aliases: String): JsonObject? = value(*aliases) as? JsonObject

private fun JsonObject.textValue(vararg aliases: String): String = value(*aliases).asPlannerText()

private fun JsonElement?.asPlannerText(): String = when (this) {
    null -> ""
    is JsonPrimitive -> contentOrNull.orEmpty().trim()
    is JsonArray -> map { it.asPlannerText() }.filter { it.isNotBlank() }.joinToString(", ")
    is JsonObject -> entries.mapNotNull { (key, value) ->
        value.asPlannerText().takeIf { it.isNotBlank() }?.let { "$key: $it" }
    }.joinToString("; ")
    else -> ""
}

private fun JsonElement?.toBeats(): List<RpgChapterBeat> {
    val elements = when (this) {
        is JsonArray -> toList()
        is JsonObject -> value("items", "beats", "storyBeats")?.let { nested ->
            if (nested === this) emptyList() else nested.toBeats().map { JsonPrimitive("${it.title}: ${it.summary}") }
        }.orEmpty()
        is JsonPrimitive -> contentOrNull.orEmpty().lines().filter { it.isNotBlank() }.map(::JsonPrimitive)
        else -> emptyList()
    }
    return elements.take(5).mapIndexedNotNull { index, element ->
        when (element) {
            is JsonObject -> {
                val title = element.textValue("title", "name", "beat").ifBlank { "Beat ${index + 1}" }
                val summary = element.textValue("summary", "description", "details", "event", "content")
                RpgChapterBeat(
                    id = element.textValue("id").ifBlank { "beat-${index + 1}" },
                    title = title,
                    summary = summary,
                    completed = false,
                )
            }
            else -> element.asPlannerText().trim().takeIf { it.isNotBlank() }?.let { line ->
                val clean = line.replace(Regex("^\\s*(?:[-*]|\\d+[.)])\\s*"), "")
                RpgChapterBeat(
                    id = "beat-${index + 1}",
                    title = clean.substringBefore(':').trim().ifBlank { "Beat ${index + 1}" },
                    summary = clean.substringAfter(':', clean).trim(),
                )
            }
        }
    }
}

private fun RpgChapterOutline.hasContent(): Boolean = listOf(
    workingTitle, premise, primaryObjective, antagonist, importantLocations, optionalBeat, majorChallenge, climax, possibleOutcomes,
).any { it.isNotBlank() } || beats.isNotEmpty()

private fun RpgOpeningSceneGuideline.hasContent(): Boolean = listOf(
    title, locationAndAtmosphere, startingCast, immediateObjective, conflictAndStakes, complication, firstDecisionHook, sceneArtTags,
).any { it.isNotBlank() }

/** Finds the first complete JSON object, even when a model wraps it in prose or a Markdown fence. */
private fun extractJsonObject(text: String): JsonObject? {
    text.indices.filter { text[it] == '{' }.forEach { start ->
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val parsed = runCatching {
                                plannerJson.parseToJsonElement(text.substring(start, index + 1)) as? JsonObject
                            }.getOrNull()
                            if (parsed != null) return parsed
                            break
                        }
                    }
                }
            }
        }
    }
    return null
}

fun chapterPlanPrompt(setup: RpgCampaignSetupSnapshot, plan: RpgAdventurePlan): String = """
You are preparing Chapter One for an RPG campaign. Return ONLY one JSON object matching this shape:
{"outline":{"workingTitle":"","premise":"","primaryObjective":"","antagonist":"","importantLocations":"","beats":[{"id":"beat-1","title":"","summary":"","completed":false}],"optionalBeat":"","majorChallenge":"","climax":"","possibleOutcomes":""},"openingScene":{"title":"","locationAndAtmosphere":"","startingCast":"","immediateObjective":"","conflictAndStakes":"","complication":"","firstDecisionHook":"","sceneArtTags":""}}
Create 3 to 5 flexible beats. Do not write the actual scene yet.

CAMPAIGN SETUP
Title: ${setup.title}
Setting: ${setup.setting}
Mode: ${setup.modeId}
Rules: ${setup.ruleSystem}
House rules: ${setup.houseRules}
Characters: ${setup.characters}
POV: ${setup.pointOfView}
Tense: ${setup.tense}
Player role: ${setup.playerRole}

CREATE YOUR OWN ADVENTURE
Plot: ${plan.answer("plot", "Choose a fitting central conflict")}
First goal: ${plan.answer("goal", "Choose a fitting first objective")}
First scene: ${plan.answer("scene", "Choose a fitting opening location")}
Party: ${plan.answer("party", setup.characters.ifBlank { "Solo protagonist" })}
Tone: ${plan.answer("tone", "Hopeful adventure")}
Complication: ${plan.answer("complication", "Choose a fitting complication")}
""".trimIndent()

fun parseChapterPlan(text: String): RpgChapterPlanPayload? {
    val root = extractJsonObject(text) ?: return null
    val outlineObject = root.objectValue("outline", "chapterOutline", "chapterPlan", "chapterOne") ?: root
    val sceneObject = root.objectValue("openingScene", "openingSceneGuideline", "sceneOne", "opening")
        ?: outlineObject.objectValue("openingScene", "openingSceneGuideline", "sceneOne", "opening")
        ?: JsonObject(emptyMap())
    val beats = outlineObject.value("beats", "storyBeats", "plannedBeats", "chapterBeats").toBeats()
    val outline = RpgChapterOutline(
        workingTitle = outlineObject.textValue("workingTitle", "chapterTitle", "title"),
        premise = outlineObject.textValue("premise", "chapterPremise", "plotPremise", "plot", "centralConflict"),
        primaryObjective = outlineObject.textValue("primaryObjective", "chapterObjective", "objective", "firstGoal"),
        antagonist = outlineObject.textValue("antagonist", "mainAntagonist", "faction", "threat"),
        importantLocations = outlineObject.textValue("importantLocations", "locations", "keyLocations"),
        beats = beats,
        optionalBeat = outlineObject.textValue("optionalBeat", "explorationBeat", "companionBeat", "optionalExplorationOrCompanionBeat"),
        majorChallenge = outlineObject.textValue("majorChallenge", "plannedCombat", "combat", "challenge"),
        climax = outlineObject.textValue("climax", "chapterClimax"),
        possibleOutcomes = outlineObject.textValue("possibleOutcomes", "outcomes", "consequences", "possibleChapterOutcomesAndConsequences"),
    )
    val opening = RpgOpeningSceneGuideline(
        title = sceneObject.textValue("title", "sceneTitle", "openingTitle"),
        locationAndAtmosphere = sceneObject.textValue("locationAndAtmosphere", "locationTimeAndAtmosphere", "location", "setting", "atmosphere"),
        startingCast = sceneObject.textValue("startingCast", "cast", "party"),
        immediateObjective = sceneObject.textValue("immediateObjective", "objective", "firstGoal"),
        conflictAndStakes = sceneObject.textValue("conflictAndStakes", "openingConflictAndStakes", "conflict", "stakes"),
        complication = sceneObject.textValue("complication", "initialComplication", "threat"),
        firstDecisionHook = sceneObject.textValue("firstDecisionHook", "decisionHook", "firstChoice", "hook"),
        sceneArtTags = sceneObject.textValue("sceneArtTags", "artTags", "tags"),
    )
    return RpgChapterPlanPayload(outline, opening).takeIf { outline.hasContent() || opening.hasContent() }
}

fun completeChapterPlan(
    generated: RpgChapterPlanPayload?,
    fallback: RpgChapterPlanPayload,
): RpgChapterPlanPayload {
    val sourceOutline = generated?.outline ?: RpgChapterOutline()
    val sourceScene = generated?.openingScene ?: RpgOpeningSceneGuideline()
    val generatedBeats = sourceOutline.beats.filter { it.title.isNotBlank() || it.summary.isNotBlank() }
    val beats = (generatedBeats + fallback.outline.beats)
        .distinctBy { (it.title + it.summary).lowercase() }
        .take(5)
        .mapIndexed { index, beat -> beat.copy(id = "beat-${index + 1}") }
    return RpgChapterPlanPayload(
        outline = RpgChapterOutline(
            workingTitle = sourceOutline.workingTitle.ifBlank { fallback.outline.workingTitle },
            premise = sourceOutline.premise.ifBlank { fallback.outline.premise },
            primaryObjective = sourceOutline.primaryObjective.ifBlank { fallback.outline.primaryObjective },
            antagonist = sourceOutline.antagonist.ifBlank { fallback.outline.antagonist },
            importantLocations = sourceOutline.importantLocations.ifBlank { fallback.outline.importantLocations },
            beats = beats,
            optionalBeat = sourceOutline.optionalBeat.ifBlank { fallback.outline.optionalBeat },
            majorChallenge = sourceOutline.majorChallenge.ifBlank { fallback.outline.majorChallenge },
            climax = sourceOutline.climax.ifBlank { fallback.outline.climax },
            possibleOutcomes = sourceOutline.possibleOutcomes.ifBlank { fallback.outline.possibleOutcomes },
        ),
        openingScene = RpgOpeningSceneGuideline(
            title = sourceScene.title.ifBlank { fallback.openingScene.title },
            locationAndAtmosphere = sourceScene.locationAndAtmosphere.ifBlank { fallback.openingScene.locationAndAtmosphere },
            startingCast = sourceScene.startingCast.ifBlank { fallback.openingScene.startingCast },
            immediateObjective = sourceScene.immediateObjective.ifBlank { fallback.openingScene.immediateObjective },
            conflictAndStakes = sourceScene.conflictAndStakes.ifBlank { fallback.openingScene.conflictAndStakes },
            complication = sourceScene.complication.ifBlank { fallback.openingScene.complication },
            firstDecisionHook = sourceScene.firstDecisionHook.ifBlank { fallback.openingScene.firstDecisionHook },
            sceneArtTags = sourceScene.sceneArtTags.ifBlank { fallback.openingScene.sceneArtTags },
        ),
    )
}

fun fallbackChapterPlan(setup: RpgCampaignSetupSnapshot, plan: RpgAdventurePlan): RpgChapterPlanPayload {
    val plot = plan.answer("plot", "A hidden threat disturbs ${setup.setting}")
    val goal = plan.answer("goal", "Investigate the first sign of danger")
    val location = plan.answer("scene", "A crossroads at the edge of town")
    val party = plan.answer("party", setup.characters.ifBlank { "Solo protagonist" })
    val complication = plan.answer("complication", "A time limit")
    return RpgChapterPlanPayload(
        outline = RpgChapterOutline(
            workingTitle = "The First Sign",
            premise = plot,
            primaryObjective = goal,
            antagonist = complication,
            importantLocations = location,
            beats = listOf(
                RpgChapterBeat("beat-1", "The Hook", "The party discovers the immediate problem."),
                RpgChapterBeat("beat-2", "The First Lead", "A choice reveals allies, enemies, and a dangerous lead."),
                RpgChapterBeat("beat-3", "The Confrontation", "The party faces the chapter's central threat."),
            ),
            optionalBeat = "A companion or exploration scene reveals a personal stake.",
            majorChallenge = "A challenge appropriate to ${setup.modeId}.",
            climax = "The party confronts the source of the opening threat.",
            possibleOutcomes = "Success opens a new lead; failure changes the cost without ending the campaign.",
        ),
        openingScene = RpgOpeningSceneGuideline(
            title = "Scene One",
            locationAndAtmosphere = location,
            startingCast = party,
            immediateObjective = goal,
            conflictAndStakes = plot,
            complication = complication,
            firstDecisionHook = "Choose how the party responds to the first sign of danger.",
            sceneArtTags = "${setup.setting}, $location",
        ),
    )
}

fun openingScenePrompt(
    setup: RpgCampaignSetupSnapshot,
    plan: RpgAdventurePlan,
    outline: RpgChapterOutline,
    scene: RpgOpeningSceneGuideline,
): String {
    val modeGuidance = when (RpgCombatRuleset.fromId(setup.modeId)) {
        RpgCombatRuleset.CardBattle -> "Focused Tactical Cards is authoritative. Frame danger and possible combat through Adams Haven cards, AP/EP, readable enemy intent, and statuses. Do not use D&D dice or d20 language."
        RpgCombatRuleset.DndD20 -> "D&D d20 is authoritative. Use the saved character sheet and visible deterministic d20 checks when a check is actually required."
        RpgCombatRuleset.TextReactions -> "Text Reactions is authoritative. Let written actions drive play and never silently replace them with D&D or card-combat mechanics."
    }
    return """
Act as the AI Dungeon Master. Write the actual playable opening scene now, using the verified campaign plan as canon.
Return ONLY JSON: {"prose":"full scene prose","choices":["choice one","choice two","choice three"],"sceneArtTags":"comma separated tags"}.
The prose must establish location, cast, immediate danger or opportunity, and the first objective. End at a decision point without choosing for the player. Keep the three choices actionable and distinct. Do not include planning notes or numbered choices in prose.
Active player-facing mode: ${RpgCombatRuleset.fromId(setup.modeId).label}. $modeGuidance

Campaign: ${setup.title}; ${setup.setting}; mode ${setup.modeId}; rules ${setup.ruleSystem}; POV ${setup.pointOfView}; tense ${setup.tense}.
CYOA plot: ${plan.answer("plot", outline.premise)}
Chapter title: ${outline.workingTitle}
Chapter premise: ${outline.premise}
Chapter objective: ${outline.primaryObjective}
Opening title: ${scene.title}
Location: ${scene.locationAndAtmosphere}
Cast: ${scene.startingCast}
Immediate objective: ${scene.immediateObjective}
Conflict and stakes: ${scene.conflictAndStakes}
Complication: ${scene.complication}
Decision hook: ${scene.firstDecisionHook}
Art tags: ${scene.sceneArtTags}
""".trimIndent()
}

fun parseSceneDraft(text: String): RpgSceneDraft? {
    val root = extractJsonObject(text) ?: return null
    val prose = root.textValue("prose", "narration", "sceneProse", "openingScene", "text", "content")
    val choicesElement = root.value("choices", "actions", "options", "decisions")
    val choices = when (choicesElement) {
        is JsonArray -> choicesElement.map { choice ->
            when (choice) {
                is JsonObject -> choice.textValue("title", "action", "choice", "text").ifBlank { choice.asPlannerText() }
                else -> choice.asPlannerText()
            }
        }
        else -> choicesElement.asPlannerText().lines()
    }.map { it.trim() }.filter { it.isNotBlank() }.take(3)
    return RpgSceneDraft(
        prose = prose,
        choices = choices,
        sceneArtTags = root.textValue("sceneArtTags", "artTags", "tags", "sceneArt"),
    ).takeIf { it.prose.isNotBlank() }
}

fun completeSceneDraft(generated: RpgSceneDraft?, fallback: RpgSceneDraft): RpgSceneDraft {
    val source = generated ?: RpgSceneDraft()
    return RpgSceneDraft(
        prose = source.prose.ifBlank { fallback.prose },
        choices = (source.choices + fallback.choices).map { it.trim() }.filter { it.isNotBlank() }.distinct().take(3),
        sceneArtTags = source.sceneArtTags.ifBlank { fallback.sceneArtTags },
    )
}

fun fallbackSceneDraft(plan: RpgAdventurePlan, scene: RpgOpeningSceneGuideline): RpgSceneDraft = RpgSceneDraft(
    prose = "${scene.locationAndAtmosphere}. ${scene.startingCast} arrive as ${scene.complication.lowercase()} changes everything. " +
        "Their immediate objective is ${scene.immediateObjective.lowercase()}. ${scene.conflictAndStakes} " +
        "The moment demands a decision: ${scene.firstDecisionHook}",
    choices = listOf("Investigate the immediate danger", "Protect the party and observe", "Approach a nearby witness"),
    sceneArtTags = scene.sceneArtTags.ifBlank { plan.answer("scene", "fantasy opening scene") },
)
