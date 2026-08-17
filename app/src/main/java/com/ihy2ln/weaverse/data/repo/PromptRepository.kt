package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionEntity
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionModelEntity
import com.ihy2ln.weaverse.data.db.entity.PresetEntity
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entity.PromptModelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the prompt library: folders, prompts, attached models, presets. */
@Singleton
class PromptRepository @Inject constructor(private val db: AppDatabase) {
    fun observeFolders(): Flow<List<PromptFolderEntity>> = db.promptFolderDao().observeAll()
    suspend fun upsertFolder(folder: PromptFolderEntity) = db.promptFolderDao().upsert(folder)
    suspend fun deleteFolder(folder: PromptFolderEntity) = db.promptFolderDao().delete(folder)

    fun observePrompt(id: String): Flow<PromptEntity?> = db.promptDao().observeById(id)
    suspend fun getPrompt(id: String): PromptEntity? = db.promptDao().getById(id)
    fun observePromptsInFolder(folderId: String): Flow<List<PromptEntity>> = db.promptDao().observeByFolder(folderId)
    suspend fun upsertPrompt(prompt: PromptEntity) = db.promptDao().upsert(prompt)
    suspend fun deletePrompt(prompt: PromptEntity) = db.promptDao().delete(prompt)

    fun observePromptModels(promptId: String): Flow<List<PromptModelEntity>> =
        db.promptModelDao().observeByPrompt(promptId)

    suspend fun upsertPromptModel(model: PromptModelEntity) = db.promptModelDao().upsert(model)
    suspend fun deletePromptModel(model: PromptModelEntity) = db.promptModelDao().delete(model)

    fun observeModelCollections(): Flow<List<ModelCollectionEntity>> = db.modelCollectionDao().observeAll()
    suspend fun upsertModelCollection(collection: ModelCollectionEntity) = db.modelCollectionDao().upsert(collection)
    suspend fun deleteModelCollection(collection: ModelCollectionEntity) = db.modelCollectionDao().delete(collection)

    fun observeModelCollectionModels(collectionId: String): Flow<List<ModelCollectionModelEntity>> =
        db.modelCollectionModelDao().observeByCollection(collectionId)

    suspend fun upsertModelCollectionModel(model: ModelCollectionModelEntity) =
        db.modelCollectionModelDao().upsert(model)

    suspend fun deleteModelCollectionModel(model: ModelCollectionModelEntity) =
        db.modelCollectionModelDao().delete(model)

    fun observePresets(): Flow<List<PresetEntity>> = db.presetDao().observeAll()
    fun observePresetsForPrompt(promptId: String): Flow<List<PresetEntity>> = db.presetDao().observeByPrompt(promptId)
    suspend fun upsertPreset(preset: PresetEntity) = db.presetDao().upsert(preset)
    suspend fun deletePreset(preset: PresetEntity) = db.presetDao().delete(preset)
}
