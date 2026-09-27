package com.ihy2ln.weaverse.feature.chatting.social

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SocialRepeatGuardTest {
    @Test fun exactAndRephrasedPostAreCaught() {
        val old = "Found the same stray dog outside the garage again. Gave her water and she stayed."
        assertNotNull(SocialRepeatGuard.repeated("$old [pic: stray dog]", listOf(old)))
        assertNotNull(SocialRepeatGuard.repeated("That stray dog outside the garage stayed after I gave her water!", listOf(old)))
    }

    @Test fun favoriteTopicCanHaveNewEvent() {
        val old = "Finally got my new graphics card running. The frame rate in Starfield doubled."
        assertNull(SocialRepeatGuard.repeated("Sold the old graphics card today and used the cash for a racing wheel.", listOf(old)))
    }

    @Test fun repeatedReplyIsCaughtWithinThread() {
        val old = "I already said the garage is closed until Friday. Please stop asking about it."
        assertNotNull(SocialRepeatGuard.repeated("The garage is closed until Friday. Please stop asking.", listOf(old), reply = true))
    }
}
