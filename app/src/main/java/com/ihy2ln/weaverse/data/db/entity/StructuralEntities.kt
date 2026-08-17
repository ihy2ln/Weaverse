package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val description: String = "",
    /** The series-wide premise injected into every member's assembled prompt ahead of book-level
     * content (Revision 02 §3/§10). */
    val premise: String = "",
    /** Auto-maintained rolling synopsis across all members; regenerated on demand via a seeded
     * "Series Summarization" prompt, editable by hand (Revision 02 §3) — generation itself is a
     * documented follow-up (rev02-04b), this column exists so hand-editing already works. */
    val rollingSummary: String = "",
    val summaryUpdatedAt: Long? = null,
    val createdAt: Long = nowEpochMillis(),
)

/** A book or roleplay session that belongs to a [SeriesEntity] (Revision 02 §3/§10). Books also
 * carry a direct `seriesId` FK (existing since Phase 3) for the simple "which series is this
 * book in" query; this table is the ordered, per-member view `SeriesScreen` and `ContextBuilder`
 * need (drag-reorder position, and a per-member rolling summary distinct from the series-wide
 * one). Roleplay-session membership ([SeriesMemberType.RpSession]) has no creation flow wired up
 * yet — rev02-04b, see BUILD_NOTES. */
@Entity(
    tableName = "series_members",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("seriesId")],
)
data class SeriesMemberEntity(
    @PrimaryKey val id: String = newId(),
    val seriesId: String,
    val memberType: SeriesMemberType,
    val memberId: String,
    val sortOrder: Int = 0,
    val summary: String = "",
)

enum class SeriesMemberType { Book, RpSession }

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("seriesId")],
)
data class BookEntity(
    @PrimaryKey val id: String = newId(),
    val seriesId: String? = null,
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
    val targetWordCount: Int = 0,
    val coverMediaId: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "acts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ActEntity(
    @PrimaryKey val id: String = newId(),
    val bookId: String,
    val title: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = ActEntity::class,
            parentColumns = ["id"],
            childColumns = ["actId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("actId")],
)
data class ChapterEntity(
    @PrimaryKey val id: String = newId(),
    val actId: String,
    val title: String,
    val sortOrder: Int = 0,
    val summary: String = "",
)

@Entity(
    tableName = "scenes",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class SceneEntity(
    @PrimaryKey val id: String = newId(),
    val chapterId: String,
    val title: String,
    val sortOrder: Int = 0,
    /** Serialized [com.ihy2ln.weaverse.core.text.Document] JSON — real block-editor type lands in Phase 5. */
    val docJson: String = "",
    val plainText: String = "",
    val summary: String = "",
    /** JSON list of scene-beat block state — real shape lands alongside the Write screen in Phase 10. */
    val beatsJson: String = "[]",
    val wordCount: Int = 0,
    val status: SceneStatus = SceneStatus.Draft,
    val pov: String = "",
    val povCharacterId: String? = null,
    val inWorldDate: String? = null,
    val labelIds: List<String> = emptyList(),
    val colorHex: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "scene_codex_links",
    primaryKeys = ["sceneId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CodexEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class SceneCodexLinkEntity(
    val sceneId: String,
    val entryId: String,
    val source: CodexLinkSource = CodexLinkSource.Auto,
)
