package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.LabelEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: SnippetEntity)

    @Delete
    suspend fun delete(snippet: SnippetEntity)

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getById(id: String): SnippetEntity?

    @Query(
        "SELECT * FROM snippets WHERE scopeType = :scopeType AND scopeId = :scopeId " +
            "ORDER BY pinned DESC, createdAt DESC",
    )
    fun observeByScope(scopeType: ScopeType, scopeId: String): Flow<List<SnippetEntity>>
}

@Dao
interface LabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: LabelEntity)

    @Delete
    suspend fun delete(label: LabelEntity)

    @Query("SELECT * FROM labels WHERE scopeId = :scopeId ORDER BY name")
    fun observeByScope(scopeId: String): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LabelEntity>
}
