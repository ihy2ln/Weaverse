package com.ihy2ln.weaverse.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ihy2ln.weaverse.data.db.entity.SceneStatus

/** The reading/writing themes from the Format menu (spec §11), plus [Custom] (Revision 02 §4:
 * "Theme mode: Light · Sepia · Dark · OLED Black · Custom") — its actual colors live in
 * [CustomThemeSettings]/[customColorScheme], not a fixed [ColorScheme] constant like the other four. */
enum class AppTheme(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Sepia("Sepia"),
    OledBlack("OLED Black"),
    Custom("Custom"),
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB9740A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE3B8),
    onPrimaryContainer = Color(0xFF4A2E00),
    secondary = Color(0xFF6F5C9E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DFF6),
    onSecondaryContainer = Color(0xFF251C40),
    tertiary = Color(0xFF2E7D77),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF0EC),
    onTertiaryContainer = Color(0xFF00201D),
    error = Color(0xFFC4574B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDAD6),
    onErrorContainer = Color(0xFF410E08),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF211D16),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF211D16),
    surfaceVariant = Color(0xFFF0E6D6),
    onSurfaceVariant = Color(0xFF4F473A),
    outline = Color(0xFF81786A),
    outlineVariant = Color(0xFFD3C7B4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF0B65C),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF663F00),
    onPrimaryContainer = Color(0xFFFFDDAC),
    secondary = Color(0xFFCBBEEA),
    onSecondary = Color(0xFF392A5C),
    secondaryContainer = Color(0xFF514074),
    onSecondaryContainer = Color(0xFFE7DFF6),
    tertiary = Color(0xFF8DD4CD),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFB0F1EA),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690600),
    errorContainer = Color(0xFF93342A),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEAE1D4),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEAE1D4),
    surfaceVariant = Color(0xFF4F473A),
    onSurfaceVariant = Color(0xFFD3C7B4),
    outline = Color(0xFF9C9284),
    outlineVariant = Color(0xFF4F473A),
)

private val SepiaScheme = lightColorScheme(
    primary = Color(0xFF8A5A24),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAD3A6),
    onPrimaryContainer = Color(0xFF2E1D00),
    secondary = Color(0xFF6F5C9E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DFF6),
    onSecondaryContainer = Color(0xFF251C40),
    tertiary = Color(0xFF2E7D77),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF0EC),
    onTertiaryContainer = Color(0xFF00201D),
    error = Color(0xFF8B3A2E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF3D3CB),
    onErrorContainer = Color(0xFF390D06),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF4A3B27),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF4A3B27),
    surfaceVariant = Color(0xFFE6D9BC),
    onSurfaceVariant = Color(0xFF5C4B34),
    outline = Color(0xFF8A7A5C),
    outlineVariant = Color(0xFFD8C79E),
)

private val OledBlackScheme = darkColorScheme(
    primary = Color(0xFFF0B65C),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF3A2400),
    onPrimaryContainer = Color(0xFFFFDDAC),
    secondary = Color(0xFFCBBEEA),
    onSecondary = Color(0xFF392A5C),
    secondaryContainer = Color(0xFF3A2D5C),
    onSecondaryContainer = Color(0xFFE7DFF6),
    tertiary = Color(0xFF8DD4CD),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00302C),
    onTertiaryContainer = Color(0xFFB0F1EA),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690600),
    errorContainer = Color(0xFF73241C),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEAE1D4),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAE1D4),
    surfaceVariant = Color(0xFF141310),
    onSurfaceVariant = Color(0xFFC7BDAE),
    outline = Color(0xFF8A8072),
    outlineVariant = Color(0xFF3A362C),
)

/** [customSettings] is only consulted for [AppTheme.Custom]; every other theme ignores it, so
 * callers that don't have a loaded [CustomThemeSettings] yet (or never will, e.g. code that only
 * ever runs for a fixed theme) can pass null safely. */
fun colorSchemeFor(theme: AppTheme, customSettings: CustomThemeSettings? = null): ColorScheme = when (theme) {
    AppTheme.Light -> LightScheme
    AppTheme.Dark -> DarkScheme
    AppTheme.Sepia -> SepiaScheme
    AppTheme.OledBlack -> OledBlackScheme
    AppTheme.Custom -> customColorScheme(customSettings ?: CustomThemeSettings())
}

/** True for dark-background themes, used to pick light-vs-dark status bar icons. [Custom] defers
 * to its own [CustomThemeSettings.baseIsDark] rather than a fixed answer. */
fun AppTheme.isDark(customSettings: CustomThemeSettings? = null): Boolean = when (this) {
    AppTheme.Dark, AppTheme.OledBlack -> true
    AppTheme.Custom -> customSettings?.baseIsDark ?: false
    else -> false
}

/**
 * Fixed default colors per codex category (spec §11, extended to the full
 * 10-category built-in set by Revision 02 §2 — the first 6 keep their
 * original spec §11 colors unchanged). These are the starting color for a
 * newly created category; both categories and individual entries can
 * override their own color afterward. [icon] is the string stored in
 * `CodexCategoryEntity.icon`, resolved to a real glyph by
 * `feature.novel.codex.iconFor`.
 */
enum class CodexCategoryKind(val label: String, val defaultColor: Color, val icon: String) {
    Characters("Characters", Color(0xFF4A90D9), "person"),
    Locations("Locations", Color(0xFF3FA66A), "place"),
    ObjectsItems("Objects/Items", Color(0xFF8B6FD1), "inventory"),
    Lore("Lore", Color(0xFFD98A3F), "menu_book"),
    Factions("Factions", Color(0xFFC4574B), "flag"),
    Subplots("Subplots", Color(0xFF3FA9A0), "call_split"),
    MagicTechSystems("Magic/Tech Systems", Color(0xFF7E6BD1), "bolt"),
    EventsTimeline("Events/Timeline", Color(0xFFC98BB0), "event"),
    Organizations("Organizations", Color(0xFF5B8DA6), "domain"),
    Notes("Notes", Color(0xFF8A8A8A), "note"),
}

/** Left-edge stripe color/label for scene cards, keyed by `scenes.status`. */
val SceneStatus.color: Color
    get() = when (this) {
        SceneStatus.Draft -> Color(0xFF9E958A)
        SceneStatus.Revised -> Color(0xFFE8A33D)
        SceneStatus.Final -> Color(0xFF3FA66A)
    }

val SceneStatus.label: String
    get() = name

/**
 * Curated palette offered by [com.ihy2ln.weaverse.core.ui.ColorPickerDialog]
 * for labels, per-entry/category color overrides, and roleplay character
 * accents, in addition to free-form hex entry.
 */
val CuratedColorPalette: List<Color> = listOf(
    Color(0xFFC4574B), Color(0xFFD9784A), Color(0xFFD98A3F), Color(0xFFE0B23C),
    Color(0xFFA8B23C), Color(0xFF3FA66A), Color(0xFF3FA9A0), Color(0xFF4AA0D9),
    Color(0xFF4A90D9), Color(0xFF6F7ED9), Color(0xFF8B6FD1), Color(0xFFB16FD1),
    Color(0xFFD16FA8), Color(0xFF9E958A), Color(0xFF6B6259), Color(0xFF4A4038),
)
