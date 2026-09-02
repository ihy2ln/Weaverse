package com.ihy2ln.weaverse.feature.novel.plan

/**
 * What the manuscript levels are called in a given workspace. A campaign has the
 * same shape as a book — Adventure/Day/Mission/Event maps onto
 * Book/Chapter/Scene/SceneBeat — so RPG reuses the manuscript entities and the
 * Plan screen, and only the wording changes.
 */
data class PlanVocabulary(
    val book: String,
    val chapter: String,
    val scene: String,
    val sceneBeat: String,
) {
    companion object {
        val Novel = PlanVocabulary(
            book = "Book",
            chapter = "Chapter",
            scene = "Scene",
            sceneBeat = "Scene beat",
        )
        val Rpg = PlanVocabulary(
            book = "Adventure",
            chapter = "Day",
            scene = "Mission",
            sceneBeat = "Event",
        )
    }
}
