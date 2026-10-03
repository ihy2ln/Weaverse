package com.ihy2ln.weaverse.core.manga

/**
 * Loads a catalog page the way the site's own UI renders it (scripts run). The APK uses a
 * WebView; a host without one can return the plain HTML and lose only script-built listings.
 */
interface RenderedCatalog {
    suspend fun load(url: String): String
}
