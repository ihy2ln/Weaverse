package com.ihy2ln.weaverse.data.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionModelsTest {
    @Test
    fun roundTripsActionMap() {
        val encoded = ActionModels.encode(
            mapOf(
                ActionModelKeys.SCENE_BEAT to "openrouter/deepseek/deepseek-v4-flash",
                ActionModelKeys.SHORTEN to "openrouter/openai/gpt-5.6-luna",
            ),
        )
        val decoded = ActionModels.decode(encoded)
        assertEquals("openrouter/openai/gpt-5.6-luna", decoded[ActionModelKeys.SHORTEN])
        assertEquals("openrouter/deepseek/deepseek-v4-flash", decoded[ActionModelKeys.SCENE_BEAT])
    }

    @Test
    fun blankAndJunkDecodeToEmpty() {
        assertTrue(ActionModels.decode("").isEmpty())
        assertTrue(ActionModels.decode("not-json").isEmpty())
    }
}
