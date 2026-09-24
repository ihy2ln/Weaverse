package com.ihy2ln.weaverse.core.manga

/** Local ordering is explicitly limited to loaded results; source ordering remains available. */
object CatalogSort {
    val options = listOf("Source order" to "source", "Popular" to "popular", "Latest updates" to "latest",
        "Title A–Z (loaded)" to "title", "Title Z–A (loaded)" to "title_desc",
        "Highest score (loaded)" to "score", "Newest release year (loaded)" to "year")

    fun apply(items: List<MangaSearchResult>, order: String): List<MangaSearchResult> = when (order) {
        "title" -> items.sortedBy { it.title.lowercase() }
        "title_desc" -> items.sortedByDescending { it.title.lowercase() }
        "score" -> items.sortedWith(compareByDescending<MangaSearchResult> { it.score.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }.thenBy { it.title.lowercase() })
        "year" -> items.sortedWith(compareByDescending<MangaSearchResult> { it.year.toIntOrNull() ?: Int.MIN_VALUE }.thenBy { it.title.lowercase() })
        else -> items
    }
}
