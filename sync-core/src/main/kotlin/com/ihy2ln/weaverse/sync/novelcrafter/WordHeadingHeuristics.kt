package com.ihy2ln.weaverse.sync.novelcrafter

/**
 * Novelcrafter "full word" exports store Day / Chapter / Scene titles as plain
 * paragraphs (no Markdown `#` markers). Promote those lines so [NovelcrafterZipParser.parseNovelMd]
 * can build acts → chapters → scenes.
 */
object WordHeadingHeuristics {
    private val day = Regex("""^Day\s+\d+\s*$""", RegexOption.IGNORE_CASE)
    private val chapterOnly = Regex("""^Chapter\s+\d+\s*$""", RegexOption.IGNORE_CASE)
    private val chapterScene = Regex("""^Chapter\s+\d+\s*[-–—:]\s+.+$""", RegexOption.IGNORE_CASE)

    fun apply(text: String): String {
        val lines = text.replace("\r\n", "\n").lines()
        if (lines.any { it.startsWith("# ") || it.startsWith("## ") }) return text
        return lines.mapIndexed { index, raw ->
            val line = raw.trim()
            when {
                index == 0 && line.isNotBlank() && line.length <= 80 && !line.startsWith("#") ->
                    "# $line"
                day.matches(line) -> "## $line"
                chapterScene.matches(line) -> "#### $line"
                chapterOnly.matches(line) -> "### $line"
                else -> raw
            }
        }.joinToString("\n")
    }
}
