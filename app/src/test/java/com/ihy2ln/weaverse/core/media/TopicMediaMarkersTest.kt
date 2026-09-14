package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopicMediaMarkersTest {
    @Test
    fun parsesAndRemovesPrivateMarker() {
        val parsed = parseTopicMediaReply(
            "A battered helmet rolls into the firelight.\n[[MEDIA|topic=Helmet|kind=image]]",
        )

        assertEquals("A battered helmet rolls into the firelight.", parsed.visibleText)
        assertEquals(listOf(TopicMediaRequest("helmet", "image")), parsed.requests)
    }

    @Test
    fun rejectsTraversalAndUnknownKindCharacters() {
        val parsed = parseTopicMediaReply("Look. [[MEDIA|topic=../Helmet\\secret|kind=audio]]")

        assertEquals(listOf(TopicMediaRequest("helmetsecret", "any")), parsed.requests)
        assertFalse(parsed.visibleText.contains("MEDIA"))
    }

    @Test
    fun capsRequestsAtTwo() {
        val parsed = parseTopicMediaReply(
            "Scene [[MEDIA|topic=helmet|kind=any]] [[MEDIA|topic=forest|kind=video]] " +
                "[[MEDIA|topic=castle|kind=image]]",
        )

        assertEquals(2, parsed.requests.size)
    }

    @Test
    fun fallsBackToClearlyMentionedConfiguredTopic() {
        val parsed = parseTopicMediaReply("She raises the ancient iron helmet from its stand.")

        assertEquals(
            listOf(TopicMediaRequest("Helmet")),
            topicMediaRequestsFor(parsed, listOf("Helmet", "Landscapes")),
        )
    }

    @Test
    fun partialMarkerNeverLeaksWhileStreaming() {
        val visible = topicMediaVisibleText("The valley opens below. [[MEDIA|topic=land")

        assertEquals("The valley opens below.", visible)
        assertTrue(!visible.contains("[["))
    }
}
