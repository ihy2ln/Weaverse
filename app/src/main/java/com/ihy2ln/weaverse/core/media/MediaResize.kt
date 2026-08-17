package com.ihy2ln.weaverse.core.media

import kotlin.math.abs

/**
 * `widthPercent` snapping for a selected media block's drag handles (spec
 * §7: "snapping to 25/33/50/66/75/100% with haptic ticks"). Pure math so the
 * snap behavior is unit-testable independent of the actual drag gesture.
 */
object MediaResize {
    val snapPoints = listOf(25f, 33f, 50f, 66f, 75f, 100f)
    private const val SNAP_THRESHOLD = 4f
    const val MIN_WIDTH_PERCENT = 20f
    const val MAX_WIDTH_PERCENT = 100f

    /** Snaps [rawPercent] to the nearest snap point within [SNAP_THRESHOLD], else passes it through clamped. */
    fun resolve(rawPercent: Float): Float {
        val clamped = rawPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
        val nearest = snapPoints.minByOrNull { abs(it - clamped) } ?: return clamped
        return if (abs(nearest - clamped) <= SNAP_THRESHOLD) nearest else clamped
    }

    /** True when [resolve] lands on a different snap point than the previous frame — the haptic trigger. */
    fun crossedSnapPoint(previousResolved: Float, newResolved: Float): Boolean =
        previousResolved != newResolved && newResolved in snapPoints
}
