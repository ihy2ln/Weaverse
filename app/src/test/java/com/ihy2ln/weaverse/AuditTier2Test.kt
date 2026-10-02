package com.ihy2ln.weaverse

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiRetry
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterErrorMapper
import com.ihy2ln.weaverse.feature.roleplay.chat.RoleplayGeneration
import com.ihy2ln.weaverse.feature.search.SearchText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditTier2Test {
    @Test
    fun `search excerpts show the words around the match`() {
        val text = "a ".repeat(200) + "the dragon wakes " + "b ".repeat(200)
        val excerpt = SearchText.excerpt(text, "Dragon")
        assertTrue(excerpt.contains("dragon"), excerpt)
        assertTrue(excerpt.startsWith("…"))
        assertEquals("short text", SearchText.excerpt("short   text", "zzz"))
    }

    @Test
    fun `roleplay history keeps the newest turns that fit`() {
        val history = (1..40).map { (if (it % 2 == 0) "assistant" else "user") to "message $it " + "x".repeat(400) }
        val kept = RoleplayGeneration.fitHistory(history, budgetTokens = 1_000)
        assertTrue(kept.size in RoleplayGeneration.MIN_KEPT_MESSAGES until history.size)
        assertEquals(history.last(), kept.last())
        assertEquals(history.takeLast(kept.size), kept)
        // A tiny budget still sends the latest exchanges.
        assertEquals(RoleplayGeneration.MIN_KEPT_MESSAGES, RoleplayGeneration.fitHistory(history, 0).size)
        assertEquals(history, RoleplayGeneration.fitHistory(history, 1_000_000))
    }

    @Test
    fun `transient provider failures are retried, others are not`() {
        assertTrue(AiRetry.isRetryable(AIError.RateLimited(5)))
        assertTrue(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(529, "overloaded")))
        assertTrue(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(500, "")))
        assertTrue(AiRetry.isRetryable(AIError.NoNetwork()))
        assertFalse(AiRetry.isRetryable(AIError.InvalidKey()))
        assertFalse(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(400, "bad")))
    }
}

class RealFeedSourcesTest {
    @Test
    fun `reddit api listings become posts with real authors and media`() {
        val listing = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"data":{"children":[
              {"kind":"t3","data":{"id":"a1","title":"Sunset","selftext":"","author":"alice","subreddit":"pics",
               "permalink":"/r/pics/comments/a1/sunset/","over_18":false,"score":120,"num_comments":9,
               "url":"https://i.redd.it/x.jpg","preview":{"images":[{"source":{"url":"https://preview.redd.it/x.jpg"}}]}}},
              {"kind":"t3","data":{"id":"a2","title":"Clip","author":"bob","subreddit":"nsfw","permalink":"/r/nsfw/comments/a2/clip/",
               "over_18":true,"secure_media":{"reddit_video":{"fallback_url":"https://v.redd.it/a2/DASH_480.mp4"}}}},
              {"kind":"t3","data":{"id":"a3","title":"Rules","author":"mod","stickied":true,"permalink":"/r/pics/comments/a3/"}}
            ]}}""",
        )
        val posts = com.ihy2ln.weaverse.feature.chatting.social.RedditJson.posts(listing)
        assertEquals(listOf("a1", "a2"), posts.map { it.id })
        assertEquals("https://www.reddit.com/r/pics/comments/a1/sunset/", posts[0].url)
        assertEquals("https://i.redd.it/x.jpg", posts[0].media)
        assertFalse(posts[0].isLoop)
        assertTrue(posts[1].over18)
        assertTrue(posts[1].isLoop)
    }

    @Test
    fun `adult filter checks tags and drops minors, leaks and fakes`() {
        val ok = com.ihy2ln.weaverse.feature.chatting.social.RealWebFeed.Companion::adultTextIsEligible
        assertFalse(ok("Twerking, 18 Years Old, Teen"))
        assertFalse(ok("petite-chicks, schoolgirls"))
        assertFalse(ok("Little Step Sis learns"))
        assertFalse(ok("OnlyFans leaked set"))
        assertFalse(ok("celebrity deepfake"))
        assertTrue(ok("Care to join me in the tub? Babe, Bath, Brunette"))
    }

    @Test
    fun `lemmy communities and forum pages are recognised`() {
        val sources = com.ihy2ln.weaverse.feature.chatting.social.CustomFeedSource.parseAll(
            "!sexygirls@lemmynsfw.com\nhttps://lemmy.world/c/technology\nhttps://forum.example.com/",
        )
        assertEquals(
            listOf("sexygirls@lemmynsfw.com", "technology@lemmy.world", "https://forum.example.com"),
            sources.map { it.value },
        )
        val html = """<html><head><link rel="alternate" type="application/rss+xml" href="/forums/-/index.rss" /></head></html>"""
        assertEquals("https://forum.example.com/forums/-/index.rss",
            com.ihy2ln.weaverse.feature.chatting.media.FeedXml.discover(html, "https://forum.example.com/"))
    }
}
