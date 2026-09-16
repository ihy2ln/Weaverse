package com.ihy2ln.weaverse.core.manga

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GallerySourceTest {
    private val base = "https://e-hentai.org/"
    private fun doc(html: String) = Jsoup.parse(html, base)
    @Test fun sessionOnlyAllowsExactHttpsHosts() {
        assertTrue(GallerySessionPolicy.allowed(base))
        assertTrue(GallerySessionPolicy.allowed("https://exhentai.org/"))
        assertTrue(GallerySessionPolicy.allowed("https://forums.e-hentai.org/index.php", true))
        listOf("http://e-hentai.org/", "https://e-hentai.org.evil.test/", "https://evil.test/?e-hentai.org",
            "https://e-hentai.org:8443/", "https://user@e-hentai.org/", "file:///private", "https://forums.e-hentai.org/").forEach {
            assertFalse(GallerySessionPolicy.allowed(it), it)
        }
    }
    @Test fun cookieParsingDropsUnrelatedAndInvalidValues() {
        assertEquals(mapOf("ipb_member_id" to "123", "ipb_pass_hash" to "fixture"), GallerySessionPolicy.session("other=secret; ipb_member_id=123; ipb_pass_hash=fixture; igneous=bad\r\ninjection"))
        assertTrue(GallerySessionPolicy.loggedIn("ipb_member_id=123; ipb_pass_hash=fixture"))
        assertFalse(GallerySessionPolicy.loggedIn("ipb_member_id=0; ipb_pass_hash=fixture"))
        assertFalse(GallerySessionPolicy.loggedIn("ipb_member_id=123"))
    }
    @Test fun catalogKeepsTitleCoverAndCursorWithoutSidebarDuplicates() {
        val page = doc("""<table class='itg'><tr><td><div class='glthumb'><img data-src='https://images.example.test/cover.jpg'></div></td><td><a href='/g/123/abc/'><div class='glink'>Fixture gallery</div></a><div class='gt' title='language:english'>english</div></td></tr><tr><th>Ad</th></tr></table><a id='unext' href='/?next=123'>Next</a>""")
        val result = GalleryHtml.catalog(page, "ehentai").single()
        assertEquals("Fixture gallery", result.title)
        assertEquals("/g/123/abc/", result.remoteId)
        assertEquals("https://images.example.test/cover.jpg", result.coverUrl)
        assertEquals(listOf("language:english"), result.tags)
        assertEquals("https://e-hentai.org/?next=123", GalleryHtml.nextCatalog(page))
        assertNull(GalleryHtml.nextCatalog(doc("<a id='unext' href='https://evil.test/'>Next</a>")))
    }
    @Test fun detailsKeepNamespacedTagsAndRating() {
        val detail = GalleryHtml.details(doc("""<h1 id='gn'>Correct title</h1><h1 id='gj'>Alternate</h1><div id='gd1'><div style="background:url(https://images.example.test/cover.jpg)"></div></div><div id='gdc'>Manga</div><div id='rating_label'>Average: 4.50</div><table id='taglist'><tr><td>language:</td><td><a id='ta_language'>english</a></td></tr><tr><td>artist:</td><td><a id='ta_artist'>Fixture artist</a></td></tr></table>"""), MangaSearchResult("ehentai", "/g/123/abc/", "Old"))
        assertEquals("Correct title", detail.title)
        assertEquals(listOf("english"), detail.languages)
        assertEquals(listOf("Fixture artist"), detail.artists)
        assertEquals("4.50", detail.score)
        assertEquals("Adult", detail.rating)
    }
    private fun source(enabled: Boolean = true, response: (String) -> Pair<Int, String>): GallerySource {
        val account = mockk<GalleryAccountManager> {
            every { state } returns MutableStateFlow(GalleryAccountState(enabled, false, false))
            every { cookieHeader(any()) } returns null
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = response(chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .header("Content-Type", "text/html").body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        return GallerySource(account, client, false)
    }
    @Test fun disabledSourceMakesNoNetworkRequests() = runBlocking {
        val source = source(false) { throw AssertionError("Disabled source sent a request") }
        assertThrows(IllegalStateException::class.java) { runBlocking { source.search("") } }
        Unit
    }
    @Test fun filtersEncodeWebsiteParametersAndRejectInvalidRanges() {
        val source = source { 200 to "" }
        val filters = source.nativeFilters()
        filters.filterIsInstance<WebsiteText>().first { it.parameter == "f_spf" }.state = "25"
        filters.filterIsInstance<WebsiteSelect>().first { it.parameter == "f_srdd" }.state = 2
        val url = GalleryHtml.searchUrl(base.removeSuffix("/"), "language:english -artist:example", filters)
        assertTrue(url.contains("f_spf=25")); assertTrue(url.contains("f_srdd=3")); assertTrue(url.contains("f_sp=on"))
        filters.filterIsInstance<WebsiteText>().first { it.parameter == "f_spt" }.state = "10"
        assertThrows(IllegalArgumentException::class.java) { GalleryHtml.searchUrl(base, "", filters) }
    }
    @Test fun pagesFollowThumbnailPagesAndResolveImagePagesInOrder() = runBlocking {
        val requested = mutableListOf<String>()
        val source = source { url ->
            requested += url
            200 to when {
                url.endsWith("?p=1") -> "<div id='gdt'><a href='/s/two/123-2'><img title='Page 2'></a></div>"
                url.contains("/g/") -> "<div id='gdt'><a href='/s/one/123-1'><img title='Page 1'></a></div><table class='ptt'><tr><td><a href='?p=1'>&gt;</a></td></tr></table>"
                url.contains("/s/one/") -> "<img id='img' src='https://images.example.test/one.jpg'>"
                else -> "<img id='img' src='https://images.example.test/two.jpg'>"
            }
        }
        val pages = source.pages(MangaChapter("ehentai", "id", "id", "Fixture", "Gallery", canonicalUrl = "${base}g/123/abc/"))
        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
        assertEquals(listOf("https://images.example.test/one.jpg", "https://images.example.test/two.jpg"), pages.map { it.remoteUrl })
        assertEquals(4, requested.size)
    }
    @Test fun accessDeniedIsAnErrorNotAnEmptySuccessfulCatalog() {
        val source = source { 403 to "Denied" }
        assertThrows(IllegalStateException::class.java) { runBlocking { source.search("") } }
    }
}
