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
                WeaverseDatabase.MIGRATION_12_13,
                WeaverseDatabase.MIGRATION_13_14,
                WeaverseDatabase.MIGRATION_14_15,
                WeaverseDatabase.MIGRATION_15_16,
                WeaverseDatabase.MIGRATION_16_17,
                WeaverseDatabase.MIGRATION_17_18,
                WeaverseDatabase.MIGRATION_18_19,
                WeaverseDatabase.MIGRATION_19_20,
                WeaverseDatabase.MIGRATION_20_21,
                WeaverseDatabase.MIGRATION_21_22,
                WeaverseDatabase.MIGRATION_22_23,
                WeaverseDatabase.MIGRATION_23_24, WeaverseDatabase.MIGRATION_24_25, WeaverseDatabase.MIGRATION_25_26,
                WeaverseDatabase.MIGRATION_26_27,
                WeaverseDatabase.MIGRATION_27_28,
                WeaverseDatabase.MIGRATION_28_29,
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
