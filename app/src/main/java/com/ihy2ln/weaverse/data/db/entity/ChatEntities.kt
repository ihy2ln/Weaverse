package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_threads",
    foreignKeys = [
        ForeignKey(
            entity = PromptEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("scopeId"), Index("promptId"), Index("sceneId")],
)
data class ChatThreadEntity(
    @PrimaryKey val id: String = newId(),
    val scopeId: String,
    val name: String,
    val pinned: Boolean = false,
    val promptId: String? = null,
    val modelRef: String? = null,
    val sceneId: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String = newId(),
    val threadId: String,
    val role: ChatRole,
    /** Serialized [com.ihy2ln.weaverse.core.text.Document] JSON (blocks, so images work) — Phase 5. */
    val contentJson: String = "",
    /**
     * Plain-text extraction of [contentJson], not in the original spec table
     * listing but needed so `chat_messages` can actually be FTS-indexed per
     * spec §4's Search section ("FTS4 tables mirroring … chat_messages …").
     */
    val plainText: String = "",
    /** Entry IDs + per-section token cost used to build this message's prompt — real schema in Phase 9. */
    val contextUsedJson: String = "[]",
    val tokenCount: Int = 0,
    val wordCount: Int = 0,
    val createdAt: Long = nowEpochMillis(),
)
