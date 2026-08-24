package com.ihy2ln.weaverse.di

import android.content.Context
import androidx.room.Room
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
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
            .addMigrations(
                WeaverseDatabase.MIGRATION_5_6,
                WeaverseDatabase.MIGRATION_6_7,
                WeaverseDatabase.MIGRATION_7_8,
            )
            .fallbackToDestructiveMigration()
            .build()
}
