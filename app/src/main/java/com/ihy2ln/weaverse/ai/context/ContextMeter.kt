package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.prompt.PromptTokens
import java.util.Locale

data class ContextMeterReading(
    val usedTokens: Int,
    val limitTokens: Int,
) {
    val label: String
        get() = "context: ${ContextMeter.formatCount(usedTokens)} / ${ContextMeter.formatCount(limitTokens)}"

    val fraction: Float
        get() = if (limitTokens <= 0) 0f else (usedTokens.toFloat() / limitTokens).coerceIn(0f, 1f)
}

object ContextMeter {
    const val DEFAULT_LIMIT = 32_768

    fun estimateTokens(text: String): Int = PromptTokens.estimate(text)

    fun used(assembled: AssembledPrompt, extraUser: String = ""): Int {
        val system = assembled.systemBlocks.sumOf { estimateTokens(it) }
        val history = assembled.messages.sumOf { estimateTokens(it.second) }
        val extra = if (extraUser.isBlank()) 0 else estimateTokens(extraUser)
        return system + history + extra
    }

    fun reading(
        assembled: AssembledPrompt,
        extraUser: String = "",
        limitTokens: Int = DEFAULT_LIMIT,
    ): ContextMeterReading = ContextMeterReading(
        usedTokens = used(assembled, extraUser),
        limitTokens = limitTokens.coerceAtLeast(1),
    )

    fun limitFor(modelRef: String, models: List<ModelInfo>): Int {
        val id = modelRef.substringAfter('/').ifBlank { modelRef }
        return models.firstOrNull { it.id == id }?.contextLength
            ?: models.firstOrNull { modelRef.endsWith(it.id) }?.contextLength
            ?: DEFAULT_LIMIT
    }

    fun formatCount(n: Int): String {
        if (n >= 10_000) {
            val k = n / 1000.0
            return String.format(Locale.US, "%.1fk", k)
        }
        if (n >= 1_000) {
            val k = n / 1000.0
            return String.format(Locale.US, "%.1fk", k)
        }
        return n.toString()
    }
}
