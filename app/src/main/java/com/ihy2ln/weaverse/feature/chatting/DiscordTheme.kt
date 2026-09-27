package com.ihy2ln.weaverse.feature.chatting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.ihy2ln.weaverse.core.ui.theme.InkThemeTokens
import com.ihy2ln.weaverse.core.ui.theme.LocalInkTokens
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** Discord's own surface colours, in its dark and light themes. */
data class DiscordColors(
    val dark: Boolean,
    /** Server rail, the darkest strip. */
    val rail: Color,
    /** Channel sidebar and member list. */
    val sidebar: Color,
    /** The conversation itself. */
    val chat: Color,
    /** User panel under the channel list. */
    val panel: Color,
    /** Input box, popouts, embeds. */
    val elevated: Color,
    val hover: Color,
    val selected: Color,
    val text: Color,
    val muted: Color,
    val header: Color,
    val divider: Color,
    val codeBg: Color,
    val link: Color,
    val mentionBg: Color,
    val mentionPill: Color,
    val mentionPillText: Color,
) {
    val blurple = Color(0xFF5865F2)
    val green = Color(0xFF23A55A)
    val idle = Color(0xFFF0B232)
    val dnd = Color(0xFFF23F43)
    val red = Color(0xFFF23F43)
    val gold = Color(0xFFF0B232)
}

private val DiscordDark = DiscordColors(
    dark = true,
    rail = Color(0xFF1E1F22),
    sidebar = Color(0xFF2B2D31),
    chat = Color(0xFF313338),
    panel = Color(0xFF232428),
    elevated = Color(0xFF383A40),
    hover = Color(0xFF35373C),
    selected = Color(0xFF404249),
    text = Color(0xFFDBDEE1),
    muted = Color(0xFF949BA4),
    header = Color(0xFFF2F3F5),
    divider = Color(0xFF3F4147),
    codeBg = Color(0xFF2B2D31),
    link = Color(0xFF00A8FC),
    mentionBg = Color(0x1AF0B232),
    mentionPill = Color(0x4D5865F2),
    mentionPillText = Color(0xFFC9CDFB),
)

private val DiscordLight = DiscordColors(
    dark = false,
    rail = Color(0xFFE3E5E8),
    sidebar = Color(0xFFF2F3F5),
    chat = Color(0xFFFFFFFF),
    panel = Color(0xFFEBEDEF),
    elevated = Color(0xFFEBEDEF),
    hover = Color(0xFFF2F3F5),
    selected = Color(0xFFD7D9DC),
    text = Color(0xFF313338),
    muted = Color(0xFF5C5E66),
    header = Color(0xFF060607),
    divider = Color(0xFFE1E2E4),
    codeBg = Color(0xFFF2F3F5),
    link = Color(0xFF006CE7),
    mentionBg = Color(0x1FF0B232),
    mentionPill = Color(0x265865F2),
    mentionPillText = Color(0xFF505CDC),
)

val LocalDiscordColors = staticCompositionLocalOf { DiscordDark }

@Composable
fun discordColors(): DiscordColors = LocalDiscordColors.current

/**
 * Follows the app's light or dark theme with Discord's matching palette, and re-skins
 * the shared ink tokens so the prompt window and dialogs inside it match too.
 */
@Composable
fun DiscordTheme(content: @Composable () -> Unit) {
    val colors = if (inkTokens().background.luminance() < 0.5f) DiscordDark else DiscordLight
    val tokens = InkThemeTokens(
        background = colors.sidebar,
        panel = colors.rail,
        page = colors.chat,
        hairline = colors.divider,
        hover = colors.hover,
        primaryText = colors.text,
        secondaryText = colors.muted,
        activePill = colors.blurple,
        activePillLabel = Color.White,
    )
    CompositionLocalProvider(
        LocalDiscordColors provides colors,
        LocalInkTokens provides tokens,
        content = content,
    )
}

/** Presence shown on the writer's avatar and on members. */
enum class DiscordStatus(val label: String) {
    Online("Online"),
    Idle("Idle"),
    DoNotDisturb("Do Not Disturb"),
    Invisible("Invisible"),
}

fun DiscordColors.statusColor(status: DiscordStatus): Color = when (status) {
    DiscordStatus.Online -> green
    DiscordStatus.Idle -> idle
    DiscordStatus.DoNotDisturb -> dnd
    DiscordStatus.Invisible -> muted
}

/** One chunk of a parsed Discord message: prose, a fenced code block, or a > quote. */
sealed interface DiscordBlock {
    data class Prose(val text: String) : DiscordBlock
    data class Code(val code: String, val language: String) : DiscordBlock
    data class Quote(val text: String) : DiscordBlock
}

/** Splits a message into ``` code blocks, > quote lines, and ordinary prose. */
fun parseDiscordBlocks(raw: String): List<DiscordBlock> {
    val out = mutableListOf<DiscordBlock>()
    val fence = Regex("```([a-zA-Z0-9+#-]*)\\n?([\\s\\S]*?)```")
    var cursor = 0
    fence.findAll(raw).forEach { match ->
        if (match.range.first > cursor) out += proseAndQuotes(raw.substring(cursor, match.range.first))
        out += DiscordBlock.Code(match.groupValues[2].trimEnd(), match.groupValues[1])
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) out += proseAndQuotes(raw.substring(cursor))
    return out.filterNot { it is DiscordBlock.Prose && it.text.isBlank() }
}

private fun proseAndQuotes(text: String): List<DiscordBlock> {
    val out = mutableListOf<DiscordBlock>()
    val prose = StringBuilder()
    val quote = StringBuilder()
    fun flushProse() {
        if (prose.isNotEmpty()) out += DiscordBlock.Prose(prose.toString().trim('\n'))
        prose.clear()
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) out += DiscordBlock.Quote(quote.toString().trimEnd('\n'))
        quote.clear()
    }
    text.lines().forEach { line ->
        if (line.startsWith("> ") || line == ">") {
            flushProse()
            quote.append(line.removePrefix(">").removePrefix(" ")).append('\n')
        } else {
            flushQuote()
            prose.append(line).append('\n')
        }
    }
    flushProse()
    flushQuote()
    return out
}

/**
 * Discord's inline markdown: **bold**, *italic* / _italic_, __underline__, ~~strike~~,
 * `code`, ||spoiler||, @mentions and links. Spoilers are hidden until [revealSpoilers].
 */
fun discordInline(
    text: String,
    colors: DiscordColors,
    revealSpoilers: Boolean,
): AnnotatedString = buildAnnotatedString {
    val token = Regex(
        "(\\*\\*[^*]+?\\*\\*)|(__[^_]+?__)|(~~[^~]+?~~)|(\\|\\|[^|]+?\\|\\|)|(`[^`]+?`)|" +
            "(\\*[^*\\s][^*]*?\\*)|(_[^_\\s][^_]*?_)|(@[A-Za-z][A-Za-z'.-]*(?: [A-Z][A-Za-z'.-]*)?)|" +
            "(https?://\\S+)",
    )
    var cursor = 0
    token.findAll(text).forEach { m ->
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        val v = m.value
        when {
            v.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(discordInline(v.substring(2, v.length - 2), colors, revealSpoilers))
            }
            v.startsWith("__") -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(discordInline(v.substring(2, v.length - 2), colors, revealSpoilers))
            }
            v.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(discordInline(v.substring(2, v.length - 2), colors, revealSpoilers))
            }
            v.startsWith("||") -> {
                val inner = v.substring(2, v.length - 2)
                if (revealSpoilers) {
                    withStyle(SpanStyle(background = colors.selected)) { append(inner) }
                } else {
                    withStyle(SpanStyle(background = colors.rail, color = colors.rail)) { append(inner) }
                }
            }
            v.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBg),
            ) { append(v.substring(1, v.length - 1)) }
            v.startsWith("*") || v.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(discordInline(v.substring(1, v.length - 1), colors, revealSpoilers))
            }
            v.startsWith("@") -> withStyle(
                SpanStyle(
                    background = colors.mentionPill,
                    color = colors.mentionPillText,
                    fontWeight = FontWeight.Medium,
                ),
            ) { append(v) }
            else -> withStyle(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)) {
                append(v)
            }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/** True when the whole message is one to three emoji, which Discord shows jumbo-sized. */
fun isJumboEmoji(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 24) return false
    if (trimmed.any { it.isLetterOrDigit() }) return false
    val count = trimmed.codePoints().filter { cp ->
        Character.getType(cp) == Character.OTHER_SYMBOL.toInt() || cp in 0x1F000..0x1FAFF
    }.count()
    return count in 1..3
}
