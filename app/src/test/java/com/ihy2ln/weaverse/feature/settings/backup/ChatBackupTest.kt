package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.data.db.entity.ChatRole
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatBackupTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val backup = ChatBackup(
        threadName = "Workshop Chat",
        messages = listOf(
            ChatMessageBackup(role = ChatRole.User, plainText = "How should Mara react here?"),
            ChatMessageBackup(role = ChatRole.Assistant, plainText = "She'd deflect with dry humor before admitting she's scared."),
        ),
    )

    @Test
    fun `decoding an encoded chat backup recovers every message`() {
        val decoded = json.decodeFromString<ChatBackup>(json.encodeToString(backup))
        assertEquals(backup, decoded)
    }

    @Test
    fun `markdown round-trip preserves role and text for every message`() {
        val markdown = backup.toOutline().toMarkdown()
        val reimported = markdown.parseMarkdownOutline(fallbackTitle = "fallback").toChatBackup()

        assertEquals(backup.threadName, reimported.threadName)
        assertEquals(backup.messages, reimported.messages)
    }

    @Test
    fun `unprefixed paragraphs are skipped rather than guessed at`() {
        val outline = "Just some prose with no role prefix.".parseMarkdownOutline(fallbackTitle = "Chat")
        val reimported = outline.toChatBackup()

        assertEquals(emptyList<ChatMessageBackup>(), reimported.messages)
    }
}
