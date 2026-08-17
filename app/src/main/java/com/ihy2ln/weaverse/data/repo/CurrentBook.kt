package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The book every Novel-mode screen (Plan/Write/Chat/Review/Codex) should operate on: whichever
 * book [AppSettingsRepository.currentBookId] points at, falling back to the first book in the
 * library if nothing is selected yet or the selected book was deleted. Shared by every
 * Novel-mode ViewModel so "switch books" (the Books rail tab) actually takes effect everywhere
 * at once, instead of each screen independently picking "whichever book comes first".
 */
fun observeCurrentBookId(libraryRepository: LibraryRepository, settingsRepository: AppSettingsRepository): Flow<String?> =
    combine(libraryRepository.observeBooks(), settingsRepository.currentBookId) { books, selectedId ->
        books.firstOrNull { it.id == selectedId }?.id ?: books.firstOrNull()?.id
    }
