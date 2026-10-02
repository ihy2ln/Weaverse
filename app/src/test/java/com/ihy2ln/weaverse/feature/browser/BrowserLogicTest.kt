package com.ihy2ln.weaverse.feature.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowserLogicTest {
    @Test
    fun addressesLoadAndEverythingElseIsSearched() {
        assertEquals("https://example.com", resolveTyped("example.com", SearchEngine.Brave))
        assertEquals("https://en.m.wikipedia.org/wiki/Loom", resolveTyped(" en.m.wikipedia.org/wiki/Loom ", SearchEngine.Brave))
        assertEquals("http://localhost:8080", resolveTyped("http://localhost:8080", SearchEngine.Brave))
        assertEquals("https://192.168.1.4:3000/x", resolveTyped("192.168.1.4:3000/x", SearchEngine.Brave))
        assertEquals(WeaverPages.SOCIAL, resolveTyped("weaver://social", SearchEngine.Brave))
        assertEquals("https://search.brave.com/search?q=how+to+weave", resolveTyped("how to weave", SearchEngine.Brave))
        assertEquals("https://duckduckgo.com/?q=loom", resolveTyped("loom", SearchEngine.DuckDuckGo))
        assertEquals(WeaverPages.NEW_TAB, resolveTyped("   ", SearchEngine.Brave))
    }

    @Test
    fun theAddressBarShowsTheSite() {
        assertEquals("wikipedia.org", displayUrl("https://www.wikipedia.org/"))
        assertEquals("en.wikipedia.org/wiki/Loom", displayUrl("https://en.wikipedia.org/wiki/Loom?x=1#top"))
        assertEquals("", displayUrl(WeaverPages.NEW_TAB))
        assertEquals("cnn.com", hostOf("https://www.cnn.com/world"))
        assertEquals("social", hostOf(WeaverPages.SOCIAL))
    }

    @Test
    fun searchResultPagesShowTheSearchedWords() {
        assertEquals("how to weave", searchTermsOf("https://search.brave.com/search?q=how+to+weave&source=web"))
        assertNull(searchTermsOf("https://en.wikipedia.org/wiki/Loom?q=no"))
    }

    @Test
    fun trackerHostsMatchTheirSubdomains() {
        assertTrue(Shields.isTrackerHost("stats.g.doubleclick.net"))
        assertTrue(Shields.isTrackerHost("www.google-analytics.com"))
        assertFalse(Shields.isTrackerHost("wikipedia.org"))
        assertFalse(Shields.isTrackerHost("notdoubleclick.net.example.com"))
    }

    @Test
    fun modePagesAreKnown() {
        assertEquals("Roleplay", WeaverPages.MODES["weaver://rpg"])
        assertTrue(BrowserData.DEFAULT_FAVORITES.any { it.url == WeaverPages.SOCIAL })
        assertTrue(BrowserData.DEFAULT_FAVORITES.filter { it.url in WeaverPages.MODES }.size == WeaverPages.MODES.size)
    }
}
