package com.ihy2ln.weaverse.core.manga

/** Local refinements never pretend to be a website's server-side query parameters. */
data class CatalogRefinements(
    val minimumScore: Int = 0,
    val minimumChapters: Int = 0,
    val volumes: String = "",
    val contentRating: String = "",
) {
    val needsChapters: Boolean get() = minimumChapters > 0 || volumes.isNotBlank()
    val active: Boolean get() = minimumScore > 0 || needsChapters || contentRating.isNotBlank()

    fun matches(manga: MangaSearchResult, chapters: List<MangaChapter>? = null): Boolean {
        if (minimumScore > 0 && (score(manga.score.ifBlank { manga.rating }) ?: return false) < minimumScore) return false
        if (contentRating.isNotBlank() && contentRating != manga.rating.trim().lowercase()) return false
        if (needsChapters) {
            val known = chapters ?: return false
            if (known.distinctBy { it.remoteId }.size < minimumChapters) return false
            val hasVolumes = known.any { it.volume.isNotBlank() }
            if (volumes == "yes" && !hasVolumes) return false
            // No chapters is unknown, not evidence of a series without volumes.
            if (volumes == "no" && (hasVolumes || known.isEmpty())) return false
        }
        return true
    }

    companion object {
        fun score(raw: String): Double? {
            val parts = raw.trim().split('/')
            val value = parts.firstOrNull()?.trim()?.toDoubleOrNull() ?: return null
            val maximum = if (parts.size == 2) parts[1].trim().toDoubleOrNull() ?: return null else 10.0
            if (parts.size > 2 || !maximum.isFinite() || maximum <= 0 || !value.isFinite() || value !in 0.0..maximum) return null
            return value / maximum * 10
        }
    }
}
