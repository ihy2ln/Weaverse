package com.ihy2ln.weaverse.ai.prompt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PromptTokenContext(
    val tense: String = "past tense",
    val language: String = "General English",
    val bookTitle: String = "",
    val seriesTitle: String = "",
    val seriesDescription: String = "",
    val today: String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)),
)

object PromptTokens {
    fun apply(text: String, ctx: PromptTokenContext): String {
        if (text.isBlank()) return text
        var out = PromptAddOns.resolveBlocks(text)
        out = out
            .replace("{novel.tense}", ctx.tense.ifBlank { "past tense" })
            .replace("{novel.language}", ctx.language.ifBlank { "General English" })
            .replace("{book.title}", ctx.bookTitle)
            .replace("{series.title}", ctx.seriesTitle)
            .replace("{series.description}", ctx.seriesDescription)
            .replace("{today}", ctx.today)
            .replace("{genre}", PromptAddOns.genreLabel)
        return out
    }

    /** Rough English estimate used by the live context meter (~4 characters per token). */
    fun estimate(text: String): Int = (text.length / 4).coerceAtLeast(if (text.isBlank()) 0 else 1)

    fun meterLabel(used: Int, limit: Int): String {
        fun k(n: Int) = "%.1fk".format(n / 1000.0)
        return "${k(used)} / ${k(limit)}"
    }
}
