package com.ihy2ln.weaverse.feature.chatting.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditRssTest {
    private val sample = "<feed><entry><author><name>/u/v78</name><uri>https://www.reddit.com/user/v78</uri></author><category term=\"gifs\" label=\"r/gifs\"/><content type=\"html\">&lt;table&gt; &lt;tr&gt;&lt;td&gt; &lt;a href=&quot;https://www.reddit.com/r/gifs/comments/1giw0we/i_drew_this_pixel_art_animation_in_memory_of/&quot;&gt; &lt;img src=&quot;https://preview.redd.it/5apc9mfquqyd1.gif?width=640&amp;amp;crop=smart&amp;amp;s=8f123b5068d573968dc9166ece3f16d749a1d74c&quot; alt=&quot;I drew this pixel art animation in memory of Peanut the Squirrel [OC]&quot; title=&quot;I drew this pixel art animation in memory of Peanut the Squirrel [OC]&quot; /&gt; &lt;/a&gt; &lt;/td&gt;&lt;td&gt; &amp;#32; submitted by &amp;#32; &lt;a href=&quot;https://www.reddit.com/user/v78&quot;&gt; /u/v78 &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href=&quot;https://i.redd.it/5apc9mfquqyd1.gif&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt; &amp;#32; &lt;span&gt;&lt;a href=&quot;https://www.reddit.com/r/gifs/comments/1giw0we/i_drew_this_pixel_art_animation_in_memory_of/&quot;&gt;[comments]&lt;/a&gt;&lt;/span&gt; &lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;</content><id>t3_1giw0we</id><media:thumbnail url=\"https://preview.redd.it/5apc9mfquqyd1.gif?width=640&amp;crop=smart&amp;s=8f123b5068d573968dc9166ece3f16d749a1d74c\" /><link href=\"https://www.reddit.com/r/gifs/comments/1giw0we/i_drew_this_pixel_art_animation_in_memory_of/\" /><updated>2024-11-03T20:12:25+00:00</updated><published>2024-11-03T20:12:25+00:00</published><title>I drew this pixel art animation in memory of Peanut the Squirrel [OC]</title></entry></feed>"

    @Test
    fun parsesTitleAuthorSubredditAndDirectGif() {
        val entry = RedditRss.parse(sample).single()
        assertEquals("I drew this pixel art animation in memory of Peanut the Squirrel [OC]", entry.title)
        assertEquals("v78", entry.author)
        assertEquals("gifs", entry.subreddit)
        assertEquals("https://i.redd.it/5apc9mfquqyd1.gif", entry.directMedia)
        assertTrue(entry.url.startsWith("https://www.reddit.com/r/gifs/comments/1giw0we/"))
        assertTrue(entry.preview!!.contains("width=640&crop=smart"))
    }

    @Test
    fun becomesACreditedGif() {
        val picture = RedditRss.toPicture(RedditRss.parse(sample).single(), gifsOnly = true)!!
        assertTrue(picture.isGif)
        assertEquals("u/v78 · r/gifs", picture.credit)
        assertEquals("https://i.redd.it/5apc9mfquqyd1.gif", picture.fullUrl)
    }
}
