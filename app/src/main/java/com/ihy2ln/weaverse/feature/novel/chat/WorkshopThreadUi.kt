package com.ihy2ln.weaverse.feature.novel.chat

import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity

data class WorkshopThreadUi(
    val id: String,
    val name: String,
    val pinned: Boolean,
    val updatedAt: Long,
    val messageCount: Int,
) {
    companion object {
        fun from(entity: ChatThreadEntity, messageCount: Int): WorkshopThreadUi =
            WorkshopThreadUi(
                id = entity.id,
                name = entity.name.ifBlank { "Unnamed thread" },
                pinned = entity.pinned,
                updatedAt = entity.updatedAt,
                messageCount = messageCount,
            )
    }
}

object WorkshopThreadList {
    fun filter(threads: List<WorkshopThreadUi>, query: String): List<WorkshopThreadUi> {
        val needle = query.trim()
        if (needle.isEmpty()) return threads
        return threads.filter { it.name.contains(needle, ignoreCase = true) }
    }

    fun pinned(threads: List<WorkshopThreadUi>): List<WorkshopThreadUi> =
        threads.filter { it.pinned }

    fun unpinned(threads: List<WorkshopThreadUi>): List<WorkshopThreadUi> =
        threads.filter { !it.pinned }
}
