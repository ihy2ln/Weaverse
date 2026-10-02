package com.ihy2ln.weaverse.feature.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.resume

/** What a tab is doing right now; not saved. */
data class TabLive(
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blocked: Int = 0,
    val favicon: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    val secure: Boolean = true,
    val desktop: Boolean = false,
)

data class LeoMessage(val fromLeo: Boolean, val text: String)

data class FindState(val query: String = "", val active: Int = 0, val total: Int = 0)

sealed interface BrowserEvent {
    /** Leave the browser for another Weaverse workspace (an [com.ihy2ln.weaverse.feature.shell.AppMode] name). */
    data class OpenMode(val mode: String) : BrowserEvent
    data class Notice(val text: String) : BrowserEvent
    data class LinkMenu(val tabId: String, val link: String?, val image: String?) : BrowserEvent
    data class Share(val url: String, val title: String) : BrowserEvent
    data object PickFiles : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val store: BrowserStore,
    private val aiGeneration: AiGenerationService,
) : ViewModel() {
    val data: StateFlow<BrowserData> = store.data

    private val _live = MutableStateFlow<Map<String, TabLive>>(emptyMap())
    val live: StateFlow<Map<String, TabLive>> = _live

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<BrowserEvent> = _events

    /** A page's video playing full screen (null = none). */
    private val _fullscreen = MutableStateFlow<View?>(null)
    val fullscreen: StateFlow<View?> = _fullscreen
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val _find = MutableStateFlow<FindState?>(null)
    val find: StateFlow<FindState?> = _find

    private val _leo = MutableStateFlow(listOf<LeoMessage>())
    val leo: StateFlow<List<LeoMessage>> = _leo
    private val _leoBusy = MutableStateFlow(false)
    val leoBusy: StateFlow<Boolean> = _leoBusy

    private val views = HashMap<String, WebView>()
    /** Site in the address bar per tab, read on the network thread by Shields. */
    private val pageHosts = ConcurrentHashMap<String, String>()
    /** Where each tab came from when it moved between web pages and Weaverse pages. */
    private val backStacks = HashMap<String, ArrayDeque<String>>()
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    init {
        val d = store.data.value
        if (d.selectedTabId == null) store.update { it.copy(selectedTabId = it.tabs.lastOrNull()?.id) }
        // A private session never outlives the app.
        deletePrivateProfile()
    }

    // ---------------------------------------------------------------- tabs

    val selectedTab: BrowserTab?
        get() = data.value.let { d -> d.tabs.firstOrNull { it.id == d.selectedTabId } ?: d.tabs.lastOrNull() }

    fun select(tabId: String) = store.update { it.copy(selectedTabId = tabId) }

    fun newTab(url: String = WeaverPages.NEW_TAB, private: Boolean = false, select: Boolean = true): BrowserTab {
        if (url in WeaverPages.MODES) {
            _events.tryEmit(BrowserEvent.OpenMode(WeaverPages.MODES.getValue(url)))
            return selectedTab ?: BrowserTab()
        }
        val tab = BrowserTab(url = url, title = if (WeaverPages.isInternal(url)) WeaverPages.titleOf(url) else url, private = private)
        store.update { d ->
            val index = d.tabs.indexOfFirst { it.id == d.selectedTabId }
            val tabs = d.tabs.toMutableList().apply { add(if (index >= 0) index + 1 else size, tab) }
            d.copy(tabs = tabs, selectedTabId = if (select) tab.id else d.selectedTabId)
        }
        return tab
    }

    /** Focus a tab already showing [url], or open one. */
    fun openOrFocus(url: String) {
        val existing = data.value.tabs.firstOrNull { !it.private && it.url.substringBefore('?') == url }
        if (existing != null) select(existing.id) else newTab(url)
    }

    fun closeTab(tabId: String) {
        val closing = data.value.tabs.firstOrNull { it.id == tabId } ?: return
        destroyView(tabId)
        store.update { d ->
            val index = d.tabs.indexOfFirst { it.id == tabId }
            val rest = d.tabs.filterNot { it.id == tabId }
            val samePrivacy = rest.filter { it.private == closing.private }
            val next = when {
                d.selectedTabId != tabId -> d.selectedTabId
                else -> (samePrivacy.getOrNull((index - 1).coerceAtLeast(0)) ?: samePrivacy.lastOrNull() ?: rest.lastOrNull())?.id
            }
            val tabs = rest.ifEmpty { listOf(BrowserTab()) }
            d.copy(
                tabs = tabs,
                selectedTabId = next ?: tabs.last().id,
                recentlyClosed = if (closing.url == WeaverPages.NEW_TAB) d.recentlyClosed else (listOf(closing) + d.recentlyClosed).take(25),
            )
        }
        if (closing.private && data.value.tabs.none { it.private }) deletePrivateProfile()
    }

    fun closeAll(private: Boolean) {
        data.value.tabs.filter { it.private == private }.forEach { destroyView(it.id) }
        store.update { d ->
            val rest = d.tabs.filterNot { it.private == private }.ifEmpty { listOf(BrowserTab()) }
            d.copy(tabs = rest, selectedTabId = rest.last().id)
        }
        if (private) deletePrivateProfile()
    }

    fun reopenClosed(tab: BrowserTab) {
        store.update { it.copy(recentlyClosed = it.recentlyClosed.filterNot { c -> c.id == tab.id }) }
        newTab(tab.url)
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) = store.update { d ->
        d.copy(tabs = d.tabs.map { if (it.id == tabId) transform(it) else it })
    }

    private fun updateLive(tabId: String, transform: (TabLive) -> TabLive) = _live.update { m ->
        m + (tabId to transform(m[tabId] ?: TabLive()))
    }

    // ---------------------------------------------------------------- navigation

    fun submit(tabId: String, typed: String) {
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return
        val engine = if (tab.private) data.value.settings.privateSearchEngine else data.value.settings.searchEngine
        navigate(tabId, resolveTyped(typed, engine))
    }

    fun navigate(tabId: String, rawUrl: String) {
        if (rawUrl in WeaverPages.MODES) {
            _events.tryEmit(BrowserEvent.OpenMode(WeaverPages.MODES.getValue(rawUrl)))
            return
        }
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return
        val url = upgrade(rawUrl)
        if (WeaverPages.isInternal(url)) {
            if (url != tab.url) backStacks.getOrPut(tabId) { ArrayDeque() }.addLast(tab.url)
            updateTab(tabId) { it.copy(url = url, title = WeaverPages.titleOf(url)) }
            pageHosts.remove(tabId)
            return
        }
        val web = views[tabId]
        if (tab.isInternal && web != null) backStacks.getOrPut(tabId) { ArrayDeque() }.addLast(tab.url)
        updateTab(tabId) { it.copy(url = url, title = url) }
        // Without a WebView yet, the page loads when the tab is shown.
        web?.loadUrl(url)
    }

    /** Back inside the tab; false when there is nowhere left to go. */
    fun back(tabId: String): Boolean {
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return false
        val web = views[tabId]
        if (!tab.isInternal && web?.canGoBack() == true) { web.goBack(); return true }
        val previous = backStacks[tabId]?.removeLastOrNull() ?: return false
        if (!WeaverPages.isInternal(previous) && web != null) {
            val current = web.url ?: previous
            updateTab(tabId) { it.copy(url = current, title = web.title ?: current) }
        } else {
            updateTab(tabId) { it.copy(url = previous, title = WeaverPages.titleOf(previous)) }
        }
        return true
    }

    fun canGoBack(tabId: String): Boolean {
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return false
        return (!tab.isInternal && views[tabId]?.canGoBack() == true) || backStacks[tabId].orEmpty().isNotEmpty()
    }

    fun forward(tabId: String) { views[tabId]?.takeIf { it.canGoForward() }?.goForward() }

    fun reload(tabId: String) { views[tabId]?.reload() }

    fun stop(tabId: String) { views[tabId]?.stopLoading() }

    fun home(tabId: String) = navigate(tabId, WeaverPages.NEW_TAB)

    private fun upgrade(url: String): String {
        if (!data.value.settings.shields.upgradeHttps || !url.startsWith("http://")) return url
        val host = Uri.parse(url).host.orEmpty()
        val local = host == "localhost" || host.endsWith(".local") || host.endsWith(".test") ||
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
        return if (local || shieldsDown(host)) url else "https://" + url.removePrefix("http://")
    }

    // ---------------------------------------------------------------- shields

    fun shieldsDown(host: String): Boolean = host.removePrefix("www.") in data.value.shieldsDownHosts

    fun setShieldsUp(host: String, up: Boolean) {
        store.update { d ->
            d.copy(shieldsDownHosts = if (up) d.shieldsDownHosts - host else d.shieldsDownHosts + host)
        }
        data.value.selectedTabId?.let { reload(it) }
    }

    fun setShields(transform: (ShieldsDefaults) -> ShieldsDefaults) {
        store.update { it.copy(settings = it.settings.copy(shields = transform(it.settings.shields))) }
        // Script and cookie settings live on each WebView.
        views.forEach { (id, web) -> data.value.tabs.firstOrNull { it.id == id }?.let { applySettings(web, it) } }
    }

    fun setSettings(transform: (BrowserSettings) -> BrowserSettings) {
        store.update { it.copy(settings = transform(it.settings)) }
        views.forEach { (id, web) -> data.value.tabs.firstOrNull { it.id == id }?.let { applySettings(web, it) } }
    }

    fun clearStats() = store.update { it.copy(stats = BrowserStats()) }

    private fun countBlocked(tabId: String) {
        updateLive(tabId) { it.copy(blocked = it.blocked + 1) }
        val private = data.value.tabs.firstOrNull { it.id == tabId }?.private == true
        if (!private) store.update { d ->
            d.copy(stats = d.stats.copy(
                blocked = d.stats.blocked + 1,
                bytesSaved = d.stats.bytesSaved + Shields.BYTES_PER_BLOCK,
                msSaved = d.stats.msSaved + Shields.MS_PER_BLOCK,
            ))
        }
    }

    // ---------------------------------------------------------------- bookmarks, history, favorites

    fun isBookmarked(url: String) = data.value.bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String) = store.update { d ->
        if (d.bookmarks.any { it.url == url }) d.copy(bookmarks = d.bookmarks.filterNot { it.url == url })
        else d.copy(bookmarks = listOf(BrowserBookmark(url, title.ifBlank { url })) + d.bookmarks)
    }

    fun removeBookmark(url: String) = store.update { d -> d.copy(bookmarks = d.bookmarks.filterNot { it.url == url }) }

    fun removeVisit(visit: BrowserVisit) = store.update { d -> d.copy(history = d.history - visit) }

    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        if (history) store.update { it.copy(history = emptyList(), recentlyClosed = emptyList()) }
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
        if (cache) views.values.forEach { it.clearCache(true) }
        _events.tryEmit(BrowserEvent.Notice("Browsing data deleted"))
    }

    fun addFavorite(url: String, title: String) = store.update { d ->
        if (d.favorites.any { it.url == url }) d else d.copy(favorites = d.favorites + BrowserFavorite(url, title.ifBlank { hostOf(url) }))
    }

    fun removeFavorite(url: String) = store.update { d -> d.copy(favorites = d.favorites.filterNot { it.url == url }) }

    private fun recordVisit(tab: BrowserTab, url: String, title: String) {
        if (tab.private || !data.value.settings.saveHistory || WeaverPages.isInternal(url) || url.startsWith("about:")) return
        store.update { d ->
            val last = d.history.firstOrNull()
            val history = if (last?.url == url) listOf(last.copy(title = title, at = System.currentTimeMillis())) + d.history.drop(1)
            else listOf(BrowserVisit(url, title)) + d.history
            d.copy(history = history.take(BrowserStore.MAX_HISTORY))
        }
    }

    // ---------------------------------------------------------------- page tools

    fun share(tabId: String) {
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return
        _events.tryEmit(BrowserEvent.Share(tab.url, tab.title))
    }

    fun setDesktop(tabId: String, desktop: Boolean) {
        val web = views[tabId] ?: return
        updateLive(tabId) { it.copy(desktop = desktop) }
        web.settings.userAgentString = if (desktop) desktopAgent(web) else null
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = desktop
        web.reload()
    }

    private fun desktopAgent(web: WebView): String =
        WebSettings.getDefaultUserAgent(web.context)
            .replace(Regex("\\(Linux; Android [^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "")

    fun startFind(tabId: String) { _find.value = FindState(); views[tabId]?.setFindListener { active, total, _ ->
        _find.update { it?.copy(active = if (total == 0) 0 else active + 1, total = total) }
    } }

    fun find(tabId: String, query: String) {
        _find.update { it?.copy(query = query) }
        if (query.isBlank()) views[tabId]?.clearMatches() else views[tabId]?.findAllAsync(query)
    }

    fun findNext(tabId: String, forward: Boolean) = views[tabId]?.findNext(forward)

    fun endFind(tabId: String) { views[tabId]?.clearMatches(); _find.value = null }

    /** A small picture of the page for the tab switcher. */
    fun captureThumbnail(tabId: String) {
        val web = views[tabId] ?: return
        if (web.width <= 0 || web.height <= 0 || data.value.tabs.firstOrNull { it.id == tabId }?.isInternal == true) return
        runCatching {
            val full = web.drawToBitmap()
            val scaled = full.scale(full.width / 3, full.height / 3)
            if (scaled !== full) full.recycle()
            updateLive(tabId) { it.copy(thumbnail = scaled) }
        }
    }

    fun onFilesPicked(uris: List<Uri>) {
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        _fullscreen.value = null
    }

    fun download(tabId: String, url: String, userAgent: String? = null, contentDisposition: String? = null, mime: String? = null) {
        val web = views[tabId] ?: return
        val context = web.context.applicationContext
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setMimeType(mime)
                .addRequestHeader("User-Agent", userAgent ?: web.settings.userAgentString)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            store.update { d -> d.copy(downloads = listOf(BrowserDownload(id, url, name, mime.orEmpty())) + d.downloads) }
            _events.tryEmit(BrowserEvent.Notice("Downloading $name"))
        }.onFailure { _events.tryEmit(BrowserEvent.Notice("Couldn't download $name")) }
    }

    fun removeDownload(item: BrowserDownload) = store.update { d -> d.copy(downloads = d.downloads - item) }

    // ---------------------------------------------------------------- Leo

    fun clearLeo() { _leo.value = emptyList() }

    /** Ask Leo, Weaverse's assistant, about anything, or about the page that's open. */
    fun askLeo(tabId: String?, question: String, aboutPage: Boolean) {
        if (question.isBlank() || _leoBusy.value) return
        _leo.update { it + LeoMessage(false, question) }
        _leoBusy.value = true
        viewModelScope.launch {
            val page = if (aboutPage && tabId != null) pageText(tabId) else null
            val tab = tabId?.let { id -> data.value.tabs.firstOrNull { it.id == id } }
            val history = _leo.value.dropLast(1).takeLast(8).map { (if (it.fromLeo) "assistant" else "user") to it.text }
            val system = buildList {
                add("You are Leo, the AI assistant built into WeaverBrowser inside the Weaverse writing app. Answer clearly and concisely in plain text.")
                if (page != null) add("The user is reading \"${tab?.title}\" (${tab?.url}). Page text, possibly cut short:\n\n" + page.take(14_000))
            }
            val reply = runCatching {
                aiGeneration.complete(
                    userMessage = question,
                    assembled = AssembledPrompt(systemBlocks = system, messages = history, usedEntries = emptyList(), tokenBreakdown = emptyList()),
                    maxTokens = 900,
                    temperature = 0.4,
                ).text.trim()
            }.getOrElse { it.message?.takeIf { m -> m.isNotBlank() } ?: "Leo couldn't answer. Check your model and API key in Settings." }
            _leo.update { it + LeoMessage(true, reply.ifBlank { "(No answer came back.)" }) }
            _leoBusy.value = false
        }
    }

    private suspend fun pageText(tabId: String): String? {
        val web = views[tabId] ?: return null
        return suspendCancellableCoroutine { cont ->
            web.evaluateJavascript("(function(){return [document.title, (document.body ? document.body.innerText : '')];})()") { raw ->
                val text = runCatching { JSONArray(raw).let { it.optString(0) + "\n\n" + it.optString(1) } }.getOrNull()
                if (cont.isActive) cont.resume(text)
            }
        }
    }

    // ---------------------------------------------------------------- WebViews

    /**
     * The tab's WebView, kept for as long as the tab is open so switching tabs or workspaces
     * doesn't reload pages. Its context is swapped to the current Activity each time it's shown.
     */
    fun webViewFor(tabId: String, activity: Context): WebView? {
        val tab = data.value.tabs.firstOrNull { it.id == tabId } ?: return null
        views[tabId]?.let { web ->
            (web.context as? MutableContextWrapper)?.baseContext = activity
            return web
        }
        val web = createWebView(tab, activity)
        views[tabId] = web
        if (!tab.isInternal) web.loadUrl(tab.url)
        return web
    }

    fun hasView(tabId: String) = views.containsKey(tabId)

    private fun destroyView(tabId: String) {
        views.remove(tabId)?.let { web ->
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        pageHosts.remove(tabId)
        backStacks.remove(tabId)
        _live.update { it - tabId }
    }

    private fun deletePrivateProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching { ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab, activity: Context): WebView {
        val web = WebView(MutableContextWrapper(activity))
        if (tab.private && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
                WebViewCompat.setProfile(web, PRIVATE_PROFILE)
            }
        }
        web.settings.apply {
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (tab.private) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        applySettings(web, tab)
        if (data.value.settings.desktopByDefault) {
            web.settings.userAgentString = desktopAgent(web)
            updateLive(tab.id) { it.copy(desktop = true) }
        }
        if (data.value.settings.shields.blockFingerprinting && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(web, Shields.FINGERPRINT_SCRIPT, setOf("*")) }
        }
        val tabId = tab.id
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val pageHost = pageHosts[tabId].orEmpty()
                if (pageHost.isNotEmpty() && pageHost in data.value.shieldsDownHosts) return null
                val level = data.value.settings.shields.adBlock
                if (!Shields.shouldBlock(request.url, pageHost, level, request.isForMainFrame)) return null
                view.post { countBlocked(tabId) }
                return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme.orEmpty().lowercase()
                return when {
                    WeaverPages.isInternal(url) -> { navigate(tabId, url); true }
                    scheme == "http" && request.isForMainFrame && upgrade(url) != url -> { view.loadUrl(upgrade(url)); true }
                    scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript" -> false
                    else -> { openExternally(view.context, url); true }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageHosts[tabId] = hostOf(url)
                updateLive(tabId) { it.copy(blocked = 0, progress = 5, favicon = favicon ?: it.favicon, secure = url.startsWith("https://")) }
                updateTab(tabId) { if (it.isInternal) it else it.copy(url = url) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                val current = data.value.tabs.firstOrNull { it.id == tabId } ?: return
                if (data.value.settings.shields.adBlock == AdBlockLevel.Aggressive && !shieldsDown(hostOf(url))) {
                    view.evaluateJavascript(Shields.cosmeticScript(), null)
                }
                recordVisit(current, url, view.title ?: url)
                if (!current.private) CookieManager.getInstance().flush()
                updateLive(tabId) { it.copy(canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                pageHosts[tabId] = hostOf(url)
                updateTab(tabId) { if (it.isInternal) it else it.copy(url = url) }
                updateLive(tabId) { it.copy(canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = updateLive(tabId) { it.copy(progress = newProgress) }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) updateTab(tabId) { if (it.isInternal) it else it.copy(title = title) }
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) = updateLive(tabId) { it.copy(favicon = icon) }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val host = pageHosts[tabId].orEmpty()
                val allowed = isUserGesture || !data.value.settings.shields.blockPopups || host in data.value.popupHosts
                if (!allowed) {
                    _events.tryEmit(BrowserEvent.Notice("Pop-up blocked"))
                    return false
                }
                val opener = data.value.tabs.firstOrNull { it.id == tabId } ?: return false
                val child = newTab(url = "about:blank", private = opener.private)
                val childView = createWebView(child, (view.context as? MutableContextWrapper)?.baseContext ?: view.context)
                views[child.id] = childView
                (resultMsg.obj as WebView.WebViewTransport).webView = childView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                views.entries.firstOrNull { it.value === window }?.key?.let { closeTab(it) }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenCallback = callback
                _fullscreen.value = view
            }

            override fun onHideCustomView() {
                fullscreenCallback = null
                _fullscreen.value = null
            }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                _events.tryEmit(BrowserEvent.PickFiles)
                return true
            }
        }
        web.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            download(tabId, url, userAgent, contentDisposition, mimetype)
        }
        web.setOnLongClickListener { v ->
            val hit = (v as WebView).hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> { _events.tryEmit(BrowserEvent.LinkMenu(tabId, hit.extra, null)); true }
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val image = hit.extra
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    val msg = handler.obtainMessage()
                    v.requestFocusNodeHref(msg)
                    val link = msg.data.getString("url")?.takeIf { it.isNotBlank() && it != image }
                    _events.tryEmit(BrowserEvent.LinkMenu(tabId, link, image))
                    true
                }
                else -> false
            }
        }
        return web
    }

    private fun applySettings(web: WebView, tab: BrowserTab) {
        val shields = data.value.settings.shields
        val host = hostOf(tab.url)
        val down = shieldsDown(host)
        web.settings.javaScriptEnabled = down || !shields.blockScripts
        web.settings.textZoom = data.value.settings.textZoom
        val cookies = if (tab.private && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { ProfileStore.getInstance().getProfile(PRIVATE_PROFILE)?.cookieManager }.getOrNull()
        } else null
        (cookies ?: CookieManager.getInstance()).apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, down || !shields.blockCrossSiteCookies)
        }
    }

    private fun openExternally(context: Context, url: String) {
        runCatching {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            } else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // intent:// links carry a web fallback for when the app isn't installed.
                intent.getStringExtra("browser_fallback_url")?.let { fallback -> data.value.selectedTabId?.let { navigate(it, fallback) } }
                    ?: _events.tryEmit(BrowserEvent.Notice("No app can open this link"))
            }
        }
    }

    override fun onCleared() {
        views.keys.toList().forEach { destroyView(it) }
        deletePrivateProfile()
        super.onCleared()
    }

    companion object {
        const val PRIVATE_PROFILE = "weaver-private"
    }
}
