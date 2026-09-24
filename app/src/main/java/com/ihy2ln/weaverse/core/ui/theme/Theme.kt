package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified

val LocalInkTokens = staticCompositionLocalOf { StreamingTokens }

/** The active visual identity, for the few places that vary beyond color/type. */
val LocalAppearanceProfile = staticCompositionLocalOf { AppearanceProfile.Streaming }

/**
 * How see-through glass panels are, 0 (solid) to 1 (clear). Panels drawn with
 * `Modifier.glassPanel()` read this so one setting frosts every page at once.
 */
val LocalGlassClarity = staticCompositionLocalOf { 0.3f }

/**
 * The app theme. [textScale] and [lineSpacing] are the Appearance settings'
 * font size and line height: text scale multiplies the system font scale, so it
 * reaches every `sp` in the app (hand-set sizes included), and line spacing
 * multiplies each typography style's line height, expressed in `em` so text that
 * overrides its font size keeps proportional leading.
 */
@Composable
fun WeaverseTheme(
    themeMode: AppThemeMode = AppThemeMode.Dark,
    profile: AppearanceProfile = AppearanceProfile.Streaming,
    textScale: Float = 1f,
    lineSpacing: Float = 1f,
    glassClarity: Float = LocalGlassClarity.current,
    content: @Composable () -> Unit,
) {
    val tokens = profile.tokens(themeMode)
    val dark = profile == AppearanceProfile.Streaming || themeMode.isDark
    val colorScheme = remember(profile, themeMode) {
        if (profile == AppearanceProfile.Streaming) StreamingColors else colorSchemeFrom(tokens, dark)
    }
    val typography = remember(profile, lineSpacing) { profile.typography.withLineSpacing(lineSpacing) }
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, textScale) {
        Density(baseDensity.density, baseDensity.fontScale * textScale.coerceIn(0.7f, 1.6f))
    }

    CompositionLocalProvider(
        LocalContentColor provides tokens.primaryText,
        LocalInkTokens provides tokens,
        LocalAppearanceProfile provides profile,
        LocalGlassClarity provides glassClarity.coerceIn(0f, 1f),
        LocalDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = profile.shapes,
            content = content,
        )
    }
}

/**
 * Every surface tier is derived from the profile tokens so dialogs, menus, sheets,
 * sliders and switches cannot fall back to Material's unrelated default purple.
 */
internal fun colorSchemeFrom(tokens: InkThemeTokens, dark: Boolean): ColorScheme {
    val accentWash = tokens.activePill.copy(alpha = if (dark) 0.22f else 0.14f).compositeOver(tokens.panel)
    val raised = tokens.hover.copy(alpha = 0.5f).compositeOver(tokens.panel)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tokens.activePill, onPrimary = tokens.activePillLabel,
        primaryContainer = accentWash, onPrimaryContainer = tokens.primaryText,
        secondary = tokens.activePill, onSecondary = tokens.activePillLabel,
        secondaryContainer = accentWash, onSecondaryContainer = tokens.primaryText,
        tertiary = tokens.activePill, onTertiary = tokens.activePillLabel,
        tertiaryContainer = accentWash, onTertiaryContainer = tokens.primaryText,
        background = tokens.background, onBackground = tokens.primaryText,
        surface = tokens.panel, onSurface = tokens.primaryText,
        surfaceVariant = tokens.hover, onSurfaceVariant = tokens.secondaryText,
        surfaceTint = Color.Transparent,
        surfaceDim = tokens.background, surfaceBright = tokens.hover,
        surfaceContainerLowest = tokens.background, surfaceContainerLow = tokens.page,
        surfaceContainer = tokens.panel, surfaceContainerHigh = raised,
        surfaceContainerHighest = tokens.hover,
        outline = tokens.hairline, outlineVariant = tokens.hairline.copy(alpha = 0.5f).compositeOver(tokens.panel),
        inverseSurface = tokens.primaryText, inverseOnSurface = tokens.background,
        inversePrimary = tokens.activePill,
    )
}

/** Rewrites each style's line height as a multiple of its own font size, scaled by [spacing]. */
internal fun Typography.withLineSpacing(spacing: Float): Typography {
    fun TextStyle.spaced(): TextStyle {
        if (!fontSize.isSpecified || !lineHeight.isSpecified || !fontSize.isSp || !lineHeight.isSp) return this
        return copy(lineHeight = (lineHeight.value / fontSize.value * spacing).em)
    }
    return Typography(
        displayLarge = displayLarge.spaced(), displayMedium = displayMedium.spaced(), displaySmall = displaySmall.spaced(),
        headlineLarge = headlineLarge.spaced(), headlineMedium = headlineMedium.spaced(), headlineSmall = headlineSmall.spaced(),
        titleLarge = titleLarge.spaced(), titleMedium = titleMedium.spaced(), titleSmall = titleSmall.spaced(),
        bodyLarge = bodyLarge.spaced(), bodyMedium = bodyMedium.spaced(), bodySmall = bodySmall.spaced(),
        labelLarge = labelLarge.spaced(), labelMedium = labelMedium.spaced(), labelSmall = labelSmall.spaced(),
    )
}

/** The active profile, for surfaces that need more than the color/type tokens. */
@Composable
fun appearanceProfile(): AppearanceProfile = LocalAppearanceProfile.current

/**
 * Profile-aware corner radii. Prefer these over the fixed [InkSpacing] values in
 * UI code so a profile's shape language (Arcade's square pixels, Chill's soft
 * corners) reaches hand-rolled `RoundedCornerShape`s, not just Material components.
 */
@Composable
fun inkRadiusSm(): androidx.compose.ui.unit.Dp =
    LocalAppearanceProfile.current.cornerRadius * 0.75f

@Composable
fun inkRadiusMd(): androidx.compose.ui.unit.Dp =
    LocalAppearanceProfile.current.cornerRadius

@Composable
fun inkTokens(): InkThemeTokens = LocalInkTokens.current

fun Color.toHexString(): String = "#%06X".format(0xFFFFFF and this.value.toInt())
