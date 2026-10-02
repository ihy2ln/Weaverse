package com.ihy2ln.weaverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
            WeaverseTheme(
                themeMode = prefs.themeMode,
                profile = prefs.appearanceProfile,
                textScale = prefs.uiTextScalePercent / 100f,
                lineSpacing = prefs.uiLineSpacing,
                glassClarity = prefs.glassClarityPercent / 100f,
            ) {
                val tokens = inkTokens()
                val themed = resolveSectionColor(prefs.appearance.chrome, tokens.panel)
                val barColor = if (themed.alpha < 0.4f) tokens.panel else themed.copy(alpha = 1f)
                LaunchedEffect(barColor) {
                    val argb = barColor.toArgb()
                    val lightBars = barColor.luminance() > 0.5f
                    val style = if (lightBars) SystemBarStyle.light(argb, argb) else SystemBarStyle.dark(argb)
                    // The status bar is see-through: AppShell draws Home's splash and the
                    // wallpaper behind it, and its own glass strip on other pages.
                    val clear = android.graphics.Color.TRANSPARENT
                    val statusStyle = if (lightBars) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
                    enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = style)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    color = barColor,
                ) {
                    // Back used to quit outright, which is easy to trigger by accident
                    // with the edge-swipe gesture mid-scene. Confirm first.
                    var confirmExit by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = !confirmExit) { confirmExit = true }
                    if (confirmExit) {
                        AlertDialog(
                            onDismissRequest = { confirmExit = false },
                            title = { Text("Close Weaverse?") },
                            text = { Text("Your work is saved as you go.") },
                            confirmButton = {
                                TextButton(onClick = { finish() }) { Text("Close") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmExit = false }) { Text("Stay") }
                            },
                        )
                    }
                    // AppShell pads its pages for the system bars itself, so Home's splash
                    // and the wallpaper can run underneath them.
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppShell()
                    }


                }
            }
        }
    }
}
