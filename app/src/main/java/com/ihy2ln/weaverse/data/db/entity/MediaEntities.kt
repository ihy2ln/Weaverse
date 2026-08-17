package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file under `filesDir/media/<id>.<ext>` (never a bitmap in the DB — spec
 * §7). [relativePath] is relative to that media root, e.g. `"<id>.jpg"`;
 * [thumbnailPath] is relative to `filesDir/media/thumbs/`.
 */
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String = newId(),
    val type: MediaType,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val thumbnailPath: String? = null,
    val checksum: String,
    val createdAt: Long = nowEpochMillis(),
)

/**
 * Tracks which owner(s) reference a piece of media, for orphan cleanup (spec
 * §7 "Maintenance action removes orphaned files"). No FK/cascade to
 * [MediaEntity] on purpose — a maintenance sweep needs to see usages whose
 * media row may already be gone, and owners are polymorphic ([MediaOwnerType]
 * + an owner id) rather than one real foreign-keyed column.
 */
@Entity(
    tableName = "media_usages",
    primaryKeys = ["mediaId", "ownerType", "ownerId"],
    indices = [Index("ownerType", "ownerId")],
)
data class MediaUsageEntity(
    val mediaId: String,
    val ownerType: MediaOwnerType,
    val ownerId: String,
)
