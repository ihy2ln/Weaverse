package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A whole visual identity — palette, typography and shape together — rather than
 * just a color swap. [Classic] preserves the app's original look and keeps the
 * four [AppThemeMode]s meaningful; the styled profiles carry their own mood and
 * only distinguish light from dark.
 *
 * Typography deliberately uses the platform's built-in families
 * ([FontFamily.Serif] / [FontFamily.Monospace] / [FontFamily.SansSerif]) so a
 * profile costs no bundled font assets and works with the network off.
 */
enum class AppearanceProfile(
    val label: String,
    val blurb: String,
) {
    Classic("Classic", "The original Weaverse look, with all four themes."),
    Fantasy("Fantasy", "Parchment and ink — scroll-styled serif pages."),
    Arcade("Arcade", "8-bit: hard pixel corners and a monospace HUD."),
    Synthwave("Synthwave", "Neon on deep violet, wide and glowing."),
    Chill("Chill", "Soft, muted and roomy — easy on the eyes."),
    Tabletop("Tabletop", "Rulebook dark — near-black, crimson, serif headings."),
    ;

    /** Styled profiles fix their own mood; only Classic honours all four modes. */
    val usesThemeModes: Boolean get() = this == Classic

    fun tokens(mode: AppThemeMode): InkThemeTokens = when (this) {
        Classic -> tokensFor(mode)
        Fantasy -> if (mode.isDark) FantasyDark else FantasyLight
        Arcade -> if (mode.isDark) ArcadeDark else ArcadeLight
        Synthwave -> if (mode.isDark) SynthwaveDark else SynthwaveLight
        Chill -> if (mode.isDark) ChillDark else ChillLight
        Tabletop -> if (mode.isDark) TabletopDark else TabletopLight
    }

    val typography: Typography get() = when (this) {
        Classic -> WeaverseTypography
        Fantasy -> FantasyTypography
        Arcade -> ArcadeTypography
        Synthwave -> SynthwaveTypography
        Chill -> ChillTypography
        Tabletop -> TabletopTypography
    }

    /** Corner rounding is the cheapest, loudest shape signal: 0dp reads as pixel art. */
    val cornerRadius: androidx.compose.ui.unit.Dp get() = when (this) {
        Classic -> InkSpacing.radiusMd
        Fantasy -> 4.dp
        Arcade -> 0.dp
        Synthwave -> 14.dp
        Chill -> 20.dp
        // Rulebook apps keep corners tight so panels read as printed plates.
        Tabletop -> 3.dp
    }

    val shapes: Shapes get() = Shapes(
        extraSmall = RoundedCornerShape(cornerRadius * 0.5f),
        small = RoundedCornerShape(cornerRadius * 0.75f),
        medium = RoundedCornerShape(cornerRadius),
        large = RoundedCornerShape(cornerRadius * 1.5f),
        extraLarge = RoundedCornerShape(cornerRadius * 2f),
    )
}

/** Styled profiles collapse the four modes down to light vs dark. */
val AppThemeMode.isDark: Boolean
    get() = this == AppThemeMode.Dark || this == AppThemeMode.OledBlack

// ---------------------------------------------------------------------------
// Palettes
// ---------------------------------------------------------------------------

private val FantasyLight = InkThemeTokens(
    background = Color(0xFFEFE3C8),
    panel = Color(0xFFF6EDD8),
    page = Color(0xFFFBF4E3),
    hairline = Color(0xFFBFA77A),
    hover = Color(0xFFE3D3AE),
    primaryText = Color(0xFF3B2A17),
    secondaryText = Color(0xFF7A6244),
    activePill = Color(0xFF8A5A2B),
    activePillLabel = Color(0xFFFBF4E3),
)

private val FantasyDark = InkThemeTokens(
    background = Color(0xFF1E1811),
    panel = Color(0xFF2A2118),
    page = Color(0xFF332921),
    hairline = Color(0xFF5C4A33),
    hover = Color(0xFF3D3125),
    primaryText = Color(0xFFEBDCC0),
    secondaryText = Color(0xFFB09B76),
    activePill = Color(0xFFC08A45),
    activePillLabel = Color(0xFF1E1811),
)

private val ArcadeLight = InkThemeTokens(
    background = Color(0xFFDCE3E8),
    panel = Color(0xFFEDF1F4),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFF2B3A44),
    hover = Color(0xFFC5D2DB),
    primaryText = Color(0xFF11181D),
    secondaryText = Color(0xFF44575F),
    activePill = Color(0xFF1B7F3B),
    activePillLabel = Color(0xFFFFFFFF),
)

private val ArcadeDark = InkThemeTokens(
    background = Color(0xFF0B0F14),
    panel = Color(0xFF131C23),
    page = Color(0xFF0F171D),
    hairline = Color(0xFF31E06B),
    hover = Color(0xFF1C2A33),
    primaryText = Color(0xFFDFF7E6),
    secondaryText = Color(0xFF6FD79A),
    activePill = Color(0xFF31E06B),
    activePillLabel = Color(0xFF07110B),
)

private val SynthwaveLight = InkThemeTokens(
    background = Color(0xFFF3E9FB),
    panel = Color(0xFFFBF4FF),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFD1A6E8),
    hover = Color(0xFFE9D7F7),
    primaryText = Color(0xFF2C0F45),
    secondaryText = Color(0xFF7A4AA0),
    activePill = Color(0xFFD6249F),
    activePillLabel = Color(0xFFFFFFFF),
)

private val SynthwaveDark = InkThemeTokens(
    background = Color(0xFF12082A),
    panel = Color(0xFF1D0F3D),
    page = Color(0xFF180B33),
    hairline = Color(0xFF7A2FA8),
    hover = Color(0xFF2A1552),
    primaryText = Color(0xFFF2E4FF),
    secondaryText = Color(0xFFB98BE0),
    activePill = Color(0xFFFF3CAC),
    activePillLabel = Color(0xFF12082A),
)

private val ChillLight = InkThemeTokens(
    background = Color(0xFFEFF3F1),
    panel = Color(0xFFF8FAF9),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFCBD8D2),
    hover = Color(0xFFE2EAE6),
    primaryText = Color(0xFF2A3A34),
    secondaryText = Color(0xFF6C817A),
    activePill = Color(0xFF6BA292),
    activePillLabel = Color(0xFFFFFFFF),
)

private val ChillDark = InkThemeTokens(
    background = Color(0xFF181D1C),
    panel = Color(0xFF222927),
    page = Color(0xFF1E2523),
    hairline = Color(0xFF3B4744),
    hover = Color(0xFF2A3331),
    primaryText = Color(0xFFE2EAE6),
    secondaryText = Color(0xFF93A8A1),
    activePill = Color(0xFF7FB8A6),
    activePillLabel = Color(0xFF181D1C),
)

private val TabletopLight = InkThemeTokens(
    background = Color(0xFFEDEAE4),
    panel = Color(0xFFF7F5F1),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFB9B2A6),
    hover = Color(0xFFE0DCD3),
    primaryText = Color(0xFF1B1A18),
    secondaryText = Color(0xFF6B665D),
    activePill = Color(0xFF9E2A2B),
    activePillLabel = Color(0xFFFFFFFF),
)

private val TabletopDark = InkThemeTokens(
    background = Color(0xFF0E0E10),
    panel = Color(0xFF17171A),
    page = Color(0xFF121215),
    hairline = Color(0xFF3A3A40),
    hover = Color(0xFF202026),
    primaryText = Color(0xFFE8E6E3),
    secondaryText = Color(0xFF9A9AA2),
    activePill = Color(0xFFC53131),
    activePillLabel = Color(0xFFFFFFFF),
)

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------

private fun typographyOf(
    display: FontFamily,
    body: FontFamily,
    headingWeight: FontWeight = FontWeight.SemiBold,
    headingSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit = 26.sp,
    bodySpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = headingSpacing,
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = headingWeight,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = headingSpacing,
    ),
    titleLarge = TextStyle(
        fontFamily = display,
        fontWeight = headingWeight,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = headingSpacing,
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = headingSpacing,
    ),
    bodyLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = bodyLineHeight,
        letterSpacing = bodySpacing,
    ),
    bodyMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = bodyLineHeight * 0.8f,
        letterSpacing = bodySpacing,
    ),
    labelLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = headingSpacing,
    ),
)

private val FantasyTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.Serif,
    headingWeight = FontWeight.Bold,
    bodyLineHeight = 28.sp,
)

private val ArcadeTypography = typographyOf(
    display = FontFamily.Monospace,
    body = FontFamily.Monospace,
    headingWeight = FontWeight.Bold,
    headingSpacing = 1.5.sp,
    bodyLineHeight = 24.sp,
    bodySpacing = 0.5.sp,
)

private val SynthwaveTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Bold,
    headingSpacing = 2.sp,
    bodyLineHeight = 26.sp,
)

/** Serif names over a sans body with wide small-caps labels, like a rulebook app. */
private val TabletopTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Bold,
    headingSpacing = 1.sp,
    bodyLineHeight = 24.sp,
)

private val ChillTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Normal,
    headingSpacing = 0.3.sp,
    bodyLineHeight = 30.sp,
    bodySpacing = 0.2.sp,
)
