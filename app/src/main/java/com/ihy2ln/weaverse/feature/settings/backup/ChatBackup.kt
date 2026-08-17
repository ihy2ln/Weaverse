package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.ExportNode
import com.ihy2ln.weaverse.core.export.ExportOutline
import com.ihy2ln.weaverse.data.db.entity.ChatRole
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageBackup(val role: ChatRole, val plainText: String)

@Serializable
data class ChatBackup(val formatVersion: Int = 1, val threadName: String, val messages: List<ChatMessageBackup>)

/** A chat is a flat log, not a nested document — each message becomes one plain paragraph
 * prefixed with its speaker, e.g. "User: ...", "Assistant: ...". No headings involved. */
fun ChatBackup.toOutline(): ExportOutline = ExportOutline(
    title = threadName,
    nodes = messages.map { ExportNode.Paragraph("${it.role}: ${it.plainText}") },
)

private val rolePrefixPattern = Regex("^(User|Assistant|System):\\s?(.*)$", RegexOption.DOT_MATCHES_ALL)

/** Only paragraphs matching the "Role: text" prefix this app itself writes are recognized —
 * plain prose without that prefix (e.g. from a hand-edited Markdown file) is silently skipped
 * rather than guessed at, since there's no reliable way to infer who said an unprefixed line. */
fun ExportOutline.toChatBackup(): ChatBackup {
    val messages = nodes.mapNotNull { node ->
        val text = when (node) {
            is ExportNode.Paragraph -> node.text
            is ExportNode.Heading -> node.text
        }
        val match = rolePrefixPattern.find(text) ?: return@mapNotNull null
        val role = runCatching { ChatRole.valueOf(match.groupValues[1]) }.getOrNull() ?: return@mapNotNull null
        ChatMessageBackup(role = role, plainText = match.groupValues[2])
    }
    return ChatBackup(threadName = title, messages = messages)
}
