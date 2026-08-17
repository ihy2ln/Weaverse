package com.ihy2ln.weaverse.core.ui.theme

enum class AppThemeMode {
    Light,
    Sepia,
    Dark,
    OledBlack,
}

data class InkThemeTokens(
    val background: androidx.compose.ui.graphics.Color,
    val panel: androidx.compose.ui.graphics.Color,
    val page: androidx.compose.ui.graphics.Color,
    val hairline: androidx.compose.ui.graphics.Color,
    val hover: androidx.compose.ui.graphics.Color,
    val primaryText: androidx.compose.ui.graphics.Color,
    val secondaryText: androidx.compose.ui.graphics.Color,
    val activePill: androidx.compose.ui.graphics.Color,
    val activePillLabel: androidx.compose.ui.graphics.Color,
)

fun tokensFor(mode: AppThemeMode): InkThemeTokens = when (mode) {
    AppThemeMode.Light -> InkThemeTokens(
        background = InkBackground,
        panel = InkPanel,
        page = InkBackground,
        hairline = InkHairline,
        hover = InkHover,
        primaryText = InkPrimaryText,
        secondaryText = InkSecondaryText,
        activePill = InkActivePill,
        activePillLabel = InkActivePillLabel,
    )
    AppThemeMode.Sepia -> InkThemeTokens(
        background = InkSepiaBackground,
        panel = InkSepiaPanel,
        page = InkSepiaPage,
        hairline = InkSepiaHairline,
        hover = InkSepiaHover,
        primaryText = InkSepiaText,
        secondaryText = InkSepiaSecondary,
        activePill = InkSepiaActivePill,
        activePillLabel = InkActivePillLabel,
    )
    AppThemeMode.Dark -> InkThemeTokens(
        background = InkDarkBackground,
        panel = InkDarkPanel,
        page = InkDarkPage,
        hairline = InkDarkHairline,
        hover = InkDarkHover,
        primaryText = InkDarkText,
        secondaryText = InkDarkSecondary,
        activePill = InkDarkActivePill,
        activePillLabel = InkDarkActivePillLabel,
    )
    AppThemeMode.OledBlack -> InkThemeTokens(
        background = InkOledBackground,
        panel = InkOledPanel,
        page = InkOledPage,
        hairline = InkOledHairline,
        hover = InkOledHover,
        primaryText = InkOledText,
        secondaryText = InkOledSecondary,
        activePill = InkOledActivePill,
        activePillLabel = InkOledActivePillLabel,
    )
}
