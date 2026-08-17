package com.ihy2ln.weaverse.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable

/**
 * The seven font choices from the spec's Format menu (§11). Each currently
 * maps to Android's built-in generic serif/sans-serif system typeface
 * rather than the named Google Font — the build sandbox that authored this
 * design system has no outbound network to fetch the actual (OFL-licensed)
 * font binaries, and hand-transcribing the Downloadable Fonts certificate
 * array from memory was judged too error-prone to risk (a corrupted cert
 * silently degrades every family to its fallback rather than failing loud).
 * See BUILD_NOTES.md "Bundled fonts" for the exact follow-up: drop each
 * family's .ttf files under `res/font/` and change the `family =` line
 * below to `FontFamily(Font(R.font.xxx))` — everything else (the settings
 * model, sliders, presets, per-mode overrides) is already real.
 */
enum class NamedFontFamily(val label: String, val family: FontFamily) {
    Lora("Lora", FontFamily.Serif),
    Literata("Literata", FontFamily.Serif),
    EbGaramond("EB Garamond", FontFamily.Serif),
    Merriweather("Merriweather", FontFamily.Serif),
    Inter("Inter", FontFamily.SansSerif),
    AtkinsonHyperlegible("Atkinson Hyperlegible", FontFamily.SansSerif),
    System("System", FontFamily.Default),
}

enum class ParagraphStyle { Indented, Spaced }

@Serializable
data class TypographySettings(
    val fontFamily: NamedFontFamily = NamedFontFamily.Lora,
    val fontSizeSp: Float = 16f,
    val lineHeightMultiplier: Float = 1.5f,
    val paragraphSpacingSp: Float = 8f,
    val letterSpacingSp: Float = 0f,
    val justified: Boolean = false,
    val paragraphStyle: ParagraphStyle = ParagraphStyle.Indented,
    val maxContentWidthDp: Int = 680,
) {
    companion object {
        val FontSizeRange = 12f..28f
        val LineHeightRange = 1.2f..2.2f

        /** Ships as the default for Write/Read surfaces. */
        val Manuscript = TypographySettings(
            fontFamily = NamedFontFamily.Lora,
            fontSizeSp = 17f,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingSp = 4f,
            paragraphStyle = ParagraphStyle.Indented,
        )

        /** Larger, higher-contrast-friendly, generous line height for low-light reading. */
        val NightReading = TypographySettings(
            fontFamily = NamedFontFamily.Literata,
            fontSizeSp = 19f,
            lineHeightMultiplier = 1.8f,
            paragraphSpacingSp = 10f,
            paragraphStyle = ParagraphStyle.Spaced,
        )

        /** Denser layout for scanning a lot of text on one screen (e.g. Review, Codex). */
        val Compact = TypographySettings(
            fontFamily = NamedFontFamily.Inter,
            fontSizeSp = 14f,
            lineHeightMultiplier = 1.35f,
            paragraphSpacingSp = 4f,
            paragraphStyle = ParagraphStyle.Spaced,
        )

        val NamedPresets = listOf(Manuscript, NightReading, Compact)
    }
}

/**
 * Builds a full Material 3 [Typography] scaled off [settings.fontSizeSp] as
 * the "body" anchor — this is what [WeaverseTheme] feeds into
 * [androidx.compose.material3.MaterialTheme], so every `MaterialTheme.typography.*`
 * reference in the app already reflects the user's Format-menu choices.
 */
fun buildTypography(settings: TypographySettings): Typography {
    val family = settings.fontFamily.family
    val bodySp = settings.fontSizeSp
    val lineHeight: (Float) -> TextUnit = { sizeSp -> (sizeSp * settings.lineHeightMultiplier).sp }
    val letterSpacing = settings.letterSpacingSp.sp

    fun style(sizeSp: Float, weight: FontWeight, tracking: TextUnit = letterSpacing) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = sizeSp.sp,
        lineHeight = lineHeight(sizeSp),
        letterSpacing = tracking,
    )

    return Typography(
        displayLarge = style(bodySp * 3.5f, FontWeight.Normal),
        displayMedium = style(bodySp * 2.8f, FontWeight.Normal),
        displaySmall = style(bodySp * 2.25f, FontWeight.Normal),
        headlineLarge = style(bodySp * 2f, FontWeight.SemiBold),
        headlineMedium = style(bodySp * 1.75f, FontWeight.SemiBold),
        headlineSmall = style(bodySp * 1.5f, FontWeight.SemiBold),
        titleLarge = style(bodySp * 1.375f, FontWeight.SemiBold),
        titleMedium = style(bodySp * 1.125f, FontWeight.Medium),
        titleSmall = style(bodySp * 1f, FontWeight.Medium),
        bodyLarge = style(bodySp * 1.125f, FontWeight.Normal),
        bodyMedium = style(bodySp, FontWeight.Normal),
        bodySmall = style(bodySp * 0.875f, FontWeight.Normal),
        labelLarge = style(bodySp * 0.875f, FontWeight.Medium),
        labelMedium = style(bodySp * 0.75f, FontWeight.Medium),
        labelSmall = style(bodySp * 0.6875f, FontWeight.Medium),
    )
}
