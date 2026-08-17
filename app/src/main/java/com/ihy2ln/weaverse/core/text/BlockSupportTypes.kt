package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

enum class Align { Start, Center, End }

/** `SceneBreak` renders as `***`; `HorizontalRule` as a plain `---`. */
enum class DividerStyle { SceneBreak, HorizontalRule }

enum class MediaKind { Image, Video }

/**
 * Stand-in for the spec's `RectF` (§6 `MediaBlock.cropRect`) — a serializable
 * value type instead of `android.graphics.RectF`, which kotlinx.serialization
 * has no built-in serializer for and which would otherwise pull an Android
 * platform type into this module's plain-Kotlin document model.
 */
@Serializable
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** One slot inside a [MediaStack] or [MediaGrid] (spec §8) — lighter than a full [MediaBlock]
 * since stack/grid-level concerns (overall width/align) live on the containing block, not per
 * item; per-item autoplay/loop/muted stay here since a grid cell can be an independently
 * playing video. */
@Serializable
data class MediaItemRef(
    val mediaId: String,
    val kind: MediaKind,
    val caption: List<Span> = emptyList(),
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
)

/**
 * Grid layout templates (spec §8): the five regular layouts plus three manga/comic/manhwa
 * presets. [MangaPage] is an approximation, not a true irregular/arbitrary-shaped panel grid —
 * "irregular" panel layouts are an open-ended design tool in their own right (arbitrary panel
 * boundaries, spans, rotations) that a fixed enum of templates can't represent; this renders as
 * one wide top panel over a 2-up row, which reads as "manga-ish" without pretending to be a
 * general irregular-grid editor. See BUILD_NOTES "rev02-09" for the full scope note.
 */
enum class MediaGridTemplate { TwoUp, ThreeUp, TwoByTwo, OnePlusTwo, ThreeByThree, WebtoonStrip, MangaPage, FourKoma }
