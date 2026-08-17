package com.ihy2ln.weaverse.di

import android.content.Context
import androidx.room.Room
import com.ihy2ln.weaverse.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Only the database itself is a Hilt binding; repositories take [AppDatabase]
 * directly and reach for the specific DAO(s) they need — one `@Provides`
 * function per DAO (~25 of them) would be pure boilerplate for no benefit
 * since nothing outside a repository ever injects a DAO on its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Found while renaming a persisted column for Revision 02 §2: AppDatabase has
            // stayed at `version = 1` across every entity change since Phase 3 (SnippetEntity's
            // category/pinned, CodexEntryEntity's colorHex/imageMediaId, this rename, etc.),
            // with no Migration objects and no fallback configured — Room throws
            // IllegalStateException on the very first schema-hash mismatch, crashing the app on
            // open for anyone upgrading over an existing install. No cloud sync exists (offline-
            // first per the original spec), and export/import (Book/Codex/Chat/Snippets, all 4
            // formats) already gives a real way to back up before updating, so destructive
            // fallback — reset to empty rather than crash-loop — is the safer default while this
            // schema is still moving as fast as it is. Real Migration objects (and a real version
            // bump per schema change) should replace this before the schema is treated as stable.
            .fallbackToDestructiveMigration()
            .build()
}
