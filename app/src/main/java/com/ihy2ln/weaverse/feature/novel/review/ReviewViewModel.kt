package com.ihy2ln.weaverse.feature.novel.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    private val _issues = MutableStateFlow<List<ReviewIssue>>(emptyList())
    val issues: StateFlow<List<ReviewIssue>> = _issues.asStateFlow()

    init {
        viewModelScope.launch {
            // Only a different book needs a new review, not every settings change.
            settings.preferences.map { it.selectedBookId }.distinctUntilChanged().collect { bookId ->
                _issues.value = review(bookId)
            }
        }
    }

    /** Checks every chapter of the book, in reading order. */
    private suspend fun review(bookId: String): List<ReviewIssue> {
        val dao = db.manuscriptDao()
        val issues = mutableListOf<ReviewIssue>()
        val allText = StringBuilder()
        dao.getActs(bookId).forEach { act ->
            dao.getChapters(act.id).forEach { chapter ->
                val scenes = dao.getScenes(chapter.id)
                scenes.forEach { scene ->
                    val where = listOf(chapter.title, scene.title).filter { it.isNotBlank() }.joinToString(" · ")
                    if (scene.wordCount == 0) {
                        issues += ReviewIssue("Empty scene", "$where has no words.")
                    } else if (scene.summary.isBlank()) {
                        issues += ReviewIssue("Empty summary", "$where has no summary.")
                    }
                    allText.append(scene.plainText).append(' ')
                }
                val povs = scenes.map { it.pov.trim() }.filter { it.isNotBlank() }.distinct()
                if (povs.size > 1) {
                    issues += ReviewIssue("POV drift", "${chapter.title.ifBlank { "A chapter" }} switches POV: ${povs.joinToString()}")
                }
            }
        }
        // Codex entries the manuscript never mentions are usually stale or misnamed.
        val text = allText.toString()
        if (text.isNotBlank()) {
            db.codexDao().observeEntries(bookId).first()
                .filter { it.name.isNotBlank() && !text.contains(it.name, ignoreCase = true) }
                .forEach { issues += ReviewIssue("Unused codex entry", "${it.name} never appears in the manuscript.") }
        }
        return issues.distinctBy { it.title + it.detail }
    }
}
