package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaTranslationPlanTest {

    private fun region(
        id: String,
        x: Float = 0.1f,
        y: Float = 0.1f,
        w: Float = 0.2f,
        h: Float = 0.2f,
        original: String = "助けて",
        translation: String = "Help me!",
        language: String = "ja",
    ) = PanelTextRegion(
        id = id,
        x = x,
        y = y,
        w = w,
        h = h,
        original = original,
        translation = translation,
        sourceLanguage = language,
        cleanupX = x,
        cleanupY = y,
        cleanupW = w,
        cleanupH = h,
    )

    @Test
    fun existingEnglishIsPreservedAndNeverPlannedForCleanup() {
        val japanese = region("t0")
        val englishSign = region(
            id = "t1",
            original = "DANGER",
            translation = "DANGER",
            language = "en",
        )

        val translatable = MangaTranslationPlan.translatable(listOf(japanese, englishSign))
        val preserved = MangaTranslationPlan.preservedEnglish(listOf(japanese, englishSign))

        assertEquals(listOf("t0"), translatable.map { it.id })
        assertEquals(listOf("t1"), preserved.map { it.id })
    }

    @Test
    fun latinOcrWithoutALanguageTagCountsAsExistingEnglish() {
        val untagged = region("t0", original = "CRASH", translation = "CRASH", language = "")

        assertTrue(MangaTranslationPlan.translatable(listOf(untagged)).isEmpty())
        assertEquals(listOf("t0"), MangaTranslationPlan.preservedEnglish(listOf(untagged)).map { it.id })
    }

    @Test
    fun cyrillicAndKoreanSourcesAreTranslated() {
        val cyrillic = region("t0", original = "Привет", language = "")
        val korean = region("t1", original = "괜찮아", language = "ko")

        assertEquals(listOf("t0", "t1"), MangaTranslationPlan.translatable(listOf(cyrillic, korean)).map { it.id })
    }

    @Test
    fun movingTheEnglishDoesNotMoveItsCleanupBounds() {
        val placed = region("t0", x = 0.1f, y = 0.1f, w = 0.2f, h = 0.2f)
            // The user drags the lettering to the other side of the page.
            .copy(x = 0.7f, y = 0.8f, w = 0.25f, h = 0.1f)

        val bounds = MangaTranslationPlan.cleanupBounds(listOf(placed)).single()

        assertEquals(0.1f, bounds.x, 0.0001f)
        assertEquals(0.1f, bounds.y, 0.0001f)
        assertEquals(0.2f, bounds.w, 0.0001f)
        assertEquals(0.2f, bounds.h, 0.0001f)
    }

    @Test
    fun cleanupFallsBackToThePlacementBoxWhenNoSourceBoundsWereRecorded() {
        val legacy = PanelTextRegion(
            x = 0.25f,
            y = 0.35f,
            w = 0.3f,
            h = 0.15f,
            original = "助けて",
            translation = "Help me!",
        )

        val bounds = MangaTranslationPlan.cleanupBounds(listOf(legacy)).single()

        assertEquals(0.25f, bounds.x, 0.0001f)
        assertEquals(0.3f, bounds.w, 0.0001f)
    }

    @Test
    fun residualBoxesWidenTheRegionTheyOverlap() {
        val base = MangaTranslationPlan.cleanupBounds(listOf(region("t0", x = 0.1f, y = 0.1f, w = 0.2f, h = 0.2f)))
        // Vision still sees lettering just below the box the first pass cleaned.
        val residual = listOf(MangaCleanupBounds(0.15f, 0.25f, 0.1f, 0.15f))

        val merged = MangaTranslationPlan.mergeCleanupBounds(base, residual)

        assertEquals(1, merged.size)
        assertEquals(0.1f, merged[0].x, 0.0001f)
        assertEquals(0.1f, merged[0].y, 0.0001f)
        assertEquals(0.4f, merged[0].bottom, 0.0001f)
    }

    @Test
    fun aResidualOverlappingNothingBecomesItsOwnCleanupBox() {
        val base = MangaTranslationPlan.cleanupBounds(listOf(region("t0", x = 0.05f, y = 0.05f, w = 0.1f, h = 0.1f)))
        val residual = listOf(MangaCleanupBounds(0.7f, 0.8f, 0.1f, 0.1f))

        val merged = MangaTranslationPlan.mergeCleanupBounds(base, residual)

        assertEquals(2, merged.size)
        assertEquals(0.7f, merged[1].x, 0.0001f)
    }

    @Test
    fun mergedBoundsStayInsideThePage() {
        val merged = MangaTranslationPlan.mergeCleanupBounds(
            base = listOf(MangaCleanupBounds(0.9f, 0.9f, 0.4f, 0.4f)),
            residual = listOf(MangaCleanupBounds(-0.2f, -0.2f, 0.1f, 0.1f)),
        )

        assertTrue(merged.all { it.x >= 0f && it.y >= 0f && it.right <= 1.0001f && it.bottom <= 1.0001f })
    }

    @Test
    fun reviewDraftKeepsProposedTranslationsAndFlagsUnresolvedRegions() {
        val cleaned = region("t0", x = 0.1f, y = 0.1f, w = 0.2f, h = 0.2f, translation = "Help me!")
        val stillDirty = region("t1", x = 0.5f, y = 0.5f, w = 0.2f, h = 0.2f, translation = "Run!")
        val residual = listOf(region("verify-0", x = 0.55f, y = 0.55f, w = 0.05f, h = 0.05f, translation = ""))

        val review = MangaTranslationPlan.reviewRegions(listOf(cleaned, stillDirty), residual)

        assertEquals(listOf("Help me!", "Run!"), review.take(2).map { it.translation })
        assertFalse(review.first { it.id == "t0" }.reviewRequired)
        assertTrue(review.first { it.id == "t1" }.reviewRequired)
        // The residual matched an existing region, so it is not repeated as a loose box.
        assertEquals(2, review.size)
    }

    @Test
    fun residualLetteringTheOcrPassMissedEntirelyIsAddedToTheReviewDraft() {
        val planned = listOf(region("t0", x = 0.1f, y = 0.1f, w = 0.1f, h = 0.1f))
        val residual = listOf(region("verify-0", x = 0.8f, y = 0.8f, w = 0.1f, h = 0.1f, translation = ""))

        val review = MangaTranslationPlan.reviewRegions(planned, residual)

        assertEquals(2, review.size)
        assertEquals("verify-0", review.last().id)
    }

    @Test
    fun summaryReportsSuccessRetryAndRejectionSeparately() {
        val summary = MangaTranslationPlan.summary(
            scopeLabel = "chapter",
            translatedPanels = 8,
            retriedPanels = 2,
            translatedRegions = 41,
            rejectedPages = 3,
            alreadyEnglish = 1,
        )

        assertTrue(summary.contains("41 region(s) across 8 picture(s)"), summary)
        assertTrue(summary.contains("2 needed a second cleanup pass"), summary)
        assertTrue(summary.contains("1 already in English"), summary)
        assertTrue(summary.contains("3 marked Needs review and left unchanged"), summary)
    }

    @Test
    fun aChapterWhereEveryPageFailedStillReportsTheRejections() {
        val summary = MangaTranslationPlan.summary("chapter", 0, 0, 0, 4, 0)

        assertTrue(summary.contains("4 marked Needs review"), summary)
        assertFalse(summary.contains("Lettered"), summary)
    }

    @Test
    fun aPageWithNoReadableTextSaysTheArtIsUnchanged() {
        val summary = MangaTranslationPlan.summary("page", 0, 0, 0, 0, 0)

        assertTrue(summary.contains("original art is unchanged"), summary)
    }
}
