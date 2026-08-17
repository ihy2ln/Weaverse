package com.ihy2ln.weaverse.core.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.ihy2ln.weaverse.core.text.MentionCandidate
import com.ihy2ln.weaverse.core.text.MentionScanner

private const val CODEX_MENTION_TAG = "codex_mention"

/**
 * Renders [text] with every detected codex-entry mention ([MentionScanner]) as a tappable,
 * underlined, entry-tinted inline link — "press the codex name that's in the prompt or the
 * generated text" (Scene Beat output, chat messages) to jump to that entry, the way other
 * novel-writing assistants do. Falls back to a plain [Text] when nothing in [candidates] matches,
 * so callers can use this unconditionally without checking first.
 */
@Composable
fun CodexMentionText(
    text: String,
    candidates: List<MentionCandidate>,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    entryColor: (entryId: String) -> Color = { Color.Unspecified },
) {
    val annotated = remember(text, candidates) {
        buildMentionAnnotatedString(text, candidates, entryColor)
    }
    if (annotated.getStringAnnotations(CODEX_MENTION_TAG, 0, annotated.length).isEmpty()) {
        Text(text, style = style, modifier = modifier)
    } else {
        ClickableText(
            text = annotated,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(CODEX_MENTION_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { onEntryClick(it.item) }
            },
        )
    }
}

private fun buildMentionAnnotatedString(
    text: String,
    candidates: List<MentionCandidate>,
    entryColor: (String) -> Color,
): AnnotatedString {
    val mentions = MentionScanner.findMentions(text, candidates)
    if (mentions.isEmpty()) return AnnotatedString(text)

    return AnnotatedString.Builder().apply {
        var cursor = 0
        mentions.forEach { mention ->
            if (mention.range.first > cursor) append(text.substring(cursor, mention.range.first))
            withStyle(SpanStyle(color = entryColor(mention.entryId), textDecoration = TextDecoration.Underline)) {
                pushStringAnnotation(CODEX_MENTION_TAG, mention.entryId)
                append(text.substring(mention.range.first, mention.range.last + 1))
                pop()
            }
            cursor = mention.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }.toAnnotatedString()
}
