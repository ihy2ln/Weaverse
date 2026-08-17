package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfileEntity)

    @Delete
    suspend fun delete(profile: ConnectionProfileEntity)

    @Query("SELECT * FROM connection_profiles ORDER BY sortOrder")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: String): ConnectionProfileEntity?
}
