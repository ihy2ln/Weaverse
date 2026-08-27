package com.ihy2ln.weaverse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ihy2ln.weaverse.ai.openrouter.WritingModelSeeds
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weaverse_settings")

data class SectionAppearance(
    val colorHex: String = "",
    val opacityPercent: Int = 100,
)

data class LayoutPreferences(
    val railWidthDp: Float = 320f,
    val railCollapsed: Boolean = true,
    val destBarCollapsed: Boolean = false,
    val destBarHeightDp: Float = 52f,
)

data class AppearanceOverrides(
    val chrome: SectionAppearance = SectionAppearance(),
    val rail: SectionAppearance = SectionAppearance(),
    val content: SectionAppearance = SectionAppearance(),
    val page: SectionAppearance = SectionAppearance(),
    val chatBubble: SectionAppearance = SectionAppearance(),
)

data class ExtraPromptSurfaces(
    /** Method 1 — empty-paragraph “Start writing / AI · \ manual” placeholder. */
    val inlineWriting: Boolean = false,
    /** Method 2 — in-document SCENE BEAT card. */
    val sceneBeatCard: Boolean = false,
    /** Method 3 — continue-under-last-line box. */
    val continuation: Boolean = false,
    /** Workshop Chat composer row. */
    val chatComposer: Boolean = false,
    /** Roleplay `/ AI` · `\ manual` buttons. */
    val roleplayButtons: Boolean = false,
)

enum class ExtraPromptSurface {
    InlineWriting,
    SceneBeatCard,
    Continuation,
    ChatComposer,
    RoleplayButtons,
}

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.Dark,
    val fontSizeSp: Int = 16,
    val lineHeight: Float = 1.6f,
    val defaultModelRef: String = WritingModelSeeds.DEFAULT_MODEL_REF,
    val launchMode: String = "novel",
    val colorCodingEnabled: Boolean = true,
    val selectedBookId: String = "book-adams-haven-1",
    val backgroundMediaId: String = "",
    val roleplayPresetId: String = "preset-balanced",
    val layout: LayoutPreferences = LayoutPreferences(),
    val appearance: AppearanceOverrides = AppearanceOverrides(),
    /** Overall UI brightness 5–100 (100 = full). Independent of section color pickers. */
    val appBrightnessPercent: Int = 100,
    val syncWebUrl: String = "",
    val syncPassword: String = "",
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    /**
     * Extra generators besides the compact PROMPT box. Each flag is independent;
     * all default off. The PROMPT box itself is always available.
     */
    val extraPromptSurfaces: ExtraPromptSurfaces = ExtraPromptSurfaces(),
    /**
     * Read mode: keep the current scroll offset when turning pages.
     * Default is off — new pages start at the top.
     */
    val keepScrollOnPageChange: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val secureKeys: SecureKeyStore,
) {
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = AppThemeMode.entries.find { it.name == prefs[KEY_THEME] } ?: AppThemeMode.Dark,
            fontSizeSp = prefs[KEY_FONT_SIZE] ?: 16,
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
            defaultModelRef = prefs[KEY_DEFAULT_MODEL] ?: WritingModelSeeds.DEFAULT_MODEL_REF,
            launchMode = prefs[KEY_LAUNCH_MODE] ?: "novel",
            colorCodingEnabled = prefs[KEY_COLOR_CODING] ?: true,
            selectedBookId = prefs[KEY_SELECTED_BOOK] ?: "book-adams-haven-1",
            backgroundMediaId = prefs[KEY_BACKGROUND_MEDIA] ?: "",
            roleplayPresetId = prefs[KEY_RP_PRESET] ?: "preset-balanced",
            layout = LayoutPreferences(
                railWidthDp = prefs[KEY_RAIL_WIDTH] ?: 320f,
                railCollapsed = prefs[KEY_RAIL_COLLAPSED] ?: true,
                destBarCollapsed = prefs[KEY_DEST_BAR_COLLAPSED] ?: false,
                destBarHeightDp = prefs[KEY_DEST_BAR_HEIGHT] ?: 52f,
            ),
            appearance = AppearanceOverrides(
                chrome = sectionAppearance(prefs, "chrome"),
                rail = sectionAppearance(prefs, "rail"),
                content = sectionAppearance(prefs, "content"),
                page = sectionAppearance(prefs, "page"),
                chatBubble = sectionAppearance(prefs, "chat_bubble"),
            ),
            appBrightnessPercent = prefs[KEY_APP_BRIGHTNESS] ?: 100,
            syncWebUrl = prefs[KEY_SYNC_WEB_URL] ?: "",
            syncPassword = prefs[KEY_SYNC_PASSWORD] ?: "",
            autoSync = prefs[KEY_AUTO_SYNC] ?: true,
            lastSyncAt = prefs[KEY_LAST_SYNC_AT] ?: 0L,
            extraPromptSurfaces = ExtraPromptSurfaces(
                inlineWriting = extraFlag(prefs, KEY_PROMPT_INLINE_WRITING),
                sceneBeatCard = extraFlag(prefs, KEY_PROMPT_SCENE_BEAT_CARD),
                continuation = extraFlag(prefs, KEY_PROMPT_CONTINUATION),
                chatComposer = extraFlag(prefs, KEY_PROMPT_CHAT_COMPOSER),
                roleplayButtons = extraFlag(prefs, KEY_PROMPT_ROLEPLAY_BUTTONS),
            ),
            keepScrollOnPageChange = prefs[KEY_KEEP_SCROLL_ON_PAGE_CHANGE] ?: false,
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setFontSize(sp: Int) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = sp.coerceIn(12, 28) }
    }

    suspend fun setLineHeight(value: Float) {
        context.dataStore.edit { it[KEY_LINE_HEIGHT] = value.coerceIn(1.2f, 2.2f) }
    }

    suspend fun setKeepScrollOnPageChange(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCROLL_ON_PAGE_CHANGE] = enabled }
    }

    suspend fun setDefaultModel(ref: String) {
        context.dataStore.edit { it[KEY_DEFAULT_MODEL] = ref }
    }

    suspend fun setLaunchMode(mode: String) {
        context.dataStore.edit { it[KEY_LAUNCH_MODE] = mode }
    }

    suspend fun setColorCodingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COLOR_CODING] = enabled }
    }

    suspend fun setSelectedBookId(bookId: String) {
        context.dataStore.edit { it[KEY_SELECTED_BOOK] = bookId }
    }

    suspend fun setBackgroundMediaId(mediaId: String) {
        context.dataStore.edit { it[KEY_BACKGROUND_MEDIA] = mediaId }
    }

    suspend fun setRoleplayPresetId(presetId: String) {
        context.dataStore.edit { it[KEY_RP_PRESET] = presetId }
    }

    suspend fun setRailWidthDp(width: Float) {
        context.dataStore.edit {
            it[KEY_RAIL_WIDTH] = width.coerceIn(InkSpacingRailMin, InkSpacingRailMax)
        }
    }

    suspend fun setRailCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[KEY_RAIL_COLLAPSED] = collapsed }
    }

    suspend fun setDestBarCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[KEY_DEST_BAR_COLLAPSED] = collapsed }
    }

    suspend fun setDestBarHeightDp(height: Float) {
        context.dataStore.edit { it[KEY_DEST_BAR_HEIGHT] = height.coerceIn(36f, 120f) }
    }

    suspend fun setSectionAppearance(sectionKey: String, colorHex: String, opacityPercent: Int) {
        context.dataStore.edit {
            it[stringPreferencesKey("${sectionKey}_color")] = colorHex
            it[intPreferencesKey("${sectionKey}_opacity")] = opacityPercent.coerceIn(0, 100)
        }
    }

    suspend fun setSyncPeer(url: String, password: String) {
        context.dataStore.edit {
            it[KEY_SYNC_WEB_URL] = url
            it[KEY_SYNC_PASSWORD] = password
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    suspend fun setLastSyncAt(value: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC_AT] = value }
    }

    suspend fun setAppBrightnessPercent(percent: Int) {
        context.dataStore.edit {
            it[KEY_APP_BRIGHTNESS] = percent.coerceIn(5, 100)
        }
    }

    suspend fun setShowExtraPromptSurfaces(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_SHOW_EXTRA_PROMPT_SURFACES] = enabled
            it[KEY_PROMPT_INLINE_WRITING] = enabled
            it[KEY_PROMPT_SCENE_BEAT_CARD] = enabled
            it[KEY_PROMPT_CONTINUATION] = enabled
            it[KEY_PROMPT_CHAT_COMPOSER] = enabled
            it[KEY_PROMPT_ROLEPLAY_BUTTONS] = enabled
        }
    }

    suspend fun setExtraPromptSurface(surface: ExtraPromptSurface, enabled: Boolean) {
        val key = when (surface) {
            ExtraPromptSurface.InlineWriting -> KEY_PROMPT_INLINE_WRITING
            ExtraPromptSurface.SceneBeatCard -> KEY_PROMPT_SCENE_BEAT_CARD
            ExtraPromptSurface.Continuation -> KEY_PROMPT_CONTINUATION
            ExtraPromptSurface.ChatComposer -> KEY_PROMPT_CHAT_COMPOSER
            ExtraPromptSurface.RoleplayButtons -> KEY_PROMPT_ROLEPLAY_BUTTONS
        }
        context.dataStore.edit { it[key] = enabled }
    }

    /** Clear all section color/opacity overrides back to theme defaults. */
    suspend fun resetAppearanceColors() {
        val keys = listOf("chrome", "rail", "content", "page", "chat_bubble")
        context.dataStore.edit { prefs ->
            keys.forEach { key ->
                prefs.remove(stringPreferencesKey("${key}_color"))
                prefs.remove(intPreferencesKey("${key}_opacity"))
            }
        }
    }

    fun apiKey(providerId: String): String? = secureKeys.get(providerId)

    fun setApiKey(providerId: String, key: String) = secureKeys.set(providerId, key)

    private fun extraFlag(prefs: Preferences, key: androidx.datastore.preferences.core.Preferences.Key<Boolean>): Boolean =
        prefs[key] ?: (prefs[KEY_SHOW_EXTRA_PROMPT_SURFACES] ?: false)

    private fun sectionAppearance(prefs: Preferences, key: String): SectionAppearance =
        SectionAppearance(
            colorHex = prefs[stringPreferencesKey("${key}_color")] ?: "",
            opacityPercent = prefs[intPreferencesKey("${key}_opacity")] ?: 100,
        )

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size_sp")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        private val KEY_LAUNCH_MODE = stringPreferencesKey("launch_mode")
        private val KEY_COLOR_CODING = booleanPreferencesKey("color_coding")
        private val KEY_SELECTED_BOOK = stringPreferencesKey("selected_book_id")
        private val KEY_BACKGROUND_MEDIA = stringPreferencesKey("background_media_id")
        private val KEY_RP_PRESET = stringPreferencesKey("roleplay_preset_id")
        private val KEY_RAIL_WIDTH = floatPreferencesKey("rail_width_dp")
        private val KEY_RAIL_COLLAPSED = booleanPreferencesKey("rail_collapsed")
        private val KEY_DEST_BAR_COLLAPSED = booleanPreferencesKey("dest_bar_collapsed")
        private val KEY_DEST_BAR_HEIGHT = floatPreferencesKey("dest_bar_height_dp")
        private val KEY_APP_BRIGHTNESS = intPreferencesKey("app_brightness_percent")
        private val KEY_SYNC_WEB_URL = stringPreferencesKey("sync_web_url")
        private val KEY_SYNC_PASSWORD = stringPreferencesKey("sync_password")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        private val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        private val KEY_SHOW_EXTRA_PROMPT_SURFACES = booleanPreferencesKey("show_extra_prompt_surfaces")
        private val KEY_PROMPT_INLINE_WRITING = booleanPreferencesKey("prompt_inline_writing")
        private val KEY_PROMPT_SCENE_BEAT_CARD = booleanPreferencesKey("prompt_scene_beat_card")
        private val KEY_PROMPT_CONTINUATION = booleanPreferencesKey("prompt_continuation")
        private val KEY_PROMPT_CHAT_COMPOSER = booleanPreferencesKey("prompt_chat_composer")
        private val KEY_PROMPT_ROLEPLAY_BUTTONS = booleanPreferencesKey("prompt_roleplay_buttons")
        private val KEY_KEEP_SCROLL_ON_PAGE_CHANGE = booleanPreferencesKey("keep_scroll_on_page_change")

        const val InkSpacingRailMin = 48f
        const val InkSpacingRailMax = 420f
    }
}
