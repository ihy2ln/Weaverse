package com.ihy2ln.weaverse.sync

import kotlinx.serialization.Serializable

/** Wire protocol version for Wi‑Fi / remote sync packages. */
const val SYNC_PROTOCOL_VERSION = 1

const val DEFAULT_SYNC_PORT = 8787

@Serializable
data class SyncManifest(
    val protocolVersion: Int = SYNC_PROTOCOL_VERSION,
    val exportedAt: Long,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String = "",
    val noteCount: Int = 0,
    val bookCount: Int = 0,
    val mediaFileCount: Int = 0,
)

@Serializable
data class SyncStatusResponse(
    val ok: Boolean = true,
    val protocolVersion: Int = SYNC_PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val hostMode: String,
    val port: Int,
    val pairPin: String? = null,
    val lastSyncAt: Long? = null,
    val hasLibrary: Boolean = false,
    val bookCount: Int = 0,
    val noteCount: Int = 0,
    val webUrl: String = "",
    val lanHint: String = "",
    val tls: Boolean = false,
    val certSha256: String = "",
)

@Serializable
data class SyncPairRequest(
    val pin: String,
)

@Serializable
data class SyncPairResponse(
    val ok: Boolean,
    val token: String? = null,
    val message: String = "",
    val certSha256: String = "",
    val tls: Boolean = false,
)

@Serializable
data class SyncPushResult(
    val ok: Boolean,
    val message: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val appliedRows: Int = 0,
    val deletedRows: Int = 0,
    val conflicts: Int = 0,
)

@Serializable
data class LibrarySummary(
    val books: List<BookSummary> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
)

@Serializable
data class BookSummary(
    val id: String,
    val title: String,
    val updatedAt: Long = 0L,
)

@Serializable
data class NoteSummary(
    val id: String,
    val title: String,
    val bodyPreview: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
data class NoteDetail(
    val id: String,
    val title: String,
    val body: String,
)

@Serializable
data class SceneSummary(
    val id: String,
    val bookId: String = "",
    val actTitle: String = "",
    val chapterTitle: String = "",
    val title: String,
    val summary: String = "",
    val wordCount: Int = 0,
    val status: String = "draft",
    val updatedAt: Long = 0L,
)

@Serializable
data class SceneDetail(
    val id: String,
    val title: String,
    val summary: String = "",
    val body: String,
    val wordCount: Int = 0,
    val status: String = "draft",
)

@Serializable
data class CodexEntrySummary(
    val id: String,
    val name: String,
    val category: String = "",
    val bodyPreview: String = "",
)

@Serializable
data class ThreadSummary(
    val id: String,
    val name: String,
    val updatedAt: Long = 0L,
)

@Serializable
data class ChatLine(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: Long = 0L,
)

@Serializable
data class RpChatSummary(
    val id: String,
    val title: String,
    val displayMode: String = "messenger",
    val updatedAt: Long = 0L,
)

@Serializable
data class MediaSummary(
    val id: String,
    val caption: String = "",
    val section: String = "",
    val relativePath: String = "",
)

@Serializable
data class ImportZipResult(
    val ok: Boolean,
    val message: String,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val sceneCount: Int = 0,
    val codexCount: Int = 0,
    val rpChatCount: Int = 0,
    val mediaCount: Int = 0,
)

@Serializable
data class WorkspaceSnapshot(
    val books: List<BookSummary> = emptyList(),
    val scenes: List<SceneSummary> = emptyList(),
    val codex: List<CodexEntrySummary> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    val threads: List<ThreadSummary> = emptyList(),
    val rpChats: List<RpChatSummary> = emptyList(),
    val media: List<MediaSummary> = emptyList(),
)
