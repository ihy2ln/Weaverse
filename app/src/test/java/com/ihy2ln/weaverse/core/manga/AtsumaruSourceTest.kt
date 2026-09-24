package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AtsumaruSourceTest {
    @Test fun popularSearchChaptersAndPagesUsePublicData() = runTest {
        MockWebServer().use { server ->
            val source = AtsumaruSource(OkHttpClient(), server.url("/").toString())
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"abc","title":"Fixture","image":"posters/a.jpg"}]}"""))
            val manga = source.browse(MangaBrowseMode.Popular).single()
            assertEquals("abc", manga.remoteId)
            assertEquals("https://cdn.atsu.moe/static/posters/a.jpg", manga.coverUrl)
            assertTrue(server.takeRequest().path!!.startsWith("/api/home2/popular?"))
            server.enqueue(MockResponse().setBody("""{"hits":[{"document":{"id":"abc","title":"Fixture","poster":"posters/a.jpg"}}]}"""))
            assertEquals("Fixture", source.searchPage("Fixture", 1).single().title)
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
            server.enqueue(MockResponse().setBody("""{"chapters":[{"id":"ch1","title":"Chapter 1","number":1,"createdAt":123}]}"""))
            val chapter = source.chapters(manga).single()
            assertEquals(123L, chapter.dateUpload)
            assertEquals("abc", server.takeRequest().requestUrl!!.queryParameter("mangaId"))
            server.enqueue(MockResponse().setBody("""{"readChapter":{"pages":[{"image":"/static/pages/one.avif"}]}}"""))
            assertEquals("https://cdn.atsu.moe/static/pages/one.avif", source.pages(chapter).single().remoteUrl)
        }
    }
    @Test fun genuineEmptySearchIsNotAFormatFailure() = runTest {
        MockWebServer().use { server ->
            val source = AtsumaruSource(OkHttpClient(), server.url("/").toString())
            server.enqueue(MockResponse().setBody("""{"hits":[],"found":0}"""))
            assertTrue(source.search("no-such-title").isEmpty())
        }
    }
}
