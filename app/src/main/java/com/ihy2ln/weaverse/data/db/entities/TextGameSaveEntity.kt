package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.Index

/** A versioned Text Game save kept separate from roleplay prompts and author notes. */
@Entity(
    tableName = "text_game_saves",
    primaryKeys = ["campaignId", "gameId"],
    indices = [Index("campaignId"), Index("gameId")],
)
data class TextGameSaveEntity(
    val campaignId: String,
    val gameId: String,
    val schemaVersion: Int,
    val persistentStateJson: String,
    val runStateJson: String,
    val updatedAt: Long,
)
