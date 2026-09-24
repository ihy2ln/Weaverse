package com.ihy2ln.weaverse.di

import android.content.Context
import androidx.room.Room
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.data.db.DatabaseMigrations
import com.ihy2ln.weaverse.data.db.PreMigrationBackup
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "weaverse.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeaverseDatabase {
        PreMigrationBackup.backupIfUpgrading(
            dbFile = context.getDatabasePath(DB_NAME),
            backupDir = File(context.filesDir, "backups"),
            targetVersion = WeaverseDatabase.VERSION,
        )?.let { WeaverseAiLog.i("database backed up before upgrade: ${it.name}") }

        return Room.databaseBuilder(context, WeaverseDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            // Only the pre-history versions (no recorded schema) may still be rebuilt, and the
            // backup above has already copied them aside. Any newer version must migrate.
            .fallbackToDestructiveMigrationFrom(*DatabaseMigrations.LEGACY_VERSIONS)
            // A downgrade (older APK over newer data, or a sync from a newer peer) is rebuilt
            // rather than crashing on open; the pre-upgrade backup is not taken for this case.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
