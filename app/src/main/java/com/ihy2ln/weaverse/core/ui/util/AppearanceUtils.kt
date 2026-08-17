package com.ihy2ln.weaverse.core.ui.util

import androidx.compose.ui.graphics.Color
import com.ihy2ln.weaverse.data.settings.AppearanceOverrides
import com.ihy2ln.weaverse.data.settings.SectionAppearance

/**
 * Resolve a section color. Opacity always applies — including when [SectionAppearance.colorHex]
 * is blank (theme fallback) — so opacity 0 lets background media show through.
 */
fun resolveSectionColor(
    override: SectionAppearance,
    fallback: Color,
): Color {
    val base = if (override.colorHex.isBlank()) {
        fallback
    } else {
        parseHexColor(override.colorHex, fallback)
    }
    val alpha = override.opacityPercent.coerceIn(0, 100) / 100f
    return base.copy(alpha = alpha)
}

fun AppearanceOverrides.applyToTokens(
    background: Color,
    panel: Color,
    pageFallback: Color,
): Triple<Color, Color, Color> = Triple(
    resolveSectionColor(chrome, background),
    resolveSectionColor(rail, panel),
    resolveSectionColor(page, pageFallback),
)
