package com.ihy2ln.weaverse.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** A dimmed layer that closes whatever sits on it when tapped outside. */
@Composable
private fun Scrim(onDismiss: () -> Unit, alpha: Float = .35f, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) { content() }
}

private val swallow: Modifier
    @Composable get() = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

// ---------------------------------------------------------------- Shields

@Composable
fun ShieldsPanel(
    host: String,
    isWeb: Boolean,
    blocked: Int,
    shields: ShieldsDefaults,
    up: Boolean,
    colors: BrowserColors,
    favicon: android.graphics.Bitmap?,
    onToggle: (Boolean) -> Unit,
    onShields: ((ShieldsDefaults) -> ShieldsDefaults) -> Unit,
    onDismiss: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    Scrim(onDismiss, alpha = .2f) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 62.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(colors.menu).then(swallow).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.field)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isWeb) SiteIcon("https://$host", favicon, colors, size = 40) else ShieldGlyph(up = true, size = 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(if (isWeb) host else "Weaverse page", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (!isWeb) "Shields aren't needed here" else if (up) "Shields up for this site" else "Shields down for this site",
                            color = colors.text, fontSize = 15.sp,
                        )
                    }
                    if (isWeb) Switch(
                        checked = up,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (up) "$blocked" else "0", color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("  Trackers, ads, and more blocked.", color = colors.text, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.field)) {
                Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Advanced controls", color = colors.accent, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Icon(if (advanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = colors.text)
                }
                if (advanced) {
                    Text("These apply to every site.", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
                    Text("Trackers & ads blocking", color = colors.text, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 6.dp))
                    Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdBlockLevel.entries.forEach { level ->
                            val on = shields.adBlock == level
                            Text(level.label, color = if (on) colors.onAccent else colors.text, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.accent else colors.menu)
                                    .clickable { onShields { s -> s.copy(adBlock = level) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    ShieldSwitch("Upgrade connections to HTTPS", shields.upgradeHttps, colors) { v -> onShields { it.copy(upgradeHttps = v) } }
                    ShieldSwitch("Block scripts", shields.blockScripts, colors) { v -> onShields { it.copy(blockScripts = v) } }
                    ShieldSwitch("Block fingerprinting", shields.blockFingerprinting, colors) { v -> onShields { it.copy(blockFingerprinting = v) } }
                    ShieldSwitch("Block cross-site cookies", shields.blockCrossSiteCookies, colors) { v -> onShields { it.copy(blockCrossSiteCookies = v) } }
                    ShieldSwitch("Block pop-ups", shields.blockPopups, colors) { v -> onShields { it.copy(blockPopups = v) } }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Text(
                "If this site appears broken, try Shields down.\nNote: this may reduce WeaverBrowser's privacy protections.",
                color = colors.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ShieldSwitch(label: String, checked: Boolean, colors: BrowserColors, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}

// ---------------------------------------------------------------- menu

@Composable
fun BrowserMenu(
    tab: BrowserTab,
    live: TabLive,
    colors: BrowserColors,
    bookmarked: Boolean,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onPage: (String) -> Unit,
    onLeo: () -> Unit,
    onFind: () -> Unit,
    onDesktop: () -> Unit,
    onAddFavorite: () -> Unit,
    onForward: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onReload: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val web = !tab.isInternal
    Scrim(onDismiss, alpha = .15f) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp).navigationBarsPadding()
                .widthIn(max = 330.dp).fillMaxWidth(.66f).fillMaxHeight(.92f)
                .clip(RoundedCornerShape(26.dp)).background(colors.menu).then(swallow),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                @Composable fun item(icon: ImageVector, label: String, trailing: (@Composable () -> Unit)? = null, action: () -> Unit) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onDismiss(); action() }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = colors.text, modifier = Modifier.size(24.dp))
                        Text(label, color = colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 18.dp))
                        trailing?.invoke()
                    }
                }
                @Composable fun divider() = Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(colors.hairline))
                item(Icons.Filled.Add, "New tab", action = onNewTab)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onDismiss(); onNewPrivateTab() }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassesGlyph(colors.text, size = 24.dp)
                    Text("New Private tab", color = colors.text, fontSize = 17.sp, modifier = Modifier.padding(start = 18.dp))
                }
                divider()
                item(Icons.Outlined.History, "History") { onPage(WeaverPages.HISTORY) }
                item(Icons.Outlined.Download, "Downloads") { onPage(WeaverPages.DOWNLOADS) }
                item(Icons.Outlined.Bookmarks, "Bookmarks") { onPage(WeaverPages.BOOKMARKS) }
                item(Icons.Outlined.AutoAwesome, "Leo AI", action = onLeo)
                item(Icons.Outlined.Tab, "Recent tabs") { onPage(WeaverPages.RECENT_TABS) }
                if (web) {
                    divider()
                    item(Icons.Outlined.FindInPage, "Find in page", action = onFind)
                    item(Icons.Outlined.Public, "Desktop site", trailing = {
                        Icon(if (live.desktop) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (live.desktop) colors.accent else colors.text)
                    }, action = onDesktop)
                    item(Icons.Outlined.PushPin, "Add to favorites", action = onAddFavorite)
                }
                divider()
                item(Icons.Outlined.Public, "WeaverSocial") { onPage(WeaverPages.SOCIAL) }
                item(Icons.AutoMirrored.Outlined.Chat, "Chats") { onPage(WeaverPages.CHATS) }
                item(Icons.Outlined.Contacts, "Contacts") { onPage(WeaverPages.CONTACTS) }
                divider()
                item(Icons.Outlined.Settings, "Settings") { onPage(WeaverPages.SETTINGS) }
                item(Icons.Outlined.Tune, "Weaverse settings", action = onAppSettings)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", live.canGoForward && web, colors) { onDismiss(); onForward() }
                RoundAction(if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder, "Bookmark", web, colors) { onDismiss(); onBookmark() }
                RoundAction(Icons.Outlined.Share, "Share", web, colors) { onDismiss(); onShare() }
                RoundAction(Icons.Outlined.Refresh, "Reload", web, colors) { onDismiss(); onReload() }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, enabled: Boolean, colors: BrowserColors, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(colors.field).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (enabled) colors.text else colors.muted.copy(alpha = .5f), modifier = Modifier.size(26.dp)) }
}

// ---------------------------------------------------------------- tab switcher

@Composable
fun TabSwitcher(
    data: BrowserData,
    live: Map<String, TabLive>,
    startPrivate: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var private by remember { mutableStateOf(startPrivate) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val colors = browserColors(private)
    val tabs = data.tabs.filter { it.private == private }
        .filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
    Box(Modifier.fillMaxSize().background(colors.page).then(swallow)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 8.dp)) {
                Row(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(colors.field).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(50)).background(if (!private) colors.bar else Color.Transparent)
                        .clickable { private = false }, contentAlignment = Alignment.Center) {
                        TabCountGlyph(data.tabs.count { !it.private }, colors.text)
                    }
                    Box(Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(50)).background(if (private) colors.bar else Color.Transparent)
                        .clickable { private = true }, contentAlignment = Alignment.Center) {
                        GlassesGlyph(colors.text, size = 26.dp)
                    }
                }
                Text("Done", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(12.dp))
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(colors.field).padding(horizontal = 20.dp, vertical = 14.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Search your tabs", color = colors.muted, fontSize = 17.sp); inner() },
            )
            if (tabs.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (private) GlassesGlyph(colors.muted, size = 56.dp)
                    Text(if (private) "No Private tabs" else "No tabs", color = colors.text, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                    if (private) Text("Private tabs don't keep history, cookies or site data after you close them.", color = colors.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tabs, key = { it.id }) { item ->
                        val selected = item.id == data.selectedTabId
                        val tabLive = live[item.id]
                        val frame = if (selected) colors.accent else colors.field
                        val onFrame = if (selected) colors.onAccent else colors.text
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(26.dp)).background(frame)
                                .clickable { onSelect(item.id) }.padding(6.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (item.url == WeaverPages.NEW_TAB) ShieldGlyph(up = true, size = 22.dp, mono = onFrame)
                                else SiteIcon(item.url, tabLive?.favicon, colors, size = 22)
                                Text(item.title.ifBlank { item.url }, color = onFrame, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).clickable { onClose(item.id) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, "Close tab", tint = onFrame, modifier = Modifier.size(20.dp))
                                }
                            }
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(colors.bar), contentAlignment = Alignment.Center) {
                                val thumb = tabLive?.thumbnail
                                if (thumb != null && !item.isInternal) {
                                    Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (item.url == WeaverPages.NEW_TAB) ShieldGlyph(up = true, size = 40.dp) else SiteIcon(item.url, tabLive?.favicon, colors, size = 40)
                                        Text(displayUrl(item.url).ifEmpty { "New tab" }, color = colors.muted, fontSize = 12.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).navigationBarsPadding().size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(colors.accent).clickable { onNewTab(private) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "New tab", tint = colors.onAccent, modifier = Modifier.size(34.dp)) }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 24.dp).navigationBarsPadding().size(52.dp).clip(RoundedCornerShape(50)).clickable { menu = true },
            contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreVert, "Tab menu", tint = colors.text) }
        if (menu) {
            Scrim({ menu = false }, alpha = .15f) {
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 84.dp).navigationBarsPadding().widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(24.dp)).background(colors.menu).then(swallow).padding(vertical = 8.dp)) {
                    @Composable fun row(label: String, icon: @Composable () -> Unit, action: () -> Unit) {
                        Row(Modifier.clickable { menu = false; action() }.padding(horizontal = 20.dp, vertical = 14.dp).widthIn(min = 240.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon(); Text(label, color = colors.text, fontSize = 17.sp, modifier = Modifier.padding(start = 18.dp))
                        }
                    }
                    row("New tab", { Icon(Icons.Filled.Add, null, tint = colors.text) }) { onNewTab(false) }
                    row("New Private tab", { GlassesGlyph(colors.text, size = 24.dp) }) { onNewTab(true) }
                    row("Close all tabs", { Icon(Icons.Filled.Close, null, tint = colors.text) }) { onCloseAll(private) }
                    row("Settings", { Icon(Icons.Outlined.Settings, null, tint = colors.text) }, onSettings)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Leo

@Composable
fun LeoSheet(viewModel: BrowserViewModel, tab: BrowserTab, colors: BrowserColors, onDismiss: () -> Unit) {
    val messages by viewModel.leo.collectAsState()
    val busy by viewModel.leoBusy.collectAsState()
    var input by remember { mutableStateOf("") }
    var usePage by remember { mutableStateOf(!tab.isInternal) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val send = { text: String -> viewModel.askLeo(tab.id, text, usePage && !tab.isInternal); input = "" }
    Scrim(onDismiss) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.78f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.menu).then(swallow).imePadding().navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accent)
                Text("Leo AI", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                if (messages.isNotEmpty()) Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable { viewModel.clearLeo() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Delete, "New conversation", tint = colors.text)
                }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close Leo", tint = colors.text)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (messages.isEmpty()) item {
                    Column {
                        Text("Hi, I'm Leo. Ask me anything, or about the page you're on.", color = colors.text, fontSize = 16.sp)
                        Spacer(Modifier.height(14.dp))
                        val prompts = if (tab.isInternal) listOf("Give me a writing prompt", "Help me name a character", "What can WeaverBrowser do?")
                        else listOf("Summarize this page", "What are the key points?", "Explain this page simply")
                        prompts.forEach { p ->
                            Text(p, color = colors.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                                .border(BorderStroke(1.dp, colors.hairline), RoundedCornerShape(14.dp)).clickable { send(p) }.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromLeo) Alignment.CenterStart else Alignment.CenterEnd) {
                        Text(m.text, color = if (m.fromLeo) colors.text else colors.onAccent, fontSize = 15.sp,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (m.fromLeo) colors.field else colors.accent).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
                if (busy) item { Text("Leo is thinking…", color = colors.muted, fontSize = 14.sp) }
            }
            if (!tab.isInternal) {
                Row(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).clickable { usePage = !usePage }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (usePage) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (usePage) colors.accent else colors.muted, modifier = Modifier.size(20.dp))
                    Text("Use this page: ${hostOf(tab.url)}", color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(colors.field).padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner -> if (input.isEmpty()) Text("Ask Leo", color = colors.muted, fontSize = 16.sp); inner() },
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(if (input.isBlank() || busy) colors.field else colors.accent)
                    .clickable(enabled = input.isNotBlank() && !busy) { send(input) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Send", tint = if (input.isBlank() || busy) colors.muted else colors.onAccent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- long-press link menu

@Composable
fun LinkMenuDialog(
    menu: BrowserEvent.LinkMenu,
    colors: BrowserColors,
    onDismiss: () -> Unit,
    onOpenNewTab: (String, Boolean) -> Unit,
    onDownload: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val target = menu.link ?: menu.image ?: return
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.menu).padding(vertical = 10.dp)) {
            Text(target, color = colors.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            @Composable fun row(icon: ImageVector, label: String, action: () -> Unit) {
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = colors.text, modifier = Modifier.size(22.dp))
                    Text(label, color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp))
                }
            }
            menu.link?.let { link ->
                row(Icons.Outlined.OpenInNew, "Open in new tab") { onOpenNewTab(link, false) }
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); onOpenNewTab(link, true) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassesGlyph(colors.text, size = 22.dp)
                    Text("Open in Private tab", color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp))
                }
                row(Icons.Outlined.ContentCopy, "Copy link address") { copy(context, link); onCopied() }
                row(Icons.Outlined.Share, "Share link") { onShare(link) }
                row(Icons.Outlined.Download, "Download link") { onDownload(link) }
            }
            menu.image?.let { image ->
                row(Icons.Outlined.Image, "Open image in new tab") { onOpenNewTab(image, false) }
                row(Icons.Outlined.Download, "Download image") { onDownload(image) }
                if (menu.link == null) row(Icons.Outlined.ContentCopy, "Copy image address") { copy(context, image); onCopied() }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link", text))
}
