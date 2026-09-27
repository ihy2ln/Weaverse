package com.ihy2ln.weaverse.feature.chatting.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialNpcsTest {
    @Test
    fun everyPersonHasAUniqueNpcIdAndHandle() {
        val ids = SocialNpcs.characters.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(SocialNpcs::isNpc))
        val handles = SocialNpcs.characters.map { handleFor(it.name) }
        assertEquals(handles.size, handles.toSet().size)
    }

    @Test
    fun topicsPickInterestedPeopleAndFallBackToEveryone() {
        assertTrue(SocialNpcs.forTopic("pets").all { SocialNpcs.byId(it.id)!!.interests.contains("pets") })
        assertEquals(SocialNpcs.characters.size, SocialNpcs.forTopic("no-such-topic").size)
    }

    @Test
    fun captionsParseBackToTheSharer() {
        val sharers = SocialNpcs.forTopic("games").take(2)
        val raw = sharers.joinToString("\n") { "${it.name}: this is incredible [gif: mind blown]" }
        val lines = parseSocialLines(raw, sharers)
        assertEquals(sharers.map { it.id }, lines.map { it.character?.id })
    }
}
