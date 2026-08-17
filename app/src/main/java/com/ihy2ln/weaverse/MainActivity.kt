package com.ihy2ln.weaverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.WeaverseTheme
import com.ihy2ln.weaverse.feature.shell.AppShell
import com.ihy2ln.weaverse.feature.shell.AppThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val appTheme by themeViewModel.appTheme.collectAsState()
            val typography by themeViewModel.typography.collectAsState()
            val customThemeSettings by themeViewModel.customThemeSettings.collectAsState()
            WeaverseTheme(appTheme = appTheme, typography = typography, customThemeSettings = customThemeSettings) {
                AppShell()
            }
        }
    }
}
