package com.ihy2ln.weaverse.feature.novel.plan

import com.ihy2ln.weaverse.data.db.entities.SceneEntity

/** Scene summary copy for Plan Grid (compact) vs Outline (fuller). */
object SceneSummary {
    const val GRID_MAX_CHARS = 96
    const val OUTLINE_MAX_CHARS = 360

    fun source(scene: SceneEntity): String {
        val stored = collapseWs(scene.summary)
        if (stored.isNotBlank()) return stored
        return collapseWs(scene.plainText)
    }

    fun compact(text: String, maxChars: Int): String {
        val cleaned = collapseWs(text)
        if (cleaned.isBlank() || cleaned.length <= maxChars) return cleaned
        val cut = cleaned.take(maxChars)
        val atWord = cut.substringBeforeLast(' ')
        val body = atWord.ifBlank { cut }.trimEnd('.', ',', ';', ':')
        return "$body…"
    }

    private fun collapseWs(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
