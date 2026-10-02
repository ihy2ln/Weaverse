package com.ihy2ln.weaverse.feature.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WeaverBrowser's own pages. Weaverse's other workspaces live here too: WeaverSocial, the
 * character chats and Contacts open as tabs, and the other modes are one tap away.
 */
object WeaverPages {
    const val SCHEME = "weaver://"
    const val NEW_TAB = "weaver://newtab"
    const val SOCIAL = "weaver://social"
    const val CHATS = "weaver://chats"
    const val CONTACTS = "weaver://contacts"
    const val HISTORY = "weaver://history"
    const val BOOKMARKS = "weaver://bookmarks"
    const val DOWNLOADS = "weaver://downloads"
    const val SETTINGS = "weaver://settings"
    const val RECENT_TABS = "weaver://recent-tabs"

    /** Pages that leave the browser for another Weaverse workspace, by the mode's enum name. */
    val MODES = linkedMapOf(
        "weaver://novel" to "Novel",
        "weaver://rpg" to "Roleplay",
        "weaver://games" to "Games",
        "weaver://manga" to "Storyboard",
        "weaver://notes" to "Notes",
    )

    fun isInternal(url: String): Boolean = url.startsWith(SCHEME)

    fun titleOf(url: String): String = when (url.substringBefore('?')) {
        NEW_TAB -> "New tab"
        SOCIAL -> "WeaverSocial"
        CHATS -> "Chats"
        CONTACTS -> "Contacts"
        HISTORY -> "History"
        BOOKMARKS -> "Bookmarks"
        DOWNLOADS -> "Downloads"
        SETTINGS -> "Settings"
        RECENT_TABS -> "Recent tabs"
        else -> url.removePrefix(SCHEME)
    }
}

@Serializable
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = WeaverPages.NEW_TAB,
    val title: String = "New tab",
    val private: Boolean = false,
    val openedAt: Long = System.currentTimeMillis(),
) {
    val isInternal: Boolean get() = WeaverPages.isInternal(url)
    val host: String get() = hostOf(url)
}

@Serializable
data class BrowserBookmark(val url: String, val title: String, val addedAt: Long = System.currentTimeMillis())

@Serializable
data class BrowserVisit(val url: String, val title: String, val at: Long = System.currentTimeMillis())

@Serializable
data class BrowserDownload(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val at: Long = System.currentTimeMillis(),
)

/** A tile in the New Tab Page's favorites row. */
@Serializable
data class BrowserFavorite(val url: String, val title: String)

enum class AdBlockLevel(val label: String) { Aggressive("Aggressive"), Standard("Standard"), Disabled("Disabled") }

enum class SearchEngine(val label: String, val template: String, val home: String) {
    Brave("Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
    Google("Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
    Bing("Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com"),
    Startpage("Startpage", "https://www.startpage.com/do/search?q=%s", "https://www.startpage.com"),
    Qwant("Qwant", "https://www.qwant.com/?q=%s", "https://www.qwant.com"),
    Ecosia("Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org"),
}

/** Global Shields defaults; each site can turn Shields down on its own. */
@Serializable
data class ShieldsDefaults(
    val adBlock: AdBlockLevel = AdBlockLevel.Standard,
    val upgradeHttps: Boolean = true,
    val blockScripts: Boolean = false,
    val blockFingerprinting: Boolean = true,
    val blockCrossSiteCookies: Boolean = true,
    val blockPopups: Boolean = true,
)

@Serializable
data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.Brave,
    val privateSearchEngine: SearchEngine = SearchEngine.DuckDuckGo,
    val shields: ShieldsDefaults = ShieldsDefaults(),
    val showPrivacyStats: Boolean = true,
    val showFavorites: Boolean = true,
    val showBackground: Boolean = true,
    val bottomToolbar: Boolean = true,
    val desktopByDefault: Boolean = false,
    val saveHistory: Boolean = true,
    val closeTabsOnExit: Boolean = false,
    val textZoom: Int = 100,
)

@Serializable
data class BrowserStats(val blocked: Long = 0, val bytesSaved: Long = 0, val msSaved: Long = 0)

@Serializable
data class BrowserData(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val selectedTabId: String? = null,
    val bookmarks: List<BrowserBookmark> = emptyList(),
    val history: List<BrowserVisit> = emptyList(),
    val downloads: List<BrowserDownload> = emptyList(),
    val favorites: List<BrowserFavorite> = DEFAULT_FAVORITES,
    /** Hosts the writer turned Shields down for. */
    val shieldsDownHosts: Set<String> = emptySet(),
    /** Hosts allowed to open pop-ups. */
    val popupHosts: Set<String> = emptySet(),
    val settings: BrowserSettings = BrowserSettings(),
    val stats: BrowserStats = BrowserStats(),
    /** Tabs closed this session and before, newest first, for Recent tabs. */
    val recentlyClosed: List<BrowserTab> = emptyList(),
) {
    companion object {
        val DEFAULT_FAVORITES = listOf(
            BrowserFavorite(WeaverPages.SOCIAL, "WeaverSocial"),
            BrowserFavorite(WeaverPages.CHATS, "Chats"),
            BrowserFavorite(WeaverPages.CONTACTS, "Contacts"),
            BrowserFavorite("weaver://novel", "Novel"),
            BrowserFavorite("weaver://rpg", "RPG"),
            BrowserFavorite("weaver://games", "Games"),
            BrowserFavorite("weaver://manga", "Manga Studio"),
            BrowserFavorite("weaver://notes", "Brainstorm"),
        )
    }
}

fun hostOf(url: String): String =
    if (WeaverPages.isInternal(url)) url.removePrefix(WeaverPages.SCHEME).substringBefore('/')
    else runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

/**
 * Turns what was typed into the address bar into a page: an address loads (https unless it
 * says otherwise), anything else is searched with the chosen engine.
 */
fun resolveTyped(input: String, engine: SearchEngine): String {
    val text = input.trim()
    if (text.isEmpty()) return WeaverPages.NEW_TAB
    if (WeaverPages.isInternal(text) || text.startsWith("about:") || text.startsWith("data:")) return text
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) return text
    val looksLikeHost = !text.contains(' ') && (
        Regex("^[^\\s/]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(text) ||
            Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$").matches(text)
        )
    return if (looksLikeHost) "https://$text"
    else engine.template.format(java.net.URLEncoder.encode(text, "UTF-8"))
}

/** What the address bar shows when it isn't being edited: the site, like Brave. */
fun displayUrl(url: String): String = when {
    url == WeaverPages.NEW_TAB -> ""
    WeaverPages.isInternal(url) -> url
    else -> {
        val noScheme = url.substringAfter("://")
        val host = noScheme.substringBefore('/').removePrefix("www.")
        val path = noScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        if (path.isBlank()) host else "$host/$path"
    }
}

/** If the url is a search-engine results page, the words searched; Brave shows those instead. */
fun searchTermsOf(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host.orEmpty()
    val engines = listOf("search.brave.com", "duckduckgo.com", "google.", "bing.com", "startpage.com", "qwant.com", "ecosia.org")
    if (engines.none { host.contains(it) }) return null
    val q = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("q=") }?.removePrefix("q=") ?: return null
    return runCatching { java.net.URLDecoder.decode(q, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** The browser's saved state: tabs, bookmarks, history, Shields and the privacy counters. */
@Singleton
class BrowserStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "weaver_browser.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _data = MutableStateFlow(load())
    val data: StateFlow<BrowserData> = _data

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            _data.drop(1).debounce(600).collect { save(it) }
        }
    }

    fun update(transform: (BrowserData) -> BrowserData) = _data.update(transform)

    private fun load(): BrowserData {
        val loaded = runCatching { json.decodeFromString(BrowserData.serializer(), file.readText()) }.getOrNull() ?: BrowserData()
        // Private tabs never survive a restart.
        val tabs = loaded.tabs.filterNot { it.private }.ifEmpty { listOf(BrowserTab()) }
        val selected = loaded.selectedTabId?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.last().id
        return loaded.copy(tabs = tabs, selectedTabId = selected)
    }

    private fun save(data: BrowserData) {
        runCatching {
            val publicOnly = data.copy(
                tabs = data.tabs.filterNot { it.private },
                recentlyClosed = data.recentlyClosed.filterNot { it.private }.take(25),
                history = data.history.take(MAX_HISTORY),
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(BrowserData.serializer(), publicOnly))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        const val MAX_HISTORY = 2_000
    }
}
