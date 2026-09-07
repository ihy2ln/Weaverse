package com.ihy2ln.weaverse.feature.roleplay.rpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

sealed class RpgProposalParseResult {
    data class Success(val proposals: List<RpgActionProposal>) : RpgProposalParseResult()
    data class Failure(val retryMessage: String = RPG_RETRY_MESSAGE) : RpgProposalParseResult()
}

/**
 * Accepts only validated structured proposals. Incomplete AI output never
 * becomes playable choices and never mutates campaign state.
 */
object RpgProposalParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): RpgProposalParseResult {
        val payload = extractJson(raw) ?: return RpgProposalParseResult.Failure()
        val proposals = decodeProposals(payload) ?: return RpgProposalParseResult.Failure()
        if (proposals.size != 3) return RpgProposalParseResult.Failure()
        if (proposals.any { !it.isValidAiChoice() }) return RpgProposalParseResult.Failure()
        if (proposals.map { it.id }.toSet().size != 3) return RpgProposalParseResult.Failure()
        return RpgProposalParseResult.Success(proposals)
    }

    private fun RpgActionProposal.isValidAiChoice(): Boolean =
        !isCustom &&
            id.isNotBlank() &&
            id != RPG_CUSTOM_ACTION_ID &&
            title.isNotBlank() &&
            summary.isNotBlank() &&
            intent.isNotBlank() &&
            skill.isNotBlank() &&
            ability.isNotBlank() &&
            checkDc in 5..25 &&
            involvedCharacters.isNotEmpty() &&
            rewardCategories.isNotEmpty()

    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{').takeIf { it >= 0 }
            ?: candidate.indexOf('[').takeIf { it >= 0 }
            ?: return null
        return candidate.substring(start)
    }

    private fun decodeProposals(payload: String): List<RpgActionProposal>? {
        val element = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return null
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["proposals"]?.jsonArray
                ?: element["choices"]?.jsonArray
                ?: return null
            else -> return null
        }
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            runCatching {
                val dto = json.decodeFromJsonElement(RpgAiProposalDto.serializer(), obj)
                dto.toProposal()
            }.getOrNull() ?: obj.toProposalOrNull()
        }
    }

    private fun JsonObject.toProposalOrNull(): RpgActionProposal? {
        fun str(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
        fun int(key: String, default: Int): Int =
            this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default
        fun list(key: String): List<String> =
            this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.trim().takeIf(String::isNotBlank) }
                ?: str(key).split(',').map { it.trim() }.filter { it.isNotBlank() }
        val id = str("id")
        val title = str("title", "label")
        val intent = str("intent")
        if (id.isBlank() || title.isBlank() || intent.isBlank()) return null
        return RpgActionProposal(
            id = id,
            title = title,
            summary = str("summary", "description").ifBlank { intent },
            intent = intent,
            skill = str("skill", "check"),
            ability = str("ability"),
            checkDc = int("checkDc", int("dc", 12)),
            involvedCharacters = list("involvedCharacters"),
            riskTier = runCatching { RpgRiskTier.valueOf(str("riskTier", "risk")) }
                .getOrDefault(RpgRiskTier.Medium),
            rewardCategories = list("rewardCategories"),
        )
    }
}

@Serializable
private data class RpgAiProposalDto(
    val id: String,
    val title: String,
    val summary: String = "",
    val intent: String,
    val skill: String,
    val ability: String,
    val checkDc: Int = 12,
    val involvedCharacters: List<String> = emptyList(),
    val riskTier: RpgRiskTier = RpgRiskTier.Medium,
    val rewardCategories: List<String> = emptyList(),
) {
    fun toProposal() = RpgActionProposal(
        id = id.trim(),
        title = title.trim(),
        summary = summary.trim().ifBlank { intent.trim() },
        intent = intent.trim(),
        skill = skill.trim(),
        ability = ability.trim(),
        checkDc = checkDc,
        involvedCharacters = involvedCharacters.map { it.trim() }.filter { it.isNotBlank() },
        riskTier = riskTier,
        rewardCategories = rewardCategories.map { it.trim() }.filter { it.isNotBlank() },
    )
}

fun customActionProposal(text: String = ""): RpgActionProposal {
    val trimmed = text.trim()
    val inferred = inferCustomCheck(trimmed)
    return RpgActionProposal(
        id = RPG_CUSTOM_ACTION_ID,
        title = "Write your own action",
        summary = if (trimmed.isBlank()) {
            "Describe what you do. The app will preview the check, then you confirm."
        } else {
            trimmed
        },
        intent = if (trimmed.isBlank()) "Player-authored action" else trimmed,
        skill = inferred.skill,
        ability = inferred.ability,
        checkDc = inferred.dc,
        involvedCharacters = listOf("You"),
        riskTier = inferred.risk,
        rewardCategories = listOf("custom"),
        isCustom = true,
        customText = trimmed,
    )
}

internal data class InferredCheck(
    val skill: String,
    val ability: String,
    val dc: Int,
    val risk: RpgRiskTier,
)

internal fun inferCustomCheck(text: String): InferredCheck {
    val value = text.lowercase()
    return when {
        listOf("attack", "strike", "slash", "fight").any { it in value } ->
            InferredCheck("Melee", "Strength", 13, RpgRiskTier.High)
        listOf("sneak", "hide", "steal", "lock").any { it in value } ->
            InferredCheck("Stealth", "Dexterity", 13, RpgRiskTier.Medium)
        listOf("search", "inspect", "investigate").any { it in value } ->
            InferredCheck("Investigation", "Intelligence", 12, RpgRiskTier.Low)
        listOf("notice", "listen", "track", "scout").any { it in value } ->
            InferredCheck("Perception", "Wisdom", 12, RpgRiskTier.Low)
        listOf("persuade", "convince", "comfort", "talk").any { it in value } ->
            InferredCheck("Persuasion", "Charisma", 12, RpgRiskTier.Medium)
        else -> InferredCheck("Action check", "Wisdom", 12, RpgRiskTier.Medium)
    }
}
