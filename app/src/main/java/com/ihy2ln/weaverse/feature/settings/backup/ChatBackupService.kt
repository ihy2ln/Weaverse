package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.DocxCodec
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.export.parseHtmlOutline
import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toHtml
import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.repo.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Workshop Chat export/import (spec follow-up: "chats ... should have the same import and
 * export options" as Books/Codex) — same four formats. Import appends the backup's messages
 * onto the end of [threadId]'s existing history rather than replacing it. */
@Singleton
class ChatBackupService @Inject constructor(private val chatRepository: ChatRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(threadId: String, format: ExportFormat): ByteArray? {
        val thread = chatRepository.getThread(threadId) ?: return null
        val messages = chatRepository.observeMessages(threadId).first().map {
            ChatMessageBackup(role = it.role, plainText = it.plainText)
        }
        val backup = ChatBackup(threadName = thread.name, messages = messages)

        return when (format) {
            ExportFormat.Json -> json.encodeToString(backup).toByteArray(Charsets.UTF_8)
            ExportFormat.Markdown -> backup.toOutline().toMarkdown().toByteArray(Charsets.UTF_8)
            ExportFormat.Html -> backup.toOutline().toHtml().toByteArray(Charsets.UTF_8)
            ExportFormat.Docx -> DocxCodec.encode(backup.toOutline())
        }
    }

    /** Returns how many messages were imported. */
    suspend fun import(bytes: ByteArray, format: ExportFormat, threadId: String): Int {
        val backup = when (format) {
            ExportFormat.Json -> json.decodeFromString<ChatBackup>(bytes.toString(Charsets.UTF_8))
            ExportFormat.Markdown -> bytes.toString(Charsets.UTF_8).parseMarkdownOutline("Chat").toChatBackup()
            ExportFormat.Html -> bytes.toString(Charsets.UTF_8).parseHtmlOutline("Chat").toChatBackup()
            ExportFormat.Docx -> DocxCodec.decode(bytes).toChatBackup()
        }

        backup.messages.forEach { messageBackup ->
            chatRepository.upsertMessage(
                ChatMessageEntity(
                    threadId = threadId,
                    role = messageBackup.role,
                    plainText = messageBackup.plainText,
                    wordCount = messageBackup.plainText.wordCount(),
                ),
            )
        }
        return backup.messages.size
    }
}
