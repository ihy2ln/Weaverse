package com.ihy2ln.weaverse.feature.storyboard

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.ihy2ln.weaverse.core.manga.GalleryAccountManager
import com.ihy2ln.weaverse.core.manga.GallerySessionPolicy

@Composable
internal fun GalleryAccountSettings(account: GalleryAccountManager, onClose: () -> Unit) {
    val state by account.state.collectAsState()
    var login by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("E-Hentai / ExHentai") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Built-in sources · 18+\nOnly enable these sources if you are an adult and are allowed to access them in your location.")
            Row { Text("Enable 18+ sources", Modifier.weight(1f)); Switch(state.enabled, { account.enable(it) }) }
            Text(if (state.sessionSaved) "Website session saved. Access is checked by the website on each request." else "Not signed in. Public E-Hentai browsing may work without an account.")
            Button(onClick = { login = true }, enabled = state.enabled, modifier = Modifier.fillMaxWidth()) { Text(if (state.sessionSaved) "Open website login / account" else "Log in on website") }
            Row { Text("Enable ExHentai", Modifier.weight(1f)); Switch(state.restricted, { account.enableRestricted(it) }, enabled = state.enabled && state.sessionSaved) }
            Text("Requires an eligible account. Signing in does not guarantee ExHentai access. Website restrictions are not bypassed.", style = MaterialTheme.typography.bodySmall)
            if (state.sessionSaved) TextButton(onClick = { logout = true }) { Text("Log out of these sources") }
            Text("Passwords are entered on the official website, never saved by WeaverVerse. Session secrets are excluded from backups. Favorites sync is not enabled; no remote favorites will be changed.", style = MaterialTheme.typography.bodySmall)
            if (status.isNotBlank()) Text(status)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
    if (login) GalleryLoginDialog(account, onClose = { login = false }, onSaved = { login = false; status = "Website session saved." })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Log out?") }, text = { Text("Removes this account session from WeaverVerse. Downloads and library entries are kept; other source logins are not removed.") },
        confirmButton = { TextButton(onClick = { account.logout(); logout = false; status = "Logged out." }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}

/** Official website form, without password interception, JS injection or debug bridges. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GalleryLoginDialog(account: GalleryAccountManager, onClose: () -> Unit, onSaved: () -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var host by remember { mutableStateOf("forums.e-hentai.org") }
    var status by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { browser?.stopLoading(); browser?.destroy() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { if (browser?.canGoBack() == true) browser?.goBack() else onClose() }) { Text("Back") }
                    TextButton(onClick = { browser?.loadUrl("https://e-hentai.org/bounce_login.php?b=d&bt=1-1") }) { Text("Alternate login") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text("Official website: $host", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium)
                if (status.isNotBlank()) Text(status, Modifier.padding(12.dp))
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        @Suppress("DEPRECATION") settings.saveFormData = false
                        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val allowed = GallerySessionPolicy.allowed(request.url.toString(), login = true)
                                if (!allowed && request.isForMainFrame) status = "Navigation outside the official login domains was blocked."
                                return request.isForMainFrame && !allowed
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                if (GallerySessionPolicy.allowed(url, login = true)) host = android.net.Uri.parse(url).host.orEmpty()
                            }
                        }
                        browser = this
                        loadUrl("https://forums.e-hentai.org/index.php?act=Login")
                    }
                })
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        if (account.prepareRestrictedWebSession()) browser?.loadUrl("https://exhentai.org/uconfig.php")
                        else status = "Complete website login first."
                    }, modifier = Modifier.weight(1f)) { Text("Check ExHentai access") }
                    Button(onClick = {
                        if (account.saveWebSession()) onSaved() else status = "No signed-in session found. Complete website login first."
                    }, modifier = Modifier.weight(1f).padding(8.dp)) { Text("Save session") }
                }
            }
        }
    }
}
