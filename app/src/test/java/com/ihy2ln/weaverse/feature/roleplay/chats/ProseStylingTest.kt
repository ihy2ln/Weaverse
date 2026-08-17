package com.ihy2ln.weaverse.feature.roleplay.chats

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProseStylingTest {
    private val narration = Color.Red
    private val speech = Color.Blue
    private val ooc = Color.Gray
    private val body = Color.Black

    @Test
    fun `plain text with no markers keeps body color`() {
        val result = buildProseAnnotatedString("Just narration prose.", narration, speech, ooc, body)
        assertEquals("Just narration prose.", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(body, result.spanStyles.single().item.color)
    }

    @Test
    fun `asterisked text is italic in the narration color`() {
        val result = buildProseAnnotatedString("*The wind howls.*", narration, speech, ooc, body)
        assertEquals("The wind howls.", result.text)
        val style = result.spanStyles.single().item
        assertEquals(narration, style.color)
        assertEquals(FontStyle.Italic, style.fontStyle)
    }

    @Test
    fun `quoted speech is colored with the speech color`() {
        val result = buildProseAnnotatedString("\"Hello there.\"", narration, speech, ooc, body)
        assertEquals("Hello there.", result.text)
        assertEquals(speech, result.spanStyles.single().item.color)
    }

    @Test
    fun `bracketed ooc is muted and smaller`() {
        val result = buildProseAnnotatedString("[out of character note]", narration, speech, ooc, body)
        assertEquals("out of character note", result.text)
        val style = result.spanStyles.single().item
        assertEquals(ooc, style.color)
        assertTrue(style.fontSize.value < 14f)
    }

    @Test
    fun `mixed markers strip delimiters and style each segment independently`() {
        val result = buildProseAnnotatedString(
            "*She sighs.* \"I told you.\" [ooc: aside]",
            narration,
            speech,
            ooc,
            body,
        )
        assertEquals("She sighs. I told you. ooc: aside", result.text)
        // Five appended runs: narration, body(space), speech, body(space), ooc — not merged,
        // even though the two body-colored spaces share identical styling.
        assertEquals(5, result.spanStyles.size)
        assertEquals(narration, result.spanStyles[0].item.color)
        assertEquals(speech, result.spanStyles[2].item.color)
        assertEquals(ooc, result.spanStyles[4].item.color)
    }
}
