package com.ihy2ln.weaverse.feature.roleplay.rpg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgProposalParserTest {
    private val validJson = """
        {
          "proposals": [
            {
              "id": "scout_treeline",
              "title": "Scout the treeline",
              "summary": "Read the vines.",
              "intent": "Find a quiet path.",
              "skill": "Perception",
              "ability": "Wisdom",
              "checkDc": 11,
              "involvedCharacters": ["You", "Mira"],
              "riskTier": "Low",
              "rewardCategories": ["exploration"]
            },
            {
              "id": "approach_scout",
              "title": "Approach the wounded scout",
              "summary": "Offer water.",
              "intent": "Stabilize the scout.",
              "skill": "Persuasion",
              "ability": "Charisma",
              "checkDc": 12,
              "involvedCharacters": ["You", "Mira"],
              "riskTier": "Medium",
              "rewardCategories": ["relationship"]
            },
            {
              "id": "search_cart",
              "title": "Search the ruined cart",
              "summary": "Pull salvage.",
              "intent": "Recover moonroot.",
              "skill": "Investigation",
              "ability": "Intelligence",
              "checkDc": 12,
              "involvedCharacters": ["You"],
              "riskTier": "Medium",
              "rewardCategories": ["crafting"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun acceptsExactlyThreeStructuredProposals() {
        val parsed = RpgProposalParser.parse(validJson)
        assertTrue(parsed is RpgProposalParseResult.Success)
        assertEquals(3, (parsed as RpgProposalParseResult.Success).proposals.size)
    }

    @Test
    fun malformedJsonFallsBackWithoutChoices() {
        val parsed = RpgProposalParser.parse("sorry I cannot help with that")
        assertTrue(parsed is RpgProposalParseResult.Failure)
        assertEquals(RPG_RETRY_MESSAGE, (parsed as RpgProposalParseResult.Failure).retryMessage)
    }

    @Test
    fun twoChoicesAreRejected() {
        val parsed = RpgProposalParser.parse(
            """{"proposals":[{"id":"a","title":"A","summary":"s","intent":"i","skill":"x","ability":"Wisdom","checkDc":12,"involvedCharacters":["You"],"riskTier":"Low","rewardCategories":["exploration"]},{"id":"b","title":"B","summary":"s","intent":"i","skill":"x","ability":"Wisdom","checkDc":12,"involvedCharacters":["You"],"riskTier":"Low","rewardCategories":["exploration"]}]}""",
        )
        assertTrue(parsed is RpgProposalParseResult.Failure)
    }

    @Test
    fun missingIntentIsRejected() {
        val parsed = RpgProposalParser.parse(
            validJson.replace("\"intent\": \"Find a quiet path.\"", "\"intent\": \"\""),
        )
        assertTrue(parsed is RpgProposalParseResult.Failure)
    }

    @Test
    fun fencedJsonIsAccepted() {
        val parsed = RpgProposalParser.parse("Here you go:\n```json\n$validJson\n```")
        assertTrue(parsed is RpgProposalParseResult.Success)
    }
}
