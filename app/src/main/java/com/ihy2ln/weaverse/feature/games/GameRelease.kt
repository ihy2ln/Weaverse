package com.ihy2ln.weaverse.feature.games

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GameRelease(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
)

/** The published Android APK is the source for the embedded game's data pack. */
object GameReleaseChecker {
    private const val LATEST = "https://api.github.com/repos/ihy2ln/AdamsHavenCardGame/releases/latest"

    suspend fun latest(): GameRelease = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Weaverse-Games")
        }
        try {
            if (connection.responseCode != 200) error("GitHub returned ${connection.responseCode}.")
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = release.getString("tag_name").removePrefix("v")
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith("-Android.apk", ignoreCase = true) }
                ?: error("The latest release has no Android APK.")
            val url = asset.getString("browser_download_url")
            require(url.startsWith("https://github.com/ihy2ln/AdamsHavenCardGame/releases/download/")) {
                "Unexpected download location in the GitHub release."
            }
            val digest = asset.optString("digest").removePrefix("sha256:")
            require(digest.matches(Regex("[0-9a-fA-F]{64}"))) { "The release has no SHA-256 checksum." }
            GameRelease(version, url, digest.lowercase())
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(available: String, installed: String?): Boolean {
        if (installed.isNullOrBlank()) return true
        val a = available.split('.').map { it.toIntOrNull() ?: return true }
        val b = installed.split('.').map { it.toIntOrNull() ?: return true }
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (difference != 0) return difference > 0
        }
        return false
    }
}
