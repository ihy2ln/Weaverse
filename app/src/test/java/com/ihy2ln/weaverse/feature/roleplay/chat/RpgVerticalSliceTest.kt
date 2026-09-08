package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgVerticalSliceTest {
    @Test
    fun parsesThreeChoicesAndSceneArtWithoutLeakingMarker() {
        val text = """
            The gate opens.
            1. Enter the ruins — follow the blue lantern.
            2. Question the guard — learn who sent him.
            3. Circle the wall — search for another way.
            [[SCENE_ART:misty_ruins|category=wilderness|mood=tense]]
        """.trimIndent()
        assertEquals(3, parseRpgActionChoices(text).size)
        assertEquals("Enter the ruins", parseRpgActionChoices(text).first().title)
        assertEquals("misty_ruins", parseRpgSceneArtChoice(text)?.assetId)
        assertTrue("SCENE_ART" !in stripRpgMetadata(text))
    }

    @Test
    fun startupPromptsNameTheThreeRequiredAiFields() {
        val prompt = adventureAiStartupFieldsPrompt()
        assertTrue("Character backstory" in prompt)
        assertTrue("Current situation" in prompt)
        assertTrue("Future goals" in prompt)
    }
}
