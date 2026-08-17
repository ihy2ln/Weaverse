package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Provider connection metadata (spec §8.1: "Multiple named connection
 * profiles per provider"). The API key itself never lives here — it's kept
 * in `SecretsStore`'s `EncryptedSharedPreferences`, keyed by this row's
 * [id] (spec ground rules: "never plaintext, never committed").
 */
@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey val id: String = newId(),
    val providerType: AIProviderType,
    val label: String,
    val baseUrl: String,
    val sortOrder: Int = 0,
    val createdAt: Long = nowEpochMillis(),
)
