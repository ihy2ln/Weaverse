package com.ihy2ln.weaverse.core.manga

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MediaTagCatalogTest {
    @Test fun broadVocabularyIsUniqueAndSearchable() {
        assertTrue(MediaTagCatalog.all.size >= 824, "Expected at least 824 useful tags, got ${MediaTagCatalog.all.size}")
        assertEquals(MediaTagCatalog.all.size, MediaTagCatalog.all.map(CatalogTags::normalize).toSet().size)
        assertTrue(MediaTagCatalog.search(MediaTagCatalog.all, "based novel").contains("Based on a Web Novel"))
        assertEquals(listOf("Sci-Fi"), MediaTagCatalog.search(listOf("Sci-Fi", "Romance"), "sciencefiction"))
        assertEquals(listOf("Sci-Fi"), MediaTagCatalog.search(listOf("Sci-Fi", "Romance"), "science fiction"))
        assertTrue(MediaTagCatalog.all.containsAll(listOf("Family Life", "Primarily Adult Cast", "Royalty", "Childhood Friends", "Reincarnation", "Female Empowerment", "Murder", "Based on a Light Novel")))
        println("WeaverVerse curated tags: ${MediaTagCatalog.all.size}; groups: ${MediaTagCatalog.groups.size}")
    }

    @Test fun sourceAndCustomTagsSurviveAndAliasesDoNotDuplicate() {
        val options = MediaTagCatalog.options(listOf("Female Protagonist", "Unique Source Tag"), "!Custom tag")
        assertTrue(options.containsAll(listOf("Female Protagonist", "Unique Source Tag", "Custom tag")))
        assertEquals(1, options.count { CatalogTags.normalize(it) == "femalelead" })
        assertEquals("!Female Lead", CatalogTags.cycle("Female Protagonist", "Female Lead"))
        assertTrue(CatalogTags.matches(listOf("Female Protagonist"), "Female Lead", false))
        assertFalse(CatalogTags.matches(listOf("Female Protagonist"), "!Female Lead", false))
    }

    @Test fun numericRefinementsRejectUnknownAndNormalizeExplicitScales() {
        val title = MangaSearchResult("fixture", "1", "Test", score = "4/5", rating = "safe")
        assertTrue(CatalogRefinements(minimumScore = 8, contentRating = "safe").matches(title))
        assertFalse(CatalogRefinements(minimumScore = 9).matches(title))
        assertFalse(CatalogRefinements(minimumScore = 5).matches(title.copy(score = "")))
        assertNull(CatalogRefinements.score("NaN"))
        assertNull(CatalogRefinements.score("Infinity"))
        assertNull(CatalogRefinements.score("11"))
        assertNull(CatalogRefinements.score("5/0"))
    }

    @Test fun chapterAndVolumeRefinementsUseActualEntries() {
        val title = MangaSearchResult("fixture", "1", "Test")
        val chapter = MangaChapter("fixture", "ch1", "1", "Test", "Chapter 1", volume = "1")
        assertTrue(CatalogRefinements(minimumChapters = 1, volumes = "yes").matches(title, listOf(chapter)))
        assertFalse(CatalogRefinements(minimumChapters = 2).matches(title, listOf(chapter, chapter)))
        assertFalse(CatalogRefinements(volumes = "no").matches(title, null))
        assertFalse(CatalogRefinements(volumes = "no").matches(title, emptyList()))
        assertTrue(CatalogRefinements(volumes = "no").matches(title, listOf(chapter.copy(volume = ""))))
    }
}
