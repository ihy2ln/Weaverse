package com.ihy2ln.weaverse.core.manga

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class CatalogMetadataDeviceTest {
    @Test fun mangaDexLiveFiltersAndMetadata() = runBlocking {
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)
        try {
            val source = MangaDexSource(http)
            val filters = source.loadNativeFilters()
            val action = filters.filterIsInstance<WebsiteGroup>().flatMap { it.state }.filterIsInstance<WebsiteTri>().first { it.name == "Action" }
            action.state = eu.kanade.tachiyomi.source.model.Filter.TriState.STATE_INCLUDE
            val rows = source.searchPage("", 0, filters)
            assertTrue(rows.isNotEmpty())
            assertTrue(rows.all { "Action" in it.tags })
            assertTrue(!rows.first().coverUrl.isNullOrBlank())
            assertFalse(rows.any { it.year == "null" })
        } finally { http.close() }
    }

    @Test fun comixLiveFiltersAndDetailMetadata() = renderedSource("comix")
    @Test fun mangaFireLiveFiltersAndDetailMetadata() = renderedSource("mangafire")

    private fun renderedSource(id: String) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient()
        val source = PublicHtmlMangaSources(client, MangaWebLinkImporter(client), RenderedCatalogClient(context)).sources.first { it.descriptor.id == id }
        val filters = source.loadNativeFilters()
        val genres = filters.filterIsInstance<WebsiteGroup>().first { it.name == "Genres" }
        genres.state.filterIsInstance<WebsiteTri>().first { it.name == "Action" }.state = eu.kanade.tachiyomi.source.model.Filter.TriState.STATE_INCLUDE
        val rows = source.searchPage("", 0, filters)
        assertTrue("$id returned no filtered titles", rows.isNotEmpty())
        assertTrue("$id missing cover", !rows.first().coverUrl.isNullOrBlank())
        val details = source.details(rows.first())
        assertTrue("$id missing Action metadata: ${details.tags}", details.tags.any { it.equals("Action", true) })
        assertTrue("$id missing title", details.title.isNotBlank())
    }

    @Test fun metadataMigrationPreservesExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(21) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE manga_series(id TEXT PRIMARY KEY, title TEXT, coverUrl TEXT, tags TEXT)")
                    db.execSQL("INSERT INTO manga_series VALUES ('stable-id','Actual title','cover.jpg','Action')")
                    db.execSQL("CREATE TABLE manga_pages(id TEXT PRIMARY KEY, localPath TEXT)")
                    db.execSQL("INSERT INTO manga_pages VALUES ('page-id','original.jpg')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            WeaverseDatabase.MIGRATION_21_22.migrate(db)
            db.query("SELECT * FROM manga_series").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Actual title", row.getString(row.getColumnIndexOrThrow("title")))
                assertEquals("cover.jpg", row.getString(row.getColumnIndexOrThrow("coverUrl")))
                for (field in listOf("publicationType", "releaseYear", "contentRating", "catalogScore")) assertEquals("", row.getString(row.getColumnIndexOrThrow(field)))
            }
            db.query("SELECT localPath FROM manga_pages WHERE id='page-id'").use { row -> assertTrue(row.moveToFirst()); assertEquals("original.jpg", row.getString(0)) }
        } finally { helper.close() }
    }

    @Test fun rawkumaLiveFiltersAndMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient()
        val source = PublicHtmlMangaSources(client, MangaWebLinkImporter(client), RenderedCatalogClient(context)).sources.first { it.descriptor.id == "rawkuma" }
        val filters = source.loadNativeFilters()
        val group = filters.filterIsInstance<WebsiteGroup>().first { it.parameter == "the_genre" }
        (group.state.filterIsInstance<WebsiteCheck>().first { it.value == "action" }).state = true
        val rows = source.searchPage("", 0, filters)
        assertTrue("Action filter returned no titles", rows.isNotEmpty())
        val details = source.details(rows.first())
        assertNotEquals("Last Updates", details.title)
        assertTrue("Missing cover", !details.coverUrl.isNullOrBlank())
        assertTrue("Source tag not carried through", details.tags.any { it.equals("Action", true) })
    }

    @Test fun atsumaruLiveFilterIdsAreNotLabels() = runBlocking {
        val source = AtsumaruSource(OkHttpClient())
        val filters = source.loadNativeFilters()
        val group = filters.filterIsInstance<WebsiteGroup>().first { it.parameter == "genreIds" }
        val action = group.state.filterIsInstance<WebsiteTri>().first { it.name == "Action" }
        assertNotEquals("Action", action.value)
        action.state = eu.kanade.tachiyomi.source.model.Filter.TriState.STATE_INCLUDE
        assertTrue(source.searchPage("", 0, filters).isNotEmpty())
    }
}
