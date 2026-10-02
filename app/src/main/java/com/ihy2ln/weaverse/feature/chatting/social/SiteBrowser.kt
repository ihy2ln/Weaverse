package com.ihy2ln.weaverse.feature.chatting.social

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** One site in the social browser: its name, start page, brand color and section. */
data class BrowseSite(val name: String, val url: String, val colorHex: String, val section: String, val adult: Boolean = false)

/** The social browser's built-in sites, by section. Your own sites are added on top. */
object BrowseSites {
    val ALL = listOf(
        BrowseSite("X", "https://x.com/home", "#000000", "Social"),
        BrowseSite("TikTok", "https://www.tiktok.com/foryou", "#FE2C55", "Social"),
        BrowseSite("Instagram", "https://www.instagram.com/", "#C13584", "Social"),
        BrowseSite("Facebook", "https://m.facebook.com/", "#1877F2", "Social"),
        BrowseSite("Threads", "https://www.threads.net/", "#101010", "Social"),
        BrowseSite("Bluesky", "https://bsky.app/", "#1185FE", "Social"),
        BrowseSite("Mastodon", "https://mastodon.social/explore", "#6364FF", "Social"),
        BrowseSite("Tumblr", "https://www.tumblr.com/dashboard", "#36465D", "Social"),
        BrowseSite("Pinterest", "https://www.pinterest.com/", "#E60023", "Social"),
        BrowseSite("YouTube", "https://m.youtube.com/", "#FF0000", "Video"),
        BrowseSite("Twitch", "https://m.twitch.tv/", "#9146FF", "Video"),
        BrowseSite("Reddit", "https://www.reddit.com/", "#FF4500", "Forums"),
        BrowseSite("Lemmy", "https://lemmy.world/", "#00BC8C", "Forums"),
        BrowseSite("4chan", "https://boards.4chan.org/v/", "#789922", "Forums"),
        BrowseSite("Civitai", "https://civitai.com/images", "#1971C2", "Art"),
        BrowseSite("OnlyFans", "https://onlyfans.com/", "#00AFF0", "18+", adult = true),
        BrowseSite("Fansly", "https://fansly.com/", "#1FA7F8", "18+", adult = true),
        BrowseSite("RedGIFs", "https://www.redgifs.com/", "#E8205B", "18+", adult = true),
        BrowseSite("Pornhub", "https://www.pornhub.com/", "#FF9000", "18+", adult = true),
        BrowseSite("Eporner", "https://www.eporner.com/", "#3D7FD9", "18+", adult = true),
        BrowseSite("lemmynsfw", "https://lemmynsfw.com/", "#00BC8C", "18+", adult = true),
        BrowseSite("Civitai 18+", "https://civitai.red/images", "#1971C2", "18+", adult = true),
    )

    /** Your own sites, one "Name|https://…" per line (or just the address). */
    fun decodeCustom(text: String): List<BrowseSite> = text.lines().mapNotNull { line ->
        val raw = line.trim().ifBlank { return@mapNotNull null }
        val url = raw.substringAfter('|').trim().let { if (it.startsWith("http")) it else "https://$it" }
        val name = if ('|' in raw) raw.substringBefore('|').trim() else url.substringAfter("://").substringBefore('/').removePrefix("www.")
        BrowseSite(name, url, "#5B6474", "Your sites")
    }
}

/**
 * WeaverSocial as a social-media browser: the sites you use, each opening as itself with your
 * sign-in kept like any browser (X, TikTok, Instagram, Reddit, OnlyFans…), plus your own sites.
 * Open sites stay as tabs. It's a browser only: nothing is read out of the pages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialBrowserHub(
    adultEnabled: Boolean,
    customSites: String,
    onSaveCustomSites: (String) -> Unit,
    tabs: List<BrowseSite>,
    onOpen: (BrowseSite) -> Unit,
    onCloseTab: (BrowseSite) -> Unit,
    surface: Color,
    text: Color,
    muted: Color,
    raised: Color,
) {
    var adding by remember { mutableStateOf("") }
    val sites = BrowseSites.ALL.filter { adultEnabled || !it.adult } + BrowseSites.decodeCustom(customSites)
    Column(Modifier.fillMaxSize().background(surface).verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        Text("Browse", color = text, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp))
        Text("Your social sites inside WeaverSocial. Sign in once; each site remembers you.", color = muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (tabs.isNotEmpty()) {
            Section("Open tabs", text)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { tab ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(brand(tab.colorHex)).clickable { onOpen(tab) }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("✕", color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).clickable { onCloseTab(tab) }.padding(horizontal = 6.dp))
                    }
                }
            }
        }
        sites.groupBy { it.section }.forEach { (section, list) ->
            Section(section, text)
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { site ->
                    Column(Modifier.width(100.dp).clip(RoundedCornerShape(14.dp)).background(raised).clickable { onOpen(site) }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(brand(site.colorHex)), contentAlignment = Alignment.Center) {
                            Text(site.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Text(site.name, color = text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Section("Add a site", text)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(adding, { adding = it }, singleLine = true, textStyle = TextStyle(color = text, fontSize = 15.sp),
                cursorBrush = SolidColor(text),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(raised).padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (adding.isEmpty()) Text("Any site or forum address", color = muted, fontSize = 15.sp)
                    inner()
                })
            Spacer(Modifier.width(8.dp))
            Text("Add", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(brand("#7C5CFF")).clickable(enabled = adding.isNotBlank()) {
                    onSaveCustomSites((customSites.lines() + adding.trim()).filter { it.isNotBlank() }.distinct().joinToString("\n"))
                    adding = ""
                }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Section(title: String, color: Color) {
    Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp))
}

private fun brand(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.DarkGray)

/**
 * One site open as itself: the site's own pages with back/forward and reload, sign-in kept
 * between visits. Only a browser; nothing is read out of the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteBrowser(site: BrowseSite, onClose: () -> Unit, onHub: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf(site.url) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onHub() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(brand(site.colorHex)).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BarButton("⌂", true, onHub)
            BarButton("‹", canBack) { web?.goBack() }
            BarButton("›", canForward) { web?.goForward() }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(site.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(address, color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BarButton("⟳", true) { web?.reload() }
            BarButton("✕", true, onClose)
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.White)
        }
        key(site.url) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                address = url
                                canBack = view.canGoBack()
                                canForward = view.canGoForward()
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                // Keep the sign-in for next time.
                                CookieManager.getInstance().flush()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        loadUrl(site.url)
                        web = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BarButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = .35f), fontSize = 19.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 9.dp, vertical = 4.dp))
}
