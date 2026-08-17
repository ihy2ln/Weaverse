package com.ihy2ln.weaverse.ai.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptTemplateEngineTest {

    @Test
    fun stripsComments() {
        val out = PromptTemplateEngine.render("{! a note !}Hello", PromptRenderContext())
        assertEquals("Hello", out)
    }

    @Test
    fun substitutesPlainTokens() {
        val ctx = PromptRenderContext(novelTense = "present tense", novelLanguage = "British English")
        val out = PromptTemplateEngine.render("Write in {novel.tense}, {novel.language}.", ctx)
        assertEquals("Write in present tense, British English.", out)
    }

    @Test
    fun bookTitleTokenResolves() {
        val ctx = PromptRenderContext(novelTitle = "Isekai Gacha")
        val out = PromptTemplateEngine.render("Story: {book.title}", ctx)
        assertEquals("Story: Isekai Gacha", out)
    }

    @Test
    fun unknownTokenResolvesToEmpty() {
        val out = PromptTemplateEngine.render("Before[{totally.unknown}]After", PromptRenderContext())
        assertEquals("Before[]After", out)
    }

    @Test
    fun includeResolvesComponentAndItsOwnTokens() {
        val ctx = PromptRenderContext(
            novelTitle = "Isekai Gacha",
            componentBlocks = mapOf("Codex" to "World: {book.title}"),
        )
        val out = PromptTemplateEngine.render("""{include("Weaverse/Codex")}""", ctx)
        assertEquals("World: Isekai Gacha", out)
    }

    @Test
    fun conditionalIncludesBodyWhenTrue() {
        val ctx = PromptRenderContext(storySoFar = "Something happened.")
        val out = PromptTemplateEngine.render(
            "{#if storySoFar}So far: {storySoFar}{#endif}",
            ctx,
        )
        assertEquals("So far: Something happened.", out)
    }

    @Test
    fun conditionalOmitsBodyWhenFalse() {
        val out = PromptTemplateEngine.render(
            "{#if storySoFar}So far: {storySoFar}{#endif}done",
            PromptRenderContext(storySoFar = ""),
        )
        assertEquals("done", out)
    }

    @Test
    fun nestedConditionalsResolveCorrectly() {
        val withCharacter = PromptTemplateEngine.render(
            "{#if pov}POV: {pov.type}{#if pov.character} from {pov.character}{#endif}.{#endif}",
            PromptRenderContext(pov = "third", povType = "third limited", povCharacter = "Mara"),
        )
        assertEquals("POV: third limited from Mara.", withCharacter)

        val withoutCharacter = PromptTemplateEngine.render(
            "{#if pov}POV: {pov.type}{#if pov.character} from {pov.character}{#endif}.{#endif}",
            PromptRenderContext(pov = "third", povType = "third limited", povCharacter = ""),
        )
        assertEquals("POV: third limited.", withoutCharacter)

        val noPov = PromptTemplateEngine.render(
            "{#if pov}POV: {pov.type}{#if pov.character} from {pov.character}{#endif}.{#endif}rest",
            PromptRenderContext(),
        )
        assertEquals("rest", noPov)
    }

    @Test
    fun andRequiresAllTrue() {
        val ctx = PromptRenderContext(
            textBefore = "",
            povCharacter = "Mara",
            scenePreviousPovCharacter = "Mara",
        )
        val out = PromptTemplateEngine.render(
            "{#if and(isStartOfText, pov.character is pov.character(scene.previous))}yes{#endif}",
            ctx,
        )
        assertEquals("yes", out)
    }

    @Test
    fun andFailsWhenOneConditionFalse() {
        val ctx = PromptRenderContext(
            textBefore = "already writing",
            povCharacter = "Mara",
            scenePreviousPovCharacter = "Mara",
        )
        val out = PromptTemplateEngine.render(
            "{#if and(isStartOfText, pov.character is pov.character(scene.previous))}yes{#endif}",
            ctx,
        )
        assertEquals("", out)
    }

    @Test
    fun eitherRequiresOneTrue() {
        val ctx = PromptRenderContext(textAfter = "more text")
        val out = PromptTemplateEngine.render(
            "{#if either(hasTextBefore, hasTextAfter)}has surrounding text{#endif}",
            ctx,
        )
        assertEquals("has surrounding text", out)
    }

    @Test
    fun lastWordsTakesTrailingWords() {
        val ctx = PromptRenderContext(scenePreviousFullText = "one two three four five")
        val out = PromptTemplateEngine.render(
            "{lastWords(scene.fullText(scene.previous), 2)}",
            ctx,
        )
        assertEquals("four five", out)
    }

    @Test
    fun wordsBeforeAndAfterTrimToCount() {
        val ctx = PromptRenderContext(textBefore = "alpha beta gamma delta", textAfter = "epsilon zeta eta")
        assertEquals("gamma delta", PromptTemplateEngine.render("{wordsBefore(2)}", ctx))
        assertEquals("epsilon zeta", PromptTemplateEngine.render("{wordsAfter(2)}", ctx))
    }

    @Test
    fun removeWhitespaceCollapsesRuns() {
        val ctx = PromptRenderContext(sceneFullTextCurrent = "line one\n\n   line   two")
        val out = PromptTemplateEngine.render("{removeWhitespace(scene.fullText)}", ctx)
        assertEquals("line one line two", out)
    }

    @Test
    fun ifsRendersTextOnlyWhenTrue() {
        val trueCtx = PromptRenderContext(message = "hello")
        assertEquals("shown", PromptTemplateEngine.render("""{ifs(message, "shown")}""", trueCtx))
        val falseCtx = PromptRenderContext(message = "")
        assertEquals("", PromptTemplateEngine.render("""{ifs(message, "shown")}""", falseCtx))
    }

    @Test
    fun inputWordsMapsToOutputWords() {
        val ctx = PromptRenderContext(outputWords = 350)
        assertEquals("350", PromptTemplateEngine.render("""{input("Words")}""", ctx))
    }

    @Test
    fun isStartOfTextReflectsTextBefore() {
        assertTrue(PromptRenderContext(textBefore = "").isStartOfText)
        assertFalse(PromptRenderContext(textBefore = "already here").isStartOfText)
    }
}
