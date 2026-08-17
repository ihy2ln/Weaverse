package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "snippets", indices = [Index("scopeType", "scopeId")])
data class SnippetEntity(
    @PrimaryKey val id: String = newId(),
    val scopeType: ScopeType,
    val scopeId: String,
    val title: String,
    val body: String,
    val category: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = nowEpochMillis(),
)

@Entity(tableName = "labels", indices = [Index("scopeId")])
data class LabelEntity(
    @PrimaryKey val id: String = newId(),
    val scopeId: String,
    val name: String,
    val colorHex: String,
)

@Entity(tableName = "prompt_folders")
data class PromptFolderEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: PromptType,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "prompts",
    foreignKeys = [
        ForeignKey(
            entity = PromptFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class PromptEntity(
    @PrimaryKey val id: String = newId(),
    val folderId: String,
    val name: String,
    val type: PromptType,
    val description: String = "",
    /** Ordered message-block JSON with variable placeholders — real schema in Phase 8 (AI layer). */
    val instructionsJson: String = "[]",
    /** Sampler overrides / stop sequences / streaming / max tokens JSON — real schema in Phase 8. */
    val advancedJson: String = "{}",
    val isSystem: Boolean = false,
    val createdAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "prompt_models",
    foreignKeys = [
        ForeignKey(
            entity = PromptEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("promptId")],
)
data class PromptModelEntity(
    @PrimaryKey val id: String = newId(),
    val promptId: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val sortOrder: Int = 0,
    /** Per-model sampler param overrides JSON — real schema in Phase 8. */
    val paramsJson: String = "{}",
)

@Entity(tableName = "model_collections")
data class ModelCollectionEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
)

@Entity(
    tableName = "model_collection_models",
    foreignKeys = [
        ForeignKey(
            entity = ModelCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId")],
)
data class ModelCollectionModelEntity(
    @PrimaryKey val id: String = newId(),
    val collectionId: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "presets",
    foreignKeys = [
        ForeignKey(
            entity = PromptEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("promptId")],
)
data class PresetEntity(
    @PrimaryKey val id: String = newId(),
    val promptId: String? = null,
    val name: String,
    /** Sampler settings JSON — real schema in Phase 8/11 (roleplay presets). */
    val paramsJson: String = "{}",
)
