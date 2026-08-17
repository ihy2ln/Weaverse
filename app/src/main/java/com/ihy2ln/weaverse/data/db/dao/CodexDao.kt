package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.CodexRelationEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import kotlinx.coroutines.flow.Flow

@Dao
interface CodexCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CodexCategoryEntity)

    @Delete
    suspend fun delete(category: CodexCategoryEntity)

    @Query("SELECT * FROM codex_categories WHERE scopeType = :scopeType AND scopeId = :scopeId ORDER BY sortOrder")
    fun observeByScope(scopeType: ScopeType, scopeId: String): Flow<List<CodexCategoryEntity>>

    @Query("SELECT * FROM codex_categories WHERE id = :id")
    suspend fun getById(id: String): CodexCategoryEntity?
}

@Dao
interface CodexEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CodexEntryEntity)

    @Delete
    suspend fun delete(entry: CodexEntryEntity)

    @Query("SELECT * FROM codex_entries WHERE id = :id")
    suspend fun getById(id: String): CodexEntryEntity?

    @Query("SELECT * FROM codex_entries WHERE id = :id")
    fun observeById(id: String): Flow<CodexEntryEntity?>

    @Query("SELECT * FROM codex_entries WHERE categoryId = :categoryId ORDER BY name")
    fun observeByCategory(categoryId: String): Flow<List<CodexEntryEntity>>

    @Query("SELECT * FROM codex_entries WHERE scopeType = :scopeType AND scopeId = :scopeId ORDER BY name")
    fun observeByScope(scopeType: ScopeType, scopeId: String): Flow<List<CodexEntryEntity>>

    /** Every non-disabled entry in scope, for [com.ihy2ln.weaverse.ai.context.ContextBuilder] keyword scanning (Phase 9). */
    @Query("SELECT * FROM codex_entries WHERE scopeType = :scopeType AND scopeId = :scopeId AND disabled = 0")
    suspend fun getActiveByScope(scopeType: ScopeType, scopeId: String): List<CodexEntryEntity>

    @Query("SELECT * FROM codex_entries WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CodexEntryEntity>
}

@Dao
interface CodexEntryLoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lore: CodexEntryLoreEntity)

    @Delete
    suspend fun delete(lore: CodexEntryLoreEntity)

    @Query("SELECT * FROM codex_entries_lore WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): CodexEntryLoreEntity?

    @Query("SELECT * FROM codex_entries_lore WHERE entryId IN (:entryIds)")
    suspend fun getByEntryIds(entryIds: List<String>): List<CodexEntryLoreEntity>
}

@Dao
interface CodexRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relation: CodexRelationEntity)

    @Delete
    suspend fun delete(relation: CodexRelationEntity)

    @Query("SELECT * FROM codex_relations WHERE fromEntryId = :entryId OR toEntryId = :entryId")
    fun observeForEntry(entryId: String): Flow<List<CodexRelationEntity>>
}
