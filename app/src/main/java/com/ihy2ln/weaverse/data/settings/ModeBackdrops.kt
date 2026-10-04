package com.ihy2ln.weaverse.data.settings

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The backgrounds each mode's pages sit on, keyed by mode ("home", "novel", "rpg", "games",
 * "browser", "manga", "notes"). Each entry is "art:<key>" (Weaverse key art), "video:<key>"
 * (a built-in focus video), "media:<id>" (a picture or video the user added) or "wallpaper"
 * (the appearance wallpaper) or "asset:<path>" (a painting bundled in the app's assets). An
 * empty list means the mode's default pool ([BackdropPool]).
 */
object ModeBackdrops {
    const val ART = "art:"
    const val MEDIA = "media:"
    const val VIDEO = "video:"
    const val WALLPAPER = "wallpaper"
    const val ASSET = "asset:"

    /** Rotation choices, in seconds, for Settings → Mode backgrounds. */
    val INTERVALS = listOf(30, 60, 120, 300, 600)
    const val DEFAULT_INTERVAL_S = 120

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    fun decode(text: String?): Map<String, List<String>> =
        if (text.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyMap())

    fun encode(map: Map<String, List<String>>): String =
        json.encodeToString(serializer, map.mapValues { (_, v) -> v.distinct() }.filterValues { it.isNotEmpty() })
}

/**
 * The paintings each mode rotates through when the user hasn't picked its backgrounds: the
 * mode's key art, then Adams Haven location paintings (portrait, phone-sized) and the focus
 * videos' stills that suit it. Home gets all of them.
 */
object BackdropPool {
    private const val LOCATIONS = "images/adams_haven/locations/"
    private const val STILLS = "videos/ambient/"

    private fun places(vararg names: String) = names.map { ModeBackdrops.ASSET + LOCATIONS + it + ".webp" }
    private fun stills(vararg names: String) = names.map { ModeBackdrops.ASSET + STILLS + it + ".jpg" }

    private val byMode: Map<String, List<String>> = mapOf(
        "novel" to places(
            "mythoria", "elysium-vale", "major-continents-of-elysium-vale", "major-cities-of-mythoria",
            "arcanis", "elysara", "veridian", "silverwood-forest",
        ) + stills("rain"),
        "rpg" to places(
            "silverbrook-adventure-guild", "silverbrook-city", "the-frosted-mug-inn", "heroica",
            "the-argent-bourse", "landscape-town", "adams-haven",
        ) + stills("forest"),
        "games" to places(
            "starter-base", "fixer-upper", "landscape-farm", "town-lot-painterly", "afm-bunkers-rooms", "the-sandcastle",
        ) + stills("space"),
        "manga" to places(
            "los-angeles-asteroid", "nexara-prime", "california", "usa", "ucla",
            "ucla-neo-campus-interactive-media-digital-narrative-complex", "pacific-bastion-coastal-residence",
        ) + stills("aurora"),
        "notes" to places("serenity-atoll", "aqualuria", "the-harmonized-expanse") + stills("ocean", "snow"),
        "browser" to places("nexara-prime", "the-argent-bourse", "silverbrook-city", "california") + stills("space"),
    )

    /** Every bundled painting, once each. */
    val all: List<String> get() = byMode.values.flatten().distinct()

    /** [modeKey]'s default rotation: its own key art first, then its paintings. */
    fun of(modeKey: String): List<String> {
        val own = listOf(ModeBackdrops.ART + modeKey)
        return own + if (modeKey == "home") all + byMode.keys.map { ModeBackdrops.ART + it } else byMode[modeKey].orEmpty()
    }

    /** The asset path inside an "asset:" entry. */
    fun assetPath(entry: String): String? = entry.takeIf { it.startsWith(ModeBackdrops.ASSET) }?.removePrefix(ModeBackdrops.ASSET)
}
