package com.ihy2ln.weaverse.feature.roleplay.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RpgCombatTest {
    private fun encounter() = RpgEncounterSetup(
        id = "enc-1",
        title = "The Glass Gate",
        stakes = "The party must reach the gate before nightfall.",
        enemies = listOf(RpgCombatant("golem", "Gate Golem", 8, armorClass = 12, isEnemy = true)),
        party = listOf(RpgCombatant("hero", "Hero", 20, attackModifier = 4)),
    )

    @Test
    fun cardBattleRejectsUnaffordableCardsAndResolvesOutcome() {
        val state = createRpgEncounter(encounter(), RpgCombatRuleset.CardBattle)
        val illegal = previewRpgCombatAction(state.copy(ep = 0), RpgCombatAction("hero", "arcane_burst", "golem"))
        assertFalse(illegal.legal)
        val resolved = resolveRpgCombatAction(state, RpgCombatAction("hero", "arcane_burst", "golem"), seed = 2)
        assertEquals(0, resolved.combatants.first { it.id == "golem" }.hp)
        assertTrue(resolved.combatants.first { it.id == "golem" }.statuses.contains(RpgStatusEffect.Exposed))
    }

    @Test
    fun d20AndTextModesShareNormalizedVictoryContract() {
        val d20 = resolveRpgCombatAction(
            createRpgEncounter(encounter(), RpgCombatRuleset.DndD20),
            RpgCombatAction("hero", targetId = "golem"),
            seed = 20,
        )
        val text = resolveRpgCombatAction(
            createRpgEncounter(encounter(), RpgCombatRuleset.TextReactions),
            RpgCombatAction("hero", targetId = "golem", text = "I strike when the gate opens."),
            seed = 2,
        )
        assertEquals(RpgCombatRuleset.DndD20, d20.ruleset)
        assertEquals(RpgCombatRuleset.TextReactions, text.ruleset)
        assertEquals("enc-1", d20.outcome?.encounterId ?: "enc-1")
        assertEquals("enc-1", text.outcome?.encounterId ?: "enc-1")
    }

    @Test
    fun setupParserDefaultsOldCampaignsToD20() {
        assertEquals(RpgCombatRuleset.DndD20, rpgCombatRulesetFromSetup("Campaign: Old save"))
        assertEquals(RpgCombatRuleset.CardBattle, rpgCombatRulesetFromSetup("Combat style: rpg-cards"))
        assertEquals(RpgCombatRuleset.CardBattle, RpgCombatRuleset.fromId("Focused Tactical Cards"))
        assertEquals(RpgCombatRuleset.TextReactions, rpgCombatRulesetFromSetup("Game mode: Text Reactions. Resolve text actions."))
        assertEquals(
            RpgCombatRuleset.CardBattle,
            rpgCombatRulesetFromSetup("Game mode: rpg-cards\nCombat style: rpg-d20"),
        )
    }

    @Test
    fun activeEncounterRoundTripsForForceCloseResume() {
        val active = createRpgEncounter(encounter(), RpgCombatRuleset.CardBattle)
        val encoded = Json.encodeToString(active)
        val restored = Json.decodeFromString<RpgCombatState>(encoded)

        assertEquals(RpgCombatRuleset.CardBattle, restored.ruleset)
        assertEquals("The Glass Gate", restored.encounter.title)
        assertEquals(active.hand, restored.hand)
        assertEquals(active.combatants, restored.combatants)
    }
}
