package com.ihy2ln.weaverse.feature.storyboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The predicate the chapter list applies for the Language picker. */
private fun matchesLanguage(chapterLanguage: String, filter: String): Boolean =
    filter.isBlank() || chapterLanguage.trim().equals(filter, ignoreCase = true)

class MangaLanguageFilterTest {
    private val chapters = listOf("en", "EN", " en ", "fr", "es-la", "pt-br", "")

    @Test
    fun englishFilterKeepsOnlyEnglishRegardlessOfCasingOrPadding() {
        assertEquals(3, chapters.count { matchesLanguage(it, "en") })
    }

    @Test
    fun anyKeepsEverything() {
        assertEquals(chapters.size, chapters.count { matchesLanguage(it, "") })
    }

    @Test
    fun regionTagsDoNotCollideWithTheirBaseLanguage() {
        // "es-la" must not be caught by an "es" filter, or Latin America leaks into Spain.
        assertEquals(0, chapters.count { matchesLanguage(it, "es") })
        assertEquals(1, chapters.count { matchesLanguage(it, "es-la") })
    }
}
