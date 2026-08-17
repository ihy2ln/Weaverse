package com.ihy2ln.weaverse.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.ui.AppTheme
import com.ihy2ln.weaverse.core.ui.CustomThemeSettings
import com.ihy2ln.weaverse.core.ui.TypographySettings
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Feeds [com.ihy2ln.weaverse.core.ui.WeaverseTheme] at the `MainActivity` root — the one place
 * outside a NavHost that still needs a Hilt-backed read of persisted Appearance settings. */
@HiltViewModel
class AppThemeViewModel @Inject constructor(settings: AppSettingsRepository) : ViewModel() {
    val appTheme: StateFlow<AppTheme> = settings.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.Light)

    val typography: StateFlow<TypographySettings> = settings.typography
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TypographySettings.Manuscript)

    val customThemeSettings: StateFlow<CustomThemeSettings> = settings.customThemeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomThemeSettings())
}
