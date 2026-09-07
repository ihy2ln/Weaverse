package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity

@Dao
interface RpgCampaignSaveDao {
    @Query("SELECT * FROM rpg_campaign_saves WHERE campaignId = :campaignId LIMIT 1")
    suspend fun get(campaignId: String): RpgCampaignSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RpgCampaignSaveEntity)

    @Query("DELETE FROM rpg_campaign_saves WHERE campaignId = :campaignId")
    suspend fun delete(campaignId: String)
}
