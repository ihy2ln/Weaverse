package com.ihy2ln.weaverse.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.sync.RoomSyncSql
import com.ihy2ln.weaverse.sync.SyncSchema
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeaverseDatabase =
        Room.databaseBuilder(context, WeaverseDatabase::class.java, "weaverse.db")
            .addMigrations(WeaverseDatabase.MIGRATION_5_6,
                WeaverseDatabase.MIGRATION_6_7,
                WeaverseDatabase.MIGRATION_7_8,
                WeaverseDatabase.MIGRATION_8_9,
                WeaverseDatabase.MIGRATION_9_10,
                WeaverseDatabase.MIGRATION_10_11,
                WeaverseDatabase.MIGRATION_11_12,
            )
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        runCatching { SyncSchema.ensure(RoomSyncSql(db)) }
                    }
                },
            )
            .fallbackToDestructiveMigration()
            .build()
}
