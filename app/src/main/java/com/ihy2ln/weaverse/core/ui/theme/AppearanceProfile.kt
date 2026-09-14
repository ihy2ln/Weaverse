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
    Noir("Noir", "Black and white crime drama — stark and cinematic."),
    Storybook("Storybook", "Warm cream pages with forest-green ink."),
    Cyberdeck("Cyberdeck", "Gunmetal terminal with cyan readouts."),
    Meadow("Meadow", "Sunlit greens and buttercream — friendly and roomy."),
    Ember("Ember", "Charcoal panels lit by forge-orange glow."),
    DeepSea("Deep Sea", "Abyssal navy lit by bioluminescent teal."),
    Sakura("Sakura", "Petal pink on clean white — soft spring serif."),
    Sunset("Sunset", "Amber skies fading into violet dusk."),
    Frost("Frost", "Glacial blues on clean ice."),
    Royal("Royal", "Deep purple halls with gold trim."),
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
        Noir -> if (mode.isDark) NoirDark else NoirLight
        Storybook -> if (mode.isDark) StorybookDark else StorybookLight
        Cyberdeck -> if (mode.isDark) CyberdeckDark else CyberdeckLight
        Meadow -> if (mode.isDark) MeadowDark else MeadowLight
        Ember -> if (mode.isDark) EmberDark else EmberLight
        DeepSea -> if (mode.isDark) DeepSeaDark else DeepSeaLight
        Sakura -> if (mode.isDark) SakuraDark else SakuraLight
        Sunset -> if (mode.isDark) SunsetDark else SunsetLight
        Frost -> if (mode.isDark) FrostDark else FrostLight
        Royal -> if (mode.isDark) RoyalDark else RoyalLight
    }

    val typography: Typography get() = when (this) {
        Classic -> WeaverseTypography
        Fantasy -> FantasyTypography
        Arcade -> ArcadeTypography
        Synthwave -> SynthwaveTypography
        Chill -> ChillTypography
        Tabletop -> TabletopTypography
        Noir -> NoirTypography
        Storybook -> StorybookTypography
        Cyberdeck -> CyberdeckTypography
        Meadow -> MeadowTypography
        Ember -> EmberTypography
        DeepSea -> DeepSeaTypography
        Sakura -> SakuraTypography
        Sunset -> SunsetTypography
        Frost -> FrostTypography
        Royal -> RoyalTypography
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
        Noir -> 2.dp
        Storybook -> 6.dp
        Cyberdeck -> 2.dp
        Meadow -> 18.dp
        Ember -> 8.dp
        DeepSea -> 10.dp
        Sakura -> 16.dp
        Sunset -> 12.dp
        Frost -> 14.dp
        Royal -> 6.dp
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

private val NoirLight = InkThemeTokens(
    background = Color(0xFFE8E8E8),
    panel = Color(0xFFF4F4F4),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFF1A1A1A),
    hover = Color(0xFFD8D8D8),
    primaryText = Color(0xFF111111),
    secondaryText = Color(0xFF555555),
    activePill = Color(0xFF111111),
    activePillLabel = Color(0xFFFFFFFF),
)

private val NoirDark = InkThemeTokens(
    background = Color(0xFF0A0A0A),
    panel = Color(0xFF141414),
    page = Color(0xFF101010),
    hairline = Color(0xFF333333),
    hover = Color(0xFF1E1E1E),
    primaryText = Color(0xFFEDEDED),
    secondaryText = Color(0xFF9A9A9A),
    activePill = Color(0xFFEDEDED),
    activePillLabel = Color(0xFF0A0A0A),
)

private val StorybookLight = InkThemeTokens(
    background = Color(0xFFF3EAD7),
    panel = Color(0xFFFAF3E3),
    page = Color(0xFFFFFBF0),
    hairline = Color(0xFF4F6B4A),
    hover = Color(0xFFE7DCC2),
    primaryText = Color(0xFF2E3B2C),
    secondaryText = Color(0xFF6B7A64),
    activePill = Color(0xFF4F7A4E),
    activePillLabel = Color(0xFFFFFFFF),
)

private val StorybookDark = InkThemeTokens(
    background = Color(0xFF1A241C),
    panel = Color(0xFF243328),
    page = Color(0xFF202E24),
    hairline = Color(0xFF4E6B52),
    hover = Color(0xFF2C3E30),
    primaryText = Color(0xFFE8F0E4),
    secondaryText = Color(0xFFA3B89F),
    activePill = Color(0xFF8FBF7F),
    activePillLabel = Color(0xFF1A241C),
)

private val CyberdeckLight = InkThemeTokens(
    background = Color(0xFFC9D2D6),
    panel = Color(0xFFDEE6EA),
    page = Color(0xFFEEF4F7),
    hairline = Color(0xFF0E3A46),
    hover = Color(0xFFB7C6CC),
    primaryText = Color(0xFF0B1F26),
    secondaryText = Color(0xFF2E5A66),
    activePill = Color(0xFF0891B2),
    activePillLabel = Color(0xFFFFFFFF),
)

private val CyberdeckDark = InkThemeTokens(
    background = Color(0xFF0A1418),
    panel = Color(0xFF102025),
    page = Color(0xFF0D1B20),
    hairline = Color(0xFF22D3EE),
    hover = Color(0xFF16323A),
    primaryText = Color(0xFFD9FBFF),
    secondaryText = Color(0xFF67E8F9),
    activePill = Color(0xFF22D3EE),
    activePillLabel = Color(0xFF06222A),
)

private val MeadowLight = InkThemeTokens(
    background = Color(0xFFEAF4DC),
    panel = Color(0xFFF5FBEA),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFA3B86C),
    hover = Color(0xFFDCEBBF),
    primaryText = Color(0xFF2C3B1E),
    secondaryText = Color(0xFF6B7A50),
    activePill = Color(0xFF76A83B),
    activePillLabel = Color(0xFFFFFFFF),
)

private val MeadowDark = InkThemeTokens(
    background = Color(0xFF17200F),
    panel = Color(0xFF212D17),
    page = Color(0xFF1C2713),
    hairline = Color(0xFF5C7A3A),
    hover = Color(0xFF2A3A1D),
    primaryText = Color(0xFFEAF4DC),
    secondaryText = Color(0xFFA8C285),
    activePill = Color(0xFF97C255),
    activePillLabel = Color(0xFF17200F),
)

private val EmberLight = InkThemeTokens(
    background = Color(0xFFEFE4DA),
    panel = Color(0xFFF8EFE7),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFC2410C),
    hover = Color(0xFFE8D5C4),
    primaryText = Color(0xFF291A10),
    secondaryText = Color(0xFF7A5C42),
    activePill = Color(0xFFEA580C),
    activePillLabel = Color(0xFFFFFFFF),
)

private val EmberDark = InkThemeTokens(
    background = Color(0xFF140D08),
    panel = Color(0xFF221610),
    page = Color(0xFF1B120C),
    hairline = Color(0xFF9A3412),
    hover = Color(0xFF2E1D12),
    primaryText = Color(0xFFF5E4D5),
    secondaryText = Color(0xFFC88A5E),
    activePill = Color(0xFFF97316),
    activePillLabel = Color(0xFF1A0E06),
)

private val DeepSeaLight = InkThemeTokens(
    background = Color(0xFFDCE8EE),
    panel = Color(0xFFEAF2F6),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFF155E75),
    hover = Color(0xFFC4D8E2),
    primaryText = Color(0xFF0B1F2A),
    secondaryText = Color(0xFF33647A),
    activePill = Color(0xFF0E7490),
    activePillLabel = Color(0xFFFFFFFF),
)

private val DeepSeaDark = InkThemeTokens(
    background = Color(0xFF050D14),
    panel = Color(0xFF0B1822),
    page = Color(0xFF081420),
    hairline = Color(0xFF155E75),
    hover = Color(0xFF12242F),
    primaryText = Color(0xFFDCF2F8),
    secondaryText = Color(0xFF7FB6C9),
    activePill = Color(0xFF06B6D4),
    activePillLabel = Color(0xFF04222B),
)

private val SakuraLight = InkThemeTokens(
    background = Color(0xFFFBE9ED),
    panel = Color(0xFFFDF4F6),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFD98BA0),
    hover = Color(0xFFF5D9E0),
    primaryText = Color(0xFF3B1723),
    secondaryText = Color(0xFF99647A),
    activePill = Color(0xFFC2527A),
    activePillLabel = Color(0xFFFFFFFF),
)

private val SakuraDark = InkThemeTokens(
    background = Color(0xFF200D13),
    panel = Color(0xFF2E151D),
    page = Color(0xFF271119),
    hairline = Color(0xFF7A3A4E),
    hover = Color(0xFF3A1D26),
    primaryText = Color(0xFFF9E4EA),
    secondaryText = Color(0xFFC98BA0),
    activePill = Color(0xFFE16E93),
    activePillLabel = Color(0xFF200D13),
)

private val SunsetLight = InkThemeTokens(
    background = Color(0xFFFBEADB),
    panel = Color(0xFFFDF3E7),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFFC2703D),
    hover = Color(0xFFF4DFC8),
    primaryText = Color(0xFF331B0E),
    secondaryText = Color(0xFF8A5C3A),
    activePill = Color(0xFFD97706),
    activePillLabel = Color(0xFFFFFFFF),
)

private val SunsetDark = InkThemeTokens(
    background = Color(0xFF170D1E),
    panel = Color(0xFF251230),
    page = Color(0xFF1E0F27),
    hairline = Color(0xFF6D28D9),
    hover = Color(0xFF2E1A3A),
    primaryText = Color(0xFFFCEADD),
    secondaryText = Color(0xFFC99BB4),
    activePill = Color(0xFFF59E0B),
    activePillLabel = Color(0xFF1F1104),
)

private val FrostLight = InkThemeTokens(
    background = Color(0xFFE3EEF9),
    panel = Color(0xFFF1F7FD),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFF93B8D4),
    hover = Color(0xFFD3E4F2),
    primaryText = Color(0xFF12283C),
    secondaryText = Color(0xFF4A6E8C),
    activePill = Color(0xFF2F7BBF),
    activePillLabel = Color(0xFFFFFFFF),
)

private val FrostDark = InkThemeTokens(
    background = Color(0xFF0A1220),
    panel = Color(0xFF122032),
    page = Color(0xFF0E1A2A),
    hairline = Color(0xFF2E5C86),
    hover = Color(0xFF1A2C42),
    primaryText = Color(0xFFE2EFF9),
    secondaryText = Color(0xFF8FB4D1),
    activePill = Color(0xFF4FA8E0),
    activePillLabel = Color(0xFF08131F),
)

private val RoyalLight = InkThemeTokens(
    background = Color(0xFFEAE4F4),
    panel = Color(0xFFF5F1FB),
    page = Color(0xFFFFFFFF),
    hairline = Color(0xFF6D5BA6),
    hover = Color(0xFFDDD4EC),
    primaryText = Color(0xFF241A3D),
    secondaryText = Color(0xFF5E5480),
    activePill = Color(0xFF5B3FA8),
    activePillLabel = Color(0xFFFFFFFF),
)

private val RoyalDark = InkThemeTokens(
    background = Color(0xFF120C1E),
    panel = Color(0xFF1C1430),
    page = Color(0xFF171126),
    hairline = Color(0xFF4C3A7A),
    hover = Color(0xFF241A3D),
    primaryText = Color(0xFFEDE7F8),
    secondaryText = Color(0xFFA794D1),
    activePill = Color(0xFF8B5CF6),
    activePillLabel = Color(0xFF140D24),
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

private val NoirTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Bold,
    headingSpacing = 0.5.sp,
    bodyLineHeight = 26.sp,
)

private val StorybookTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.Serif,
    headingWeight = FontWeight.Bold,
    bodyLineHeight = 28.sp,
)

private val CyberdeckTypography = typographyOf(
    display = FontFamily.Monospace,
    body = FontFamily.Monospace,
    headingWeight = FontWeight.Bold,
    headingSpacing = 1.5.sp,
    bodyLineHeight = 24.sp,
    bodySpacing = 0.5.sp,
)

private val MeadowTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.SemiBold,
    bodyLineHeight = 28.sp,
)

private val EmberTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.SemiBold,
    bodyLineHeight = 26.sp,
)

private val DeepSeaTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.SemiBold,
    bodyLineHeight = 26.sp,
)

private val SakuraTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.Serif,
    headingWeight = FontWeight.Normal,
    bodyLineHeight = 28.sp,
)

private val SunsetTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.SemiBold,
    bodyLineHeight = 26.sp,
)

private val FrostTypography = typographyOf(
    display = FontFamily.SansSerif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Medium,
    bodyLineHeight = 26.sp,
)

private val RoyalTypography = typographyOf(
    display = FontFamily.Serif,
    body = FontFamily.SansSerif,
    headingWeight = FontWeight.Bold,
    headingSpacing = 1.sp,
    bodyLineHeight = 26.sp,
)
