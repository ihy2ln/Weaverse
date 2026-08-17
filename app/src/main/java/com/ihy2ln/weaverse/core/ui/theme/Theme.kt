package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalInkTokens = staticCompositionLocalOf { tokensFor(AppThemeMode.Light) }

@Composable
fun WeaverseTheme(
    themeMode: AppThemeMode = AppThemeMode.Light,
    content: @Composable () -> Unit,
) {
    val tokens = tokensFor(themeMode)
    val colorScheme = when (themeMode) {
        AppThemeMode.Light, AppThemeMode.Sepia -> lightColorScheme(
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
        AppThemeMode.Dark, AppThemeMode.OledBlack -> darkColorScheme(
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

    CompositionLocalProvider(LocalInkTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WeaverseTypography,
            content = content,
        )
    }
}

@Composable
fun inkTokens(): InkThemeTokens = LocalInkTokens.current

fun Color.toHexString(): String = "#%06X".format(0xFFFFFF and this.value.toInt())
