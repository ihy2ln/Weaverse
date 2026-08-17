package com.ihy2ln.weaverse.feature.roleplay.chats

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Dungeon-master mode's colour roles (spec §9): `*asterisked action text*` narration,
 * `"quoted speech"`, `[bracketed OOC]`. */
enum class ProseRole { Narration, Speech, Ooc }

private val prosePatterns: List<Pair<Regex, ProseRole>> = listOf(
    Regex("\\*([^*]+)\\*") to ProseRole.Narration,
    Regex("\"([^\"]+)\"") to ProseRole.Speech,
    Regex("\\[([^]]+)]") to ProseRole.Ooc,
)

/**
 * Automatic prose styling (spec §9): `*action*` renders italic in [narrationColor],
 * `"speech"` renders in [speechColor], `[OOC]` renders muted/small in [oocColor]. Plain-text
 * segments outside any marker keep [bodyColor]. Same greedy-earliest-match, left-to-right scan
 * as [com.ihy2ln.weaverse.core.text.MarkdownConverter.parseSpans] — the established pattern in
 * this codebase for "find the next marker, split, recurse" text parsing — adapted to build an
 * [AnnotatedString] instead of a span list, since this is UI-only presentation, not part of the
 * persisted document model (the underlying [com.ihy2ln.weaverse.data.db.entity.RpMessageEntity.plainText]
 * stores the raw markers, so switching display modes re-derives styling, never loses it).
 */
fun buildProseAnnotatedString(
    text: String,
    narrationColor: Color,
    speechColor: Color,
    oocColor: Color,
    bodyColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendProse(builder, text, narrationColor, speechColor, oocColor, bodyColor)
    return builder.toAnnotatedString()
}

private fun appendProse(
    builder: AnnotatedString.Builder,
    text: String,
    narrationColor: Color,
    speechColor: Color,
    oocColor: Color,
    bodyColor: Color,
) {
    if (text.isEmpty()) return

    var earliestMatch: MatchResult? = null
    var earliestRole: ProseRole? = null
    for ((regex, role) in prosePatterns) {
        val match = regex.find(text) ?: continue
        if (earliestMatch == null || match.range.first < earliestMatch.range.first) {
            earliestMatch = match
            earliestRole = role
        }
    }

    val match = earliestMatch
    val role = earliestRole
    if (match == null || role == null) {
        builder.withStyle(SpanStyle(color = bodyColor)) { append(text) }
        return
    }

    val before = text.substring(0, match.range.first)
    val inner = match.groupValues[1]
    val after = text.substring(match.range.last + 1)

    if (before.isNotEmpty()) builder.withStyle(SpanStyle(color = bodyColor)) { append(before) }
    val style = when (role) {
        ProseRole.Narration -> SpanStyle(color = narrationColor, fontStyle = FontStyle.Italic)
        ProseRole.Speech -> SpanStyle(color = speechColor)
        // "Small" needs an absolute size here (no ambient body-text size to scale relative to
        // in a non-@Composable function) — 12sp reads as a caption-scale size against this
        // app's bodyLarge/bodyMedium manuscript type without needing that context threaded in.
        ProseRole.Ooc -> SpanStyle(color = oocColor, fontSize = 12.sp)
    }
    // The bracket/quote/asterisk markers themselves aren't rendered — only the text inside them.
    builder.withStyle(style) { append(inner) }
    appendProse(builder, after, narrationColor, speechColor, oocColor, bodyColor)
}
