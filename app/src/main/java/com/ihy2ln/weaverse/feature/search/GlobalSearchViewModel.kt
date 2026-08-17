package com.ihy2ln.weaverse.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.repo.ChatRepository
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import com.ihy2ln.weaverse.data.repo.SearchRepository
import com.ihy2ln.weaverse.data.repo.SearchResults
import com.ihy2ln.weaverse.data.repo.SnippetLabelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchCategory { Scene, Codex, Chat, Roleplay, Snippet }

data class SearchResultRow(val category: SearchCategory, val id: String, val title: String, val snippet: String)

/**
 * Backs the global search overlay (spec §4/§9: "Global Search across Scenes,
 * Codex, Chats, Snippets"). Debounces input (300ms) before hitting
 * [SearchRepository.search], then resolves the returned ids against each
 * owning repository for a title + text snippet to render. Tapping a result
 * doesn't jump anywhere yet — none of Plan/Codex/Chat/RP Chats expose a
 * "open this exact item" entry point from outside their own screen (see
 * BUILD_NOTES "Phase 12 deviations/gaps"); this proves the search/resolve
 * data path end to end, which is the spec's own §12 checkpoint wording.
 */
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    private val codexRepository: CodexRepository,
    private val chatRepository: ChatRepository,
    private val roleplayRepository: RoleplayRepository,
    private val snippetLabelRepository: SnippetLabelRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<SearchResultRow>>(emptyList())
    val results: StateFlow<List<SearchResultRow>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(300)
            val found = searchRepository.search(text)
            _results.value = resolveResults(found)
            _isSearching.value = false
        }
    }

    private suspend fun resolveResults(found: SearchResults): List<SearchResultRow> = buildList {
        found.sceneIds.forEach { id ->
            libraryRepository.getScene(id)?.let { scene ->
                add(SearchResultRow(SearchCategory.Scene, id, scene.title, scene.plainText.take(120)))
            }
        }
        codexRepository.getEntries(found.codexEntryIds).forEach { entry ->
            add(SearchResultRow(SearchCategory.Codex, entry.id, entry.name, entry.plainText.take(120)))
        }
        found.chatMessageIds.forEach { id ->
            chatRepository.getMessage(id)?.let { message ->
                val threadName = chatRepository.getThread(message.threadId)?.name ?: "Workshop Chat"
                add(SearchResultRow(SearchCategory.Chat, id, threadName, message.plainText.take(120)))
            }
        }
        found.rpMessageIds.forEach { id ->
            roleplayRepository.getMessage(id)?.let { message ->
                val chatTitle = message.chatId.let { roleplayRepository.getChat(it) }?.title ?: "Roleplay Chat"
                add(SearchResultRow(SearchCategory.Roleplay, id, chatTitle, message.plainText.take(120)))
            }
        }
        found.snippetIds.forEach { id ->
            snippetLabelRepository.getSnippet(id)?.let { snippet ->
                add(SearchResultRow(SearchCategory.Snippet, id, snippet.title, snippet.body.take(120)))
            }
        }
    }
}
