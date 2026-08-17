package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.data.db.entity.SelectiveLogic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ContextBuilderTest {
    private fun entry(
        id: String,
        name: String,
        aliases: List<String> = emptyList(),
        body: String = "$name's description.",
        alwaysInclude: Boolean = false,
        isConstant: Boolean = false,
        disabled: Boolean = false,
        trackByNameAlias: Boolean = true,
        keys: List<String> = emptyList(),
        secondaryKeys: List<String> = emptyList(),
        selectiveLogic: SelectiveLogic = SelectiveLogic.AndAny,
        insertionOrder: Int = 100,
        probability: Int = 100,
        caseSensitive: Boolean = false,
        matchWholeWords: Boolean = true,
        recursionAllowed: Boolean = true,
    ) = CodexEntryContext(
        id = id, name = name, aliases = aliases, bodyText = body, alwaysInclude = alwaysInclude,
        isConstant = isConstant, disabled = disabled, trackByNameAlias = trackByNameAlias, keys = keys, secondaryKeys = secondaryKeys,
        selectiveLogic = selectiveLogic, insertionOrder = insertionOrder, probability = probability,
        caseSensitive = caseSensitive, matchWholeWords = matchWholeWords, recursionAllowed = recursionAllowed,
    )

    private fun novelScope(currentScene: String) = ContextScope.Novel(currentSceneText = currentScene)

    @Test
    fun `entry name in scan text is detected`() {
        val entries = listOf(entry(id = "1", name = "John Zhao"))
        val result = ContextBuilder.build(
            scope = novelScope("John Zhao walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `entry alias in scan text is detected`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", aliases = listOf("Zhao")))
        val result = ContextBuilder.build(
            scope = novelScope("Zhao walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `name-alias tracking disabled skips auto-detection by name`() {
        val entries = listOf(entry(id = "1", name = "Grace", trackByNameAlias = false))
        val result = ContextBuilder.build(
            scope = novelScope("Grace walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertFalse("1" in result.usedEntryIds)
    }

    @Test
    fun `name-alias tracking disabled still matches via keys`() {
        val entries = listOf(entry(id = "1", name = "Grace", trackByNameAlias = false, keys = listOf("the empress")))
        val result = ContextBuilder.build(
            scope = novelScope("The empress walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `unrelated entries are not detected`() {
        val entries = listOf(entry(id = "1", name = "John Zhao"), entry(id = "2", name = "Mara Voss"))
        val result = ContextBuilder.build(
            scope = novelScope("John Zhao walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertFalse("2" in result.usedEntryIds)
    }

    @Test
    fun `whole word matching does not match substrings`() {
        val entries = listOf(entry(id = "1", name = "Mara", matchWholeWords = true))
        val result = ContextBuilder.build(
            scope = novelScope("The marathon runners gathered at dawn."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertFalse("1" in result.usedEntryIds)
    }

    @Test
    fun `disabling whole word matching allows substring matches`() {
        val entries = listOf(entry(id = "1", name = "Mara", matchWholeWords = false))
        val result = ContextBuilder.build(
            scope = novelScope("The marathon runners gathered at dawn."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `case sensitive matching respects case`() {
        val entries = listOf(entry(id = "1", name = "Mara", caseSensitive = true))
        val lower = ContextBuilder.build(novelScope("mara walked in."), ContextTrigger(""), entries)
        val exact = ContextBuilder.build(novelScope("Mara walked in."), ContextTrigger(""), entries)
        assertFalse("1" in lower.usedEntryIds)
        assertTrue("1" in exact.usedEntryIds)
    }

    @Test
    fun `lore keys with andAny secondary logic require at least one secondary key`() {
        val entries = listOf(
            entry(
                id = "1", name = "The Old Mill", keys = listOf("mill"),
                secondaryKeys = listOf("water", "stone"), selectiveLogic = SelectiveLogic.AndAny,
            ),
        )
        val noSecondary = ContextBuilder.build(novelScope("They passed the mill."), ContextTrigger(""), entries)
        val withSecondary = ContextBuilder.build(novelScope("The mill's water wheel creaked."), ContextTrigger(""), entries)
        assertFalse("1" in noSecondary.usedEntryIds)
        assertTrue("1" in withSecondary.usedEntryIds)
    }

    @Test
    fun `lore keys with andAll secondary logic require every secondary key`() {
        val entries = listOf(
            entry(
                id = "1", name = "The Old Mill", keys = listOf("mill"),
                secondaryKeys = listOf("water", "stone"), selectiveLogic = SelectiveLogic.AndAll,
            ),
        )
        val onlyOne = ContextBuilder.build(novelScope("The mill's water wheel creaked."), ContextTrigger(""), entries)
        val both = ContextBuilder.build(novelScope("The stone mill's water wheel creaked."), ContextTrigger(""), entries)
        assertFalse("1" in onlyOne.usedEntryIds)
        assertTrue("1" in both.usedEntryIds)
    }

    @Test
    fun `lore keys with notAny secondary logic exclude when any secondary key present`() {
        val entries = listOf(
            entry(
                id = "1", name = "The Old Mill", keys = listOf("mill"),
                secondaryKeys = listOf("abandoned"), selectiveLogic = SelectiveLogic.NotAny,
            ),
        )
        val withExcluder = ContextBuilder.build(novelScope("The abandoned mill loomed."), ContextTrigger(""), entries)
        val withoutExcluder = ContextBuilder.build(novelScope("The mill loomed."), ContextTrigger(""), entries)
        assertFalse("1" in withExcluder.usedEntryIds)
        assertTrue("1" in withoutExcluder.usedEntryIds)
    }

    @Test
    fun `alwaysInclude entries are included regardless of scan text`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", alwaysInclude = true))
        val result = ContextBuilder.build(novelScope("Nothing relevant here."), ContextTrigger(""), entries)
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `constant entries are included regardless of scan text`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", isConstant = true))
        val result = ContextBuilder.build(novelScope("Nothing relevant here."), ContextTrigger(""), entries)
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `disabled entries are never included even when named`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", disabled = true, alwaysInclude = true))
        val result = ContextBuilder.build(novelScope("John Zhao walked in."), ContextTrigger(""), entries)
        assertFalse("1" in result.usedEntryIds)
    }

    @Test
    fun `recursion pulls in entries mentioned only inside another matched entry's body`() {
        val entries = listOf(
            entry(id = "1", name = "John Zhao", body = "Carries Zhao's Compass everywhere."),
            entry(id = "2", name = "Zhao's Compass", body = "A brass compass."),
        )
        val result = ContextBuilder.build(
            scope = novelScope("John Zhao walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            maxRecursionDepth = 2,
        )
        assertTrue("1" in result.usedEntryIds)
        assertTrue("2" in result.usedEntryIds, "expected recursion to pull in the compass entry via entry 1's body text")
    }

    @Test
    fun `recursion respects recursionAllowed false on the matched entry`() {
        val entries = listOf(
            entry(id = "1", name = "John Zhao", body = "Carries Zhao's Compass everywhere.", recursionAllowed = false),
            entry(id = "2", name = "Zhao's Compass", body = "A brass compass."),
        )
        val result = ContextBuilder.build(
            scope = novelScope("John Zhao walked into the room."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
        )
        assertTrue("1" in result.usedEntryIds)
        assertFalse("2" in result.usedEntryIds)
    }

    @Test
    fun `recursion does not loop forever when two entries reference each other`() {
        val entries = listOf(
            entry(id = "1", name = "Alpha", body = "Friends with Beta."),
            entry(id = "2", name = "Beta", body = "Friends with Alpha."),
        )
        val result = ContextBuilder.build(novelScope("Alpha arrived."), ContextTrigger(""), entries)
        assertTrue("1" in result.usedEntryIds)
        assertTrue("2" in result.usedEntryIds)
    }

    @Test
    fun `probability zero never includes a keyword-matched entry`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", probability = 0))
        val result = ContextBuilder.build(
            novelScope("John Zhao walked in."), ContextTrigger(""), entries,
            random = Random(42),
        )
        assertFalse("1" in result.usedEntryIds)
    }

    @Test
    fun `probability 100 always includes a keyword-matched entry`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", probability = 100))
        val result = ContextBuilder.build(
            novelScope("John Zhao walked in."), ContextTrigger(""), entries,
            random = Random(1),
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `manual include forces an otherwise-unmatched entry in`() {
        val entries = listOf(entry(id = "1", name = "Mara Voss"))
        val result = ContextBuilder.build(
            scope = novelScope("Nothing relevant here."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            manualIncludeIds = setOf("1"),
        )
        assertTrue("1" in result.usedEntryIds)
    }

    @Test
    fun `removing a chip (manual exclude) removes that entry from the assembled prompt`() {
        val entries = listOf(entry(id = "1", name = "John Zhao"))
        val withoutExclude = ContextBuilder.build(novelScope("John Zhao walked in."), ContextTrigger(""), entries)
        val withExclude = ContextBuilder.build(
            scope = novelScope("John Zhao walked in."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            manualExcludeIds = setOf("1"),
        )
        assertTrue("1" in withoutExclude.usedEntryIds)
        assertFalse("1" in withExclude.usedEntryIds)
    }

    @Test
    fun `manual exclude overrides even alwaysInclude and constant entries`() {
        val entries = listOf(entry(id = "1", name = "John Zhao", alwaysInclude = true, isConstant = true))
        val result = ContextBuilder.build(
            scope = novelScope("irrelevant"),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            manualExcludeIds = setOf("1"),
        )
        assertFalse("1" in result.usedEntryIds)
    }

    @Test
    fun `budget eviction drops the lowest-priority section when over budget`() {
        val longEntryBody = "word ".repeat(2000)
        val entries = listOf(entry(id = "1", name = "John Zhao", body = longEntryBody))
        val result = ContextBuilder.build(
            scope = novelScope("John Zhao walked in."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            tokenBudget = TokenBudget(contextLimit = 50, reserveForResponse = 20),
        )
        // Budget is far too small for the 2000-word entry body -> it must be dropped.
        assertFalse("1" in result.usedEntryIds)
        assertTrue(result.droppedSectionLabels.isNotEmpty())
    }

    @Test
    fun `budget eviction keeps higher priority sections and drops lower priority ones`() {
        val entries = listOf(
            entry(id = "1", name = "Constant Entry", isConstant = true, body = "short"),
            entry(id = "2", name = "Huge Entry", body = "word ".repeat(2000)),
        )
        val result = ContextBuilder.build(
            scope = novelScope("Constant Entry and Huge Entry both appear here."),
            trigger = ContextTrigger(""),
            codexEntries = entries,
            tokenBudget = TokenBudget(contextLimit = 50, reserveForResponse = 20),
        )
        assertTrue("1" in result.usedEntryIds, "the small constant entry should survive the budget")
        assertFalse("2" in result.usedEntryIds, "the huge entry should be dropped")
    }

    @Test
    fun `token breakdown reports every candidate section with its estimated size`() {
        val entries = listOf(entry(id = "1", name = "John Zhao"))
        val result = ContextBuilder.build(novelScope("John Zhao walked in."), ContextTrigger(""), entries)
        assertTrue(result.tokenBreakdown.isNotEmpty())
        assertTrue(result.tokenBreakdown.all { it.tokenCount >= 0 })
    }

    @Test
    fun `series premise and prior-member summaries are injected as system blocks`() {
        val scope = ContextScope.Novel(
            currentSceneText = "John arrives in town.",
            seriesContext = SeriesContext(
                premise = "A detective solves one mystery per book in a haunted seaside town.",
                previousMemberSummaries = listOf("Book one: the lighthouse keeper vanished."),
            ),
        )
        val result = ContextBuilder.build(scope = scope, trigger = ContextTrigger(""), codexEntries = emptyList())
        assertTrue(result.systemBlocks.any { it.contains("A detective solves one mystery") })
        assertTrue(result.systemBlocks.any { it.contains("the lighthouse keeper vanished") })
    }

    @Test
    fun `a null or empty series context adds no series section`() {
        val withNull = ContextBuilder.build(novelScope("Scene text."), ContextTrigger(""), emptyList())
        val withEmpty = ContextBuilder.build(
            scope = ContextScope.Novel(currentSceneText = "Scene text.", seriesContext = SeriesContext()),
            trigger = ContextTrigger(""),
            codexEntries = emptyList(),
        )
        assertEquals(withNull.systemBlocks, withEmpty.systemBlocks)
    }

    @Test
    fun `series section is ordered ahead of the book system block`() {
        val scope = ContextScope.Novel(
            currentSceneText = "Scene text.",
            styleGuide = "Warm, grounded prose.",
            seriesContext = SeriesContext(premise = "A long-running saga."),
        )
        val result = ContextBuilder.build(scope = scope, trigger = ContextTrigger(""), codexEntries = emptyList())
        val seriesIndex = result.systemBlocks.indexOfFirst { it.contains("A long-running saga") }
        val styleIndex = result.systemBlocks.indexOfFirst { it.contains("Warm, grounded prose") }
        assertTrue(seriesIndex in 0 until styleIndex, "series block should come before the book's own style guide")
    }
}
