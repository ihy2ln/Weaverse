package com.ihy2ln.weaverse.feature.settings

import androidx.lifecycle.ViewModel
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.feature.settings.backup.BookBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Backs Settings' Data section (export/import) — thin wrapper over [BookBackupService];
 * the Uri/file-picking side lives in the screen, same split as Phase 11's card codec. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: AppSettingsRepository,
    private val bookBackupService: BookBackupService,
) : ViewModel() {
    suspend fun exportCurrentBook(format: ExportFormat): ByteArray? {
        val bookId = observeCurrentBookId(libraryRepository, settingsRepository).first() ?: return null
        return bookBackupService.export(bookId, format)
    }

    suspend fun importBook(bytes: ByteArray, format: ExportFormat): String = bookBackupService.import(bytes, format).title
}
