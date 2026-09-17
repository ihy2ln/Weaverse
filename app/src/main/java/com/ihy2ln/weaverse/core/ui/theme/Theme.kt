package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalInkTokens = staticCompositionLocalOf { StreamingTokens }

/** The active visual identity, for the few places that vary beyond color/type. */
val LocalAppearanceProfile = staticCompositionLocalOf { AppearanceProfile.Streaming }

@Composable
fun WeaverseTheme(
    themeMode: AppThemeMode = AppThemeMode.Dark,
    profile: AppearanceProfile = AppearanceProfile.Streaming,
    content: @Composable () -> Unit,
) {
    val tokens = profile.tokens(themeMode)
    val colorScheme = if (profile == AppearanceProfile.Streaming) StreamingColors else if (!themeMode.isDark) {
        lightColorScheme(
            primary = tokens.activePill,
            onPrimary = tokens.activePillLabel,
            background = tokens.background,
            onBackground = tokens.primaryText,
            surface = tokens.panel,
            onSurface = tokens.primaryText,
            surfaceVariant = tokens.hover,
            onSurfaceVariant = tokens.secondaryText,
            outline = tokens.hairline,
        )
    } else {
        darkColorScheme(
            primary = tokens.activePill,
            onPrimary = tokens.activePillLabel,
            background = tokens.background,
            onBackground = tokens.primaryText,
            surface = tokens.panel,
            onSurface = tokens.primaryText,
            surfaceVariant = tokens.hover,
            onSurfaceVariant = tokens.secondaryText,
            outline = tokens.hairline,
        )
    }

    CompositionLocalProvider(
        LocalContentColor provides tokens.primaryText,
        LocalInkTokens provides tokens,
        LocalAppearanceProfile provides profile,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = profile.typography,
            shapes = profile.shapes,
            content = content,
        )
    }
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
