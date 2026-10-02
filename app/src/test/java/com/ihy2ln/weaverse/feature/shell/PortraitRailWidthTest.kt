package com.ihy2ln.weaverse.feature.shell

import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the portrait rail coerceIn bug: max must be ≥ min or Kotlin throws
 * IllegalArgumentException and the book shell crashes on every portrait open.
 */
class PortraitRailWidthTest {
    @Test
    fun portraitRailRangeIsValid() {
        assertTrue(
            InkSpacing.railPortraitMin <= InkSpacing.railPortraitMax,
            "railPortraitMin must be <= railPortraitMax",
        )
    }

    @Test
    fun landscapeRailRangeIsValid() {
        assertTrue(
            InkSpacing.railMin <= InkSpacing.railMax,
            "railMin must be <= railMax",
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
