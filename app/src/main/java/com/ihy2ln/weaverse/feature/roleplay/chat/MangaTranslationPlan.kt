package com.ihy2ln.weaverse.feature.roleplay.chat

/**
 * Normalized 0..1 source-cleanup box. Deliberately free of Android types so the whole
 * translate/verify/retry decision can be unit-tested without a device.
 */
data class MangaCleanupBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    fun clamped(): MangaCleanupBounds {
        val left = x.coerceIn(0f, 1f)
        val top = y.coerceIn(0f, 1f)
        val rightEdge = right.coerceIn(left, 1f)
        val bottomEdge = bottom.coerceIn(top, 1f)
        return MangaCleanupBounds(left, top, rightEdge - left, bottomEdge - top)
    }
}

/** How one page ended up after rendering and Vision verification. */
enum class MangaPageOutcome { Translated, Retried, Rejected }

/**
 * Proposed translations kept for a page that failed verification. The page itself keeps its
 * original media; this is what the editor reopens so the user can finish the job by hand.
 */
data class MangaReviewDraft(
    val pageId: String,
    val blockId: String,
    val regions: List<PanelTextRegion>,
    val reason: String,
)

/**
 * The pure decisions behind one page of manga translation: which lettering is foreign and
 * must be replaced, which is already English and must survive untouched, and how a failed
 * verification pass widens the cleanup for exactly one retry.
 */
object MangaTranslationPlan {

    /** Source lettering that must be removed and replaced with English. */
    fun translatable(regions: List<PanelTextRegion>): List<PanelTextRegion> =
        regions.filter { it.original.isNotBlank() || it.translation.isNotBlank() }
            .filter(PanelAi::needsEnglishTranslation)

    /** Lettering already printed in English. These pixels are never cleaned or re-lettered. */
    fun preservedEnglish(regions: List<PanelTextRegion>): List<PanelTextRegion> =
        regions.filterNot(PanelAi::needsEnglishTranslation)

    /**
     * Fixed source bounds for cleanup. These come from the OCR box, never from where the
     * English replacement happens to sit, so moving the English later cannot move the hole.
     */
    fun cleanupBounds(regions: List<PanelTextRegion>): List<MangaCleanupBounds> =
        regions.map { region ->
            MangaCleanupBounds(
                x = if (region.cleanupX >= 0f) region.cleanupX else region.x,
                y = if (region.cleanupY >= 0f) region.cleanupY else region.y,
                w = if (region.cleanupW > 0f) region.cleanupW else region.w,
                h = if (region.cleanupH > 0f) region.cleanupH else region.h,
            ).clamped()
        }.filter { it.w > 0f && it.h > 0f }

    /**
     * Second attempt only: the verifier's residual boxes unioned into the bounds already
     * cleaned. A residual that overlaps nothing becomes a cleanup box of its own, which is
     * how lettering the first OCR pass missed entirely still gets removed.
     */
    fun mergeCleanupBounds(
        base: List<MangaCleanupBounds>,
        residual: List<MangaCleanupBounds>,
    ): List<MangaCleanupBounds> {
        val merged = base.map { it.clamped() }.toMutableList()
        residual.map { it.clamped() }.forEach { extra ->
            if (extra.w <= 0f || extra.h <= 0f) return@forEach
            val hit = merged.indexOfFirst { overlaps(it, extra) }
            if (hit >= 0) merged[hit] = union(merged[hit], extra) else merged += extra
        }
        return merged
    }

    /**
     * What the page editor reopens after a rejection: every proposed translation, with the
     * boxes the verifier still found source lettering in flagged for review.
     */
    fun reviewRegions(
        planned: List<PanelTextRegion>,
        residual: List<PanelTextRegion>,
    ): List<PanelTextRegion> {
        val residualBounds = cleanupBounds(residual)
        val matched = BooleanArray(residualBounds.size)
        val reviewed = planned.mapIndexed { index, region ->
            val bounds = cleanupBounds(listOf(region)).firstOrNull()
            var unresolved = false
            if (bounds != null) {
                residualBounds.forEachIndexed { residualIndex, other ->
                    if (overlaps(bounds, other)) {
                        matched[residualIndex] = true
                        unresolved = true
                    }
                }
            }
            region.copy(
                id = region.id.ifBlank { "review-$index" },
                reviewRequired = unresolved,
            )
        }
        val leftovers = residual.filterIndexed { index, _ -> !matched.getOrElse(index) { false } }
        return reviewed + leftovers
    }

    /** Successful, retried and rejected pages are reported separately, never rolled together. */
    fun summary(
        scopeLabel: String,
        translatedPanels: Int,
        retriedPanels: Int,
        translatedRegions: Int,
        rejectedPages: Int,
        alreadyEnglish: Int,
    ): String {
        if (translatedPanels == 0 && rejectedPages == 0 && alreadyEnglish == 0) {
            return "No readable text was returned for this $scopeLabel. The original art is unchanged."
        }
        val parts = mutableListOf<String>()
        if (translatedPanels > 0) {
            parts += "Lettered $translatedRegions region(s) across $translatedPanels picture(s)"
        }
        if (retriedPanels > 0) parts += "$retriedPanels needed a second cleanup pass"
        if (alreadyEnglish > 0) parts += "$alreadyEnglish already in English"
        if (rejectedPages > 0) parts += "$rejectedPages marked Needs review and left unchanged"
        return parts.joinToString("; ") + ". Originals were kept."
    }

    /** Touching counts as overlapping: antialiased glyph edges routinely land one pixel out. */
    internal fun overlaps(a: MangaCleanupBounds, b: MangaCleanupBounds): Boolean =
        a.x <= b.right && b.x <= a.right && a.y <= b.bottom && b.y <= a.bottom

    internal fun union(a: MangaCleanupBounds, b: MangaCleanupBounds): MangaCleanupBounds {
        val left = minOf(a.x, b.x)
        val top = minOf(a.y, b.y)
        val right = maxOf(a.right, b.right)
        val bottom = maxOf(a.bottom, b.bottom)
        return MangaCleanupBounds(left, top, right - left, bottom - top).clamped()
    }
}
