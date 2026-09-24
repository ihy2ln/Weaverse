package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.core.manga.extension.MihonExtensionSourceAdapter
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import rx.Observable

class MihonExtensionBrowseTest {
    @Test fun initialBrowseExecutesLegacyExtensionInsteadOfDefaultEmptyList() = runTest {
        var requestedPage = 0
        val source = object : CatalogueSource {
            override val id = 42L
            override val name = "Fixture"
            override val lang = "en"
            override val supportsLatest = true
            override fun fetchPopularManga(page: Int): Observable<MangasPage> {
                requestedPage = page
                return Observable.just(MangasPage(listOf(SManga.create().apply { title = "Actual extension result"; url = "/fixture" }), true))
            }
        }
        val adapter = MihonExtensionSourceAdapter(source, "fixture.extension")
        assertEquals("Actual extension result", adapter.browse(MangaBrowseMode.Popular).single().title)
        assertEquals(1, requestedPage)
        adapter.browsePage(MangaBrowseMode.Popular, 2)
        assertEquals(3, requestedPage)
    }
}
