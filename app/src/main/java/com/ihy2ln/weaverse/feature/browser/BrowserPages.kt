package com.ihy2ln.weaverse.feature.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// ---------------------------------------------------------------- New Tab Page

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewTabPage(
    data: BrowserData,
    colors: BrowserColors,
    private: Boolean,
    onOpen: (String) -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onHideStats: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<BrowserFavorite?>(null) }
    Box(
        Modifier.fillMaxSize().then(
            if (private) Modifier.background(Brush.verticalGradient(listOf(colors.bar, colors.page))) else Modifier,
        ),
    ) {
        // Brave puts a photo behind its New Tab Page; here the mode's backdrop (key art, a focus
        // video or the user's own picks) shows through from the shell, with the accent glowing in.
        if (!private) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(listOf(colors.accent.copy(alpha = .28f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(120f, 80f), radius = 1300f),
                ),
            )
        }
        if (private) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GlassesGlyph(colors.accent, size = 84.dp)
                Text("This is a Private tab", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                // Separate cookies need a WebView with profile support; older ones share them.
                val separateProfile = androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)
                Text(
                    (if (separateProfile) "WeaverBrowser doesn't save your history, cookies or site data from Private tabs once you close them. "
                    else "WeaverBrowser doesn't save your history from Private tabs. This phone's Android System WebView is too old " +
                        "to keep their cookies separate, so update it from the Play Store for fully private tabs. ") +
                        "Downloads and bookmarks you make are kept. Searches go to ${data.settings.privateSearchEngine.label}.",
                    color = colors.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 16.dp)) {
            if (data.settings.showPrivacyStats) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.cardScrim).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = colors.text, modifier = Modifier.size(22.dp))
                        Text("Privacy Stats", color = colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onHideStats), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.VisibilityOff, "Hide Privacy Stats", tint = colors.text, modifier = Modifier.size(22.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        val (bytes, bytesUnit) = sizeParts(data.stats.bytesSaved)
                        val (time, timeUnit) = timeParts(data.stats.msSaved)
                        Stat(compact(data.stats.blocked), "", "Trackers & Ads\nBlocked", colors.statOrange, Modifier.weight(1f), colors.text)
                        Stat(bytes, bytesUnit, "Est. Data\nSaved", colors.accent, Modifier.weight(1f), colors.text)
                        Stat(time, timeUnit, "Est. Time\nSaved", colors.text, Modifier.weight(1f), colors.text)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (data.settings.showFavorites) {
                FlowRow(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.cardScrim).padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 4,
                ) {
                    data.favorites.forEach { fav ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .combinedClickable(onClick = { onOpen(fav.url) }, onLongClick = { removing = fav }).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FavoriteIcon(fav.url, colors)
                            Text(fav.title, color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
                        }
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { adding = true }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(colors.text.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = colors.page, modifier = Modifier.size(30.dp))
                        }
                        Text("Add new", color = colors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    // Keeps the last row's tiles the same width as the others.
                    repeat((4 - (data.favorites.size + 1) % 4) % 4) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
    if (adding) AddFavoriteDialog(colors, onDismiss = { adding = false }, onAdd = { url, title ->
        adding = false
        onAddFavorite(resolveTyped(url, data.settings.searchEngine), title)
    })
    removing?.let { fav ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(fav.title) },
            text = { Text("Remove this from your favorites?") },
            confirmButton = { TextButton(onClick = { onRemoveFavorite(fav.url); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(value: String, unit: String, label: String, color: Color, modifier: Modifier, labelColor: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 40.sp)) { append(value) }
                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 22.sp)) { append(unit) }
            },
            color = color,
        )
        Text(label, color = labelColor, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}

private fun sizeParts(bytes: Long): Pair<String, String> = when {
    bytes >= 1L shl 30 -> "%.1f".format(bytes / (1L shl 30).toDouble()) to "GB"
    bytes >= 1L shl 20 -> "%.0f".format(bytes / (1L shl 20).toDouble()) to "MB"
    else -> (bytes / 1024).toString() to "KB"
}

private fun timeParts(ms: Long): Pair<String, String> {
    val s = ms / 1000
    return when {
        s >= 86_400 -> (s / 86_400).toString() to "d"
        s >= 3_600 -> (s / 3_600).toString() to "h"
        s >= 60 -> (s / 60).toString() to "min"
        else -> s.toString() to "s"
    }
}

private fun favoriteIcon(url: String): ImageVector? = when (url) {
    WeaverPages.SOCIAL -> Icons.Outlined.Public
    WeaverPages.CHATS -> Icons.AutoMirrored.Outlined.Chat
    WeaverPages.CONTACTS -> Icons.Outlined.Contacts
    "weaver://novel" -> Icons.AutoMirrored.Outlined.MenuBook
    "weaver://rpg" -> Icons.Outlined.Style
    "weaver://games" -> Icons.Outlined.SportsEsports
    "weaver://manga" -> Icons.Outlined.Collections
    "weaver://notes" -> Icons.Outlined.Lightbulb
    WeaverPages.HISTORY -> Icons.Outlined.History
    WeaverPages.BOOKMARKS -> Icons.Outlined.Bookmarks
    WeaverPages.DOWNLOADS -> Icons.Outlined.Download
    WeaverPages.SETTINGS -> Icons.Outlined.Settings
    else -> null
}

@Composable
private fun FavoriteIcon(url: String, colors: BrowserColors) {
    val icon = favoriteIcon(url)
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(if (icon != null) colors.accent else colors.text.copy(alpha = .92f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = colors.onAccent, modifier = Modifier.size(28.dp))
        else Text(hostOf(url).take(1).uppercase(), color = colors.page, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddFavoriteDialog(colors: BrowserColors, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add favorite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(title, { title = it }, "Name", colors)
                Field(url, { url = it }, "Address (e.g. archiveofourown.org)", colors)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(url, title) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, colors: BrowserColors) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.field).padding(14.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = colors.muted, fontSize = 16.sp); inner() },
    )
}

// ---------------------------------------------------------------- History, bookmarks, downloads, recent tabs

@Composable
private fun PageTitle(title: String, colors: BrowserColors, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun EntryRow(url: String, title: String, subtitle: String, colors: BrowserColors, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SiteIcon(url, null, colors, size = 34)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title.ifBlank { url }, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Remove", tint = colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyNote(text: String, colors: BrowserColors) {
    Text(text, color = colors.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
}

private fun dayLabel(at: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    return DateFormat.getDateInstance(DateFormat.FULL).format(Date(at))
}

@Composable
fun HistoryPage(data: BrowserData, colors: BrowserColors, onOpen: (String) -> Unit, onRemove: (BrowserVisit) -> Unit, onClear: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val visits = data.history.filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageTitle("History", colors) { if (data.history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Clear", color = colors.accent) } }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Field(query, { query = it }, "Search your history", colors) }
        }
        if (visits.isEmpty()) item { EmptyNote(if (data.history.isEmpty()) "Pages you visit will appear here." else "No matches.", colors) }
        visits.groupBy { dayLabel(it.at) }.forEach { (day, list) ->
            item(key = "day-$day") { Text(day, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)) }
            items(list, key = { "${it.url}-${it.at}" }) { v ->
                EntryRow(v.url, v.title, "${time.format(Date(v.at))} · ${displayUrl(v.url)}", colors, onOpen = { onOpen(v.url) }, onRemove = { onRemove(v) })
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Clear history?") },
        text = { Text("Every page in your history and Recent tabs is removed.") },
        confirmButton = { TextButton(onClick = { confirm = false; onClear() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
fun BookmarksPage(data: BrowserData, colors: BrowserColors, onOpen: (String) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Bookmarks", colors) }
        if (data.bookmarks.isEmpty()) item { EmptyNote("Tap ☆ in the menu on any page to bookmark it.", colors) }
        items(data.bookmarks, key = { it.url }) { b ->
            EntryRow(b.url, b.title, displayUrl(b.url), colors, onOpen = { onOpen(b.url) }, onRemove = { onRemove(b.url) })
        }
    }
}

@Composable
fun DownloadsPage(data: BrowserData, colors: BrowserColors, onRemove: (BrowserDownload) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageTitle("Downloads", colors) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("All files", color = colors.accent) }
            }
        }
        if (data.downloads.isEmpty()) item { EmptyNote("Files you download are saved to your phone's Downloads folder.", colors) }
        items(data.downloads, key = { it.id }) { d ->
            EntryRow(d.url, d.fileName, "${dayLabel(d.at)} · ${hostOf(d.url)}", colors, onOpen = { openDownload(context, d) }, onRemove = { onRemove(d) })
        }
    }
}

private fun openDownload(context: Context, item: BrowserDownload) {
    runCatching {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(item.id) ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType.ifBlank { manager.getMimeTypeForDownloadedFile(item.id) })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun RecentTabsPage(data: BrowserData, colors: BrowserColors, onReopen: (BrowserTab) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Recent tabs", colors) }
        item { Text("Recently closed", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp)) }
        if (data.recentlyClosed.isEmpty()) item { EmptyNote("Tabs you close will appear here.", colors) }
        items(data.recentlyClosed, key = { it.id }) { t ->
            EntryRow(t.url, t.title, displayUrl(t.url), colors, onOpen = { onReopen(t) }, onRemove = null)
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun BrowserSettingsPage(
    data: BrowserData,
    colors: BrowserColors,
    onSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onShields: ((ShieldsDefaults) -> ShieldsDefaults) -> Unit,
    onClearData: (Boolean, Boolean, Boolean) -> Unit,
    onClearStats: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val s = data.settings
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = private engine
    var clearing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PageTitle("Settings", colors)
        Group("Features", colors)
        LinkRow("Weaverse settings", "Models, appearance, backups and the rest of the app", colors, onOpenAppSettings)
        Group("Search engines", colors)
        LinkRow("Standard tab", s.searchEngine.label, colors) { picking = false }
        LinkRow("Private tab", s.privateSearchEngine.label, colors) { picking = true }
        Group("Shields & privacy", colors)
        Text("Trackers & ads blocking", color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdBlockLevel.entries.forEach { level ->
                val on = s.shields.adBlock == level
                Text(level.label, color = if (on) colors.onAccent else colors.text, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.accent else colors.field)
                        .clickable { onShields { it.copy(adBlock = level) } }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
        ToggleRow("Upgrade connections to HTTPS", null, s.shields.upgradeHttps, colors) { v -> onShields { it.copy(upgradeHttps = v) } }
        ToggleRow("Block scripts", "Sites may not work", s.shields.blockScripts, colors) { v -> onShields { it.copy(blockScripts = v) } }
        ToggleRow("Block fingerprinting", "Applies to tabs opened from now on", s.shields.blockFingerprinting, colors) { v -> onShields { it.copy(blockFingerprinting = v) } }
        ToggleRow("Block cross-site cookies", null, s.shields.blockCrossSiteCookies, colors) { v -> onShields { it.copy(blockCrossSiteCookies = v) } }
        ToggleRow("Block pop-ups", null, s.shields.blockPopups, colors) { v -> onShields { it.copy(blockPopups = v) } }
        ToggleRow("Save browsing history", null, s.saveHistory, colors) { v -> onSettings { it.copy(saveHistory = v) } }
        LinkRow("Delete browsing data", "History, cookies and site data, cached files", colors) { clearing = true }
        LinkRow("Reset Privacy Stats", "${data.stats.blocked} trackers & ads blocked so far", colors, onClearStats)
        Group("New Tab Page", colors)
        ToggleRow("Show Privacy Stats", null, s.showPrivacyStats, colors) { v -> onSettings { it.copy(showPrivacyStats = v) } }
        ToggleRow("Show favorites", null, s.showFavorites, colors) { v -> onSettings { it.copy(showFavorites = v) } }
        Group("Display", colors)
        ToggleRow("Bottom toolbar", "Home, bookmarks, search, tabs and menu at the bottom", s.bottomToolbar, colors) { v -> onSettings { it.copy(bottomToolbar = v) } }
        ToggleRow("Desktop site by default", null, s.desktopByDefault, colors) { v -> onSettings { it.copy(desktopByDefault = v) } }
        Text("Text size: ${s.textZoom}%", color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp, top = 12.dp))
        Slider(
            value = s.textZoom.toFloat(),
            onValueChange = { v -> onSettings { it.copy(textZoom = (v / 5).toInt() * 5) } },
            valueRange = 50f..200f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Group("About", colors)
        Text(
            "WeaverBrowser runs on Android System WebView. Shields block known ad and tracker networks on this phone; nothing you browse is sent to Weaverse.",
            color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
    picking?.let { forPrivate ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (forPrivate) "Private tab search engine" else "Search engine") },
            text = {
                Column {
                    SearchEngine.entries.forEach { engine ->
                        val current = if (forPrivate) s.privateSearchEngine else s.searchEngine
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            onSettings { if (forPrivate) it.copy(privateSearchEngine = engine) else it.copy(searchEngine = engine) }
                            picking = null
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(engine.label, modifier = Modifier.weight(1f), fontWeight = if (engine == current) FontWeight.Bold else FontWeight.Normal)
                            if (engine == current) Text("✓")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } },
        )
    }
    if (clearing) ClearDataDialog(colors, onDismiss = { clearing = false }) { h, c, k -> clearing = false; onClearData(h, c, k) }
}

@Composable
private fun ClearDataDialog(colors: BrowserColors, onDismiss: () -> Unit, onClear: (Boolean, Boolean, Boolean) -> Unit) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete browsing data") },
        text = {
            Column {
                ToggleRow("Browsing history", null, history, colors) { history = it }
                ToggleRow("Cookies and site data", "Signs you out of most sites", cookies, colors) { cookies = it }
                ToggleRow("Cached images and files", null, cache, colors) { cache = it }
            }
        },
        confirmButton = { TextButton(onClick = { onClear(history, cookies, cache) }, enabled = history || cookies || cache) { Text("Delete data") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Group(title: String, colors: BrowserColors) {
    Text(title, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun LinkRow(title: String, subtitle: String?, colors: BrowserColors, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, color = colors.text, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, colors: BrowserColors, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}
