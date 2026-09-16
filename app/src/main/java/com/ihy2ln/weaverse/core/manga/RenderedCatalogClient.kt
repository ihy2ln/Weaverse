package com.ihy2ln.weaverse.core.manga

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Loads the site's own public UI normally; no token fabrication or challenge solving. */
@Singleton
class RenderedCatalogClient @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(url: String): String = mutex.withLock {
        withContext(Dispatchers.Main) {
            val browser = WebView(context)
            try {
                browser.settings.javaScriptEnabled = true
                browser.settings.domStorageEnabled = true
                browser.settings.allowFileAccess = false
                browser.settings.allowContentAccess = false
                browser.webViewClient = WebViewClient()
                browser.layout(0, 0, 1080, 1920)
                browser.loadUrl(url)
                withTimeoutOrNull(25_000) {
                    var previous = ""
                    var stable = 0
                    while (true) {
                        delay(700)
                        val html = suspendCancellableCoroutine<String> { continuation ->
                            browser.evaluateJavascript("document.documentElement.outerHTML") { encoded ->
                                if (continuation.isActive) continuation.resume(JSONTokener(encoded).nextValue() as? String ?: "")
                            }
                        }
                        val doc = Jsoup.parse(html)
                        if (doc.title().contains("just a moment", true) || doc.selectFirst("form#challenge-form") != null) {
                            error("This source requires browser verification. Open its website and complete verification before retrying.")
                        }
                        val catalogLinks = doc.select("a[href*='/title/'], a[href*='/manga/'], a[href*='/series/'], a[href*='/read/']")
                        val links = catalogLinks.size
                        val text = doc.body().text()
                        val ready = links > 2 || text.contains("No results", true) || text.contains("No titles found", true)
                        val signature = catalogLinks.joinToString { it.attr("href") } + if (links == 0) text else ""
                        if (ready && signature == previous) stable++ else stable = 0
                        if (ready && stable >= 2) return@withTimeoutOrNull html
                        previous = signature
                    }
                    @Suppress("UNREACHABLE_CODE") ""
                } ?: error("The source's rendered catalog did not finish loading. Open the source website and retry.")
            } finally {
                browser.stopLoading(); browser.destroy()
            }
        }
    }
}
