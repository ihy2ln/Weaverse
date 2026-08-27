package com.ihy2ln.weaverse.feature.library

import com.ihy2ln.weaverse.data.db.entities.RpChatEntity

/**
 * Home shelf modes that reuse Roleplay chats, each opening a different canvas:
 * RPG → DM 3×3, Chatting → messenger, Storyboard → manga 6×6.
 */
object HomeModeRouting {
    const val MESSENGER = "messenger"
    const val DUNGEON_MASTER = "dungeonMaster"
    const val STORYBOARD = "roleplay"

    fun displayModeForHome(modeId: String): String? = when (modeId) {
        "Chatting" -> MESSENGER
        "Storyboard" -> STORYBOARD
        "Roleplay" -> DUNGEON_MASTER
        else -> null
    }

    fun normalizeDisplayMode(mode: String): String = when (mode) {
        DUNGEON_MASTER, STORYBOARD, MESSENGER -> mode
        else -> MESSENGER
    }

    fun subtitleForHome(modeId: String): String = when (modeId) {
        "Chatting" -> "Messenger chat"
        "Storyboard" -> "Storyboard · manga"
        else -> "RPG campaign"
    }

    fun latestChatForHomeMode(modeId: String, chats: List<RpChatEntity>): RpChatEntity? {
        val preferred = displayModeForHome(modeId)
        return preferred?.let { mode ->
            chats.filter { normalizeDisplayMode(it.displayMode) == mode }
                .maxByOrNull { it.updatedAt }
        } ?: chats.maxByOrNull { it.updatedAt }
    }
}
