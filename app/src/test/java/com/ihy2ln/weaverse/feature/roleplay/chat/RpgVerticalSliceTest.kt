package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgVerticalSliceTest {
    @Test
    fun parsesThreeChoicesAndSceneArtWithoutLeakingMarker() {
        val text = """
            The gate opens.
            [[RPG_CHOICE|id=1|title=Enter the ruins|description=Follow the blue lantern.]]
            [[RPG_CHOICE|id=2|title=Question the guard|description=Learn who sent him.]]
            [[RPG_CHOICE|id=3|title=Circle the wall|description=Search for another way.]]
            [[SCENE_ART:misty_ruins|category=wilderness|mood=tense]]
        """.trimIndent()
        assertEquals(3, parseRpgActionChoices(text).size)
        assertEquals("Enter the ruins", parseRpgActionChoices(text).first().title)
        assertEquals("misty_ruins", parseRpgSceneArtChoice(text)?.assetId)
        assertTrue("SCENE_ART" !in stripRpgMetadata(text))
        assertTrue("RPG_CHOICE" !in stripRpgMetadata(text))
    }

    @Test
    fun numberedPlanningTextNeverBecomesGameplayChoices() {
        val outline = """
            1. Establish the party at the ruined shrine.
            2. Reveal the antagonist's first move.
            3. Let the party choose which lead to follow.
        """.trimIndent()

        assertTrue(parseRpgActionChoices(outline).isEmpty())
    }

    @Test
    fun startupPromptsNameTheThreeRequiredAiFields() {
        val prompt = adventureAiStartupFieldsPrompt()
        assertTrue("Character backstory" in prompt)
        assertTrue("Current situation" in prompt)
        assertTrue("Future goals" in prompt)
    }
}
