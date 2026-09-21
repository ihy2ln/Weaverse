package com.ihy2ln.weaverse.feature.storyboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaLanguageLabelTest {
    @Test
    fun readsPlainLanguageTags() {
        assertEquals("English (EN)", mangaLanguageLabel("en"))
        assertEquals("French (FR)", mangaLanguageLabel("fr"))
    }

    @Test
    fun overridesTagsTheJdkReadsDifferently() {
        // MangaDex means Latin America here; the JDK resolves "LA" to Laos.
        assertEquals("Spanish (Latin America) (ES-LA)", mangaLanguageLabel("es-la"))
        assertEquals("Portuguese (Brazil) (PT-BR)", mangaLanguageLabel("pt-br"))
    }

    @Test
    fun fallsBackToTheRawCode() {
        assertEquals("Unknown", mangaLanguageLabel("  "))
        assertEquals("XX", mangaLanguageLabel("xx"))
    }
}
