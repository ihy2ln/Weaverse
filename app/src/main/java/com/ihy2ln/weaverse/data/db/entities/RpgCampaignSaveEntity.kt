package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Versioned RPG campaign bundle. Kept separate from `text_game_saves` so the
 * two modes never share reducer state or save schema.
 */
@Entity(
    tableName = "rpg_campaign_saves",
    primaryKeys = ["campaignId"],
    indices = [Index("campaignId")],
)
data class RpgCampaignSaveEntity(
    val campaignId: String,
    val schemaVersion: Int,
    val stateJson: String,
    val updatedAt: Long,
)
