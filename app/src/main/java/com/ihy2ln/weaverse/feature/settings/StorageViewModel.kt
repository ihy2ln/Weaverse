package com.ihy2ln.weaverse.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaMaintenance
import com.ihy2ln.weaverse.data.repo.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs Settings' Storage section — media byte total (Phase 3) and the "remove orphaned
 * files" maintenance action (Phase 6's `MediaMaintenance`, previously unlinked from any screen). */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaMaintenance: MediaMaintenance,
) : ViewModel() {
    val totalBytes: StateFlow<Long> = mediaRepository.observeTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _lastCleanupCount = MutableStateFlow<Int?>(null)
    val lastCleanupCount: StateFlow<Int?> = _lastCleanupCount

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning

    fun cleanUpOrphanedMedia() {
        if (_isCleaning.value) return
        viewModelScope.launch {
            _isCleaning.value = true
            _lastCleanupCount.value = mediaMaintenance.deleteOrphanedMedia()
            _isCleaning.value = false
        }
    }
}
