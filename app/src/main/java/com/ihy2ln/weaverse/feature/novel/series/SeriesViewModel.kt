package com.ihy2ln.weaverse.feature.novel.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberType
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A [SeriesMemberEntity] alongside the book title it points at — `SeriesSheet` shouldn't have
 * to cross-reference [SeriesMemberEntity.memberId] against the book list itself. */
data class SeriesMemberRow(val member: SeriesMemberEntity, val bookTitle: String)

/**
 * Backs the series management sheet, opened from [com.ihy2ln.weaverse.feature.shell.AppHeaderBar]'s
 * series line (Revision 02 §1.2/§3). Scoped to whichever book is currently open — the spec's
 * "changeable later from the header's series line" is exactly what [joinExistingSeries]/
 * [leaveSeries]/[createSeriesAndJoin] do for that book.
 *
 * Roleplay-session series membership isn't wired here — [SeriesMemberType.RpSession] exists in
 * the data model but has no creation flow yet (rev02-04b, see BUILD_NOTES).
 */
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val currentBookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSeries: StateFlow<List<SeriesEntity>> = libraryRepository.observeSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentBook: StateFlow<BookEntity?> = currentBookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeBook(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSeries: StateFlow<SeriesEntity?> = currentBook
        .flatMapLatest { book ->
            val seriesId = book?.seriesId
            if (seriesId == null) flowOf(null) else libraryRepository.observeSeries(seriesId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<SeriesMemberRow>> = currentSeries
        .flatMapLatest { series ->
            val seriesId = series?.id
            if (seriesId == null) {
                flowOf(emptyList())
            } else {
                combine(libraryRepository.observeSeriesMembers(seriesId), libraryRepository.observeBooks()) { memberRows, books ->
                    memberRows
                        .filter { it.memberType == SeriesMemberType.Book }
                        .map { member -> SeriesMemberRow(member, books.firstOrNull { it.id == member.memberId }?.title.orEmpty()) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSeriesAndJoin(title: String) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            val series = SeriesEntity(title = title)
            libraryRepository.upsertSeries(series)
            libraryRepository.setBookSeries(bookId, series.id)
        }
    }

    fun joinExistingSeries(seriesId: String) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch { libraryRepository.setBookSeries(bookId, seriesId) }
    }

    fun leaveSeries() {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch { libraryRepository.setBookSeries(bookId, null) }
    }

    fun updatePremise(premise: String) {
        val series = currentSeries.value ?: return
        viewModelScope.launch { libraryRepository.upsertSeries(series.copy(premise = premise)) }
    }

    fun updateRollingSummary(summary: String) {
        val series = currentSeries.value ?: return
        viewModelScope.launch {
            libraryRepository.upsertSeries(series.copy(rollingSummary = summary, summaryUpdatedAt = System.currentTimeMillis()))
        }
    }

    fun moveMemberUp(row: SeriesMemberRow) {
        val ordered = members.value.map { it.member }
        val index = ordered.indexOfFirst { it.id == row.member.id }
        if (index <= 0) return
        viewModelScope.launch { libraryRepository.swapSeriesMemberOrder(ordered[index], ordered[index - 1]) }
    }

    fun moveMemberDown(row: SeriesMemberRow) {
        val ordered = members.value.map { it.member }
        val index = ordered.indexOfFirst { it.id == row.member.id }
        if (index == -1 || index >= ordered.size - 1) return
        viewModelScope.launch { libraryRepository.swapSeriesMemberOrder(ordered[index], ordered[index + 1]) }
    }
}
