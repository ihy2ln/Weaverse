package com.ihy2ln.weaverse.feature.roleplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the roleplay rail's persisted width/collapsed state — the same
 * [AppSettingsRepository] keys [com.ihy2ln.weaverse.feature.novel.NovelShellViewModel] uses, so
 * the rail keeps one width/collapsed preference across both modes rather than two independent
 * ones a user would have to set up twice. */
@HiltViewModel
class RoleplayShellViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    val railWidthDp: StateFlow<Int> = settingsRepository.railWidthDp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsRepository.RailWidthDefault)

    val railCollapsed: StateFlow<Boolean> = settingsRepository.railCollapsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setRailWidthDp(widthDp: Int) {
        viewModelScope.launch { settingsRepository.setRailWidthDp(widthDp) }
    }

    fun setRailCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settingsRepository.setRailCollapsed(collapsed) }
    }
}
