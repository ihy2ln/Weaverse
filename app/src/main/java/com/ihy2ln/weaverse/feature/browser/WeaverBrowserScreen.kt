package com.ihy2ln.weaverse.feature.browser

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.LocalChromeCollapse
import com.ihy2ln.weaverse.data.settings.AppearanceOverrides
import com.ihy2ln.weaverse.feature.chatting.DiscordChatScreen
import com.ihy2ln.weaverse.feature.chatting.social.WeaverSocialScreen
import com.ihy2ln.weaverse.feature.roleplay.friends.FriendsScreen

/**
 * WeaverBrowser: a Brave-style browser that is also Weaverse's home. Web pages open in tabs
 * with Shields, Private tabs, bookmarks, history, downloads and Leo; WeaverSocial, the
 * character chats and Contacts open as tabs of their own, and the other modes are favorites
 * on the New Tab Page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeaverBrowserScreen(
    selectedServerId: String?,
    selectedRoomId: String?,
    onServerSelect: (String?) -> Unit,
    onRoomSelect: (String?) -> Unit,
    onOpenMode: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
    /** Opens a codex entry in the full Codex editor. */
    onOpenCodexEntry: ((String) -> Unit)? = null,
    appearance: AppearanceOverrides = AppearanceOverrides(),
    wallpaperVisible: Boolean = true,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsState()
    val live by viewModel.live.collectAsState()
    val fullscreen by viewModel.fullscreen.collectAsState()
    val findState by viewModel.find.collectAsState()
    val context = LocalContext.current
    val collapse = LocalChromeCollapse.current
    val tab = data.tabs.firstOrNull { it.id == data.selectedTabId } ?: data.tabs.last()
    val tabLive = live[tab.id] ?: TabLive()
    val colors = browserColors(tab.private)

    var editing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var shieldsOpen by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    var leoOpen by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf<BrowserEvent.LinkMenu?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.onFilesPicked(uris)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowserEvent.OpenMode -> onOpenMode(event.mode)
                is BrowserEvent.Notice -> notice = event.text
                is BrowserEvent.LinkMenu -> linkMenu = event
                is BrowserEvent.Share -> context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, event.url).putExtra(Intent.EXTRA_SUBJECT, event.title),
                        null,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                BrowserEvent.PickFiles -> runCatching { filePicker.launch("*/*") }.onFailure { viewModel.onFilesPicked(emptyList()) }
            }
        }
    }
    // Opening a conversation from Recents lands on the Chats tab.
    LaunchedEffect(selectedRoomId) { if (selectedRoomId != null) viewModel.openOrFocus(WeaverPages.CHATS) }
    LaunchedEffect(tab.id, tab.url) { collapse.expand() }

    BackHandler(fullscreen != null) { viewModel.exitFullscreen() }
    BackHandler(editing || menuOpen || shieldsOpen || switcherOpen || leoOpen || findState != null) {
        when {
            switcherOpen -> switcherOpen = false
            leoOpen -> leoOpen = false
            menuOpen -> menuOpen = false
            shieldsOpen -> shieldsOpen = false
            findState != null -> viewModel.endFind(tab.id)
            else -> editing = false
        }
    }
    val canBack = tabLive.canGoBack || viewModel.canGoBack(tab.id)
    BackHandler(canBack && !editing) { viewModel.back(tab.id) }

    val imeOpen = WindowInsets.isImeVisible
    val barsHidden = collapse.collapsed && !editing
    Box(Modifier.fillMaxSize().background(if (tab.url == WeaverPages.NEW_TAB) Color.Transparent else colors.page)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(!barsHidden, enter = expandVertically(), exit = shrinkVertically()) {
                AddressBar(
                    tab = tab,
                    live = tabLive,
                    colors = colors,
                    editing = editing,
                    shieldsUp = !viewModel.shieldsDown(tab.host),
                    onEditing = { editing = it },
                    onSubmit = { typed -> editing = false; viewModel.submit(tab.id, typed) },
                    onShields = { shieldsOpen = !shieldsOpen },
                    onReload = { if (tabLive.progress in 1..99) viewModel.stop(tab.id) else viewModel.reload(tab.id) },
                )
            }
            if (!tab.isInternal && tabLive.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { tabLive.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.accent,
                    trackColor = Color.Transparent,
                )
            }
            findState?.let { find -> FindBar(find, colors, onQuery = { viewModel.find(tab.id, it) }, onNext = { viewModel.findNext(tab.id, it) }, onClose = { viewModel.endFind(tab.id) }) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Web pages stay alive underneath Weaverse pages so going back doesn't reload them.
                if (!tab.isInternal || viewModel.hasView(tab.id)) {
                    key(tab.id) {
                        AndroidView(
                            factory = { ctx -> FrameLayout(ctx) },
                            update = { container ->
                                val web = viewModel.webViewFor(tab.id, context) ?: return@AndroidView
                                if (web.parent !== container) {
                                    (web.parent as? ViewGroup)?.removeView(web)
                                    container.removeAllViews()
                                    container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                }
                                web.setOnScrollChangeListener { _, _, y, _, oldY -> collapse.onScroll((y - oldY).toFloat(), atTop = y <= 0) }
                            },
                            onRelease = { container -> container.removeAllViews() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (tab.isInternal) {
                    Box(Modifier.fillMaxSize().background(if (tab.url == WeaverPages.NEW_TAB) Color.Transparent else colors.page)) {
                        when (tab.url.substringBefore('?')) {
                            WeaverPages.NEW_TAB -> NewTabPage(
                                data = data,
                                colors = colors,
                                private = tab.private,
                                onOpen = { viewModel.navigate(tab.id, it) },
                                onAddFavorite = { url, title -> viewModel.addFavorite(url, title) },
                                onRemoveFavorite = viewModel::removeFavorite,
                                onHideStats = { viewModel.setSettings { s -> s.copy(showPrivacyStats = false) } },
                            )
                            WeaverPages.SOCIAL -> WeaverSocialScreen(
                                selectedServerId = selectedServerId,
                                selectedRoomId = null,
                                onServerSelect = onServerSelect,
                                onRoomSelect = onRoomSelect,
                                onOpenFriends = { viewModel.navigate(tab.id, WeaverPages.CONTACTS) },
                                onOpenUrl = { url -> viewModel.newTab(url) },
                                appearance = appearance,
                                wallpaperVisible = wallpaperVisible,
                            )
                            WeaverPages.CHATS -> DiscordChatScreen(
                                selectedServerId = selectedServerId,
                                selectedRoomId = selectedRoomId,
                                onServerSelect = onServerSelect,
                                onRoomSelect = onRoomSelect,
                                onOpenFriends = { viewModel.navigate(tab.id, WeaverPages.CONTACTS) },
                                appearance = appearance,
                                wallpaperVisible = wallpaperVisible,
                                onOpenCodexEntry = onOpenCodexEntry,
                            )
                            WeaverPages.CONTACTS -> FriendsScreen(
                                onOpenChat = { chatId ->
                                    onServerSelect(null)
                                    onRoomSelect(chatId)
                                    viewModel.navigate(tab.id, WeaverPages.CHATS)
                                },
                            )
                            WeaverPages.HISTORY -> HistoryPage(data, colors, onOpen = { viewModel.navigate(tab.id, it) }, onRemove = viewModel::removeVisit,
                                onClear = { viewModel.clearBrowsingData(history = true, cookies = false, cache = false) })
                            WeaverPages.BOOKMARKS -> BookmarksPage(data, colors, onOpen = { viewModel.navigate(tab.id, it) }, onRemove = viewModel::removeBookmark)
                            WeaverPages.DOWNLOADS -> DownloadsPage(data, colors, onRemove = viewModel::removeDownload)
                            WeaverPages.RECENT_TABS -> RecentTabsPage(data, colors, onReopen = viewModel::reopenClosed)
                            WeaverPages.SETTINGS -> BrowserSettingsPage(
                                data = data,
                                colors = colors,
                                onSettings = viewModel::setSettings,
                                onShields = viewModel::setShields,
                                onClearData = viewModel::clearBrowsingData,
                                onClearStats = viewModel::clearStats,
                                onOpenAppSettings = onOpenAppSettings,
                            )
                            else -> UnknownPage(tab.url, colors)
                        }
                    }
                }
                if (editing) {
                    Suggestions(
                        data = data,
                        colors = colors,
                        private = tab.private,
                        onOpen = { url -> editing = false; viewModel.navigate(tab.id, url) },
                    )
                }
                notice?.let { text ->
                    LaunchedEffect(text) { kotlinx.coroutines.delay(2_600); notice = null }
                    Text(
                        text,
                        color = colors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                            .clip(RoundedCornerShape(12.dp)).background(colors.menu).clickable { notice = null }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            if (data.settings.bottomToolbar) {
                AnimatedVisibility(!barsHidden && !imeOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    BottomToolbar(
                        colors = colors,
                        tabCount = data.tabs.count { it.private == tab.private },
                        onHome = { viewModel.home(tab.id) },
                        onBookmarks = { viewModel.navigate(tab.id, WeaverPages.BOOKMARKS) },
                        onSearch = { editing = true },
                        onTabs = { viewModel.captureThumbnail(tab.id); switcherOpen = true },
                        onMenu = { menuOpen = true },
                    )
                }
            }
        }
        if (shieldsOpen) {
            ShieldsPanel(
                host = tab.host,
                isWeb = !tab.isInternal,
                blocked = tabLive.blocked,
                shields = data.settings.shields,
                up = !viewModel.shieldsDown(tab.host),
                colors = colors,
                favicon = tabLive.favicon,
                onToggle = { viewModel.setShieldsUp(tab.host, it) },
                onShields = viewModel::setShields,
                onDismiss = { shieldsOpen = false },
            )
        }
        if (menuOpen) {
            BrowserMenu(
                tab = tab,
                live = tabLive,
                colors = colors,
                bookmarked = viewModel.isBookmarked(tab.url),
                onDismiss = { menuOpen = false },
                onNewTab = { viewModel.newTab() },
                onNewPrivateTab = { viewModel.newTab(private = true) },
                onPage = { viewModel.navigate(tab.id, it) },
                onLeo = { leoOpen = true },
                onFind = { viewModel.startFind(tab.id) },
                onDesktop = { viewModel.setDesktop(tab.id, !tabLive.desktop) },
                onAddFavorite = { viewModel.addFavorite(tab.url, tab.title); notice = "Added to favorites" },
                onForward = { viewModel.forward(tab.id) },
                onBookmark = { viewModel.toggleBookmark(tab.url, tab.title) },
                onShare = { viewModel.share(tab.id) },
                onReload = { viewModel.reload(tab.id) },
                onAppSettings = onOpenAppSettings,
            )
        }
        if (switcherOpen) {
            TabSwitcher(
                data = data,
                live = live,
                startPrivate = tab.private,
                onSelect = { viewModel.select(it); switcherOpen = false },
                onClose = viewModel::closeTab,
                onNewTab = { private -> viewModel.newTab(private = private); switcherOpen = false },
                onCloseAll = viewModel::closeAll,
                onSettings = { viewModel.navigate(tab.id, WeaverPages.SETTINGS); switcherOpen = false },
                onDismiss = { switcherOpen = false },
            )
        }
        if (leoOpen) {
            LeoSheet(viewModel = viewModel, tab = tab, colors = colors, onDismiss = { leoOpen = false })
        }
        linkMenu?.let { menu ->
            LinkMenuDialog(
                menu = menu,
                colors = colors,
                onDismiss = { linkMenu = null },
                onOpenNewTab = { url, private -> viewModel.newTab(url, private = private, select = false); notice = if (private) "Opened in a Private tab" else "Opened in a new tab" },
                onDownload = { url -> viewModel.download(menu.tabId, url) },
                onShare = { url -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                onCopied = { notice = "Link copied" },
            )
        }
        fullscreen?.let { view ->
            AndroidView(
                factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
                update = { container ->
                    if (view.parent !== container) {
                        (view.parent as? ViewGroup)?.removeView(view)
                        container.removeAllViews()
                        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------- address bar

@Composable
private fun AddressBar(
    tab: BrowserTab,
    live: TabLive,
    colors: BrowserColors,
    editing: Boolean,
    shieldsUp: Boolean,
    onEditing: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onShields: () -> Unit,
    onReload: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fullUrl = if (tab.url == WeaverPages.NEW_TAB) "" else tab.url
    var field by remember(tab.id, editing) { mutableStateOf(TextFieldValue(fullUrl, TextRange(0, fullUrl.length))) }
    // onFocusChanged also reports "not focused" when the field first appears; only a real
    // loss of focus (tapping elsewhere) should end editing.
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) runCatching { focus.requestFocus(); keyboard?.show() } else focusManager.clearFocus()
    }
    Row(
        Modifier.fillMaxWidth().background(colors.bar).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(colors.field)
                .clickable(enabled = !editing) { onEditing(true) }.padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leading: ImageVector = when {
                editing || tab.url == WeaverPages.NEW_TAB -> Icons.Filled.Search
                tab.isInternal -> Icons.Outlined.Public
                !live.secure -> Icons.Outlined.Warning
                else -> Icons.Outlined.Tune
            }
            Icon(leading, null, tint = colors.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.text, fontSize = 17.sp),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { onSubmit(field.text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) onEditing(false) },
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) Text(if (tab.private) "Search privately or type URL" else "Search or type URL", color = colors.muted, fontSize = 17.sp, maxLines = 1)
                            inner()
                        },
                    )
                } else {
                    val shown = searchTermsOf(tab.url) ?: displayUrl(tab.url)
                    Text(
                        shown.ifEmpty { if (tab.private) "Search privately or type URL" else "Search or type URL" },
                        color = if (shown.isEmpty()) colors.muted else colors.text,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (editing) {
                if (field.text.isNotEmpty()) BarIcon(Icons.Filled.Close, "Clear", colors.text) { field = TextFieldValue("") }
            } else if (!tab.isInternal) {
                if (live.progress in 1..99) BarIcon(Icons.Filled.Close, "Stop", colors.text, onReload)
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onShields), contentAlignment = Alignment.Center) {
                    ShieldGlyph(up = shieldsUp, size = 26.dp)
                }
            } else {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onShields), contentAlignment = Alignment.Center) {
                    ShieldGlyph(up = true, size = 26.dp, mono = colors.text)
                }
            }
        }
        if (editing) {
            Text("Cancel", color = colors.accent, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onEditing(false) }.padding(horizontal = 10.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Suggestions(data: BrowserData, colors: BrowserColors, private: Boolean, onOpen: (String) -> Unit) {
    // The field's current text isn't hoisted, so suggestions list what's most useful to reopen.
    val items = (data.bookmarks.map { Triple(it.url, it.title, true) } +
        data.history.distinctBy { it.url }.take(40).map { Triple(it.url, it.title, false) }).distinctBy { it.first }.take(30)
    LazyColumn(Modifier.fillMaxSize().background(colors.page)) {
        item {
            Text(if (private) "Private: nothing you visit is saved" else "Recent and bookmarked", color = colors.muted, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
        }
        items(items, key = { it.first }) { (url, title, bookmark) ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(url) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (bookmark) Icons.Outlined.BookmarkBorder else Icons.Outlined.History, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(title.ifBlank { url }, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(displayUrl(url), color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FindBar(find: FindState, colors: BrowserColors, onQuery: (String) -> Unit, onNext: (Boolean) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().background(colors.bar).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = find.query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner -> if (find.query.isEmpty()) Text("Find in page", color = colors.muted, fontSize = 16.sp); inner() },
        )
        Text(if (find.query.isBlank()) "" else "${find.active}/${find.total}", color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
        BarIcon(Icons.Filled.KeyboardArrowUp, "Previous", colors.text) { onNext(false) }
        BarIcon(Icons.Filled.KeyboardArrowDown, "Next", colors.text) { onNext(true) }
        BarIcon(Icons.Filled.Close, "Close find", colors.text, onClose)
    }
}

// ---------------------------------------------------------------- bottom toolbar

@Composable
private fun BottomToolbar(
    colors: BrowserColors,
    tabCount: Int,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onSearch: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(colors.bar).height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(onHome) { Icon(Icons.Outlined.Home, "Home", tint = colors.text, modifier = Modifier.size(26.dp)) }
        ToolbarButton(onBookmarks) { Icon(Icons.Outlined.BookmarkBorder, "Bookmarks", tint = colors.text, modifier = Modifier.size(26.dp)) }
        ToolbarButton(onSearch) { Icon(Icons.Filled.Search, "Search", tint = colors.text, modifier = Modifier.size(28.dp)) }
        ToolbarButton(onTabs) { TabCountGlyph(tabCount, colors.text) }
        ToolbarButton(onMenu) { Icon(Icons.Filled.MoreVert, "Menu", tint = colors.text, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun ToolbarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** The site's favicon, or its first letter in a circle. */
@Composable
internal fun SiteIcon(url: String, favicon: android.graphics.Bitmap?, colors: BrowserColors, size: Int = 28) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
        if (favicon != null) {
            androidx.compose.foundation.Image(favicon.asImageBitmap(), null, modifier = Modifier.size((size * .62f).dp))
        } else {
            val label = WeaverPages.titleOf(url).takeIf { WeaverPages.isInternal(url) } ?: hostOf(url)
            Text(label.take(1).uppercase().ifEmpty { "•" }, color = Color(0xFF1B1B21), fontSize = (size * .45f).sp, maxLines = 1)
        }
    }
}

@Composable
private fun UnknownPage(url: String, colors: BrowserColors) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Text("This page doesn't exist", color = colors.text, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        Text(url, color = colors.muted, fontSize = 13.sp)
    }
}
