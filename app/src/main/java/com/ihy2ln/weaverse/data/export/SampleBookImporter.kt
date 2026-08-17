package com.ihy2ln.weaverse.data.export

import android.content.Context
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleBookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val exportManager: ProjectExportManager,
) {
    suspend fun importBundledIsekaiGachaIfMissing(): ImportOutcome? {
        val already = db.bookDao().getAll().any { it.title.equals(BOOK_TITLE, ignoreCase = true) }
        if (already) return null
        val dest = File(context.cacheDir, ASSET_NAME)
        context.assets.open(ASSET_PATH).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return exportManager.importFromFile(dest)
    }

    companion object {
        const val BOOK_TITLE = "Isekai Gacha"
        const val ASSET_NAME = "isekai-gacha-full-word.zip"
        const val ASSET_PATH = "import/isekai-gacha-full-word.zip"
    }
}
