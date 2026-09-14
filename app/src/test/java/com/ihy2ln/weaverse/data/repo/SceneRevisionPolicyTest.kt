package com.ihy2ln.weaverse.data.repo

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneRevisionPolicyTest {
    @Test
    fun firstSnapshotIsAlwaysDue() {
        assertTrue(SceneRevisionRepository.isDue(null, null, "{}", now = 1_000L))
    }

    @Test
    fun withinHourIsNotDueEvenIfContentChanged() {
        assertFalse(
            SceneRevisionRepository.isDue(
                latestCreatedAt = 1_000L,
                latestDocJson = "old",
                currentDocJson = "new",
                now = 1_000L + SceneRevisionRepository.HOUR_MS - 1,
            ),
        )
    }

    @Test
    fun afterHourUnchangedContentIsNotDue() {
        assertFalse(
            SceneRevisionRepository.isDue(
                latestCreatedAt = 1_000L,
                latestDocJson = "same",
                currentDocJson = "same",
                now = 1_000L + SceneRevisionRepository.HOUR_MS + 1,
            ),
        )
    }

    @Test
    fun afterHourChangedContentIsDue() {
        assertTrue(
            SceneRevisionRepository.isDue(
                latestCreatedAt = 1_000L,
                latestDocJson = "old",
                currentDocJson = "new",
                now = 1_000L + SceneRevisionRepository.HOUR_MS + 1,
            ),
        )
    }
}
