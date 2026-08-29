package com.ihy2ln.weaverse.core.text

import java.util.UUID

/** Insert a scene-beat prompt box at the top when the scene has none. */
fun List<Block>.ensureSceneBeatAtStart(): Pair<List<Block>, Int> {
    val existing = indexOfFirst { it is SceneBeatBlock }
    if (existing >= 0) return this to existing
    val beat = SceneBeatBlock(id = UUID.randomUUID().toString(), prompt = "")
    return (listOf<Block>(beat) + this) to 0
}

/**
 * Always append a new scene-beat box after existing prose / beats.
 * Used when Plan adds another beat onto the current scene.
 */
fun List<Block>.appendSceneBeat(prompt: String = ""): List<Block> {
    val beat = SceneBeatBlock(id = UUID.randomUUID().toString(), prompt = prompt)
    return this + beat
}

/**
 * Append generated or manual prose into the **last paragraph** so Write stays one
 * cohesive text field (oldest at the top, newest at the end). Trailing empty
 * paragraphs are dropped. A new paragraph is created only when the document does
 * not already end with prose (for example after a picture).
 */
fun List<Block>.appendParagraphs(text: String): List<Block> {
    val incoming = text.trim()
    if (incoming.isEmpty()) return this
    val withoutTrailingEmpty = dropLastWhile { block ->
        block is Paragraph && block.plainText().isBlank()
    }
    val last = withoutTrailingEmpty.lastOrNull()
    if (last is Paragraph) {
        val existing = last.plainText()
        val joined = when {
            existing.isBlank() -> incoming
            existing.last().isWhitespace() -> existing + incoming
            else -> "$existing $incoming"
        }
        return withoutTrailingEmpty.dropLast(1) + last.copy(spans = listOf(Span(joined)))
    }
    return withoutTrailingEmpty + Paragraph(UUID.randomUUID().toString(), listOf(Span(incoming)))
}

fun Document.appendParagraphs(text: String): Document =
    copy(blocks = blocks.appendParagraphs(text))

/**
 * Insert generated prose at a caret anchor: the anchor paragraph splits at the
 * caret and the prose paragraph lands between the halves. An empty anchor
 * paragraph is replaced outright; non-paragraph or out-of-range anchors append.
 */
fun Document.insertProseAt(blockIndex: Int, caret: Int, text: String): Document {
    val incoming = text.trim()
    if (incoming.isEmpty()) return this
    val block = blocks.getOrNull(blockIndex)
    if (block !is Paragraph) return appendParagraphs(text)
    val existing = block.plainText()
    val split = caret.coerceIn(0, existing.length)
    val before = existing.take(split).trimEnd()
    val after = existing.drop(split).trimStart()
    val next = blocks.toMutableList()
    if (before.isBlank() && after.isBlank()) {
        next[blockIndex] = Paragraph(UUID.randomUUID().toString(), listOf(Span(incoming)))
        return copy(blocks = next)
    }
    val replacement = buildList {
        if (before.isNotBlank()) add(Paragraph(block.id, listOf(Span(before))))
        add(Paragraph(UUID.randomUUID().toString(), listOf(Span(incoming))))
        if (after.isNotBlank()) add(Paragraph(UUID.randomUUID().toString(), listOf(Span(after))))
    }
    next.removeAt(blockIndex)
    next.addAll(blockIndex, replacement)
    return copy(blocks = next)
}

fun List<Block>.withSceneBeatPrompt(index: Int, prompt: String): List<Block> {
    val beat = getOrNull(index) as? SceneBeatBlock ?: return this
    if (beat.prompt == prompt) return this
    return toMutableList().also { it[index] = beat.copy(prompt = prompt) }
}

fun List<Block>.withSceneBeatCollapsedToggled(index: Int): List<Block> {
    val beat = getOrNull(index) as? SceneBeatBlock ?: return this
    return toMutableList().also { it[index] = beat.copy(collapsed = !beat.collapsed) }
}

/**
 * Persist the beat prompt (if [insertAfterIndex] is a [SceneBeatBlock]) and insert
 * generated prose as **one** paragraph after the beat — never inside the blue box
 * and never split into one-line-per-generation blocks.
 */
fun List<Block>.insertGeneratedProseAfter(
    insertAfterIndex: Int,
    generatedText: String,
    beatPrompt: String? = null,
): List<Block> {
    val incoming = generatedText.trim()
    val next = toMutableList()
    val beat = next.getOrNull(insertAfterIndex) as? SceneBeatBlock
    if (beat != null && beatPrompt != null) {
        next[insertAfterIndex] = beat.copy(prompt = beatPrompt)
    }
    if (incoming.isEmpty()) return next
    val insertAt = (insertAfterIndex + 1).coerceIn(0, next.size)
    next.add(insertAt, Paragraph(UUID.randomUUID().toString(), listOf(Span(incoming))))
    return next
}

/**
 * Case-insensitive ranges of Codex names / aliases and `[[wiki]]` links inside beat text.
 * Longer names win when they overlap.
 */
fun findCodexMentionRanges(text: String, names: List<String>): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val covered = BooleanArray(text.length)
    val ranges = mutableListOf<IntRange>()
    val sorted = names
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinctBy { it.lowercase() }
        .sortedByDescending { it.length }
    val lower = text.lowercase()
    for (name in sorted) {
        val needle = name.lowercase()
        var start = 0
        while (start <= lower.length - needle.length) {
            val idx = lower.indexOf(needle, start)
            if (idx < 0) break
            val end = idx + needle.length
            val leftOk = idx == 0 || !lower[idx - 1].isLetterOrDigit()
            val rightOk = end == lower.length || !lower[end].isLetterOrDigit()
            val free = (idx until end).none { covered[it] }
            if (leftOk && rightOk && free) {
                for (i in idx until end) covered[i] = true
                ranges.add(idx until end)
            }
            start = idx + 1
        }
    }
    Regex("\\[\\[([^\\]]+)\\]\\]").findAll(text).forEach { match ->
        val range = match.range
        if (range.first in covered.indices && range.none { covered.getOrElse(it) { true } }) {
            for (i in range) covered[i] = true
            ranges.add(range)
        }
    }
    return ranges.sortedBy { it.first }
}
