package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaOwnerType
import com.ihy2ln.weaverse.data.db.entity.MediaUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE checksum = :checksum LIMIT 1")
    suspend fun getByChecksum(checksum: String): MediaEntity?

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM media")
    fun observeTotalBytes(): Flow<Long>

    /** Media rows with zero rows in `media_usages` — candidates for the orphan-cleanup sweep (spec §7). */
    @Query("SELECT * FROM media WHERE id NOT IN (SELECT DISTINCT mediaId FROM media_usages)")
    suspend fun getOrphaned(): List<MediaEntity>
}

@Dao
interface MediaUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: MediaUsageEntity)

    @Query("DELETE FROM media_usages WHERE mediaId = :mediaId AND ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun remove(mediaId: String, ownerType: MediaOwnerType, ownerId: String)

    @Query("DELETE FROM media_usages WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun removeAllForOwner(ownerType: MediaOwnerType, ownerId: String)

    @Query("SELECT * FROM media_usages WHERE mediaId = :mediaId")
    fun observeByMedia(mediaId: String): Flow<List<MediaUsageEntity>>
}
