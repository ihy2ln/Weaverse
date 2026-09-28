package com.ihy2ln.weaverse.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.feature.notes.NotesViewModel
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
            val q = query.lowercase()
            val results = mutableListOf<SearchResult>()
            db.manuscriptDao().getScenesForBook(bookId).forEach { scene ->
                if (scene.title.contains(q, ignoreCase = true) || scene.plainText.contains(q, ignoreCase = true)) {
                    results += SearchResult(scene.id, SearchResultType.Scene, scene.title, scene.plainText.take(120))
                }
            }
            db.codexDao().getAllEntries().forEach { entry ->
                if (entry.name.contains(q, ignoreCase = true) || entry.plainText.contains(q, ignoreCase = true)) {
                    results += SearchResult(entry.id, SearchResultType.Codex, entry.name, entry.plainText.take(120))
                }
            }
            val snippets = db.snippetDao().get(bookId) + db.snippetDao().getByCategory(NotesViewModel.CATEGORY)
            snippets.distinctBy { it.id }.forEach { snippet ->
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
