package com.ihy2ln.weaverse.core.roleplay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterCardPngTest {
    @Test
    fun embedAndExtract_roundTripsV2Card() {
        val spec = CharacterCardSpec(
            name = "Mira",
            description = "A cartographer with ink-stained fingers.",
            personality = "wry, patient",
            scenario = "A rain-soaked port city.",
            firstMes = "\"Maps lie. That's why I draw them.\"",
            tags = listOf("original", "fantasy"),
            alternateGreetings = listOf("Need a route?"),
        )
        val json = CharacterCardPng.cardJson(spec)
        val png = CharacterCardPng.embedCardJson(CharacterCardPng.minimalPng(), json)
        assertTrue(CharacterCardPng.looksLikePng(png))
        val extracted = CharacterCardPng.extractCardJson(png)
        val parsed = CharacterCardPng.parseCardJson(extracted)
        assertEquals("Mira", parsed.name)
        assertEquals(spec.description, parsed.description)
        assertEquals(spec.firstMes, parsed.firstMes)
        assertEquals(listOf("original", "fantasy"), parsed.tags)
        assertEquals(listOf("Need a route?"), parsed.alternateGreetings)
    }

    @Test
    fun parseCardJson_readsBareV1Fields() {
        val raw = """{"name":"Kai","description":"thief","first_mes":"Hey."}"""
        val parsed = CharacterCardPng.parseCardJson(raw)
        assertEquals("Kai", parsed.name)
        assertEquals("thief", parsed.description)
        assertEquals("Hey.", parsed.firstMes)
    }
}
