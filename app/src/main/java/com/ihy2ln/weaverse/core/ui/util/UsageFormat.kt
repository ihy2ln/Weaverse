package com.ihy2ln.weaverse.core.ui.util

import com.ihy2ln.weaverse.ai.context.TokenBreakdown
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Formats token counts and API cost for human-readable UI (avoids scientific notation).
 */
object UsageFormat {
    fun formatCost(cost: Double?): String? {
        if (cost == null) return null
        if (cost == 0.0) return "$0.00"
        val absCost = abs(cost)
        val pattern = when {
            absCost >= 1.0 -> "#,##0.00"
            absCost >= 0.01 -> "0.00"
            absCost >= 0.0001 -> "0.0000"
            else -> "0.00000000"
        }
        val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        return "$${formatter.format(cost)}"
    }

    fun formatTokens(prompt: Int, completion: Int, total: Int? = null): String {
        val totalPart = total?.let { " · total ${formatCount(it)}" }.orEmpty()
        return "prompt ${formatCount(prompt)} + completion ${formatCount(completion)}$totalPart"
    }

    fun formatUsage(
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int? = null,
        cost: Double? = null,
    ): String {
        val tokens = formatTokens(promptTokens, completionTokens, totalTokens)
        val costPart = formatCost(cost)?.let { " · cost $it" }.orEmpty()
        return tokens + costPart
    }

    fun formatCount(n: Int): String = "%,d".format(Locale.US, n)

    fun formatBreakdown(items: List<TokenBreakdown>): String =
        items.filter { it.tokens > 0 }
            .joinToString(" · ") { "${it.section} ${formatCount(it.tokens)}" }
}
