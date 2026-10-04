package com.ihy2ln.weaverse.feature.games

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardSetBuilderTest {
    private val answer = """
        Sure! Here is the kit:
        ```json
        {"role":"ranger","element":"Light","tier":9,"magic":true,"title":"Lantern Courier","bio":"Runs messages by lantern light.",
         "cards":[
          {"name":"Lantern Flick","effect":"strike","ep":1,"text":"A flick of burning oil."},
          {"name":"Canal Mist","effect":"hex","ep":2,"status":"AttackDown"},
          {"name":"Courier Dash","effect":"insight","ep":1},
          {"name":"Signal Flare","effect":"sweep","ep":2},
          {"name":"Warm Light","effect":"mend","ep":1},
          {"name":"Last Delivery","effect":"strike","ep":7},
          {"name":"Extra","effect":"strike","ep":1}
         ],
         "ultimates":[{"name":"A Thousand Lanterns","effect":"nova"},{"name":"Lighthouse Vigil","effect":"sanctuary"}]}
        ```
    """.trimIndent()

    @Test
    fun aiAnswerBecomesABalancedSet() {
        val set = CardSetBuilder.fromAiAnswer(answer, "nv_mara_ab12", "Mara Vell", "The Lantern Canal", "codex-1", "art/nv_mara_ab12.jpg")
        assertEquals("Ranger", set.role)
        assertEquals("Light", set.element)
        assertEquals(6, set.tier) // clamped
        assertEquals(6, set.cards.size)
        assertEquals(2, set.ultimates.size)
        assertTrue(set.cards[0].initiation)
        assertTrue(set.cards.drop(1).none { it.initiation })
        val heavy = set.cards[5]
        assertEquals(3, heavy.ep) // ep 7 clamped
        assertEquals(2.6f, heavy.power)
        assertTrue(heavy.exhaust)
        assertTrue(heavy.magic)
        val hex = set.cards[1]
        assertEquals("AttackDown", hex.status)
        assertEquals(2, hex.duration)
        assertEquals("None", set.cards[2].target)
        assertEquals(2, set.cards[2].draw)
        assertEquals("Ultimate", set.ultimates[0].kind)
        assertEquals("AllEnemies", set.ultimates[0].target)
        assertEquals(32f, set.ultimates[1].heal)
        assertEquals(set.cards.size + set.ultimates.size, (set.cards + set.ultimates).map { it.id }.toSet().size)
        assertTrue(set.cards.all { it.id.startsWith("nv_mara_ab12_") })
    }

    @Test
    fun thinAnswersAreFilledAndAlwaysCanDealDamage() {
        val set = CardSetBuilder.fromAiAnswer(
            """{"role":"Support","cards":[{"name":"Hug","effect":"mend","ep":1},{"name":"Cheer","effect":"rally","ep":2}]}""",
            "nv_x", "X", "", "c", "",
        )
        assertEquals(6, set.cards.size)
        assertEquals(2, set.ultimates.size)
        assertTrue(set.cards.count { it.power > 0 } >= 2)
        assertThrows<IllegalArgumentException> { CardSetBuilder.fromAiAnswer("no json here", "a", "A", "", "", "") }
    }

    @Test
    fun editsRederiveNumbersAndKeepIds() {
        val set = CardSetBuilder.fromAiAnswer(answer, "nv_mara", "Mara Vell", "", "c", "")
        val card = set.cards[3]
        val edited = CardSetBuilder.edit(set, card, "Flare Storm", "Blinding.", "strike", 3, "")
        val after = edited.cards[3]
        assertEquals(card.id, after.id)
        assertEquals("Flare Storm", after.name)
        assertEquals("Enemy", after.target)
        assertEquals(2.6f, after.power)
        val restyled = CardSetBuilder.restyle(edited, "Tank", "Fire", false)
        assertEquals(edited.cards.map { it.id }, restyled.cards.map { it.id })
        assertEquals(170, restyled.hp)
        assertTrue(restyled.cards.filter { it.power > 0 }.none { it.magic })
    }

    @Test
    fun builtinCastIsRecognised() {
        assertNotNull(BuiltinCast.match("Kaela Stormfang"))
        assertNotNull(BuiltinCast.match("kaela"))
        assertEquals("daisy", BuiltinCast.match("Daisy Wildheart")?.id)
        assertNull(BuiltinCast.match("Mara Vell"))
        assertNull(BuiltinCast.match(""))
    }
}
