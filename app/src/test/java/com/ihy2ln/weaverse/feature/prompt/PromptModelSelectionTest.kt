package com.ihy2ln.weaverse.feature.prompt

import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.settings.ActionModelKeys
import com.ihy2ln.weaverse.feature.shell.AppMode
import com.ihy2ln.weaverse.feature.shell.NovelDestination
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptModelSelectionTest {
    private val flash = ModelInfo(
        id = "deepseek/deepseek-v4-flash",
        displayName = "DeepSeek V4 Flash",
        available = true,
        tags = listOf("Vision"),
    )
    private val luna = ModelInfo(
        id = "openai/gpt-5.6-luna",
        displayName = "OpenAI GPT-5.6 Luna",
        available = true,
    )
    private val missing = ModelInfo(
        id = "vendor/offline",
        displayName = "Offline",
        available = false,
    )

    @Test
    fun modelRefPrefixesOpenRouter() {
        assertEquals("openrouter/deepseek/deepseek-v4-flash", PromptModelSelection.modelRef("deepseek/deepseek-v4-flash"))
        assertEquals(
            "openrouter/deepseek/deepseek-v4-flash",
            PromptModelSelection.modelRef("openrouter/deepseek/deepseek-v4-flash"),
        )
        assertEquals("", PromptModelSelection.modelRef("  "))
    }

    @Test
    fun blankSelectionFollowsSettingsDefault() {
        assertTrue(PromptModelSelection.followsDefault(""))
        assertFalse(PromptModelSelection.followsDefault("openrouter/openai/gpt-5.6-luna"))
        assertEquals(
            "openrouter/deepseek/deepseek-v4-flash",
            PromptModelSelection.effectiveModelRef("", "openrouter/deepseek/deepseek-v4-flash"),
        )
        assertEquals(
            "openrouter/openai/gpt-5.6-luna",
            PromptModelSelection.effectiveModelRef(
                "openrouter/openai/gpt-5.6-luna",
                "openrouter/deepseek/deepseek-v4-flash",
            ),
        )
    }

    @Test
    fun shortLabelPrefersDisplayNameThenIdTail() {
        val models = listOf(flash, luna)
        assertEquals(
            "DeepSeek V4 Flash",
            PromptModelSelection.shortLabel("openrouter/deepseek/deepseek-v4-flash", models),
        )
        assertEquals("kimi-k3", PromptModelSelection.shortLabel("openrouter/moonshotai/kimi-k3"))
        assertEquals("Default", PromptModelSelection.shortLabel(""))
    }

    @Test
    fun filterMatchesIdNameAndTags() {
        val models = listOf(flash, luna, missing)
        assertEquals(3, PromptModelSelection.filter(models, "").size)
        assertEquals(listOf(luna), PromptModelSelection.filter(models, "luna"))
        assertEquals(listOf(flash), PromptModelSelection.filter(models, "vision"))
        assertEquals(listOf(flash), PromptModelSelection.filter(models, "deepseek-v4"))
        assertTrue(PromptModelSelection.filter(models, "no-such-model").isEmpty())
    }

    @Test
    fun isSelectedUsesOverrideThenDefault() {
        assertTrue(
            PromptModelSelection.isSelected(
                flash,
                selectedRef = "",
                defaultRef = "openrouter/deepseek/deepseek-v4-flash",
            ),
        )
        assertTrue(
            PromptModelSelection.isSelected(
                luna,
                selectedRef = "openrouter/openai/gpt-5.6-luna",
                defaultRef = "openrouter/deepseek/deepseek-v4-flash",
            ),
        )
        assertFalse(
            PromptModelSelection.isSelected(
                flash,
                selectedRef = "openrouter/openai/gpt-5.6-luna",
                defaultRef = "openrouter/deepseek/deepseek-v4-flash",
            ),
        )
    }

    @Test
    fun dockActionKeySplitsSceneBeatWorkshopAndRoleplay() {
        assertEquals(
            ActionModelKeys.SCENE_BEAT,
            PromptModelSelection.dockActionKey(AppMode.Novel, NovelDestination.Write.name, PromptEntryKind.Ai),
        )
        assertEquals(
            ActionModelKeys.WORKSHOP,
            PromptModelSelection.dockActionKey(AppMode.Novel, NovelDestination.Chat.name, PromptEntryKind.Ai),
        )
        assertEquals(
            ActionModelKeys.ROLEPLAY_SWIPE,
            PromptModelSelection.dockActionKey(AppMode.Roleplay, null, PromptEntryKind.Ai),
        )
        assertEquals(
            ActionModelKeys.PROMPT_AI,
            PromptModelSelection.dockActionKey(AppMode.Notes, null, PromptEntryKind.Manual),
        )
    }
}
