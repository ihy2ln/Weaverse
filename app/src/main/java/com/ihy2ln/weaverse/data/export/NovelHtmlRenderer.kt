package com.ihy2ln.weaverse.data.export

import com.ihy2ln.weaverse.core.text.*

/** Offline illustrations; reference attachments are never passed here. */
internal object NovelHtmlRenderer {
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun spans(spans: List<Span>) = spans.joinToString("") { span ->
        var text = escape(span.text).replace("\n", "<br/>")
        span.marks.forEach { mark ->
            val tag = when (mark) { Mark.Bold -> "strong"; Mark.Italic -> "em"; Mark.Underline -> "u"
                Mark.Strikethrough -> "s"; Mark.Code -> "code"; Mark.Superscript -> "sup"; Mark.Subscript -> "sub" }
            text = "<$tag>$text</$tag>"
        }
        text
    }
    fun render(blocks: List<Block>, images: Map<String, String>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is Paragraph -> "<p>${spans(block.spans)}</p>"
            is Heading -> block.level.coerceIn(1, 6).let { "<h$it>${spans(block.spans)}</h$it>" }
            is Quote -> "<blockquote>${spans(block.spans)}</blockquote>"
            is ListItem -> "<p>${if (block.ordered) "1." else "•"} ${spans(block.spans)}</p>"
            is CodeBlock -> "<pre>${escape(block.text)}</pre>"
            is Divider -> "<hr/>"
            is MediaBlock -> {
                val caption = spans(block.caption)
                val data = images[block.mediaId]?.takeIf { it.startsWith("data:image/") && ";base64," in it }
                if (block.kind == MediaKind.Image && data != null)
                    "<figure><img src=\"${escape(data)}\" alt=\"${escape(block.caption.plainText())}\"/><figcaption>$caption</figcaption></figure>"
                else "<figure><p>[${block.kind.name} not embedded; retained in WeaverVerse. Use a full backup to preserve the media.]</p><figcaption>$caption</figcaption></figure>"
            }
            is MediaStackBlock -> render(block.mediaIds.map { MediaBlock(it, it, MediaKind.Image) }, images)
            is MediaGridBlock -> render(block.mediaIds.map { MediaBlock(it, it, MediaKind.Image) }, images)
            is SceneBeatBlock -> ""
        }
    }
}
