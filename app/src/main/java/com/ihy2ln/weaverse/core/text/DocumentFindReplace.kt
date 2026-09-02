package com.ihy2ln.weaverse.core.text

data class FindHit(
    val blockIndex: Int,
    val start: Int,
    val end: Int,
)

object DocumentFindReplace {
    fun findAll(
        blocks: List<Block>,
        query: String,
        caseSensitive: Boolean = false,
    ): List<FindHit> {
        if (query.isEmpty()) return emptyList()
        val needle = if (caseSensitive) query else query.lowercase()
        val hits = mutableListOf<FindHit>()
        blocks.forEachIndexed { index, block ->
            val haystack = blockText(block) ?: return@forEachIndexed
            val search = if (caseSensitive) haystack else haystack.lowercase()
            var from = 0
            while (from <= search.length - needle.length) {
                val at = search.indexOf(needle, from)
                if (at < 0) break
                hits += FindHit(index, at, at + query.length)
                from = at + needle.length.coerceAtLeast(1)
            }
        }
        return hits
    }

    fun replaceHit(blocks: List<Block>, hit: FindHit, replacement: String): List<Block> {
        val block = blocks.getOrNull(hit.blockIndex) ?: return blocks
        val next = replaceInBlock(block, hit.start, hit.end, replacement) ?: return blocks
        return blocks.toMutableList().also { it[hit.blockIndex] = next }
    }

    fun replaceAll(
        blocks: List<Block>,
        query: String,
        replacement: String,
        caseSensitive: Boolean = false,
    ): Pair<List<Block>, Int> {
        if (query.isEmpty()) return blocks to 0
        var count = 0
        val next = blocks.map { block ->
            val text = blockText(block) ?: return@map block
            val replaced = if (caseSensitive) {
                if (!text.contains(query)) return@map block
                count += occurrences(text, query)
                text.replace(query, replacement)
            } else {
                val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                val matches = regex.findAll(text).count()
                if (matches == 0) return@map block
                count += matches
                text.replace(regex, Regex.escapeReplacement(replacement))
            }
            replaceWholeText(block, replaced) ?: block
        }
        return next to count
    }

    private fun occurrences(text: String, query: String): Int {
        var from = 0
        var n = 0
        while (from <= text.length - query.length) {
            val at = text.indexOf(query, from)
            if (at < 0) break
            n++
            from = at + query.length.coerceAtLeast(1)
        }
        return n
    }

    private fun blockText(block: Block): String? = when (block) {
        is Paragraph -> block.spans.plainText()
        is Heading -> block.spans.plainText()
        is Quote -> block.spans.plainText()
        is ListItem -> block.spans.plainText()
        is CodeBlock -> block.text
        else -> null
    }

    private fun replaceInBlock(block: Block, start: Int, end: Int, text: String): Block? = when (block) {
        is Paragraph -> block.copy(spans = block.spans.replaceRangeText(start, end, text))
        is Heading -> block.copy(spans = block.spans.replaceRangeText(start, end, text))
        is Quote -> block.copy(spans = block.spans.replaceRangeText(start, end, text))
        is ListItem -> block.copy(spans = block.spans.replaceRangeText(start, end, text))
        is CodeBlock -> {
            val body = block.text
            val s = start.coerceIn(0, body.length)
            val e = end.coerceIn(s, body.length)
            block.copy(text = body.substring(0, s) + text + body.substring(e))
        }
        else -> null
    }

    private fun replaceWholeText(block: Block, text: String): Block? = when (block) {
        is Paragraph -> block.copy(spans = listOf(Span(text)))
        is Heading -> block.copy(spans = listOf(Span(text)))
        is Quote -> block.copy(spans = listOf(Span(text)))
        is ListItem -> block.copy(spans = listOf(Span(text)))
        is CodeBlock -> block.copy(text = text)
        else -> null
    }
}
