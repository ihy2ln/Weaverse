package com.ihy2ln.weaverse.sync.web

/**
 * The web version of Weaverse (served by Weaverse Desktop and by the phone's own host): the
 * same Home and modes as the app, over the synced library. The page lives in
 * resources/web so it can be edited as plain HTML, CSS and JavaScript.
 */
private fun webResource(name: String): String =
    WebAssetsAnchor::class.java.getResourceAsStream("/web/$name")?.use { it.readBytes().decodeToString() }
        ?: "/* missing web asset $name */"

private object WebAssetsAnchor

fun webIndexHtml(): String = webResource("index.html")

fun webAppCss(): String = webResource("app.css")

fun webAppJs(): String = webResource("app.js")

/** Mode keys with key art at /art/<key>.webp. */
val webArtKeys = setOf("home", "novel", "rpg", "games", "browser", "manga", "notes")
