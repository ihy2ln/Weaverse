package com.ihy2ln.weaverse.data.export

import com.ihy2ln.weaverse.core.text.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NovelHtmlRendererTest {
    @Test fun preservesProseIllustrationOrderAndEscapesMarkup() {
        val html = NovelHtmlRenderer.render(listOf(
            Paragraph("a", listOf(Span("Before <script>", setOf(Mark.Bold)))),
            MediaBlock("b", "art", MediaKind.Image, caption = listOf(Span("Map & coast"))),
            Paragraph("c", listOf(Span("After"))),
        ), mapOf("art" to "data:image/png;base64,YQ==", "reference-only" to "data:image/png;base64,Yg=="))
        assertTrue(html.contains("<strong>Before &lt;script&gt;</strong>"))
        assertTrue(html.indexOf("Before") < html.indexOf("<img"))
        assertTrue(html.indexOf("<img") < html.indexOf("After"))
        assertTrue(html.contains("Map &amp; coast"))
        assertFalse(html.contains("Yg=="))
    }
    @Test fun missingImagesAndAudioHaveNoticesAndNeverAutoplay() {
        val html = NovelHtmlRenderer.render(listOf(MediaBlock("a", "lost", MediaKind.Image), MediaBlock("b", "music", MediaKind.Audio, autoplay = true)), emptyMap())
        assertTrue(html.contains("Image not embedded"))
        assertTrue(html.contains("Audio not embedded"))
        assertFalse(html.contains("autoplay"))
    }
}
