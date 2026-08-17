package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionProfileRepository @Inject constructor(private val db: AppDatabase) {
    fun observeAll(): Flow<List<ConnectionProfileEntity>> = db.connectionProfileDao().observeAll()
    suspend fun getById(id: String): ConnectionProfileEntity? = db.connectionProfileDao().getById(id)
    suspend fun upsert(profile: ConnectionProfileEntity) = db.connectionProfileDao().upsert(profile)
    suspend fun delete(profile: ConnectionProfileEntity) = db.connectionProfileDao().delete(profile)
}
