package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

@Serializable
enum class Align { Start, Center, End, Justify }

@Serializable
enum class DividerStyle { SceneBreak, HorizontalRule }

@Serializable
enum class MediaKind { Image, Video, Audio }

@Serializable
enum class TextOverlayStyle { Plain, SpeechBubble }

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    val style: TextOverlayStyle = TextOverlayStyle.Plain,
    /** Center position within the panel, percent of panel width/height (0-100). */
    val xPercent: Float = 50f,
    val yPercent: Float = 50f,
    val widthPercent: Float = 60f,
    val fontSizeSp: Float = 16f,
    val colorHex: String = "#FFFFFF",
    val backgroundHex: String? = "#000000",
    val backgroundAlpha: Float = 0.55f,
    val rotationDeg: Float = 0f,
    /** Direction the speech-bubble tail points, degrees; unused for Plain. */
    val tailAngleDeg: Float = 270f,
)

@Serializable
enum class Mark {
    Bold, Italic, Underline, Strikethrough, Code, Superscript, Subscript,
}

@Serializable
data class Span(
    val text: String,
    val marks: Set<Mark> = emptySet(),
    val colorHex: String? = null,
    val highlightHex: String? = null,
    val codexEntryId: String? = null,
)

@Serializable
sealed interface Block {
    val id: String
}

@Serializable
data class Paragraph(
    override val id: String,
    val spans: List<Span>,
    val align: Align = Align.Start,
    val indentLevel: Int = 0,
) : Block

@Serializable
data class Heading(
    override val id: String,
    val level: Int,
    val spans: List<Span>,
) : Block

@Serializable
data class Quote(
    override val id: String,
    val spans: List<Span>,
) : Block

@Serializable
data class ListItem(
    override val id: String,
    val ordered: Boolean,
    val depth: Int,
    val spans: List<Span>,
) : Block

@Serializable
data class Divider(
    override val id: String,
    val style: DividerStyle,
) : Block

@Serializable
data class MediaBlock(
    override val id: String,
    val mediaId: String,
    val kind: MediaKind,
    val widthPercent: Float = 100f,
    val align: Align = Align.Center,
    val caption: List<Span> = emptyList(),
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
    /** Snap cell, 0-based. -1 = auto / unset. */
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    /** How many grid cells wide/tall. */
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    /** When true, show a compact bar instead of full media. */
    val collapsed: Boolean = false,
    /** Storyboard page this panel belongs to. null = the chat's default/first page. */
    val pageId: String? = null,
    /** Pan/zoom of the media within its panel frame (independent of panel size). */
    val mediaScale: Float = 1f,
    val mediaOffsetXPercent: Float = 0f,
    val mediaOffsetYPercent: Float = 0f,
    val overlays: List<TextOverlay> = emptyList(),
    /** Tilts the whole panel frame — comic pages use slanted gutters for pace. */
    val panelRotationDeg: Float = 0f,
) : Block

@Serializable
data class SceneBeatBlock(
    override val id: String,
    val prompt: String,
    val collapsed: Boolean = false,
    val generatedMessageId: String? = null,
) : Block

@Serializable
data class CodeBlock(
    override val id: String,
    val text: String,
    val language: String? = null,
) : Block

@Serializable
data class MediaStackBlock(
    override val id: String,
    val mediaIds: List<String>,
    val currentIndex: Int = 0,
    /** Snap cell, 0-based. -1 = auto / unset. */
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    val collapsed: Boolean = false,
    /** Storyboard page this panel belongs to. null = the chat's default/first page. */
    val pageId: String? = null,
    /** Pan/zoom of the media within its panel frame (independent of panel size). */
    val mediaScale: Float = 1f,
    val mediaOffsetXPercent: Float = 0f,
    val mediaOffsetYPercent: Float = 0f,
    val overlays: List<TextOverlay> = emptyList(),
    /** Tilts the whole panel frame — comic pages use slanted gutters for pace. */
    val panelRotationDeg: Float = 0f,
) : Block

@Serializable
data class MediaGridBlock(
    override val id: String,
    val mediaIds: List<String>,
    val template: String = "2-up",
    val gutterDp: Int = 8,
) : Block

@Serializable
data class Document(
    val blocks: List<Block> = emptyList(),
) {
    companion object {
        fun empty() = Document()
        fun fromPlainText(text: String, blockId: String = "p-1"): Document {
            if (text.isBlank()) return empty()
            return Document(listOf(Paragraph(blockId, listOf(Span(text)))))
        }
    }
}

fun Document.plainText(): String = buildString {
    blocks.forEach { block ->
        when (block) {
            is Paragraph -> append(block.spans.joinToString("") { it.text })
            is Heading -> append(block.spans.joinToString("") { it.text })
            is Quote -> append(block.spans.joinToString("") { it.text })
            is ListItem -> append(block.spans.joinToString("") { it.text })
            is CodeBlock -> append(block.text)
            else -> Unit
        }
        append('\n')
    }
}.trim()

fun Document.wordCount(): Int {
    val words = plainText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size
}

fun Block.plainText(): String = when (this) {
    is Paragraph -> spans.joinToString("") { it.text }
    is Heading -> spans.joinToString("") { it.text }
    is Quote -> spans.joinToString("") { it.text }
    is ListItem -> spans.joinToString("") { it.text }
    is CodeBlock -> text
    is MediaBlock -> caption.joinToString("") { it.text }
    else -> ""
}

fun Document.referencedMediaIds(): List<String> = blocks.flatMap { block ->
    when (block) {
        is MediaBlock -> listOf(block.mediaId)
        is MediaStackBlock -> block.mediaIds
        is MediaGridBlock -> block.mediaIds
        else -> emptyList()
    }
}.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

data class MediaPlacement(
    val blockId: String,
    val mediaIds: List<String>,
    val kind: String,
    val blockIndex: Int,
)

fun Document.mediaPlacement(): List<MediaPlacement> =
    blocks.mapIndexedNotNull { index, block ->
        when (block) {
            is MediaBlock -> MediaPlacement(block.id, listOf(block.mediaId), "media", index)
            is MediaStackBlock -> MediaPlacement(block.id, block.mediaIds, "stack", index)
            is MediaGridBlock -> MediaPlacement(block.id, block.mediaIds, "grid", index)
            else -> null
        }
    }

fun Document.speakableParagraphs(): List<String> = blocks.mapNotNull { block ->
    block.takeIf { it.isSpeakable() }?.plainText()?.trim()
}

fun Block.isSpeakable(): Boolean = when (this) {
    is Paragraph, is Heading, is Quote, is ListItem, is CodeBlock -> plainText().isNotBlank()
    else -> false
}

fun Paragraph.isSlashCommandResidue(): Boolean {
    val text = spans.joinToString("") { it.text }.trim()
    if (text.isEmpty()) return false
    return text.startsWith("/") || text == "\\"
}

/**
 * Insert [media] after [index], keeping surrounding prose. Only a paragraph that is
 * leftover slash-command text (`/image`, `\\`, …) is cleared.
 */
fun List<Block>.insertMediaAfter(index: Int, media: Block): List<Block> {
    if (isEmpty()) return listOf(media)
    val next = toMutableList()
    val sourceIndex = index.coerceAtLeast(-1)
    if (sourceIndex in next.indices) {
        val target = next[sourceIndex]
        if (target is Paragraph && target.isSlashCommandResidue()) {
            next[sourceIndex] = target.copy(spans = listOf(Span("")))
        }
    }
    val insertAt = (sourceIndex + 1).coerceIn(0, next.size)
    next.add(insertAt, media)
    return next
}
