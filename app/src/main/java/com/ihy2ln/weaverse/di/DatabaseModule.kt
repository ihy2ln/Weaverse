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
            .addMigrations(*WeaverseDatabase.ALL_MIGRATIONS)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        runCatching { SyncSchema.ensure(RoomSyncSql(db)) }
                    }
                },
            )
            // Only schemas older than the migration chain may be rebuilt. A missing step for
            // any newer version must crash in testing, never silently delete user data.
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .build()
}
