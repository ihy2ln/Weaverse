package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entities.TextGameSaveEntity

@Dao
interface TextGameSaveDao {
    @Query("SELECT * FROM text_game_saves WHERE campaignId = :campaignId AND gameId = :gameId LIMIT 1")
    suspend fun get(campaignId: String, gameId: String): TextGameSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TextGameSaveEntity)

    @Query("DELETE FROM text_game_saves WHERE campaignId = :campaignId AND gameId = :gameId")
    suspend fun delete(campaignId: String, gameId: String)
}
