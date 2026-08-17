package com.ihy2ln.weaverse.feature.settings

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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs Settings' Appearance section (spec §11's Format menu, persisted globally via Phase 12's
 * DataStore additions rather than per-document — see `AppSettingsRepository`'s own KDoc). */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
) : ViewModel() {
    val appTheme: StateFlow<AppTheme> = settings.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.Light)

    val typography: StateFlow<TypographySettings> = settings.typography
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TypographySettings.Manuscript)

    val customThemeSettings: StateFlow<CustomThemeSettings> = settings.customThemeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomThemeSettings())

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setAppTheme(theme) }
    }

    fun setTypography(update: TypographySettings.() -> TypographySettings) {
        viewModelScope.launch { settings.setTypography(typography.value.update()) }
    }

    fun setCustomThemeSettings(update: CustomThemeSettings.() -> CustomThemeSettings) {
        viewModelScope.launch { settings.setCustomThemeSettings(customThemeSettings.value.update()) }
    }
}
