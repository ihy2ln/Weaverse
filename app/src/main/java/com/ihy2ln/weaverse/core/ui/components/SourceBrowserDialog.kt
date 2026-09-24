package com.ihy2ln.weaverse.core.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Visible user-controlled browser; shares cookies with rendered catalogs. No auto verification. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceBrowserDialog(url: String, onClose: () -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf(url) }
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush(); browser?.stopLoading(); browser?.destroy() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row {
                    TextButton(onClick = { if (browser?.canGoBack() == true) browser?.goBack() else onClose() }) { Text("Back") }
                    TextButton(onClick = { browser?.reload() }) { Text("Reload") }
                    TextButton(onClick = onClose) { Text("Close and retry in app") }
                }
                Text(address, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme !in setOf("http", "https")
                            override fun onPageFinished(view: WebView, url: String) { address = url }
                        }
                        browser = this; loadUrl(url)
                    }
                })
            }
        }
    }
}
