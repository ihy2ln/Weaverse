package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Shared by browsing, workspaces, dialogs and specialist media tools. */
val StreamingTokens = InkThemeTokens(
    background = Color(0xFF0B0B10), panel = Color(0xFF15151E), page = Color(0xFF101017),
    hover = Color(0xFF242330), hairline = Color(0xFF353442),
    primaryText = Color.White, secondaryText = Color(0xFFA5A4B2),
    activePill = Color(0xFFB39AFF), activePillLabel = Color(0xFF0B0B10),
)

// Specify every surface tier so elevated cards, menus and sheets cannot fall back
// to Material's unrelated default palette.
val StreamingColors = darkColorScheme(
    primary = StreamingTokens.activePill, onPrimary = StreamingTokens.activePillLabel,
    primaryContainer = Color(0xFF302641), onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = StreamingTokens.activePill, onSecondary = StreamingTokens.background,
    secondaryContainer = Color(0xFF29223D), onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFFC9B8EF), onTertiary = StreamingTokens.background,
    tertiaryContainer = Color(0xFF342C43), onTertiaryContainer = Color(0xFFE8DEFF),
    background = StreamingTokens.background, onBackground = StreamingTokens.primaryText,
    surface = StreamingTokens.panel, onSurface = StreamingTokens.primaryText,
    surfaceVariant = StreamingTokens.hover, onSurfaceVariant = StreamingTokens.secondaryText,
    surfaceTint = Color.Transparent,
    surfaceDim = StreamingTokens.background, surfaceBright = Color(0xFF302F3D),
    surfaceContainerLowest = StreamingTokens.background,
    surfaceContainerLow = StreamingTokens.page, surfaceContainer = StreamingTokens.panel,
    surfaceContainerHigh = Color(0xFF1E1D29), surfaceContainerHighest = StreamingTokens.hover,
    outline = StreamingTokens.hairline, outlineVariant = Color(0xFF292833),
    inverseSurface = Color(0xFFE7E3EF), inverseOnSurface = StreamingTokens.background,
    inversePrimary = Color(0xFF614593),
)
