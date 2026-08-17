package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.ui.parseHex

/**
 * Renders a [Paragraph][com.ihy2ln.weaverse.core.text.Paragraph]'s [spans] — marks, text colour,
 * highlight — inside a `BasicTextField` (spec §7: Highlight/Text Colour/Bold/Italic/Underline/
 * Strike must be visible, not just stored). Built from [spans] directly rather than the `text`
 * [VisualTransformation.filter] receives, since [spans] is always kept in sync with the field's
 * plain text by [com.ihy2ln.weaverse.core.text.updateSpansForTextChange] — length is preserved
 * one-for-one (styling only, no characters added or removed), so [OffsetMapping.Identity] is
 * exactly right.
 */
class SpanVisualTransformation(private val spans: List<Span>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        spans.forEach { span ->
            builder.withStyle(span.toSpanStyle()) { append(span.text) }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun Span.toSpanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (Mark.Bold in marks) FontWeight.Bold else null,
    fontStyle = if (Mark.Italic in marks) FontStyle.Italic else null,
    textDecoration = marks.toTextDecoration(),
    color = colorHex?.let(::parseHex) ?: Color.Unspecified,
    background = highlightHex?.let(::parseHex)?.copy(alpha = 0.4f) ?: Color.Unspecified,
)

private fun Set<Mark>.toTextDecoration(): TextDecoration? {
    val decorations = buildList {
        if (Mark.Underline in this@toTextDecoration) add(TextDecoration.Underline)
        if (Mark.Strikethrough in this@toTextDecoration) add(TextDecoration.LineThrough)
    }
    return if (decorations.isEmpty()) null else TextDecoration.combine(decorations)
}
