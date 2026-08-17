package com.ihy2ln.weaverse.core.text

/**
 * Splits [spans] into (before, after) at plain-text character [offset], splitting a span in two
 * when the cut falls inside it. Both sides keep the original marks/colours — the shared primitive
 * every other function in this file builds on.
 */
fun splitSpansAt(spans: List<Span>, offset: Int): Pair<List<Span>, List<Span>> {
    var remaining = offset.coerceIn(0, spans.sumOf { it.text.length })
    val before = mutableListOf<Span>()
    val after = mutableListOf<Span>()
    for (span in spans) {
        when {
            remaining <= 0 -> after.add(span)
            remaining >= span.text.length -> {
                before.add(span)
                remaining -= span.text.length
            }
            else -> {
                before.add(span.copy(text = span.text.substring(0, remaining)))
                after.add(span.copy(text = span.text.substring(remaining)))
                remaining = 0
            }
        }
    }
    return before to after
}

/** The spans covering plain-text offsets `[start, end)`, boundary-split so no mark/colour leaks
 * in from outside the range. */
fun spansInRange(spans: List<Span>, start: Int, end: Int): List<Span> {
    val (_, afterStart) = splitSpansAt(spans, start)
    val (middle, _) = splitSpansAt(afterStart, end - start)
    return middle
}

/** Replaces the spans covering `[start, end)` with [replacement]; everything outside is untouched. */
fun replaceSpansInRange(spans: List<Span>, start: Int, end: Int, replacement: List<Span>): List<Span> {
    val (before, rest) = splitSpansAt(spans, start)
    val (_, after) = splitSpansAt(rest, end - start)
    return before + replacement + after
}

/** Merges adjacent spans that share identical marks/colours/mention into one — keeps span lists
 * from fragmenting on every keystroke, and keeps unformatted text as a single span the way it
 * always was before edits started preserving spans (see [updateSpansForTextChange]). */
fun mergeAdjacentSpans(spans: List<Span>): List<Span> {
    if (spans.isEmpty()) return spans
    val result = mutableListOf(spans.first())
    for (span in spans.drop(1)) {
        val last = result.last()
        val sameStyle = last.marks == span.marks &&
            last.colorHex == span.colorHex &&
            last.highlightHex == span.highlightHex &&
            last.codexEntryId == span.codexEntryId
        if (sameStyle) {
            result[result.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            result.add(span)
        }
    }
    return result
}

/**
 * Adjusts [oldSpans] for a single text-field edit (its old flattened text becoming [newText]),
 * preserving marks/colours on the unaffected prefix/suffix. Every block view used to just
 * collapse to `listOf(Span(newValue.text))` on every keystroke, silently dropping any
 * Highlight/Text Colour/Bold/etc. the moment the user typed one more character — spec §7 requires
 * "Highlights persist..."; this is what makes that true. Newly *inserted* text is always plain
 * (no inherited marks) — simplest, unambiguous default; a user who wants the new text styled
 * selects it afterwards and applies the mark, same as most editors.
 *
 * Finds the longest common prefix and (non-overlapping) suffix between the old and new text and
 * treats everything between them as the single edited region — the standard approach for
 * diffing a single-cursor text field's before/after state; it does not attempt to reconcile
 * multiple disjoint edits landing in one callback (autocorrect-style whole-word swaps can
 * occasionally produce a less-than-minimal diff, but never an incorrect result).
 */
fun updateSpansForTextChange(oldSpans: List<Span>, newText: String): List<Span> {
    val oldText = oldSpans.joinToString(separator = "") { it.text }
    if (oldText == newText) return oldSpans

    val minLen = minOf(oldText.length, newText.length)
    var prefixLen = 0
    while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) prefixLen++

    var suffixLen = 0
    val suffixBound = minLen - prefixLen
    while (
        suffixLen < suffixBound &&
        oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
    ) {
        suffixLen++
    }

    val insertedText = newText.substring(prefixLen, newText.length - suffixLen)
    val (before, rest) = splitSpansAt(oldSpans, prefixLen)
    val (_, after) = splitSpansAt(rest, oldText.length - prefixLen - suffixLen)
    val insertedSpan = if (insertedText.isEmpty()) emptyList() else listOf(Span(insertedText))

    return mergeAdjacentSpans((before + insertedSpan + after).filter { it.text.isNotEmpty() })
}

/** Toggles [mark] on `[start, end)`: removes it if every span in the range already has it, adds
 * it otherwise (spec §7's Bold/Italic/Underline/Strike selection-toolbar buttons). */
fun applyMarkToRange(spans: List<Span>, start: Int, end: Int, mark: Mark): List<Span> {
    if (start >= end) return spans
    val selected = spansInRange(spans, start, end)
    val allHaveMark = selected.isNotEmpty() && selected.all { mark in it.marks }
    val transformed = selected.map { span -> span.copy(marks = if (allHaveMark) span.marks - mark else span.marks + mark) }
    return mergeAdjacentSpans(replaceSpansInRange(spans, start, end, transformed))
}

/** Sets (or, with `colorHex = null`, clears) [Span.colorHex] on `[start, end)`. */
fun applyColorToRange(spans: List<Span>, start: Int, end: Int, colorHex: String?): List<Span> {
    if (start >= end) return spans
    val selected = spansInRange(spans, start, end)
    val transformed = selected.map { it.copy(colorHex = colorHex) }
    return mergeAdjacentSpans(replaceSpansInRange(spans, start, end, transformed))
}

/** Sets (or, with `highlightHex = null`, clears) [Span.highlightHex] on `[start, end)`. */
fun applyHighlightToRange(spans: List<Span>, start: Int, end: Int, highlightHex: String?): List<Span> {
    if (start >= end) return spans
    val selected = spansInRange(spans, start, end)
    val transformed = selected.map { it.copy(highlightHex = highlightHex) }
    return mergeAdjacentSpans(replaceSpansInRange(spans, start, end, transformed))
}
