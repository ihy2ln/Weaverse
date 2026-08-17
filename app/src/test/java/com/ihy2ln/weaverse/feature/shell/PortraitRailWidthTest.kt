package com.ihy2ln.weaverse.feature.shell

import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the portrait rail coerceIn bug: max must be ≥ min or Kotlin throws
 * IllegalArgumentException and the book shell crashes on every portrait open.
 */
class PortraitRailWidthTest {
    @Test
    fun portraitRailRangeIsValid() {
        assertTrue(
            "railPortraitMin must be <= railPortraitMax",
            InkSpacing.railPortraitMin <= InkSpacing.railPortraitMax,
        )
    }

    @Test
    fun landscapeRailRangeIsValid() {
        assertTrue(
            "railMin must be <= railMax",
            InkSpacing.railMin <= InkSpacing.railMax,
        )
    }

    @Test
    fun preferredWidthCoercesInPortraitWithoutThrowing() {
        val preferred = 320f
        val coerced = preferred.coerceIn(
            InkSpacing.railPortraitMin.value,
            InkSpacing.railPortraitMax.value,
        )
        assertTrue(coerced in InkSpacing.railPortraitMin.value..InkSpacing.railPortraitMax.value)
    }
}
