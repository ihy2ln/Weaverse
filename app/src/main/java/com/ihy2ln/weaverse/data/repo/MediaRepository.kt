package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaOwnerType
import com.ihy2ln.weaverse.data.db.entity.MediaUsageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the on-device media library shared by both modes (spec §7). */
@Singleton
class MediaRepository @Inject constructor(private val db: AppDatabase) {
    fun observeAll(): Flow<List<MediaEntity>> = db.mediaDao().observeAll()
    suspend fun getById(id: String): MediaEntity? = db.mediaDao().getById(id)
    suspend fun getByChecksum(checksum: String): MediaEntity? = db.mediaDao().getByChecksum(checksum)
    fun observeTotalBytes(): Flow<Long> = db.mediaDao().observeTotalBytes()
    suspend fun upsert(media: MediaEntity) = db.mediaDao().upsert(media)
    suspend fun delete(media: MediaEntity) = db.mediaDao().delete(media)

    fun observeUsages(mediaId: String): Flow<List<MediaUsageEntity>> = db.mediaUsageDao().observeByMedia(mediaId)

    suspend fun addUsage(mediaId: String, ownerType: MediaOwnerType, ownerId: String) =
        db.mediaUsageDao().upsert(MediaUsageEntity(mediaId, ownerType, ownerId))

    suspend fun removeUsage(mediaId: String, ownerType: MediaOwnerType, ownerId: String) =
        db.mediaUsageDao().remove(mediaId, ownerType, ownerId)

    suspend fun removeAllUsagesForOwner(ownerType: MediaOwnerType, ownerId: String) =
        db.mediaUsageDao().removeAllForOwner(ownerType, ownerId)

    /** Media rows nothing references anymore — the "remove orphaned files" maintenance action (spec §7). */
    suspend fun getOrphaned(): List<MediaEntity> = db.mediaDao().getOrphaned()
}
