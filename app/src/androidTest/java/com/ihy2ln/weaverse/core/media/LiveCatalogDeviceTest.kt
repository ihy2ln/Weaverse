package com.ihy2ln.weaverse.core.media

import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.core.manga.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/** Explicit live QA, run separately from deterministic tests. Reads public data only. */
class LiveCatalogDeviceTest {
    private fun sources(): List<MangaSourceAdapter> {
        val client = OkHttpClient()
        return PublicHtmlMangaSources(client, MangaWebLinkImporter(client),
            RenderedCatalogClient(InstrumentationRegistry.getInstrumentation().targetContext)).sources
    }
    @Test fun atsumaruCatalogSearchAndReader() = runBlocking {
        withTimeout(60000) {
            val source = sources().first { it.descriptor.id == "atsumaru" }
            val popular = source.browse(MangaBrowseMode.Popular)
            assertTrue("Empty popular", popular.isNotEmpty())
            assertTrue("Empty latest", source.browse(MangaBrowseMode.Latest).isNotEmpty())
            val found = source.search("Witch Hat Atelier")
            assertTrue("Empty search", found.isNotEmpty())
            val detail = source.details(found.first())
            val chapters = source.chapters(detail)
            assertTrue("Empty chapters", chapters.isNotEmpty())
            assertTrue("Empty pages", source.pages(chapters.first()).isNotEmpty())
        }
    }
    @Test fun comixPopularAndSearch() = runBlocking {
        withTimeout(60000) {
            val source = sources().first { it.descriptor.id == "comix" }
            assertTrue("Empty Comix popular", source.browse(MangaBrowseMode.Popular).isNotEmpty())
            val found = source.search("One Piece")
            assertTrue("Comix search did not return matching titles", found.any { it.title.contains("One Piece", true) })
            assertTrue("Empty Comix latest", source.browse(MangaBrowseMode.Latest).isNotEmpty())
            val detail = source.details(found.first { it.title.contains("One Piece", true) })
            assertTrue("Empty Comix chapters", source.chapters(detail).isNotEmpty())
        }
    }
    @Test fun mangaFirePopularAndSearch() = runBlocking {
        withTimeout(60000) {
            val source = sources().first { it.descriptor.id == "mangafire" }
            assertTrue("Empty MangaFire popular", source.browse(MangaBrowseMode.Popular).isNotEmpty())
            val found = source.search("One Piece")
            assertTrue("MangaFire search did not return matching titles", found.any { it.title.contains("One Piece", true) })
            assertTrue("Empty MangaFire latest", source.browse(MangaBrowseMode.Latest).isNotEmpty())
            val detail = source.details(found.first { it.title.contains("One Piece", true) })
            assertTrue("Empty MangaFire chapters", source.chapters(detail).isNotEmpty())
        }
    }
    @Test fun mangaDotCatalog() = runBlocking {
        withTimeout(60000) {
            assertTrue("Empty MangaDot popular", sources().first { it.descriptor.id == "mangadot" }
                .browse(MangaBrowseMode.Popular).isNotEmpty())
        }
    }
}
