package com.ihy2ln.weaverse.feature.brainstorm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrainstormWorkshopTest {
    @Test
    fun parsesThreeComparableDirections() {
        val answer = (1..3).joinToString("\n") { n ->
            """
            [[OPTION]]
            Title: Direction $n
            Premise: A different world $n.
            Strengths: Distinct stakes.
            Risks: Too broad.
            Next step: Choose a protagonist.
            [[END_OPTION]]
            """.trimIndent()
        }
        val cards = parseBrainstormAlternatives(answer)
        assertEquals(3, cards.size)
        assertEquals("Direction 2", cards[1].title)
        assertEquals("Choose a protagonist.", cards[1].nextStep)
    }

    @Test
    fun malformedAlternativeOutputFallsBackToChat() {
        assertTrue(parseBrainstormAlternatives("Here are a few ideas in freeform prose.").isEmpty())
        assertTrue(parseBrainstormAlternatives("[[OPTION]]\nTitle: One\nPremise: A path\n[[END_OPTION]]").isEmpty())
    }

    @Test
    fun boundedHistoryKeepsNewestTurnsOnceAndInOrder() {
        val history = listOf("user" to "older turn", "assistant" to "older answer",
            "user" to "new question", "assistant" to "new answer")
        assertEquals(emptyList<Pair<String, String>>(), boundedBrainstormHistory(history, budgetTokens = 8))
        val result = boundedBrainstormHistory(history, budgetTokens = 14)
        assertEquals(history.takeLast(2), result)
    }
}
