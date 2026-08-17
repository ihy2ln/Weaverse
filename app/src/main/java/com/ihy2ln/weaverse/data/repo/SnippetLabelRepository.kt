package com.ihy2ln.weaverse.data.repo

import androidx.room.withTransaction
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.LabelEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetLabelRepository @Inject constructor(private val db: AppDatabase) {
    fun observeSnippets(scopeType: ScopeType, scopeId: String): Flow<List<SnippetEntity>> =
        db.snippetDao().observeByScope(scopeType, scopeId)

    suspend fun getSnippet(id: String): SnippetEntity? = db.snippetDao().getById(id)

    suspend fun upsertSnippet(snippet: SnippetEntity) = db.withTransaction {
        db.snippetDao().upsert(snippet)
        db.snippetFtsDao().reindex(snippet.id, snippet.body)
    }

    suspend fun deleteSnippet(snippet: SnippetEntity) = db.withTransaction {
        db.snippetDao().delete(snippet)
        db.snippetFtsDao().deleteByEntityId(snippet.id)
    }

    fun observeLabels(scopeId: String): Flow<List<LabelEntity>> = db.labelDao().observeByScope(scopeId)
    suspend fun getLabels(ids: List<String>): List<LabelEntity> = db.labelDao().getByIds(ids)
    suspend fun upsertLabel(label: LabelEntity) = db.labelDao().upsert(label)
    suspend fun deleteLabel(label: LabelEntity) = db.labelDao().delete(label)
}
