package com.ihy2ln.weaverse.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CampaignSetupTemplatesTest {
    @Test
    fun rulesetsHaveUniqueIdsAndUsableDirectives() {
        assertEquals(CampaignRulesetTemplates.size, CampaignRulesetTemplates.map { it.id }.distinct().size)
        assertTrue(CampaignRulesetTemplates.all { it.label.isNotBlank() && it.directive.length > 40 })
    }

    @Test
    fun commonTabletopSystemsAreAvailable() {
        val labels = CampaignRulesetTemplates.map { it.label }
        assertTrue(labels.any { it.startsWith("D&D 5") })
        assertTrue(labels.any { it.startsWith("Pathfinder 2") })
        assertTrue(labels.any { it.contains("Custom") })
    }

    @Test
    fun settingsHaveBackendWorldGuidance() {
        assertEquals(CampaignSettingTemplates.size, CampaignSettingTemplates.map { it.id }.distinct().size)
        assertTrue(CampaignSettingTemplates.all { it.label.isNotBlank() && it.directive.length > 80 })
        assertTrue(CampaignSettingTemplates.any { it.id == "high-fantasy" && "dungeon" in it.directive })
        assertTrue(CampaignSettingTemplates.any { it.id == "custom" })
    }

    @Test
    fun perspectivesSupportSingleAndMultipleCharacterPlay() {
        assertEquals(
            CampaignPerspectiveTemplates.size,
            CampaignPerspectiveTemplates.map { it.id }.distinct().size,
        )
        assertTrue(CampaignPerspectiveTemplates.all { it.directive.length > 80 })
        assertTrue(CampaignPerspectiveTemplates.any { it.id == "third-multiple" })
        assertTrue(CampaignPerspectiveTemplates.any { it.id == "first-multiple" })
        assertTrue(CampaignPerspectiveTemplates.any { it.id == "second-person" })
    }
}
