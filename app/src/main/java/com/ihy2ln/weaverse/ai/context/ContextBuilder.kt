package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity

data class ContextChip(
    val entryId: String,
    val name: String,
    val colorHex: String?,
    val autoDetected: Boolean,
)

data class TokenBreakdown(
    val section: String,
    val tokens: Int,
)

data class AssembledPrompt(
    val systemBlocks: List<String>,
    val messages: List<Pair<String, String>>,
    val usedEntries: List<ContextChip>,
    val tokenBreakdown: List<TokenBreakdown>,
    val droppedEntryIds: List<String> = emptyList(),
)

data class ContextBuildRequest(
    val scanText: String,
    val userMessage: String = "",
    val manualIncludeIds: Set<String> = emptySet(),
    val manualExcludeIds: Set<String> = emptySet(),
    val maxContextTokens: Int = 8000,
    val reserveResponseTokens: Int = 1024,
)

class ContextBuilder {
    fun build(
        entries: List<CodexEntryEntity>,
        request: ContextBuildRequest,
    ): AssembledPrompt {
        val scanText = request.scanText + " " + request.userMessage
        val scanLower = scanText.lowercase()
        val detected = entries.filter { entry ->
            !entry.disabled &&
                (
                    entry.alwaysInclude ||
                        (entry.trackMentions && matchesNameOrAliases(entry, scanText, scanLower))
                    )
        }.filter { it.id !in request.manualExcludeIds }

        val manual = entries.filter { it.id in request.manualIncludeIds && it.id !in request.manualExcludeIds }
        val merged = (detected + manual).distinctBy { it.id }.sortedBy { it.name }

        val chips = merged.map {
            ContextChip(it.id, it.name, it.colorHex, autoDetected = it.id !in request.manualIncludeIds)
        }

        val systemBlocks = listOfNotNull(
            "You are a creative writing assistant.",
            merged.joinToString("\n\n") { "[[${it.name}]]\n${it.plainText}" }.takeIf { it.isNotBlank() },
        )

        val budget = request.maxContextTokens - request.reserveResponseTokens
        var used = systemBlocks.sumOf { estimateTokens(it) }
        val included = mutableListOf<CodexEntryEntity>()
        val dropped = mutableListOf<String>()

        merged.forEach { entry ->
            val cost = estimateTokens(entry.plainText)
            if (used + cost <= budget) {
                included.add(entry)
                used += cost
            } else {
                dropped.add(entry.id)
            }
        }

        return AssembledPrompt(
            systemBlocks = systemBlocks,
            messages = listOf("user" to request.userMessage),
            usedEntries = chips.filter { it.entryId !in dropped },
            tokenBreakdown = listOf(
                TokenBreakdown("system", systemBlocks.sumOf { estimateTokens(it) }),
                TokenBreakdown("user", estimateTokens(request.userMessage)),
            ),
            droppedEntryIds = dropped,
        )
    }

    private fun matchesNameOrAliases(entry: CodexEntryEntity, scanText: String, scanLower: String): Boolean {
        val terms = listOf(entry.name) + decodeAliases(entry.aliasesJson)
        return terms.any { term ->
            if (term.isBlank()) return@any false
            if (entry.caseSensitiveMatching) term in scanText else term.lowercase() in scanLower
        }
    }

    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
