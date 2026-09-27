package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entities.BrainstormIdeaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainstormIdeaDao {
    @Query("SELECT * FROM brainstorm_ideas ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BrainstormIdeaEntity>>

    @Query("SELECT * FROM brainstorm_ideas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BrainstormIdeaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(idea: BrainstormIdeaEntity)

    @Query("DELETE FROM brainstorm_ideas WHERE id = :id")
    suspend fun deleteById(id: String)
}
