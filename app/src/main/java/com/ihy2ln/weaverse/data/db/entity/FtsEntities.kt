package com.ihy2ln.weaverse.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Standalone (not `contentEntity`-linked) FTS4 tables — Room's automatic
 * external-content FTS sync requires an INTEGER rowid-aliased primary key on
 * the source entity, which conflicts with this schema's String UUID keys
 * (spec §4). Instead each table below carries its own [entityId] pointing
 * back to the source row, and the owning repository keeps it in sync with a
 * delete-then-insert inside the same `@Transaction` as the content write —
 * see BUILD_NOTES.md "FTS sync" for why this replaces the spec's literal
 * "kept in sync with Room triggers." [rowid] is left for SQLite to
 * auto-assign (`autoGenerate = true`); nothing ever looks a row up by it —
 * every read/write goes through [entityId].
 */
@Fts4
@Entity(tableName = "scenes_fts")
data class SceneFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val entityId: String,
    val plainText: String,
)

@Fts4
@Entity(tableName = "codex_entries_fts")
data class CodexEntryFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val entityId: String,
    val plainText: String,
)

@Fts4
@Entity(tableName = "chat_messages_fts")
data class ChatMessageFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val entityId: String,
    val plainText: String,
)

@Fts4
@Entity(tableName = "rp_messages_fts")
data class RpMessageFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val entityId: String,
    val plainText: String,
)

@Fts4
@Entity(tableName = "snippets_fts")
data class SnippetFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val entityId: String,
    val body: String,
)
