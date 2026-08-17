package com.ihy2ln.weaverse.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.util.AppMode
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
) : ViewModel() {
    private val _currentMode = MutableStateFlow(AppMode.Novel)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    init {
        viewModelScope.launch {
            _currentMode.value = settings.launchMode.first()
        }
    }

    fun toggleMode() = setMode(if (_currentMode.value == AppMode.Novel) AppMode.Roleplay else AppMode.Novel)

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        viewModelScope.launch { settings.setCurrentMode(mode) }
    }

    fun recordNovelRoute(route: String) {
        viewModelScope.launch { settings.setNovelLastRoute(route) }
    }

    fun recordRoleplayRoute(route: String) {
        viewModelScope.launch { settings.setRoleplayLastRoute(route) }
    }
}
