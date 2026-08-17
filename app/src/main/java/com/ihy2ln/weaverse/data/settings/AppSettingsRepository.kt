package com.ihy2ln.weaverse.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ihy2ln.weaverse.core.text.DocumentJson
import com.ihy2ln.weaverse.core.ui.AppTheme
import com.ihy2ln.weaverse.core.ui.CustomThemeSettings
import com.ihy2ln.weaverse.core.ui.TypographySettings
import com.ihy2ln.weaverse.core.util.AppMode
import com.ihy2ln.weaverse.data.db.entity.RpDisplayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "weaverse_settings")

/**
 * Shell-level + Appearance settings backed by DataStore (spec §5's
 * mode-switch bits, plus Phase 12's theme/typography — providers and
 * budgets are their own Room-backed screens, not DataStore prefs). Only one
 * [typography] setting exists (not yet split per-mode — spec §11's "roleplay
 * can look different from the manuscript" per-mode override is a documented
 * gap, see BUILD_NOTES "Phase 12 deviations/gaps").
 */
@Singleton
class AppSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val CURRENT_MODE = stringPreferencesKey("current_mode")
        val LAUNCH_MODE = stringPreferencesKey("launch_mode")
        val NOVEL_LAST_ROUTE = stringPreferencesKey("novel_last_route")
        val ROLEPLAY_LAST_ROUTE = stringPreferencesKey("roleplay_last_route")
        val APP_THEME = stringPreferencesKey("app_theme")
        val TYPOGRAPHY_JSON = stringPreferencesKey("typography_json")
        val CUSTOM_THEME_JSON = stringPreferencesKey("custom_theme_json")
        val CURRENT_BOOK_ID = stringPreferencesKey("current_book_id")
        val RAIL_WIDTH_DP = intPreferencesKey("rail_width_dp")
        val RAIL_COLLAPSED = booleanPreferencesKey("rail_collapsed")
        val SLASH_OVERLAY_OPACITY = floatPreferencesKey("slash_overlay_opacity")
        val SHOW_SCENE_BEATS = booleanPreferencesKey("show_scene_beats")
        val DEFAULT_RP_DISPLAY_MODE = stringPreferencesKey("default_rp_display_mode")
    }

    /** The book Plan/Write/Chat/Review/Codex should all operate on — see
     * `data/repo/CurrentBook.kt` for how this combines with the live book list. */
    val currentBookId: Flow<String?> = context.dataStore.data.map { it[Keys.CURRENT_BOOK_ID] }

    suspend fun setCurrentBookId(bookId: String) {
        context.dataStore.edit { it[Keys.CURRENT_BOOK_ID] = bookId }
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.Light
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    /** Only meaningful when [appTheme] is [AppTheme.Custom] — every other theme ignores this. */
    val customThemeSettings: Flow<CustomThemeSettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_THEME_JSON]?.let { runCatching { DocumentJson.decodeFromString<CustomThemeSettings>(it) }.getOrNull() }
            ?: CustomThemeSettings()
    }

    suspend fun setCustomThemeSettings(settings: CustomThemeSettings) {
        context.dataStore.edit { it[Keys.CUSTOM_THEME_JSON] = DocumentJson.encodeToString(settings) }
    }

    val typography: Flow<TypographySettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.TYPOGRAPHY_JSON]?.let { runCatching { DocumentJson.decodeFromString<TypographySettings>(it) }.getOrNull() }
            ?: TypographySettings.Manuscript
    }

    suspend fun setTypography(settings: TypographySettings) {
        context.dataStore.edit { it[Keys.TYPOGRAPHY_JSON] = DocumentJson.encodeToString(settings) }
    }

    val currentMode: Flow<AppMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: launchModeDefault
    }

    val launchMode: Flow<AppMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAUNCH_MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: launchModeDefault
    }

    val novelLastRoute: Flow<String?> = context.dataStore.data.map { it[Keys.NOVEL_LAST_ROUTE] }
    val roleplayLastRoute: Flow<String?> = context.dataStore.data.map { it[Keys.ROLEPLAY_LAST_ROUTE] }

    suspend fun setCurrentMode(mode: AppMode) {
        context.dataStore.edit { it[Keys.CURRENT_MODE] = mode.name }
    }

    suspend fun setLaunchMode(mode: AppMode) {
        context.dataStore.edit { it[Keys.LAUNCH_MODE] = mode.name }
    }

    suspend fun setNovelLastRoute(route: String) {
        context.dataStore.edit { it[Keys.NOVEL_LAST_ROUTE] = route }
    }

    suspend fun setRoleplayLastRoute(route: String) {
        context.dataStore.edit { it[Keys.ROLEPLAY_LAST_ROUTE] = route }
    }

    /** Left rail width in dp, clamped to the spec's 240–420dp drag range (§1.2), persisted
     * across sessions. Default (320) matches the rail's pre-Revision-02 fixed width. */
    val railWidthDp: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.RAIL_WIDTH_DP] ?: RailWidthDefault).coerceIn(RailWidthMin, RailWidthMax)
    }

    suspend fun setRailWidthDp(widthDp: Int) {
        context.dataStore.edit { it[Keys.RAIL_WIDTH_DP] = widthDp.coerceIn(RailWidthMin, RailWidthMax) }
    }

    val railCollapsed: Flow<Boolean> = context.dataStore.data.map { it[Keys.RAIL_COLLAPSED] ?: false }

    suspend fun setRailCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[Keys.RAIL_COLLAPSED] = collapsed }
    }

    /** The `/` AI overlay window's backdrop opacity (spec §6: "60% opacity over the page by
     * default with an opacity slider from 30-100%... persisted"), clamped to that range on read/write. */
    val slashOverlayOpacity: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[Keys.SLASH_OVERLAY_OPACITY] ?: SlashOverlayOpacityDefault).coerceIn(SlashOverlayOpacityMin, SlashOverlayOpacityMax)
    }

    suspend fun setSlashOverlayOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.SLASH_OVERLAY_OPACITY] = opacity.coerceIn(SlashOverlayOpacityMin, SlashOverlayOpacityMax) }
    }

    /** Global "Show scene beats" toggle (spec §6: beat blocks "can be hidden from the reading
     * view with a global... toggle"). */
    val showSceneBeats: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_SCENE_BEATS] ?: true }

    suspend fun setShowSceneBeats(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SCENE_BEATS] = show }
    }

    /** Global default for new chats (spec §9: "a per-chat toggle (and a global default)") — an
     * existing chat's own [com.ihy2ln.weaverse.data.db.entity.RpChatEntity.displayMode] always wins. */
    val defaultRpDisplayMode: Flow<RpDisplayMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_RP_DISPLAY_MODE]?.let { runCatching { RpDisplayMode.valueOf(it) }.getOrNull() } ?: RpDisplayMode.Messenger
    }

    suspend fun setDefaultRpDisplayMode(mode: RpDisplayMode) {
        context.dataStore.edit { it[Keys.DEFAULT_RP_DISPLAY_MODE] = mode.name }
    }

    companion object {
        const val RailWidthMin = 240
        const val RailWidthMax = 420
        const val RailWidthDefault = 320
        const val SlashOverlayOpacityMin = 0.3f
        const val SlashOverlayOpacityMax = 1f
        const val SlashOverlayOpacityDefault = 0.6f
        private val launchModeDefault = AppMode.Novel
    }
}
