package com.ihy2ln.weaverse.core.manga

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CatalogTagsTest {
    @Test fun cyclesIncludeExcludeClearWithoutChangingOtherTags() {
        assertEquals("Fantasy,Action", CatalogTags.cycle("Fantasy", "Action"))
        assertEquals("Fantasy,!Action", CatalogTags.cycle("Fantasy,Action", "Action"))
        assertEquals("Fantasy", CatalogTags.cycle("Fantasy,!Action", "Action"))
    }
    @Test fun exclusionsWinWithEitherMatchMode() {
        assertFalse(CatalogTags.matches(listOf("Action", "Girls Love"), "Action,!Girls Love", true))
        assertTrue(CatalogTags.matches(listOf("Action"), "Action,Fantasy", true))
        assertFalse(CatalogTags.matches(listOf("Action"), "Action,Fantasy", false))
        assertTrue(CatalogTags.matches(listOf("Fantasy"), "!Action", false))
    }
    @Test fun normalizesSourceSpellings() {
        assertTrue(CatalogTags.matches(listOf("Shonen", "Science Fiction", "One-shot"), "Shounen,Sci-Fi,Oneshot", false))
    }
}
