package com.ihy2ln.weaverse.core.roleplay

import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.core.text.encodeAliases
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Tag applied to auto-generated people so they can be told apart in the friends list. */
const val DAILY_CHARACTER_TAG = "New today"

/**
 * Generates one new roleplay character per day so the friends list keeps growing.
 *
 * Offline-first contract: this is the one feature that needs the network, so when
 * there is no API key or the call fails, it does nothing and — crucially — does not
 * stamp today's date, letting the next app launch retry.
 */
@Singleton
class DailyCharacterGenerator @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Safe to call on every app start; cheap and silent when there is nothing to do. */
    suspend fun generateIfDue(today: LocalDate = LocalDate.now()): RpCharacterEntity? {
        val prefs = settings.preferences.first()
        if (!prefs.dailyCharactersEnabled) return null
        val todayEpochDay = today.toEpochDay()
        if (prefs.lastDailyCharacterEpochDay >= todayEpochDay) return null
        if (!aiGeneration.hasApiKey()) return null

        val existingNames = runCatching { db.roleplayDao().getCharacters().map { it.name } }
            .getOrDefault(emptyList())
        val generated = runCatching { requestCharacter(existingNames) }.getOrNull() ?: return null

        db.roleplayDao().upsertCharacter(generated)
        settings.setLastDailyCharacterEpochDay(todayEpochDay)
        return generated
    }

    /**
     * User-initiated "meet someone new" — ignores the once-a-day gate and does not
     * consume it, so the automatic one still arrives. Returns null with no API key;
     * throws if the model call itself fails, so the caller can report it.
     */
    suspend fun generateNow(): RpCharacterEntity? {
        if (!aiGeneration.hasApiKey()) return null
        val existingNames = runCatching { db.roleplayDao().getCharacters().map { it.name } }
            .getOrDefault(emptyList())
        val generated = requestCharacter(existingNames)
        db.roleplayDao().upsertCharacter(generated)
        return generated
    }

    private suspend fun requestCharacter(existingNames: List<String>): RpCharacterEntity {
        val avoid = existingNames.takeLast(40).joinToString(", ").ifBlank { "(none yet)" }
        val result = aiGeneration.complete(
            userMessage = buildPrompt(avoid),
            maxTokens = 600,
            temperature = 1.0,
        )
        val parsed = parse(result.text) ?: error("Unparseable character JSON")
        val now = System.currentTimeMillis()
        return RpCharacterEntity(
            id = "rpc-${UUID.randomUUID()}",
            name = parsed.name,
            description = parsed.description,
            personality = parsed.personality,
            scenario = parsed.scenario,
            firstMes = parsed.firstMes,
            systemPrompt = DefaultAiGuides.characterSystemPrompt(
                name = parsed.name,
                description = parsed.description,
                personality = parsed.personality,
                scenario = parsed.scenario,
            ),
            tagsJson = encodeAliases(listOf(DAILY_CHARACTER_TAG) + parsed.tags),
            colorHex = pickColorHex(parsed.name),
            createdAt = now,
        )
    }

    private fun buildPrompt(avoid: String): String = """
        Invent ONE original fictional person who could show up in a messaging app.
        Make them specific and memorable, not a generic archetype. Vary era, culture,
        profession and tone from these existing contacts: $avoid

        Reply with ONLY a JSON object, no prose and no code fences:
        {
          "name": "given name, optionally with a surname",
          "description": "2-3 sentences on who they are and how they got here",
          "personality": "a comma-separated list of concrete traits",
          "scenario": "one sentence on the situation they are messaging you from",
          "firstMes": "their opening message to you, in their own voice, 1-2 sentences",
          "tags": ["one or two short lowercase category words"]
        }
    """.trimIndent()

    private fun parse(raw: String): ParsedCharacter? {
        val obj = extractJsonObject(raw) ?: return null
        fun str(key: String): String =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull().orEmpty().trim()
        val name = str("name").takeIf { it.isNotBlank() } ?: return null
        val tags = runCatching {
            obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.trim().takeIf(String::isNotBlank) }
        }.getOrNull().orEmpty()
        return ParsedCharacter(
            name = name,
            description = str("description"),
            personality = str("personality"),
            scenario = str("scenario"),
            firstMes = str("firstMes"),
            tags = tags,
        )
    }

    /** Models often wrap JSON in prose or code fences; take the outermost braces. */
    private fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrNull()
    }

    /** Stable per-name tint so a character keeps the same monogram color. */
    private fun pickColorHex(name: String): String =
        AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]

    private data class ParsedCharacter(
        val name: String,
        val description: String,
        val personality: String,
        val scenario: String,
        val firstMes: String,
        val tags: List<String>,
    )
}

/** Shared monogram/accent colors for characters without an avatar picture. */
val AvatarPalette = listOf(
    "#5865F2", "#57F287", "#FEE75C", "#EB459E", "#ED4245",
    "#3BA55D", "#FAA81A", "#9B59B6", "#1ABC9C", "#E67E22",
)

/** Deterministic avatar tint for any character, generated or hand-made. */
fun avatarColorHexFor(name: String, explicit: String?): String =
    explicit?.takeIf { it.isNotBlank() }
        ?: AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]
