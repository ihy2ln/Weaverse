package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionEntity
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionModelEntity
import com.ihy2ln.weaverse.data.db.entity.PresetEntity
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entity.PromptModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptFolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: PromptFolderEntity)

    @Delete
    suspend fun delete(folder: PromptFolderEntity)

    @Query("SELECT * FROM prompt_folders ORDER BY name")
    fun observeAll(): Flow<List<PromptFolderEntity>>

    @Query("SELECT COUNT(*) FROM prompt_folders")
    suspend fun count(): Int
}

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prompt: PromptEntity)

    @Delete
    suspend fun delete(prompt: PromptEntity)

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getById(id: String): PromptEntity?

    @Query("SELECT * FROM prompts WHERE id = :id")
    fun observeById(id: String): Flow<PromptEntity?>

    @Query("SELECT * FROM prompts WHERE folderId = :folderId ORDER BY name")
    fun observeByFolder(folderId: String): Flow<List<PromptEntity>>
}

@Dao
interface PromptModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: PromptModelEntity)

    @Delete
    suspend fun delete(model: PromptModelEntity)

    @Query("SELECT * FROM prompt_models WHERE promptId = :promptId ORDER BY sortOrder")
    fun observeByPrompt(promptId: String): Flow<List<PromptModelEntity>>
}

@Dao
interface ModelCollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(collection: ModelCollectionEntity)

    @Delete
    suspend fun delete(collection: ModelCollectionEntity)

    @Query("SELECT * FROM model_collections ORDER BY name")
    fun observeAll(): Flow<List<ModelCollectionEntity>>
}

@Dao
interface ModelCollectionModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelCollectionModelEntity)

    @Delete
    suspend fun delete(model: ModelCollectionModelEntity)

    @Query("SELECT * FROM model_collection_models WHERE collectionId = :collectionId ORDER BY sortOrder")
    fun observeByCollection(collectionId: String): Flow<List<ModelCollectionModelEntity>>
}

@Dao
interface PresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity)

    @Delete
    suspend fun delete(preset: PresetEntity)

    @Query("SELECT * FROM presets ORDER BY name")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE promptId = :promptId ORDER BY name")
    fun observeByPrompt(promptId: String): Flow<List<PresetEntity>>
}
