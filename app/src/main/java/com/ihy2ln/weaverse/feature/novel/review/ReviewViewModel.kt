package com.ihy2ln.weaverse.feature.novel.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _issues = MutableStateFlow<List<ReviewIssue>>(emptyList())
    val issues: StateFlow<List<ReviewIssue>> = _issues.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences
                .map { it.selectedBookId }
                .distinctUntilChanged()
                .collect { bookId -> _issues.value = buildIssues(bookId) }
        }
    }

    private suspend fun buildIssues(bookId: String): List<ReviewIssue> {
        val scenes = db.manuscriptDao().getScenesForBook(bookId)
        val entries = db.codexDao().getAllEntries().filter { !it.disabled }
        return withContext(Dispatchers.Default) {
            val issues = mutableListOf<ReviewIssue>()
            scenes.forEach { scene ->
                if (scene.summary.isBlank()) {
                    issues += ReviewIssue("Empty summary", "${scene.title} has no summary.")
                }
                if (scene.wordCount == 0) {
                    issues += ReviewIssue("Empty scene", "${scene.title} has no words.")
                }
            }
            val povs = scenes.map { it.pov }.distinct().filter { it.isNotBlank() }
            if (povs.size > 1) {
                issues += ReviewIssue("POV drift", "Scenes use different POV: ${povs.joinToString()}")
            }
            scenes.forEach { scene ->
                entries.forEach { entry ->
                    if (scene.plainText.contains(entry.name, ignoreCase = true)) {
                        issues += ReviewIssue(
                            "Codex mention",
                            "${entry.name} appears in ${scene.title}.",
                        )
                    }
                }
            }
            issues.distinctBy { it.title + it.detail }
        }
    }
}
