package com.ihy2ln.weaverse.feature.novel.manuscript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManuscriptRailViewModel @Inject constructor(
    settings: SettingsRepository,
    bookRepository: BookRepository,
    manuscriptRepository: ManuscriptRepository,
) : ViewModel() {
    val scenes: StateFlow<List<SceneEntity>> = settings.preferences
        .flatMapLatest { prefs ->
            flow {
                val chapterId = bookRepository.primaryChapterId(prefs.selectedBookId)
                emit(chapterId)
            }.flatMapLatest { chapterId ->
                if (chapterId != null) {
                    manuscriptRepository.observeScenes(chapterId)
                } else {
                    flowOf(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
