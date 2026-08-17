package com.ihy2ln.weaverse.data.repo

import androidx.room.withTransaction
import com.ihy2ln.weaverse.core.ui.CodexCategoryKind
import com.ihy2ln.weaverse.core.ui.toHex
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.CodexRelationEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for codex categories/entries/lore/relations — also the roleplay Codex tab. */
@Singleton
class CodexRepository @Inject constructor(private val db: AppDatabase) {
    fun observeCategories(scopeType: ScopeType, scopeId: String): Flow<List<CodexCategoryEntity>> =
        db.codexCategoryDao().observeByScope(scopeType, scopeId)

    suspend fun getCategory(id: String): CodexCategoryEntity? = db.codexCategoryDao().getById(id)
    suspend fun upsertCategory(category: CodexCategoryEntity) = db.codexCategoryDao().upsert(category)
    suspend fun deleteCategory(category: CodexCategoryEntity) = db.codexCategoryDao().delete(category)

    /**
     * Seeds the ten built-in categories (Revision 02 §2: "Built-in categories seeded on every
     * new book/series") into [scopeId], skipping any name that already exists in that scope —
     * safe to call on a book that already has some categories (e.g. [DemoDataSeeder]'s
     * hand-picked four), and safe to call twice. Built-ins are still fully renameable/deletable
     * afterward; [CodexCategoryEntity.isSystem] is just a marker, not a lock.
     */
    suspend fun seedBuiltInCategories(scopeType: ScopeType, scopeId: String) {
        val existingNames = observeCategories(scopeType, scopeId).first().map { it.name }.toSet()
        CodexCategoryKind.entries.forEachIndexed { index, kind ->
            if (kind.label !in existingNames) {
                upsertCategory(
                    CodexCategoryEntity(
                        scopeType = scopeType,
                        scopeId = scopeId,
                        name = kind.label,
                        colorHex = kind.defaultColor.toHex(),
                        icon = kind.icon,
                        sortOrder = index,
                        isSystem = true,
                    ),
                )
            }
        }
    }

    fun observeEntries(categoryId: String): Flow<List<CodexEntryEntity>> =
        db.codexEntryDao().observeByCategory(categoryId)

    fun observeEntriesForScope(scopeType: ScopeType, scopeId: String): Flow<List<CodexEntryEntity>> =
        db.codexEntryDao().observeByScope(scopeType, scopeId)

    fun observeEntry(id: String): Flow<CodexEntryEntity?> = db.codexEntryDao().observeById(id)
    suspend fun getEntry(id: String): CodexEntryEntity? = db.codexEntryDao().getById(id)
    suspend fun getEntries(ids: List<String>): List<CodexEntryEntity> = db.codexEntryDao().getByIds(ids)

    /** Active (non-disabled) entries in scope, for [com.ihy2ln.weaverse.ai.context.ContextBuilder] (Phase 9). */
    suspend fun getActiveEntries(scopeType: ScopeType, scopeId: String): List<CodexEntryEntity> =
        db.codexEntryDao().getActiveByScope(scopeType, scopeId)

    suspend fun upsertEntry(entry: CodexEntryEntity) = db.withTransaction {
        db.codexEntryDao().upsert(entry)
        db.codexEntryFtsDao().reindex(entry.id, entry.plainText)
    }

    suspend fun deleteEntry(entry: CodexEntryEntity) = db.withTransaction {
        db.codexEntryDao().delete(entry)
        db.codexEntryFtsDao().deleteByEntityId(entry.id)
    }

    /** Copy action (codex entry admin menu): a full clone — including lore — under a new id, so
     * editing the copy never touches the original. */
    suspend fun duplicateEntry(entry: CodexEntryEntity): CodexEntryEntity = db.withTransaction {
        val copy = entry.copy(id = newId(), name = "${entry.name} (copy)")
        db.codexEntryDao().upsert(copy)
        db.codexEntryFtsDao().reindex(copy.id, copy.plainText)
        db.codexEntryLoreDao().getByEntryId(entry.id)?.let { lore ->
            db.codexEntryLoreDao().upsert(lore.copy(entryId = copy.id))
        }
        copy
    }

    suspend fun getLore(entryId: String): CodexEntryLoreEntity? = db.codexEntryLoreDao().getByEntryId(entryId)
    suspend fun getLoreForEntries(entryIds: List<String>): List<CodexEntryLoreEntity> =
        db.codexEntryLoreDao().getByEntryIds(entryIds)

    suspend fun upsertLore(lore: CodexEntryLoreEntity) = db.codexEntryLoreDao().upsert(lore)

    fun observeRelations(entryId: String): Flow<List<CodexRelationEntity>> =
        db.codexRelationDao().observeForEntry(entryId)

    suspend fun upsertRelation(relation: CodexRelationEntity) = db.codexRelationDao().upsert(relation)
    suspend fun deleteRelation(relation: CodexRelationEntity) = db.codexRelationDao().delete(relation)
}
