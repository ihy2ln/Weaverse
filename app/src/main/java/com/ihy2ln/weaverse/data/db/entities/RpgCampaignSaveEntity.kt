package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rpg_campaign_saves")
data class RpgCampaignSaveEntity(
    @PrimaryKey val campaignId: String,
    val schemaVersion: Int = 1,
    val stateJson: String,
    val updatedAt: Long,
)
