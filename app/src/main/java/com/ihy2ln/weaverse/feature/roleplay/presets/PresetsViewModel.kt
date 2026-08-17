package com.ihy2ln.weaverse.feature.roleplay.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val selectedPresetId: StateFlow<String> = settings.preferences
        .map { it.roleplayPresetId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "preset-balanced")

    fun selectPreset(presetId: String) {
        viewModelScope.launch {
            settings.setRoleplayPresetId(presetId)
        }
    }
}
