package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A chosen Brainstorm idea. Unsaved AI alternatives stay in their source chat. */
@Entity(
    tableName = "brainstorm_ideas",
    indices = [Index("status"), Index("bookId"), Index("sourceThreadId")],
)
data class BrainstormIdeaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val premise: String,
    val strengths: String = "",
    val risks: String = "",
    val nextStep: String = "",
    val status: String = "Inbox",
    val pinned: Boolean = false,
    val bookId: String? = null,
    val sourceThreadId: String? = null,
    val sourceMessageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
