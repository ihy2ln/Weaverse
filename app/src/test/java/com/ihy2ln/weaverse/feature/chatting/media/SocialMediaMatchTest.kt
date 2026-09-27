package com.ihy2ln.weaverse.feature.chatting.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocialMediaMatchTest {
    private fun picture(title: String, description: String = "") = WebPicture(
        "id", title, "https://example.com/thumb", "https://example.com/full", false, "test", description = description,
    )

    @Test fun unrelatedRoomsAndPortraitsDoNotPass() {
        assertEquals(0, SocialMediaMatch.score(picture("Ornate palace ballroom"), "stray dog street photo", "I found a stray dog."))
        assertEquals(0, SocialMediaMatch.score(picture("Ornate palace ballroom"), "graphics card gaming setup", "My new GPU is fast."))
        assertEquals(0, SocialMediaMatch.score(picture("Woman in red jacket portrait"), "garage car mechanic", "Spent all night fixing my garage door."))
    }

    @Test fun matchingDescriptionsAndGifMetadataPass() {
        assertTrue(SocialMediaMatch.score(picture("Puppy playing in street"), "stray dog street photo", "I found a stray dog.") > 0)
        assertTrue(SocialMediaMatch.score(picture("", "GPU graphics card gameplay GIF"), "graphics card gaming gif", "My new GPU is fast.") > 0)
    }
}
