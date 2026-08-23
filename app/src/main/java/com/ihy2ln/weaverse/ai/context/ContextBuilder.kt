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
    /** The same `[[Name]]\nbody` codex text folded into [systemBlocks], exposed separately for `{include("Weaverse/Codex")}`. */
    val codexBlock: String = "",
)

data class ContextBuildRequest(
    val scanText: String,
    val userMessage: String = "",
    val sceneText: String = "",
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

        val framing = "You are a creative writing assistant."
        val budget = request.maxContextTokens - request.reserveResponseTokens
        var used = estimateTokens(framing)
        val included = mutableListOf<CodexEntryEntity>()
        val dropped = mutableListOf<String>()

        // Pack entries under the budget, then build the prompt from included only.
        // (Building first then "dropping" left dropped text still in systemBlocks/codexBlock.)
        merged.forEach { entry ->
            val piece = "[[${entry.name}]]\n${entry.plainText}"
            val cost = estimateTokens(piece)
            if (used + cost <= budget) {
                included.add(entry)
                used += cost
            } else {
                dropped.add(entry.id)
            }
        }

        val codexBlock = included.joinToString("\n\n") { "[[${it.name}]]\n${it.plainText}" }
        val systemBlocks = listOfNotNull(
            framing,
            codexBlock.takeIf { it.isNotBlank() },
        )
        val includedIds = included.map { it.id }.toSet()

        return AssembledPrompt(
            systemBlocks = systemBlocks,
            messages = listOf("user" to request.userMessage),
            usedEntries = merged
                .filter { it.id in includedIds }
                .map {
                    ContextChip(
                        it.id,
                        it.name,
                        it.colorHex,
                        autoDetected = it.id !in request.manualIncludeIds,
                    )
                },
            tokenBreakdown = listOf(
                TokenBreakdown("Codex", estimateTokens(codexBlock)),
                TokenBreakdown("Scene", estimateTokens(request.sceneText)),
                TokenBreakdown("User", estimateTokens(request.userMessage)),
            ).filter { it.tokens > 0 },
            droppedEntryIds = dropped,
            codexBlock = codexBlock,
        )
    }

    private fun matchesNameOrAliases(entry: CodexEntryEntity, scanText: String, scanLower: String): Boolean {
        val terms = listOf(entry.name) + decodeAliases(entry.aliasesJson)
        return terms.any { term ->
            if (term.isBlank()) return@any false
            if (entry.caseSensitiveMatching) term in scanText else term.lowercase() in scanLower
        }
    }

    private fun estimateTokens(text: String): Int =
        if (text.isBlank()) 0 else (text.length / 4).coerceAtLeast(1)
}
