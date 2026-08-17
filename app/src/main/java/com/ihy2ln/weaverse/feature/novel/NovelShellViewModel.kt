package com.ihy2ln.weaverse.feature.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Feeds the top bar's title/subtitle — the one piece of shell chrome shared by all four
 * Novel destinations, so it lives at the shell level rather than in any one screen's ViewModel.
 * Also owns the rail's persisted width/collapsed state (spec §1.2's drag-resize + collapse
 * toggle) since that, too, is shell-level, not per-destination. */
@HiltViewModel
class NovelShellViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    val currentBookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val currentBook = currentBookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeBook(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentBookTitle: StateFlow<String> = currentBook
        .map { it?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** The header's series line (Revision 02 §1.2/§3) — null when the current book isn't in a
     * series, in which case `AppHeaderBar` hides the line entirely rather than showing it blank. */
    val currentSeriesName: StateFlow<String?> = currentBook
        .flatMapLatest { book ->
            val seriesId = book?.seriesId
            if (seriesId == null) flowOf(null) else libraryRepository.observeSeries(seriesId).map { it?.title }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val railWidthDp: StateFlow<Int> = settingsRepository.railWidthDp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsRepository.RailWidthDefault)

    val railCollapsed: StateFlow<Boolean> = settingsRepository.railCollapsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setRailWidthDp(widthDp: Int) {
        viewModelScope.launch { settingsRepository.setRailWidthDp(widthDp) }
    }

    fun setRailCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settingsRepository.setRailCollapsed(collapsed) }
    }
}
