package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Reference-only attachment. Inline placements remain in the manuscript document. */
@Entity(tableName = "novel_media_links")
data class NovelMediaLink(
    @PrimaryKey val id: String,
    val bookId: String,
    val sceneId: String,
    val entryId: String = "",
    val mediaId: String,
    val caption: String = "",
    val altText: String = "",
    val provenance: String = "Imported by user",
    val createdAt: Long,
)
