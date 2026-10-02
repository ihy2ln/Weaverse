package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.feature.chatting.media.FeedXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FollowedFeedsTest {

    @Test
    fun `parses an RSS 2 outlet feed with CDATA and media thumbnail`() {
        val xml = """
            <rss><channel><title>Polygon</title>
            <item>
              <title><![CDATA[Nintendo announces a new Zelda &amp; more]]></title>
              <link>https://www.polygon.com/news/123/zelda</link>
              <dc:creator><![CDATA[Jane Writer]]></dc:creator>
              <description><![CDATA[<p>The <b>big</b> reveal.</p>]]></description>
              <media:thumbnail url="https://cdn.polygon.com/zelda.jpg" />
            </item>
            </channel></rss>
        """.trimIndent()
        val entry = FeedXml.parse(xml).single()
        assertEquals("Nintendo announces a new Zelda & more", entry.title)
        assertEquals("https://www.polygon.com/news/123/zelda", entry.url)
        assertEquals("Jane Writer", entry.author)
        assertEquals("The big reveal.", entry.summary)
        assertEquals("https://cdn.polygon.com/zelda.jpg", entry.image)
        assertEquals("Polygon", FeedXml.feedName(xml))
    }

    @Test
    fun `parses a YouTube channel feed`() {
        val xml = """
            <feed><title>Markiplier</title>
            <entry>
              <yt:videoId>abc123XYZ00</yt:videoId>
              <title>I Played The Scariest Game</title>
              <link rel="alternate" href="https://www.youtube.com/watch?v=abc123XYZ00"/>
              <author><name>Markiplier</name><uri>https://www.youtube.com/channel/UC7</uri></author>
              <media:group><media:thumbnail url="https://i1.ytimg.com/vi/abc123XYZ00/hqdefault.jpg" width="480" height="360"/>
              <media:description>New video!</media:description></media:group>
            </entry></feed>
        """.trimIndent()
        val entry = FeedXml.parse(xml).single()
        assertEquals("abc123XYZ00", entry.youtubeVideoId)
        assertEquals("Markiplier", entry.author)
        assertEquals("https://www.youtube.com/watch?v=abc123XYZ00", entry.url)
        assertEquals("New video!", entry.summary)
    }

    @Test
    fun `custom sources understand each kind of account`() {
        val sources = CustomFeedSource.parseAll(
            """
            r/wallstreetbets
            https://www.reddit.com/r/anime/
            #gamedev
            @markiplier.bsky.social
            https://bsky.app/profile/someone.bsky.social
            @Gargron@mastodon.social
            https://www.youtube.com/channel/UC7_YxT-KID8kRbqZo7MyscQ
            https://www.polygon.com/rss/index.xml
            18+ r/gonewild
            not a source
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                CustomFeedSource.Kind.Subreddit, CustomFeedSource.Kind.Subreddit, CustomFeedSource.Kind.Hashtag,
                CustomFeedSource.Kind.Bluesky, CustomFeedSource.Kind.Bluesky, CustomFeedSource.Kind.Mastodon,
                CustomFeedSource.Kind.YouTube, CustomFeedSource.Kind.Feed, CustomFeedSource.Kind.Subreddit,
            ),
            sources.map { it.kind },
        )
        assertEquals("anime", sources[1].value)
        assertEquals("Gargron@mastodon.social", sources[5].value)
        assertEquals("UC7_YxT-KID8kRbqZo7MyscQ", sources[6].value)
        assertTrue(sources.last().adult)
        assertFalse(sources.first().adult)
        assertNull(CustomFeedSource.parse("   "))
    }

    @Test
    fun `adult posts hinting at minors are never eligible`() {
        assertFalse(RealWebFeed.adultTextIsEligible("barely legal teen"))
        assertFalse(RealWebFeed.adultTextIsEligible("schoolgirl outfit"))
        assertTrue(RealWebFeed.adultTextIsEligible("New set is up, link in bio"))
    }

    @Test
    fun `adult feed kinds are off until opted in and go only to adult followers`() {
        assertTrue(FeedCategory.defaults.none { it.adult })
        assertTrue(FeedCategory.entries.filter { it.adult }.all { it.topic == SocialNpcs.ADULT_TOPIC })
        assertTrue(SocialNpcs.forTopic(SocialNpcs.ADULT_TOPIC).isNotEmpty())
        assertTrue(SocialNpcs.forTopic(SocialNpcs.ADULT_TOPIC).all { npc -> SocialNpcs.byId(npc.id)!!.interests.contains("adult") })
    }
}
