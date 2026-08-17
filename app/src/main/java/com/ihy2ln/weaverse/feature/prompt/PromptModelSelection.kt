package com.ihy2ln.weaverse.feature.prompt

import com.ihy2ln.weaverse.ai.ModelInfo

/** Session model pick for the shared prompt box — does not write Settings. */
object PromptModelSelection {
    fun modelRef(id: String): String {
        val trimmed = id.removePrefix("openrouter/").trim()
        return if (trimmed.isBlank()) "" else "openrouter/$trimmed"
    }

    fun effectiveModelRef(selectedRef: String, defaultRef: String): String =
        selectedRef.ifBlank { defaultRef }

    fun followsDefault(selectedRef: String): Boolean = selectedRef.isBlank()

    fun shortLabel(modelRef: String, models: List<ModelInfo> = emptyList()): String {
        val id = modelRef.removePrefix("openrouter/").trim()
        if (id.isBlank()) return "Default"
        val match = models.firstOrNull { it.id.equals(id, ignoreCase = true) }
        val name = match?.displayName?.trim().orEmpty()
        if (name.isNotBlank()) return name
        return id.substringAfterLast('/').ifBlank { id }
    }

    fun filter(models: List<ModelInfo>, query: String): List<ModelInfo> {
        val q = query.trim()
        if (q.isBlank()) return models
        return models.filter { model ->
            model.id.contains(q, ignoreCase = true) ||
                model.displayName.contains(q, ignoreCase = true) ||
                model.tags.any { it.contains(q, ignoreCase = true) }
        }
    }

    fun isSelected(model: ModelInfo, selectedRef: String, defaultRef: String): Boolean {
        val active = effectiveModelRef(selectedRef, defaultRef).removePrefix("openrouter/")
        return active.equals(model.id, ignoreCase = true)
    }
}
