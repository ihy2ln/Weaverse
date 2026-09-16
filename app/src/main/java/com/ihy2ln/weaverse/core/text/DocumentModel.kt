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
    /** Optional fixed box height. Zero keeps the legacy content-sized behavior. */
    val heightPercent: Float = 0f,
    val fontSizeSp: Float = 16f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val fontFamily: String = "sans-serif",
    val colorHex: String = "#FFFFFF",
    val backgroundHex: String? = "#000000",
    val backgroundAlpha: Float = 0.55f,
    val rotationDeg: Float = 0f,
    /** Direction the speech-bubble tail points, degrees; unused for Plain. */
    val tailAngleDeg: Float = 270f,
    /** Optional durable origin marker, for example manga-translation. */
    val source: String = "",
    // Older overlays have unknown edit history: protect them until explicitly selected.
    val manuallyAdjusted: Boolean = true,
    /** Manga lettering metadata. Defaults keep older saved documents compatible. */
    val autoFit: Boolean = false,
    val alignment: String = "Center",
    val writingMode: String = "Horizontal",
    val lineSpacing: Float = 1f,
    val paddingFraction: Float = .08f,
    val cleanupEnabled: Boolean = true,
    val strokeHex: String = "#FFFFFF",
    val strokeWidth: Float = 0f,
    val sourceLanguage: String = "",
    /** Fixed source lettering bounds; moving the replacement never changes these. */
    val cleanupXPercent: Float = -1f,
    val cleanupYPercent: Float = -1f,
    val cleanupWidthPercent: Float = -1f,
    val cleanupHeightPercent: Float = -1f,
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
    /** Immutable source media retained when the editor creates a derived bitmap. */
    val originalMediaId: String? = null,
    /** original | edited | colorized; additive for older serialized documents. */
    val variantKind: String = "original",
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
