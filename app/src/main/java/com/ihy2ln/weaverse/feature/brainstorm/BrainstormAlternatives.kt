package com.ihy2ln.weaverse.feature.brainstorm

/** Model output format for an Explore action. A malformed reply remains readable chat text. */
data class BrainstormAlternative(
    val title: String,
    val premise: String,
    val strengths: String,
    val risks: String,
    val nextStep: String,
)

internal const val EXPLORE_FORMAT = """
Give exactly three genuinely different creative directions. Use this format for each one, with no introduction:
[[OPTION]]
Title: short distinct name
Premise: one or two concrete sentences
Strengths: what makes this direction promising
Risks: what might weaken it
Next step: one useful question or action
[[END_OPTION]]
Do not put [[OPTION]] or [[END_OPTION]] inside a field.
"""

internal fun parseBrainstormAlternatives(text: String): List<BrainstormAlternative> {
    val blocks = Regex("\\[\\[OPTION]]([\\s\\S]*?)\\[\\[END_OPTION]]", RegexOption.IGNORE_CASE)
        .findAll(text).take(3).map { it.groupValues[1] }.toList()
    if (blocks.size != 3) return emptyList()
    return blocks.mapNotNull { block ->
        val fields = linkedMapOf<String, StringBuilder>()
        var current: String? = null
        block.lines().forEach { line ->
            val match = Regex("^(Title|Premise|Strengths|Risks|Next step):\\s*(.*)$", RegexOption.IGNORE_CASE)
                .matchEntire(line.trim())
            if (match != null) {
                current = match.groupValues[1].lowercase()
                fields[current!!] = StringBuilder(match.groupValues[2].trim())
            } else if (current != null && line.isNotBlank()) {
                fields[current]?.append(' ')?.append(line.trim())
            }
        }
        val title = fields["title"]?.toString().orEmpty().trim()
        val premise = fields["premise"]?.toString().orEmpty().trim()
        if (title.isBlank() || premise.isBlank() ||
            listOf("strengths", "risks", "next step").any { fields[it].isNullOrEmpty() }
        ) null else BrainstormAlternative(
            title = title,
            premise = premise,
            strengths = fields["strengths"]?.toString().orEmpty().trim(),
            risks = fields["risks"]?.toString().orEmpty().trim(),
            nextStep = fields["next step"]?.toString().orEmpty().trim(),
        )
    }.takeIf { it.size == 3 }.orEmpty()
}
