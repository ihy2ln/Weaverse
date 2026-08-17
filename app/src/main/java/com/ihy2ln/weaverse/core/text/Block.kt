package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

/**
 * The unit of content shared by scenes, codex entry bodies, workshop chat
 * messages, and roleplay messages (spec §6). A closed sealed hierarchy —
 * kotlinx.serialization derives polymorphic (de)serialization for it
 * automatically from the compiler plugin, no manual SerializersModule needed.
 */
@Serializable
sealed interface Block {
    val id: String
}

@Serializable
data class Paragraph(
    override val id: String,
    val spans: List<Span> = emptyList(),
    val align: Align = Align.Start,
    val indentLevel: Int = 0,
) : Block

@Serializable
data class Heading(
    override val id: String,
    val level: Int,
    val spans: List<Span> = emptyList(),
) : Block

@Serializable
data class Quote(
    override val id: String,
    val spans: List<Span> = emptyList(),
) : Block

@Serializable
data class ListItem(
    override val id: String,
    val ordered: Boolean,
    val depth: Int = 0,
    val spans: List<Span> = emptyList(),
) : Block

/** A scene-break (`***`) or plain horizontal rule — see [DividerStyle]. */
@Serializable
data class Divider(
    override val id: String,
    val style: DividerStyle = DividerStyle.SceneBreak,
) : Block

/** An image or video in the flow — the resize/align/caption unit built out in Phase 6. */
@Serializable
data class MediaBlock(
    override val id: String,
    val mediaId: String,
    val kind: MediaKind,
    val widthPercent: Float = 100f,
    val align: Align = Align.Center,
    val cropRect: CropRect? = null,
    val caption: List<Span> = emptyList(),
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
) : Block

/** Multiple images/videos sharing one slot in the flow (spec §8) — a number-wheel/counter shows
 * position (`3/7`); swipe or spin the wheel to cycle, tap to open the full-screen pager. */
@Serializable
data class MediaStack(
    override val id: String,
    val items: List<MediaItemRef>,
    val coverIndex: Int = 0,
    val widthPercent: Float = 100f,
    val align: Align = Align.Center,
    /** Null = no autoplay-through slideshow. */
    val autoplayIntervalMs: Int? = null,
) : Block

/** A configurable multi-panel media layout (spec §8), including manga/webtoon/4-koma presets. */
@Serializable
data class MediaGrid(
    override val id: String,
    val template: MediaGridTemplate,
    val items: List<MediaItemRef>,
    val gutterDp: Int = 4,
    val cornerRadiusDp: Int = 0,
    val backgroundColorHex: String? = null,
    val aspectLocked: Boolean = true,
) : Block

/** An inline AI-generation prompt card in the manuscript flow (Write screen, Phase 10). */
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
