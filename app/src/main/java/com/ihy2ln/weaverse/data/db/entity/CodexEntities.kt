package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "codex_categories", indices = [Index("scopeType", "scopeId")])
data class CodexCategoryEntity(
    @PrimaryKey val id: String = newId(),
    val scopeType: ScopeType,
    val scopeId: String,
    val name: String,
    val colorHex: String,
    val icon: String = "",
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "codex_entries",
    foreignKeys = [
        ForeignKey(
            entity = CodexCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("scopeType", "scopeId")],
)
data class CodexEntryEntity(
    @PrimaryKey val id: String = newId(),
    val categoryId: String,
    val scopeType: ScopeType,
    val scopeId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    /** Serialized [com.ihy2ln.weaverse.core.text.Document] JSON — real block-editor type lands in Phase 5. */
    val docJson: String = "",
    val plainText: String = "",
    val colorHex: String? = null,
    val alwaysInclude: Boolean = false,
    val disabled: Boolean = false,
    val imageMediaId: String? = null,
    /**
     * Backs the "AI" badge in the Codex rail row (spec §9) — not in the
     * spec's own §4 field listing for `codex_entries`, added here since the
     * UI section names the badge but the data section has nothing to back
     * it with. Set by the future "Extract" action (workshop chat -> codex
     * entry, spec §10) once that exists; defaults false for hand-authored
     * entries.
     */
    val isAiGenerated: Boolean = false,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
)

/** 1:1 extension of [CodexEntryEntity] holding the SillyTavern World Info fields (spec §4). */
@Entity(
    tableName = "codex_entries_lore",
    foreignKeys = [
        ForeignKey(
            entity = CodexEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CodexEntryLoreEntity(
    @PrimaryKey val entryId: String,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val selectiveLogic: SelectiveLogic = SelectiveLogic.AndAny,
    val insertionOrder: Int = 100,
    val position: LorePosition = LorePosition.AfterChar,
    val depth: Int = 4,
    val probability: Int = 100,
    val isConstant: Boolean = false,
    /** "Track this entry by name/alias" — when false, occurrences of `name`/`aliases` in prose
     * are ignored by both AI context-detection ([com.ihy2ln.weaverse.ai.context.ContextMatching])
     * and the inline clickable-mention scan ([com.ihy2ln.weaverse.core.text.MentionScanner]); a
     * per-entry escape hatch for a name/alias that's also a common word/phrase you don't want
     * lighting up everywhere. `keys`/`secondaryKeys` matching is unaffected. */
    val trackByNameAlias: Boolean = true,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = true,
    val scanDepth: Int = 2,
    val tokenBudgetWeight: Float = 1f,
    val recursionAllowed: Boolean = true,
    val groupName: String? = null,
)

@Entity(
    tableName = "codex_relations",
    foreignKeys = [
        ForeignKey(
            entity = CodexEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CodexEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["toEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromEntryId"), Index("toEntryId")],
)
data class CodexRelationEntity(
    @PrimaryKey val id: String = newId(),
    val fromEntryId: String,
    val toEntryId: String,
    val label: String = "",
)
