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
    val checkingUpdates: Boolean = false,
    val release: GameRelease? = null,
    val updateAvailable: Boolean = false,
    val updateProgress: Float? = null,
    val updatedBytes: Long = 0,
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
            _uiState.update {
                it.copy(
                    loading = false,
                    installed = pack,
                    updateAvailable = it.release?.let { release ->
                        GameReleaseChecker.isNewer(release.version, pack?.manifest?.version)
                    } ?: false,
                )
            }
        }
    }

    fun import(uri: Uri) {
        if (_uiState.value.importProgress != null || _uiState.value.updateProgress != null) return
        _uiState.update { it.copy(importProgress = 0f, importedBytes = 0, message = null) }
        viewModelScope.launch {
            val result = store.import(uri) { progress, copied ->
                _uiState.update {
                    it.copy(
                        importProgress = progress,
                        importedBytes = copied,
                    )
                }
            }
            val pack = store.installed()
            _uiState.update {
                it.copy(
                    importProgress = null,
                    installed = pack,
                    updateAvailable = _uiState.value.release?.let { release ->
                        GameReleaseChecker.isNewer(release.version, pack?.manifest?.version)
                    } ?: false,
                    message = result.fold(
                        onSuccess = { manifest -> "Installed ${manifest.title} ${manifest.version}".trim() },
                        onFailure = { error -> error.message ?: "The import failed." },
                    ),
                )
            }
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.checkingUpdates || _uiState.value.updateProgress != null) return
        _uiState.update { it.copy(checkingUpdates = true, message = null) }
        viewModelScope.launch {
            runCatching { GameReleaseChecker.latest() }.fold(
                onSuccess = { release ->
                    val newer = GameReleaseChecker.isNewer(release.version, _uiState.value.installed?.manifest?.version)
                    _uiState.update {
                        it.copy(
                            checkingUpdates = false,
                            release = release,
                            updateAvailable = newer,
                            message = if (newer) "Adams Haven ${release.version} is available on GitHub."
                                else "Adams Haven is up to date (${release.version}).",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(checkingUpdates = false, message = "Could not check GitHub: ${error.message ?: "network error"}")
                    }
                },
            )
        }
    }

    fun updateFromGitHub() {
        val release = _uiState.value.release ?: return
        if (!_uiState.value.updateAvailable || _uiState.value.importProgress != null || _uiState.value.updateProgress != null) return
        _uiState.update { it.copy(updateProgress = 0f, updatedBytes = 0, message = null) }
        viewModelScope.launch {
            val result = store.update(release) { progress, bytes ->
                _uiState.update { it.copy(updateProgress = progress, updatedBytes = bytes) }
            }
            val pack = store.installed()
            _uiState.update {
                it.copy(
                    installed = pack,
                    updateProgress = null,
                    updateAvailable = GameReleaseChecker.isNewer(release.version, pack?.manifest?.version),
                    message = result.fold(
                        onSuccess = { manifest -> "Installed Adams Haven ${manifest.version}." },
                        onFailure = { error -> error.message ?: "The update failed." },
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
                    updateAvailable = if (removed) it.release != null else it.updateAvailable,
                    message = if (removed) "Game data removed. Your saves are kept." else "Could not remove the game data.",
                )
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}
