package com.ihy2ln.weaverse.feature.roleplay.party

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarrierOrderTest {
    private fun carrier(name: String, kind: CarrierKind) =
        CarrierUi(characterId = name, name = name, items = emptyList(), kind = kind)

    @Test
    fun writerComesBeforeSeparatedRosterGroups() {
        assertEquals(
            listOf("Writer / You", "Team roster", "NPCs", "Enemies", "Other"),
            CarrierKind.entries.map { it.label },
        )
    }

    @Test
    fun carriersSortByGroupThenName() {
        val unsorted = listOf(
            carrier("Zara", CarrierKind.Enemy),
            carrier("bran", CarrierKind.Team),
            carrier("Aldo", CarrierKind.Npc),
            carrier("Mira", CarrierKind.You),
            carrier("Ana", CarrierKind.Team),
            carrier("Relic", CarrierKind.Other),
        )
        val sorted = unsorted.sortedWith(
            compareBy({ it.kind.ordinal }, { it.name.lowercase() }),
        )
        assertEquals(
            listOf("Mira", "Ana", "bran", "Aldo", "Zara", "Relic"),
            sorted.map { it.name },
        )
    }

    @Test
    fun tagsSeparateNpcEnemyAndOtherWhilePartyWins() {
        assertEquals(CarrierKind.Team, inventoryCarrierKind(true, "[\"enemy\"]"))
        assertEquals(CarrierKind.Npc, inventoryCarrierKind(false, "[\"merchant\"]"))
        assertEquals(CarrierKind.Enemy, inventoryCarrierKind(false, "[\"villain\"]"))
        assertEquals(CarrierKind.Other, inventoryCarrierKind(false, "[]"))
    }
}
