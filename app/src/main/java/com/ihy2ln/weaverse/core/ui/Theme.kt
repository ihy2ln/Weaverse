package com.ihy2ln.weaverse.core.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Root theme for both Novel and Roleplay modes. [appTheme] drives the
 * Format-menu color themes (§11, plus [AppTheme.Custom] from Revision 02
 * §4 — [customThemeSettings] is only consulted for that one, ignored
 * otherwise); [typography] drives font family, size, line height, and
 * spacing — each mode can pass its own [TypographySettings] so roleplay can
 * look different from the manuscript (§11, "a per-mode override so roleplay
 * can look different from the manuscript").
 */
@Composable
fun WeaverseTheme(
    appTheme: AppTheme = AppTheme.Light,
    typography: TypographySettings = TypographySettings.Manuscript,
    customThemeSettings: CustomThemeSettings? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(appTheme, customThemeSettings)
    val typographyStyles = buildTypography(typography)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !appTheme.isDark(customThemeSettings)
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyStyles,
        content = content,
    )
}
