package com.ihy2ln.weaverse.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.CodexScopes
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
            val q = query.lowercase()
            val results = mutableListOf<SearchResult>()
            // Every chapter of the book, in reading order.
            db.manuscriptDao().getReaderScenes(bookId).forEach { scene ->
                if (scene.title.lowercase().contains(q) || scene.plainText.lowercase().contains(q)) {
                    results += SearchResult(
                        scene.id, SearchResultType.Scene,
                        listOf(scene.chapterTitle, scene.title).filter { it.isNotBlank() }.joinToString(" · "),
                        SearchText.excerpt(scene.plainText, query),
                    )
                }
            }
            // The book's own codex plus the app-wide one.
            (db.codexDao().observeEntries(bookId).first() + db.codexDao().observeEntries(CodexScopes.ID).first())
                .distinctBy { it.id }
                .forEach { entry ->
                    if (entry.name.lowercase().contains(q) || entry.plainText.lowercase().contains(q)) {
                        results += SearchResult(entry.id, SearchResultType.Codex, entry.name, SearchText.excerpt(entry.plainText, query))
                    }
                }
            // Book snippets and the shared notes board.
            (db.snippetDao().get(bookId) + db.snippetDao().get(CodexScopes.ID))
                .distinctBy { it.id }
                .forEach { snippet ->
                    if (snippet.title.lowercase().contains(q) || snippet.body.lowercase().contains(q)) {
                        results += SearchResult(snippet.id, SearchResultType.Snippet, snippet.title, SearchText.excerpt(snippet.body, query))
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

/** Search-result snippets: the words around the first match, not just the opening line. */
object SearchText {
    fun excerpt(text: String, query: String, width: Int = 120): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val at = flat.indexOf(query.trim(), ignoreCase = true)
        if (at < 0 || flat.length <= width) return flat.take(width)
        val start = (at - width / 3).coerceAtLeast(0)
        val end = (start + width).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + if (end < flat.length) "…" else ""
    }
}
