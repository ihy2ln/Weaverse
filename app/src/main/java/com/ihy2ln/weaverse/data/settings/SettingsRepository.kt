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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ihy2ln.weaverse.ai.openrouter.WritingModelSeeds
import com.ihy2ln.weaverse.ai.prompt.PromptAddOns
import com.ihy2ln.weaverse.ai.prompt.PromptAgeRating
import com.ihy2ln.weaverse.ai.prompt.PromptingMode
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.core.ui.theme.AppearanceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

data class NavigationOrderPreferences(
    val workspaces: String = "",
    val novel: String = "",
    val roleplay: String = "",
    val games: String = "",
    val chatting: String = "",
    val storyboard: String = "",
    val notes: String = "",
)

enum class ExtraPromptSurface {
    InlineWriting,
    SceneBeatCard,
    Continuation,
    ChatComposer,
    RoleplayButtons,
}

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.Light,
    /** Whole visual identity (palette + typography + shape); Classic = the original look. */
    val appearanceProfile: AppearanceProfile = AppearanceProfile.Classic,
    val fontSizeSp: Int = 16,
    val lineHeight: Float = 1.6f,
    /** Paper | Sepia | Night — dedicated reader palette, independent of app chrome. */
    val readerTheme: String = "Paper",
    val defaultModelRef: String = WritingModelSeeds.DEFAULT_MODEL_REF,
    val launchMode: String = "novel",
    val colorCodingEnabled: Boolean = true,
    val selectedBookId: String = "book-adams-haven-1",
    val backgroundMediaId: String = "",
    /** Draw the appearance profile's ambient background art behind the shell. */
    val profileBackgroundEnabled: Boolean = true,
    /** Media id of the RPG town backdrop; blank draws the built-in fallback. */
    val townBackgroundMediaId: String = "",
    val roleplayPresetId: String = "preset-balanced",
    val layout: LayoutPreferences = LayoutPreferences(),
    val appearance: AppearanceOverrides = AppearanceOverrides(),
    /** Overall UI brightness 5–100 (100 = full). Independent of section color pickers. */
    val appBrightnessPercent: Int = 100,
    val syncWebUrl: String = "",
    val syncPassword: String = "",
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val syncTlsEnabled: Boolean = false,
    val syncCertSha256: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastAutoBackupAt: Long = 0L,
    val usageYearMonth: String = "",
    val usageCostUsd: Double = 0.0,
    val usagePromptTokens: Long = 0L,
    val usageCompletionTokens: Long = 0L,
    /** Epoch-day of the last auto-generated "new person"; 0 = never. */
    val lastDailyCharacterEpochDay: Long = 0L,
    /** Whether to auto-generate one new character per day (needs an API key). */
    val dailyCharactersEnabled: Boolean = true,
    /**
     * Extra generators besides the compact PROMPT box. Each flag is independent;
     * all default off. The PROMPT box itself is always available.
     */
    val extraPromptSurfaces: ExtraPromptSurfaces = ExtraPromptSurfaces(),
    val navigationOrder: NavigationOrderPreferences = NavigationOrderPreferences(),
    /** ADD-ON: ECCHI MANGAKA overlay injected into every mode's prompts. */
    val ecchiOverlay: Boolean = true,
    /** AGE RATING add-on, ranging from PG through X. */
    val promptAgeRating: PromptAgeRating = PromptAgeRating.X,
    /** The base TEMPLATE the model follows. */
    val promptingMode: PromptingMode = PromptingMode.Novel,
    /** Zero or more GENRE add-ons prepended to every prompt. */
    val selectedGenres: Set<String> = setOf(PromptAddOns.DefaultGenre),
    /** Per-device root: an Android Storage Access Framework tree URI or a local filesystem path. */
    val topicMediaLibraryRoot: String = "",
    /** When enabled, AI replies may attach an image/video from a matching topic subfolder. */
    val topicMediaAutoAttach: Boolean = false,
    /** User-added `!` quick-add keywords: keyword -> CodexEntryKind name. */
    val customBangCommands: Map<String, String> = emptyMap(),
    /** Built-in `!` keywords hidden by the user from Settings → Composer commands. */
    val removedBangKeywords: Set<String> = emptySet(),
    /** User-added `*` RPG turn commands: "keyword|description|requiresRoll(1/0)". */
    val customStarCommands: Set<String> = emptySet(),
    /** Built-in `*` keywords hidden by the user from Settings → Composer commands. */
    val removedStarKeywords: Set<String> = emptySet(),
    /** User-defined campaign setting templates: "id|label|directive". */
    val customSettingTemplates: Set<String> = emptySet(),
)

data class ReaderSavedState(
    val lastSceneId: String = "",
    val bookmarkedSceneIds: Set<String> = emptySet(),
    val paragraphIndex: Int = 0,
    val scrollOffset: Int = 0,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val secureKeys: SecureKeyStore,
) {
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = AppThemeMode.entries.find { it.name == prefs[KEY_THEME] } ?: AppThemeMode.Light,
            appearanceProfile = AppearanceProfile.entries
                .find { it.name == prefs[KEY_APPEARANCE_PROFILE] }
                ?: AppearanceProfile.Classic,
            fontSizeSp = prefs[KEY_FONT_SIZE] ?: 16,
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
            readerTheme = prefs[KEY_READER_THEME] ?: "Paper",
            defaultModelRef = prefs[KEY_DEFAULT_MODEL] ?: WritingModelSeeds.DEFAULT_MODEL_REF,
            launchMode = prefs[KEY_LAUNCH_MODE] ?: "novel",
            colorCodingEnabled = prefs[KEY_COLOR_CODING] ?: true,
            selectedBookId = prefs[KEY_SELECTED_BOOK] ?: "book-adams-haven-1",
            backgroundMediaId = prefs[KEY_BACKGROUND_MEDIA] ?: "",
            profileBackgroundEnabled = prefs[KEY_PROFILE_BACKGROUND] ?: true,
            townBackgroundMediaId = prefs[KEY_TOWN_BACKGROUND_MEDIA] ?: "",
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
            syncTlsEnabled = prefs[KEY_SYNC_TLS] ?: false,
            syncCertSha256 = prefs[KEY_SYNC_CERT_SHA] ?: "",
            autoBackupEnabled = prefs[KEY_AUTO_BACKUP] ?: false,
            lastAutoBackupAt = prefs[KEY_LAST_AUTO_BACKUP_AT] ?: 0L,
            usageYearMonth = prefs[KEY_USAGE_YEAR_MONTH] ?: "",
            usageCostUsd = (prefs[KEY_USAGE_COST] ?: 0f).toDouble(),
            usagePromptTokens = prefs[KEY_USAGE_PROMPT_TOKENS] ?: 0L,
            usageCompletionTokens = prefs[KEY_USAGE_COMPLETION_TOKENS] ?: 0L,
            lastDailyCharacterEpochDay = prefs[KEY_LAST_DAILY_CHARACTER_DAY] ?: 0L,
            dailyCharactersEnabled = prefs[KEY_DAILY_CHARACTERS_ENABLED] ?: true,
            extraPromptSurfaces = ExtraPromptSurfaces(
                inlineWriting = extraFlag(prefs, KEY_PROMPT_INLINE_WRITING),
                sceneBeatCard = extraFlag(prefs, KEY_PROMPT_SCENE_BEAT_CARD),
                continuation = extraFlag(prefs, KEY_PROMPT_CONTINUATION),
                chatComposer = extraFlag(prefs, KEY_PROMPT_CHAT_COMPOSER),
                roleplayButtons = extraFlag(prefs, KEY_PROMPT_ROLEPLAY_BUTTONS),
            ),
            navigationOrder = NavigationOrderPreferences(
                workspaces = prefs[KEY_NAV_WORKSPACES].orEmpty(),
                novel = prefs[KEY_NAV_NOVEL].orEmpty(),
                roleplay = prefs[KEY_NAV_ROLEPLAY].orEmpty(),
                games = prefs[KEY_NAV_GAMES].orEmpty(),
                chatting = prefs[KEY_NAV_CHATTING].orEmpty(),
                storyboard = prefs[KEY_NAV_STORYBOARD].orEmpty(),
                notes = prefs[KEY_NAV_NOTES].orEmpty(),
            ),
            ecchiOverlay = prefs[KEY_ECCHI_OVERLAY] ?: true,
            promptAgeRating = prefs[KEY_PROMPT_AGE_RATING]?.let(PromptAgeRating::fromId)
                ?: if (prefs[KEY_MATURE_RATING] ?: true) PromptAgeRating.X else PromptAgeRating.Pg13,
            promptingMode = PromptingMode.fromId(prefs[KEY_PROMPTING_MODE]),
            selectedGenres = prefs[KEY_SELECTED_GENRES]
                ?: prefs[KEY_GENRE_LABEL]
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::setOf)
                ?: setOf(PromptAddOns.DefaultGenre),
            topicMediaLibraryRoot = prefs[KEY_TOPIC_MEDIA_LIBRARY_ROOT].orEmpty(),
            topicMediaAutoAttach = prefs[KEY_TOPIC_MEDIA_AUTO_ATTACH] ?: false,
            customBangCommands = prefs[KEY_CUSTOM_BANGS].orEmpty()
                .mapNotNull { entry ->
                    val keyword = entry.substringBefore(':').lowercase()
                    val kindName = entry.substringAfter(':', "")
                    if (keyword.isBlank() || kindName.isBlank()) null else keyword to kindName
                }
                .toMap(),
            removedBangKeywords = prefs[KEY_REMOVED_BANGS].orEmpty()
                .map { it.lowercase() }
                .filter { it.isNotBlank() }
                .toSet(),
            customStarCommands = prefs[KEY_CUSTOM_STARS].orEmpty()
                .filter { it.substringBefore('|').isNotBlank() }
                .toSet(),
            removedStarKeywords = prefs[KEY_REMOVED_STARS].orEmpty()
                .map { it.lowercase() }
                .filter { it.isNotBlank() }
                .toSet(),
            customSettingTemplates = prefs[KEY_CUSTOM_SETTING_TEMPLATES].orEmpty()
                .filter { it.substringBefore('|').isNotBlank() }
                .toSet(),
        )
    }

    init {
        // TEMPLATE-header add-ons resolve app-wide; keep the prompt engine in sync.
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            preferences.collect { prefs ->
                PromptAddOns.ecchiOverlay = prefs.ecchiOverlay
                PromptAddOns.ageRating = prefs.promptAgeRating
                PromptAddOns.mode = prefs.promptingMode
                PromptAddOns.selectedGenres = prefs.selectedGenres
            }
        }
    }

    suspend fun setEcchiOverlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ECCHI_OVERLAY] = enabled }
    }

    suspend fun setPromptAgeRating(rating: PromptAgeRating) {
        context.dataStore.edit { it[KEY_PROMPT_AGE_RATING] = rating.id }
    }

    suspend fun setPromptingMode(mode: PromptingMode) {
        context.dataStore.edit { it[KEY_PROMPTING_MODE] = mode.id }
    }

    suspend fun setSelectedGenres(genres: Set<String>) {
        context.dataStore.edit { it[KEY_SELECTED_GENRES] = genres }
    }

    suspend fun setTopicMediaLibraryRoot(root: String) {
        context.dataStore.edit { it[KEY_TOPIC_MEDIA_LIBRARY_ROOT] = root.trim() }
    }

    suspend fun setTopicMediaAutoAttach(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TOPIC_MEDIA_AUTO_ATTACH] = enabled }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setAppearanceProfile(profile: AppearanceProfile) {
        context.dataStore.edit { it[KEY_APPEARANCE_PROFILE] = profile.name }
    }

    suspend fun setFontSize(sp: Int) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = sp.coerceIn(12, 28) }
    }

    suspend fun setLineHeight(value: Float) {
        context.dataStore.edit { it[KEY_LINE_HEIGHT] = value.coerceIn(1.2f, 2.2f) }
    }

    suspend fun setReaderTheme(theme: String) {
        context.dataStore.edit { it[KEY_READER_THEME] = theme }
    }

    fun readerState(bookId: String): Flow<ReaderSavedState> = context.dataStore.data.map { prefs ->
        ReaderSavedState(
            lastSceneId = prefs[stringPreferencesKey("reader_last_$bookId")].orEmpty(),
            bookmarkedSceneIds = prefs[stringSetPreferencesKey("reader_bookmarks_$bookId")].orEmpty(),
            paragraphIndex = prefs[intPreferencesKey("reader_para_$bookId")] ?: 0,
            scrollOffset = prefs[intPreferencesKey("reader_offset_$bookId")] ?: 0,
        )
    }

    suspend fun setReaderPosition(bookId: String, sceneId: String) {
        context.dataStore.edit { it[stringPreferencesKey("reader_last_$bookId")] = sceneId }
    }

    suspend fun setReaderScroll(bookId: String, sceneId: String, paragraphIndex: Int, scrollOffset: Int) {
        context.dataStore.edit {
            it[stringPreferencesKey("reader_last_$bookId")] = sceneId
            it[intPreferencesKey("reader_para_$bookId")] = paragraphIndex.coerceAtLeast(0)
            it[intPreferencesKey("reader_offset_$bookId")] = scrollOffset
        }
    }

    suspend fun toggleReaderBookmark(bookId: String, sceneId: String) {
        val key = stringSetPreferencesKey("reader_bookmarks_$bookId")
        context.dataStore.edit { prefs ->
            val next = prefs[key].orEmpty().toMutableSet()
            if (!next.add(sceneId)) next.remove(sceneId)
            prefs[key] = next
        }
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

    suspend fun setTownBackgroundMediaId(mediaId: String) {
        context.dataStore.edit { it[KEY_TOWN_BACKGROUND_MEDIA] = mediaId }
    }

    /** User-selected illustration for one tappable RPG town location. */
    fun townLocationMediaId(locationId: String): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("town_location_media_${locationId.filter(Char::isLetterOrDigit)}")].orEmpty()
    }

    suspend fun setTownLocationMediaId(locationId: String, mediaId: String) {
        context.dataStore.edit {
            it[stringPreferencesKey("town_location_media_${locationId.filter(Char::isLetterOrDigit)}")] = mediaId
        }
    }

    suspend fun setBackgroundMediaId(mediaId: String) {
        context.dataStore.edit { it[KEY_BACKGROUND_MEDIA] = mediaId }
    }

    suspend fun setProfileBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PROFILE_BACKGROUND] = enabled }
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

    suspend fun setSyncTlsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SYNC_TLS] = enabled }
    }

    suspend fun setSyncCertSha256(value: String) {
        context.dataStore.edit { it[KEY_SYNC_CERT_SHA] = value }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP] = enabled }
    }

    suspend fun setLastAutoBackupAt(value: Long) {
        context.dataStore.edit { it[KEY_LAST_AUTO_BACKUP_AT] = value }
    }

    suspend fun recordUsage(promptTokens: Int, completionTokens: Int, costUsd: Double?) {
        val now = java.time.YearMonth.now().toString()
        context.dataStore.edit { prefs ->
            val month = prefs[KEY_USAGE_YEAR_MONTH].orEmpty()
            val reset = month != now
            prefs[KEY_USAGE_YEAR_MONTH] = now
            val previous = if (reset) 0.0 else (prefs[KEY_USAGE_COST] ?: 0f).toDouble()
            prefs[KEY_USAGE_COST] = (previous + (costUsd ?: 0.0)).toFloat()
            prefs[KEY_USAGE_PROMPT_TOKENS] =
                (if (reset) 0L else prefs[KEY_USAGE_PROMPT_TOKENS] ?: 0L) + promptTokens.toLong()
            prefs[KEY_USAGE_COMPLETION_TOKENS] =
                (if (reset) 0L else prefs[KEY_USAGE_COMPLETION_TOKENS] ?: 0L) + completionTokens.toLong()
        }
    }

    suspend fun setLastDailyCharacterEpochDay(value: Long) {
        context.dataStore.edit { it[KEY_LAST_DAILY_CHARACTER_DAY] = value }
    }

    suspend fun setDailyCharactersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DAILY_CHARACTERS_ENABLED] = enabled }
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

    suspend fun setWorkspaceButtonOrder(ids: List<String>) {
        context.dataStore.edit { it[KEY_NAV_WORKSPACES] = encodeOrder(ids) }
    }

    suspend fun setModeButtonOrder(mode: String, ids: List<String>) {
        val key = when (mode) {
            "Novel" -> KEY_NAV_NOVEL
            "Roleplay" -> KEY_NAV_ROLEPLAY
            "Games" -> KEY_NAV_GAMES
            "Chatting" -> KEY_NAV_CHATTING
            "Storyboard" -> KEY_NAV_STORYBOARD
            else -> KEY_NAV_NOTES
        }
        context.dataStore.edit { it[key] = encodeOrder(ids) }
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

    /** Adds (or replaces) a custom `!keyword` that files entries under the given kind. */
    suspend fun addBangCommand(keyword: String, kindName: String) {
        val key = keyword.trim().lowercase()
        if (!key.matches(Regex("[a-z]+"))) return
        context.dataStore.edit { prefs ->
            val kept = prefs[KEY_CUSTOM_BANGS].orEmpty()
                .filter { it.substringBefore(':') != key }
            prefs[KEY_CUSTOM_BANGS] = (kept + "$key:$kindName").toSet()
        }
    }

    /** Removes a command row: custom keywords are deleted, built-ins are hidden. */
    suspend fun removeBangCommand(keyword: String, isBuiltIn: Boolean) {
        val key = keyword.lowercase()
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BANGS] = prefs[KEY_CUSTOM_BANGS].orEmpty()
                .filterNot { it.substringBefore(':') == key }
                .toSet()
            if (isBuiltIn) {
                prefs[KEY_REMOVED_BANGS] = (prefs[KEY_REMOVED_BANGS].orEmpty() + key).toSet()
            }
        }
    }

    /** Restores every built-in command and drops all custom ones. */
    suspend fun resetBangCommands() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CUSTOM_BANGS)
            prefs.remove(KEY_REMOVED_BANGS)
        }
    }

    /** Adds (or replaces) a custom `*keyword` RPG turn command. */
    suspend fun addStarCommand(keyword: String, description: String, requiresRoll: Boolean) {
        val key = keyword.trim().lowercase()
        if (!key.matches(Regex("[a-z]+"))) return
        val entry = "$key|${description.trim().take(120)}|${if (requiresRoll) "1" else "0"}"
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_STARS] = prefs[KEY_CUSTOM_STARS].orEmpty()
                .filterNot { it.substringBefore('|') == key }
                .toSet() + entry
        }
    }

    /** Removes a `*` command row: custom entries are deleted, built-ins are hidden. */
    suspend fun removeStarCommand(keyword: String, isBuiltIn: Boolean) {
        val key = keyword.lowercase()
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_STARS] = prefs[KEY_CUSTOM_STARS].orEmpty()
                .filterNot { it.substringBefore('|') == key }
                .toSet()
            if (isBuiltIn) {
                prefs[KEY_REMOVED_STARS] = (prefs[KEY_REMOVED_STARS].orEmpty() + key).toSet()
            }
        }
    }

    /** Restores every built-in `*` command and drops custom ones. */
    suspend fun resetStarCommands() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CUSTOM_STARS)
            prefs.remove(KEY_REMOVED_STARS)
        }
    }

    /** Adds (or replaces) a user-defined campaign setting template. */
    suspend fun addSettingTemplate(label: String, directive: String) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        val id = "custom-" + trimmedLabel.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val entry = "$id|${trimmedLabel.take(60)}|${directive.trim().take(2000)}"
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SETTING_TEMPLATES] = prefs[KEY_CUSTOM_SETTING_TEMPLATES].orEmpty()
                .filterNot { it.substringBefore('|') == id }
                .toSet() + entry
        }
    }

    /** Removes a user-defined campaign setting template by id. */
    suspend fun removeSettingTemplate(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SETTING_TEMPLATES] = prefs[KEY_CUSTOM_SETTING_TEMPLATES].orEmpty()
                .filterNot { it.substringBefore('|') == id }
                .toSet()
        }
    }

    private fun extraFlag(prefs: Preferences, key: androidx.datastore.preferences.core.Preferences.Key<Boolean>): Boolean =
        prefs[key] ?: (prefs[KEY_SHOW_EXTRA_PROMPT_SURFACES] ?: false)

    private fun sectionAppearance(prefs: Preferences, key: String): SectionAppearance =
        SectionAppearance(
            colorHex = prefs[stringPreferencesKey("${key}_color")] ?: "",
            opacityPercent = prefs[intPreferencesKey("${key}_opacity")] ?: 100,
        )

    private fun encodeOrder(ids: List<String>): String = ids
        .map { it.replace(",", "") }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_APPEARANCE_PROFILE = stringPreferencesKey("appearance_profile")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size_sp")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        private val KEY_READER_THEME = stringPreferencesKey("reader_theme")
        private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        private val KEY_LAUNCH_MODE = stringPreferencesKey("launch_mode")
        private val KEY_COLOR_CODING = booleanPreferencesKey("color_coding")
        private val KEY_SELECTED_BOOK = stringPreferencesKey("selected_book_id")
        private val KEY_BACKGROUND_MEDIA = stringPreferencesKey("background_media_id")
        private val KEY_PROFILE_BACKGROUND = booleanPreferencesKey("profile_background_enabled")
        private val KEY_TOWN_BACKGROUND_MEDIA = stringPreferencesKey("town_background_media_id")
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
        private val KEY_SYNC_TLS = booleanPreferencesKey("sync_tls_enabled")
        private val KEY_SYNC_CERT_SHA = stringPreferencesKey("sync_cert_sha256")
        private val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
        private val KEY_USAGE_YEAR_MONTH = stringPreferencesKey("usage_year_month")
        private val KEY_USAGE_COST = floatPreferencesKey("usage_cost_usd")
        private val KEY_USAGE_PROMPT_TOKENS = longPreferencesKey("usage_prompt_tokens")
        private val KEY_USAGE_COMPLETION_TOKENS = longPreferencesKey("usage_completion_tokens")
        private val KEY_LAST_DAILY_CHARACTER_DAY = longPreferencesKey("last_daily_character_day")
        private val KEY_DAILY_CHARACTERS_ENABLED = booleanPreferencesKey("daily_characters_enabled")
        private val KEY_SHOW_EXTRA_PROMPT_SURFACES = booleanPreferencesKey("show_extra_prompt_surfaces")
        private val KEY_PROMPT_INLINE_WRITING = booleanPreferencesKey("prompt_inline_writing")
        private val KEY_PROMPT_SCENE_BEAT_CARD = booleanPreferencesKey("prompt_scene_beat_card")
        private val KEY_PROMPT_CONTINUATION = booleanPreferencesKey("prompt_continuation")
        private val KEY_PROMPT_CHAT_COMPOSER = booleanPreferencesKey("prompt_chat_composer")
        private val KEY_PROMPT_ROLEPLAY_BUTTONS = booleanPreferencesKey("prompt_roleplay_buttons")
        private val KEY_NAV_WORKSPACES = stringPreferencesKey("nav_order_workspaces")
        private val KEY_NAV_NOVEL = stringPreferencesKey("nav_order_novel")
        private val KEY_NAV_ROLEPLAY = stringPreferencesKey("nav_order_roleplay")
        private val KEY_NAV_GAMES = stringPreferencesKey("nav_order_games")
        private val KEY_NAV_CHATTING = stringPreferencesKey("nav_order_chatting")
        private val KEY_NAV_STORYBOARD = stringPreferencesKey("nav_order_storyboard")
        private val KEY_NAV_NOTES = stringPreferencesKey("nav_order_notes")
        private val KEY_ECCHI_OVERLAY = booleanPreferencesKey("prompt_ecchi_overlay")
        private val KEY_PROMPT_AGE_RATING = stringPreferencesKey("prompt_age_rating")
        /** Read-only compatibility with v1.3.25's Standard/Mature toggle. */
        private val KEY_MATURE_RATING = booleanPreferencesKey("prompt_mature_rating")
        private val KEY_PROMPTING_MODE = stringPreferencesKey("prompt_template_mode")
        private val KEY_SELECTED_GENRES = stringSetPreferencesKey("prompt_selected_genres")
        private val KEY_TOPIC_MEDIA_LIBRARY_ROOT = stringPreferencesKey("topic_media_library_root")
        private val KEY_TOPIC_MEDIA_AUTO_ATTACH = booleanPreferencesKey("topic_media_auto_attach")
        /** Read-only compatibility with v1.3.24's single free-text genre field. */
        private val KEY_GENRE_LABEL = stringPreferencesKey("prompt_genre_label")
        private val KEY_CUSTOM_BANGS = stringSetPreferencesKey("bang_commands_custom")
        private val KEY_REMOVED_BANGS = stringSetPreferencesKey("bang_commands_removed")
        private val KEY_CUSTOM_STARS = stringSetPreferencesKey("star_commands_custom")
        private val KEY_REMOVED_STARS = stringSetPreferencesKey("star_commands_removed")
        private val KEY_CUSTOM_SETTING_TEMPLATES = stringSetPreferencesKey("campaign_setting_templates_custom")

        const val InkSpacingRailMin = 48f
        const val InkSpacingRailMax = 420f
    }
}
