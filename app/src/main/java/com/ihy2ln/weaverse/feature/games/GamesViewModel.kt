package com.ihy2ln.weaverse.feature.games

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GamesUiState(
    val loading: Boolean = true,
    val installed: InstalledGamePack? = null,
    /** 0..1 while a pack is being copied in, or -1 when the size is unknown; null when idle. */
    val importProgress: Float? = null,
    val importedBytes: Long = 0,
    val message: String? = null,
    val packPath: String = "",
)

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val store: GamePackStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GamesUiState(packPath = store.packFile().absolutePath))
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the pack; also picks up one pushed with adb while the app was open. */
    fun refresh() {
        viewModelScope.launch {
            val pack = store.installed()
            _uiState.update { it.copy(loading = false, installed = pack) }
        }
    }

    fun import(uri: Uri) {
        if (_uiState.value.importProgress != null) return
        _uiState.update { it.copy(importProgress = 0f, importedBytes = 0, message = null) }
        viewModelScope.launch {
            val result = store.import(uri) { copied, total ->
                _uiState.update {
                    it.copy(
                        importProgress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else -1f,
                        importedBytes = copied,
                    )
                }
            }
            val pack = store.installed()
            _uiState.update {
                it.copy(
                    importProgress = null,
                    installed = pack,
                    message = result.fold(
                        onSuccess = { manifest -> "Installed ${manifest.title} ${manifest.version}".trim() },
                        onFailure = { error -> error.message ?: "The import failed." },
                    ),
                )
            }
        }
    }

    fun remove() {
        viewModelScope.launch {
            val removed = store.remove()
            _uiState.update {
                it.copy(
                    installed = if (removed) null else it.installed,
                    message = if (removed) "Game data removed. Your saves are kept." else "Could not remove the game data.",
                )
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}
