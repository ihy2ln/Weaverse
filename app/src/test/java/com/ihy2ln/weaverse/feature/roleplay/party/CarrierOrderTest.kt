package com.ihy2ln.weaverse.feature.roleplay.party

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarrierOrderTest {
    private fun carrier(name: String, kind: CarrierKind) =
        CarrierUi(characterId = name, name = name, items = emptyList(), kind = kind)

    @Test
    fun youComesBeforeTeamWhichComesBeforeRoster() {
        assertEquals(
            listOf("You", "Team", "Roster"),
            CarrierKind.entries.map { it.label },
        )
    }

    @Test
    fun carriersSortByGroupThenName() {
        val unsorted = listOf(
            carrier("Zara", CarrierKind.Roster),
            carrier("bran", CarrierKind.Team),
            carrier("Aldo", CarrierKind.Roster),
            carrier("Mira", CarrierKind.You),
            carrier("Ana", CarrierKind.Team),
        )
        val sorted = unsorted.sortedWith(
            compareBy({ it.kind.ordinal }, { it.name.lowercase() }),
        )
        assertEquals(
            listOf("Mira", "Ana", "bran", "Aldo", "Zara"),
            sorted.map { it.name },
        )
    }
}
