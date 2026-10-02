package com.ihy2ln.weaverse.feature.chatting.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SocialMediaLinksTest {
    @Test
    fun `links to youtube, video files and pictures are recognised`() {
        assertEquals(SocialVideo.LinkMedia.YouTube("dQw4w9WgXcQ"), SocialVideo.mediaLinkIn("watch https://www.youtube.com/watch?v=dQw4w9WgXcQ now"))
        assertEquals(SocialVideo.LinkMedia.YouTube("dQw4w9WgXcQ"), SocialVideo.mediaLinkIn("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(SocialVideo.LinkMedia.YouTube("abcdefghijk"), SocialVideo.mediaLinkIn("https://youtube.com/shorts/abcdefghijk"))
        assertEquals(SocialVideo.LinkMedia.Video("https://media.example.com/clip.mp4"), SocialVideo.mediaLinkIn("clip: https://media.example.com/clip.mp4."))
        assertEquals(SocialVideo.LinkMedia.Image("https://i.redd.it/x.jpg"), SocialVideo.mediaLinkIn("(https://i.redd.it/x.jpg)"))
        assertNull(SocialVideo.mediaLinkIn("just a page https://example.com/article"))
    }

    private fun post(origin: String, site: String) = SocialPostUi(
        id = "p", authorCharacterId = "a", authorName = "A", handle = "a", colorHex = "#000000", isYou = false, verified = false,
        text = "", imagePath = null, createdAt = 0, parentId = null, likeCount = 0, repostCount = 0, replyCount = 0, viewCount = 0,
        userReaction = "", userReposted = false, bookmarked = false, topReactions = emptyList(), feeling = "", repostOf = null,
        originKind = origin, sourceSite = site,
    )

    @Test
    fun `posts are grouped by the real site they came from`() {
        assertEquals("Reddit", SocialSites.of(post(SocialFeedViewModel.ORIGIN_REAL, "Reddit · r/anime")))
        assertEquals("Bluesky", SocialSites.of(post("web_share", "Bluesky · @x")))
        assertEquals(SocialSites.WEAVERSOCIAL, SocialSites.of(post("fictional", "")))
    }
}
