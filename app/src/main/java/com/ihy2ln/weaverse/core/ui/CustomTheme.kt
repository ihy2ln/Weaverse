package com.ihy2ln.weaverse.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The seed + per-property overrides behind [AppTheme.Custom] (Revision 02
 * §4): "the chosen seed generates a full Material 3 scheme... separate
 * wheels for accent/primary, app background, panel background, manuscript
 * page background, and default body text colour." Each override is nullable
 * — null means "derived from the seed," matching the spec's "reset to theme
 * default" per wheel. [baseIsDark] picks which derivation direction
 * (lighten-for-containers vs darken-for-containers) the seed and any unset
 * overrides fall back to.
 */
@Serializable
data class CustomThemeSettings(
    val seedHex: String = "#B9740A",
    val baseIsDark: Boolean = false,
    val backgroundHex: String? = null,
    val panelHex: String? = null,
    val pageHex: String? = null,
    val bodyTextHex: String? = null,
)

/** Derives a full [ColorScheme] from [settings] — a hand-rolled tonal derivation (lighten/darken/
 * desaturate the seed for containers, etc.) rather than Material You's `dynamicColorScheme`
 * (which needs an Android 12+ system wallpaper, not a user-chosen seed) or a third-party seed-to-
 * scheme library (no such dependency exists in this project yet). Approximate, not a full HCT/
 * CAM16 tonal-palette implementation like Material's own tools use, but every slot a real
 * `ColorScheme` needs is filled in and stays roughly on-contrast. */
fun customColorScheme(settings: CustomThemeSettings): ColorScheme {
    val seed = parseHex(settings.seedHex) ?: Color(0xFFB9740A)
    val background = settings.backgroundHex?.let { parseHex(it) } ?: (if (settings.baseIsDark) seed.darken(0.9f) else seed.lighten(0.97f))
    val panel = settings.panelHex?.let { parseHex(it) } ?: background
    val bodyText = settings.bodyTextHex?.let { parseHex(it) }
        ?: (if (settings.baseIsDark) background.lighten(0.9f) else background.darken(0.85f))

    val onSeed = if (seed.relativeLuminance() > 0.4f) Color.Black else Color.White
    val primaryContainer = if (settings.baseIsDark) seed.darken(0.6f) else seed.lighten(0.85f)
    val onPrimaryContainer = if (settings.baseIsDark) seed.lighten(0.9f) else seed.darken(0.75f)
    val secondary = seed.rotateHue(40f)
    val secondaryContainer = if (settings.baseIsDark) secondary.darken(0.6f) else secondary.lighten(0.85f)
    val tertiary = seed.rotateHue(-40f)
    val tertiaryContainer = if (settings.baseIsDark) tertiary.darken(0.6f) else tertiary.lighten(0.85f)

    return if (settings.baseIsDark) {
        darkColorScheme(
            primary = seed, onPrimary = onSeed, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSeed, secondaryContainer = secondaryContainer, onSecondaryContainer = bodyText,
            tertiary = tertiary, onTertiary = onSeed, tertiaryContainer = tertiaryContainer, onTertiaryContainer = bodyText,
            background = background, onBackground = bodyText,
            surface = background, onSurface = bodyText,
            surfaceVariant = panel, onSurfaceVariant = bodyText,
            outline = bodyText.copy(alpha = 0.6f),
            outlineVariant = bodyText.copy(alpha = 0.3f),
        )
    } else {
        lightColorScheme(
            primary = seed, onPrimary = onSeed, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSeed, secondaryContainer = secondaryContainer, onSecondaryContainer = bodyText,
            tertiary = tertiary, onTertiary = onSeed, tertiaryContainer = tertiaryContainer, onTertiaryContainer = bodyText,
            background = background, onBackground = bodyText,
            surface = background, onSurface = bodyText,
            surfaceVariant = panel, onSurfaceVariant = bodyText,
            outline = bodyText.copy(alpha = 0.6f),
            outlineVariant = bodyText.copy(alpha = 0.3f),
        )
    }
}

private fun Color.relativeLuminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** WCAG 2.0 contrast ratio between two colors, from 1 (no contrast) to 21 (black on white). */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.relativeLuminance() + 0.05f
    val l2 = b.relativeLuminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}

/** WCAG AA for normal-size text requires a 4.5:1 contrast ratio. */
fun meetsWcagAA(foreground: Color, background: Color): Boolean = contrastRatio(foreground, background) >= 4.5f

/** A same-hue, higher-contrast alternative to [foreground] against [background] — nudges
 * lightness toward black or white (whichever the background is farther from) until the pair
 * clears WCAG AA, or gives up after a reasonable number of steps rather than looping forever on
 * a background color contrast can't be won against (e.g. mid-gray). */
fun suggestAccessibleColor(foreground: Color, background: Color): Color {
    if (meetsWcagAA(foreground, background)) return foreground
    val towardBlack = background.relativeLuminance() > 0.4f
    var candidate = foreground
    repeat(20) {
        candidate = if (towardBlack) candidate.darken(0.9f) else candidate.lighten(0.9f)
        if (meetsWcagAA(candidate, background)) return candidate
    }
    return candidate
}

private fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)

private fun Color.rotateHue(degrees: Float): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return this
    val hue = when {
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    val newHue = (hue + degrees + 360f) % 360f
    return Color.hsv(newHue, saturation, max, alpha)
}
