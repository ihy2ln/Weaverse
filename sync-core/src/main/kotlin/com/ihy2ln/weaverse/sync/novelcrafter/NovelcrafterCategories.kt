package com.ihy2ln.weaverse.sync.novelcrafter

object NovelcrafterCategories {
    val folderToCategory: Map<String, Pair<String, Int>> = mapOf(
        "characters" to ("Characters" to 0),
        "locations" to ("Locations" to 1),
        "objects" to ("Objects/Items" to 2),
        "lore" to ("Lore" to 3),
        "other" to ("Notes" to 9),
    )

    val palette: List<String> = listOf(
        "#4A90D9", "#3FA66A", "#8B6FD1", "#D98A3F", "#C4574B",
        "#3FA9A0", "#C98BB0", "#8A8A8A", "#6B8E23", "#5C6B7A",
    )

    fun namedColorToHex(name: String): String = when (name.lowercase()) {
        "blue" -> "#4A90D9"
        "green" -> "#3FA66A"
        "purple", "violet" -> "#8B6FD1"
        "orange" -> "#D98A3F"
        "red" -> "#C4574B"
        "teal" -> "#3FA9A0"
        "pink" -> "#C98BB0"
        "gray", "grey" -> "#8A8A8A"
        else -> if (name.startsWith("#")) name else "#4A90D9"
    }

    fun findEntry(entries: List<NcCodexEntry>, vararg needles: String): NcCodexEntry? {
        val lower = needles.map { it.lowercase() }
        return entries.firstOrNull { entry ->
            val hay = (listOf(entry.name) + entry.aliases).joinToString(" ").lowercase()
            lower.any { it in hay }
        }
    }
}
