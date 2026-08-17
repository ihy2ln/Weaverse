package com.ihy2ln.weaverse.core.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ihy2ln.weaverse.core.ui.util.parseHexColor

fun List<Span>.plainText(): String = joinToString("") { it.text }

fun Paragraph.plainText(): String = spans.plainText()

/** Tag used to find codex-link taps via [AnnotatedString.getStringAnnotations]. */
const val CodexMentionTag: String = "codex_mention"

fun List<Span>.toAnnotatedString(
    fallbackColor: Color = Color.Unspecified,
    mentions: List<CodexMention> = emptyList(),
    linkColor: Color = Color.Unspecified,
): AnnotatedString =
    buildAnnotatedString {
        var offset = 0
        forEach { span ->
            val color = span.colorHex?.let { parseHexColor(it, fallbackColor) } ?: fallbackColor
            val spanStart = offset
            val spanEnd = offset + span.text.length
            withStyle(
                SpanStyle(
                    fontWeight = if (Mark.Bold in span.marks) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (Mark.Italic in span.marks) FontStyle.Italic else FontStyle.Normal,
                    color = color,
                ),
            ) {
                append(span.text)
            }
            mentions.filter { it.start < spanEnd && it.end > spanStart }.forEach { mention ->
                val start = maxOf(mention.start, spanStart)
                val end = minOf(mention.end, spanEnd)
                addStyle(
                    SpanStyle(
                        color = if (linkColor != Color.Unspecified) linkColor else color,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                    start,
                    end,
                )
                addStringAnnotation(CodexMentionTag, mention.entryId, start, end)
            }
            offset = spanEnd
        }
    }

/** Collapse adjacent spans that share identical styling. */
fun List<Span>.coalesce(): List<Span> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<Span>()
    for (span in this) {
        if (span.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (
            last != null &&
            last.marks == span.marks &&
            last.colorHex == span.colorHex &&
            last.highlightHex == span.highlightHex &&
            last.codexEntryId == span.codexEntryId
        ) {
            out[out.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            out += span
        }
    }
    return out.ifEmpty { listOf(Span("")) }
}

fun List<Span>.slice(start: Int, end: Int): List<Span> {
    val plain = plainText()
    val s = start.coerceIn(0, plain.length)
    val e = end.coerceIn(s, plain.length)
    if (s == e) return emptyList()
    val out = mutableListOf<Span>()
    var cursor = 0
    for (span in this) {
        val spanEnd = cursor + span.text.length
        val overlapStart = maxOf(cursor, s)
        val overlapEnd = minOf(spanEnd, e)
        if (overlapStart < overlapEnd) {
            out += span.copy(text = span.text.substring(overlapStart - cursor, overlapEnd - cursor))
        }
        cursor = spanEnd
        if (cursor >= e) break
    }
    return out
}

fun List<Span>.replaceRange(start: Int, end: Int, insertion: List<Span>): List<Span> {
    val plain = plainText()
    val s = start.coerceIn(0, plain.length)
    val e = end.coerceIn(s, plain.length)
    return (slice(0, s) + insertion + slice(e, plain.length)).coalesce()
}

fun List<Span>.replaceRangeText(start: Int, end: Int, text: String, styleFrom: Span? = null): List<Span> {
    val template = styleFrom ?: slice(start, end).firstOrNull() ?: Span("")
    return replaceRange(
        start,
        end,
        listOf(
            Span(
                text = text,
                marks = template.marks,
                colorHex = template.colorHex,
                highlightHex = template.highlightHex,
                codexEntryId = template.codexEntryId,
            ),
        ),
    )
}

fun List<Span>.toggleMark(start: Int, end: Int, mark: Mark): List<Span> {
    val plain = plainText()
    val s = start.coerceIn(0, plain.length)
    val e = end.coerceIn(s, plain.length)
    if (s == e) return this
    val selected = slice(s, e)
    val allHave = selected.isNotEmpty() && selected.all { mark in it.marks }
    val restyled = selected.map { span ->
        span.copy(marks = if (allHave) span.marks - mark else span.marks + mark)
    }
    return replaceRange(s, e, restyled)
}

fun List<Span>.applyColor(start: Int, end: Int, colorHex: String?): List<Span> {
    val plain = plainText()
    val s = start.coerceIn(0, plain.length)
    val e = end.coerceIn(s, plain.length)
    if (s == e) return this
    val restyled = slice(s, e).map { it.copy(colorHex = colorHex) }
    return replaceRange(s, e, restyled)
}

/**
 * Rebuild spans after a plain-text edit, inheriting style from the character
 * before the edit start (or the first span) for inserted text.
 */
fun List<Span>.remapAfterPlainEdit(
    oldText: String,
    newText: String,
    selectionStart: Int,
    selectionEnd: Int,
): List<Span> {
    if (oldText == newText) return this
    val s = selectionStart.coerceIn(0, oldText.length)
    val e = selectionEnd.coerceIn(s, oldText.length)
    val prefixLen = s
    val suffixFromOld = oldText.length - e
    val insertedLen = (newText.length - prefixLen - suffixFromOld).coerceAtLeast(0)
    val inserted = if (insertedLen > 0) {
        newText.substring(prefixLen, prefixLen + insertedLen)
    } else {
        ""
    }
    val styleSource = when {
        s > 0 -> slice(s - 1, s).firstOrNull()
        else -> firstOrNull()
    } ?: Span("")
    val prefix = slice(0, s)
    val suffix = slice(e, oldText.length)
    val middle = if (inserted.isEmpty()) {
        emptyList()
    } else {
        listOf(
            Span(
                text = inserted,
                marks = styleSource.marks,
                colorHex = styleSource.colorHex,
                highlightHex = styleSource.highlightHex,
                codexEntryId = styleSource.codexEntryId,
            ),
        )
    }
    val rebuilt = (prefix + middle + suffix).coalesce()
    // Fallback if lengths drifted (e.g. IME quirks)
    return if (rebuilt.plainText() == newText) rebuilt else listOf(Span(newText))
}

fun AnnotatedString.toDocumentSpans(): List<Span> {
    if (isEmpty()) return listOf(Span(""))
    val out = mutableListOf<Span>()
    var index = 0
    while (index < length) {
        val style = spanStyles.firstOrNull { it.start <= index && it.end > index }?.item
        var end = index + 1
        while (end < length) {
            val next = spanStyles.firstOrNull { it.start <= end && it.end > end }?.item
            if (next != style) break
            end++
        }
        val marks = buildSet {
            if (style?.fontWeight == FontWeight.Bold || (style?.fontWeight?.weight ?: 0) >= 600) {
                add(Mark.Bold)
            }
            if (style?.fontStyle == FontStyle.Italic) add(Mark.Italic)
        }
        val colorHex = style?.color?.takeUnless { it == Color.Unspecified }?.let { color ->
            val argb = android.graphics.Color.argb(
                (color.alpha * 255).toInt().coerceIn(0, 255),
                (color.red * 255).toInt().coerceIn(0, 255),
                (color.green * 255).toInt().coerceIn(0, 255),
                (color.blue * 255).toInt().coerceIn(0, 255),
            )
            "#%06X".format(0xFFFFFF and argb)
        }
        out += Span(text = text.substring(index, end), marks = marks, colorHex = colorHex)
        index = end
    }
    return out.coalesce()
}
