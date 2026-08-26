package com.ihy2ln.weaverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.ihy2ln.weaverse.core.ui.theme.WeaverseTheme
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.resolveSectionColor
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.AppShell
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val prefs by settingsRepository.preferences.collectAsState(
                initial = com.ihy2ln.weaverse.data.settings.UserPreferences(),
            )
            WeaverseTheme(themeMode = prefs.themeMode, profile = prefs.appearanceProfile) {
                val tokens = inkTokens()
                val themed = resolveSectionColor(prefs.appearance.chrome, tokens.panel)
                val barColor = if (themed.alpha < 0.4f) tokens.panel else themed.copy(alpha = 1f)
                SideEffect {
                    val argb = barColor.toArgb()
                    val style = if (barColor.luminance() > 0.5f) {
                        SystemBarStyle.light(argb, argb)
                    } else {
                        SystemBarStyle.dark(argb)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    color = barColor,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        AppShell()
                    }
                }
            }
        }
    }
}
