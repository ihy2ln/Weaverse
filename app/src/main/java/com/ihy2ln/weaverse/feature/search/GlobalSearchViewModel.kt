package com.ihy2ln.weaverse.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchResultType(val label: String) {
    Scene("Scene"),
    Codex("Codex"),
    Snippet("Snippet"),
    WorkshopChat("Workshop"),
    RoleplayChat("Roleplay"),
}

data class SearchResult(
    val id: String,
    val type: SearchResultType,
    val title: String,
    val snippet: String,
)

data class GlobalSearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            if (query.length < 2) {
                _uiState.update { it.copy(results = emptyList()) }
                return@launch
            }
            val bookId = settings.preferences.first().selectedBookId
            val chapterId = bookRepository.primaryChapterId(bookId)
            val q = query.lowercase()
            val results = mutableListOf<SearchResult>()
            if (chapterId != null) {
                db.manuscriptDao().observeScenes(chapterId).first().forEach { scene ->
                    if (scene.title.lowercase().contains(q) || scene.plainText.lowercase().contains(q)) {
                        results += SearchResult(scene.id, SearchResultType.Scene, scene.title, scene.plainText.take(120))
                    }
                }
            }
            db.codexDao().observeEntries(bookId).first().forEach { entry ->
                if (entry.name.lowercase().contains(q) || entry.plainText.lowercase().contains(q)) {
                    results += SearchResult(entry.id, SearchResultType.Codex, entry.name, entry.plainText.take(120))
                }
            }
            db.snippetDao().observe(bookId).first().forEach { snippet ->
                if (snippet.title.lowercase().contains(q) || snippet.body.lowercase().contains(q)) {
                    results += SearchResult(snippet.id, SearchResultType.Snippet, snippet.title, snippet.body.take(120))
                }
            }
            db.workshopChatDao().observeThreads(bookId).first().forEach { thread ->
                if (thread.name.lowercase().contains(q)) {
                    results += SearchResult(thread.id, SearchResultType.WorkshopChat, thread.name, "Workshop thread")
                }
            }
            db.roleplayDao().observeChats().first().forEach { chat ->
                if (chat.title.lowercase().contains(q)) {
                    results += SearchResult(chat.id, SearchResultType.RoleplayChat, chat.title, "Roleplay chat")
                }
            }
            _uiState.update { it.copy(results = results) }
        }
    }
}
