package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.feature.chatting.social.ContentLabel
import com.ihy2ln.weaverse.feature.chatting.social.compactCount
import com.ihy2ln.weaverse.feature.chatting.social.handleFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordFormattingTest {
    @Test
    fun `code fences and quotes split out of prose`() {
        val blocks = parseDiscordBlocks("hey\n```kotlin\nval x = 1\n```\n> quoted line\nafter")
        assertEquals(
            listOf(
                DiscordBlock.Prose("hey"),
                DiscordBlock.Code("val x = 1", "kotlin"),
                DiscordBlock.Quote("quoted line"),
                DiscordBlock.Prose("after"),
            ),
            blocks,
        )
    }

    @Test
    fun `only short emoji-only messages are jumbo`() {
        assertTrue(isJumboEmoji("😂"))
        assertTrue(isJumboEmoji("🔥🔥"))
        assertFalse(isJumboEmoji("lol 😂"))
        assertFalse(isJumboEmoji(""))
    }

    @Test
    fun `writer mentions match persona, you, everyone`() {
        assertTrue(mentionsWriter("hey @Mara look", "Mara"))
        assertTrue(mentionsWriter("@everyone meeting now", "Mara"))
        assertTrue(mentionsWriter("@you there?", "Mara"))
        assertFalse(mentionsWriter("@Youssef hi", "Mara"))
        assertFalse(mentionsWriter("Mara is here", "Mara"))
    }

    @Test
    fun `reactions round-trip and drop zero counts`() {
        val json = encodeReactions(mapOf("👍" to 2, "🔥" to 0))
        assertEquals(mapOf("👍" to 2), decodeReactions(json))
        assertEquals(emptyMap<String, Int>(), decodeReactions("not json"))
    }

    @Test
    fun `social counts and handles`() {
        assertEquals("999", compactCount(999))
        assertEquals("1.2K", compactCount(1_234))
        assertEquals("2K", compactCount(2_000))
        assertEquals("48K", compactCount(48_000))
        assertEquals("2.5M", compactCount(2_500_000))
        assertEquals("elaravance", handleFor("Elara Vance"))
    }

    @Test
    fun `social parser splits quoted-nickname posters and skips unknowns`() {
        val cast = listOf(
            com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity(id = "a", name = "Dr. Elena Valera", createdAt = 0),
            com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity(id = "b", name = "Theresa \"Tessa\" Rivera", createdAt = 0),
        )
        val raw = "Dr. Elena Valera: Coffee is my only friend.\n\nTheresa \"Tessa\" Rivera: Quiet tonight.\nStill quiet.\nNobody: ignored"
        val lines = com.ihy2ln.weaverse.feature.chatting.social.parseSocialLines(raw, cast)
        assertEquals(listOf("a", "b"), lines.map { it.character?.id })
        assertEquals("Quiet tonight.\nStill quiet.\nNobody: ignored", lines[1].text)
    }

    @Test
    fun `several pictures share one media id column`() {
        val joined = com.ihy2ln.weaverse.feature.chatting.social.joinMediaIds(listOf("a", "b", "c"))
        assertEquals("a,b,c", joined)
        assertEquals(listOf("a", "b", "c"), com.ihy2ln.weaverse.feature.chatting.social.mediaIdsOf(joined))
        assertEquals(listOf("solo"), com.ihy2ln.weaverse.feature.chatting.social.mediaIdsOf("solo"))
        assertEquals(emptyList<String>(), com.ihy2ln.weaverse.feature.chatting.social.mediaIdsOf(null))
        assertEquals(null, com.ihy2ln.weaverse.feature.chatting.social.joinMediaIds(emptyList()))
        assertTrue(com.ihy2ln.weaverse.feature.chatting.media.isGifPath("/x/y/Z.GIF"))
    }

    @Test
    fun `character media tags are pulled out of the text`() {
        val (text, tags) = com.ihy2ln.weaverse.feature.chatting.media.MediaTags.extract(
            "Monday again. [gif: facepalm] and [Meme: this is fine dog] [pic: sunset]",
        )
        assertEquals("Monday again. and", text)
        assertEquals(2, tags.size)
        assertEquals(com.ihy2ln.weaverse.feature.chatting.media.WebSearchKind.Gifs, tags[0].kind)
        assertEquals("facepalm", tags[0].query)
        assertEquals(com.ihy2ln.weaverse.feature.chatting.media.WebSearchKind.Memes, tags[1].kind)
        assertEquals(
            "Brb",
            com.ihy2ln.weaverse.feature.chatting.media.MediaTags.hideWhileStreaming("Brb [gif: running a"),
        )
    }

    @Test
    fun `social openness follows the app age rating`() {
        val policy = com.ihy2ln.weaverse.feature.chatting.media.SocialContentPolicy
        val before = com.ihy2ln.weaverse.ai.prompt.PromptAddOns.ageRating
        try {
            com.ihy2ln.weaverse.ai.prompt.PromptAddOns.ageRating = com.ihy2ln.weaverse.ai.prompt.PromptAgeRating.Pg13
            assertFalse(policy.open)
            assertFalse(policy.explicit)
            com.ihy2ln.weaverse.ai.prompt.PromptAddOns.ageRating = com.ihy2ln.weaverse.ai.prompt.PromptAgeRating.R
            assertTrue(policy.open)
            assertFalse(policy.explicit)
            com.ihy2ln.weaverse.ai.prompt.PromptAddOns.ageRating = com.ihy2ln.weaverse.ai.prompt.PromptAgeRating.X
            assertTrue(policy.explicit)
            // The legal limits are always stated, however open the rating.
            assertTrue(policy.prompt().contains("under 18"))
        } finally {
            com.ihy2ln.weaverse.ai.prompt.PromptAddOns.ageRating = before
        }
    }

    @Test
    fun `labels and blocks are read from a character's text`() {
        val tags = com.ihy2ln.weaverse.feature.chatting.social.SocialTags
        val parsed = tags.parse("Vote them all out. [cw: politics, offensive] [block]")
        assertEquals("Vote them all out.", parsed.text)
        assertTrue(parsed.blocksWriter)
        assertEquals(
            setOf(
                com.ihy2ln.weaverse.feature.chatting.social.ContentLabel.Politics,
                com.ihy2ln.weaverse.feature.chatting.social.ContentLabel.Offensive,
            ),
            parsed.labels,
        )
        assertFalse(tags.parse("Just a normal day").blocksWriter)
    }

    @Test
    fun `filters hide blocked, muted, labelled and muted-word posts only`() {
        val safety = com.ihy2ln.weaverse.feature.chatting.social.SocialSafety(
            blocked = setOf("b"),
            muted = setOf("m"),
            blockedBy = setOf("x"),
            hiddenPosts = setOf("p9"),
            mutedWords = setOf("Spoiler"),
            hiddenLabels = setOf(ContentLabel.Politics),
        )
        assertTrue(safety.hides("b", "p1", "hi", emptySet()))
        assertTrue(safety.hides("m", "p1", "hi", emptySet()))
        assertTrue(safety.hides("x", "p1", "hi", emptySet()))
        assertTrue(safety.hides("a", "p9", "hi", emptySet()))
        assertTrue(safety.hides("a", "p1", "big SPOILER ahead", emptySet()))
        assertTrue(safety.hides("a", "p1", "hi", setOf(ContentLabel.Politics)))
        assertFalse(safety.hides("a", "p1", "hi", setOf(ContentLabel.Sexual)))
        assertTrue(safety.cantInteract("x"))
        assertFalse(safety.cantInteract("m"))
        // Nothing is filtered by default.
        assertFalse(com.ihy2ln.weaverse.feature.chatting.social.SocialSafety().hides("a", "p", "anything", ContentLabel.entries.toSet()))
    }

    @Test
    fun `unknown age rating falls back to X`() {
        assertEquals(com.ihy2ln.weaverse.ai.prompt.PromptAgeRating.X, com.ihy2ln.weaverse.ai.prompt.PromptAgeRating.fromId("nonsense"))
    }
}
