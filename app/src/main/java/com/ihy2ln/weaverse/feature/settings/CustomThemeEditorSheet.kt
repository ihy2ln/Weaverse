package com.ihy2ln.weaverse.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.ColorSwatch
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.CustomThemeSettings
import com.ihy2ln.weaverse.core.ui.HsvColorWheel
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.contrastRatio
import com.ihy2ln.weaverse.core.ui.customColorScheme
import com.ihy2ln.weaverse.core.ui.meetsWcagAA
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.core.ui.suggestAccessibleColor
import com.ihy2ln.weaverse.core.ui.toHex

/**
 * The colour-wheel editor behind [com.ihy2ln.weaverse.core.ui.AppTheme.Custom] (Revision 02 §4):
 * separate wheels for accent/primary, app background, panel background, manuscript page
 * background, and default body text colour, each with reset-to-default and a WCAG AA contrast
 * check against the current background. [pageHex] is captured and persisted here but doesn't
 * change anything on screen yet — the Write screen's manuscript page has no separate "page
 * surface" slot in `ColorScheme` to plug it into; wiring an actual page-background override into
 * the Write screen is follow-up work, not a `ColorScheme` derivation concern. Named-preset save/
 * export/import (spec's other §4 bullet) also isn't here — this edits the single active custom
 * theme, not a library of saved ones.
 */
@Composable
fun CustomThemeEditorSheet(onDismiss: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val settings by viewModel.customThemeSettings.collectAsState()
    var activeWheel by remember { mutableStateOf(CustomThemeProperty.Accent) }

    InkModalBottomSheet(onDismiss = onDismiss, title = "Custom theme") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            PreviewCard(settings)
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark base", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.baseIsDark,
                    onCheckedChange = { checked -> viewModel.setCustomThemeSettings { copy(baseIsDark = checked) } },
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CustomThemeProperty.entries.forEach { property ->
                    TextButton(onClick = { activeWheel = property }) {
                        Text(
                            property.label,
                            color = if (property == activeWheel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val currentHex = activeWheel.currentHex(settings)
            val currentColor = parseHex(currentHex) ?: Color.Gray
            HsvColorWheel(
                color = currentColor,
                onColorChanged = { color -> viewModel.setCustomThemeSettings { activeWheel.withHex(this, color.toHex()) } },
                modifier = Modifier.padding(top = Spacing.md),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                ColorSwatch(color = currentColor, size = 20.dp)
                Text(currentHex, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm).weight(1f))
                if (activeWheel.isOverridden(settings)) {
                    TextButton(onClick = { viewModel.setCustomThemeSettings { activeWheel.withHex(this, null) } }) {
                        Text("Reset to default")
                    }
                }
            }

            if (activeWheel != CustomThemeProperty.Accent) {
                val background = parseHex(CustomThemeProperty.AppBackground.currentHex(settings)) ?: Color.White
                val ratio = contrastRatio(currentColor, background)
                if (!meetsWcagAA(currentColor, background)) {
                    ContrastWarning(
                        ratio = ratio,
                        onFix = {
                            val fixed = suggestAccessibleColor(currentColor, background)
                            viewModel.setCustomThemeSettings { activeWheel.withHex(this, fixed.toHex()) }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

private enum class CustomThemeProperty(val label: String) {
    Accent("Accent"),
    AppBackground("Background"),
    Panel("Panel"),
    Page("Page"),
    BodyText("Body text");

    fun currentHex(settings: CustomThemeSettings): String = when (this) {
        Accent -> settings.seedHex
        AppBackground -> settings.backgroundHex ?: customColorScheme(settings).background.toHex()
        Panel -> settings.panelHex ?: customColorScheme(settings).surfaceVariant.toHex()
        Page -> settings.pageHex ?: settings.backgroundHex ?: customColorScheme(settings).background.toHex()
        BodyText -> settings.bodyTextHex ?: customColorScheme(settings).onBackground.toHex()
    }

    fun isOverridden(settings: CustomThemeSettings): Boolean = when (this) {
        Accent -> false
        AppBackground -> settings.backgroundHex != null
        Panel -> settings.panelHex != null
        Page -> settings.pageHex != null
        BodyText -> settings.bodyTextHex != null
    }

    fun withHex(settings: CustomThemeSettings, hex: String?): CustomThemeSettings = when (this) {
        Accent -> settings.copy(seedHex = hex ?: settings.seedHex)
        AppBackground -> settings.copy(backgroundHex = hex)
        Panel -> settings.copy(panelHex = hex)
        Page -> settings.copy(pageHex = hex)
        BodyText -> settings.copy(bodyTextHex = hex)
    }
}

@Composable
private fun PreviewCard(settings: CustomThemeSettings) {
    val scheme = customColorScheme(settings)
    Surface(
        color = scheme.background,
        shape = RoundedCornerShape(CornerRadius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("Sample chrome", color = scheme.onBackground, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(CornerRadius.card), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.sm)) {
                    Text("Scene 1 – 433 words", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.padding(top = Spacing.xs)) {
                        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { chipColor ->
                            Surface(
                                color = chipColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(CornerRadius.pill),
                                modifier = Modifier.padding(end = Spacing.xs),
                            ) {
                                Text(
                                    "Codex",
                                    color = chipColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContrastWarning(ratio: Float, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .clip(RoundedCornerShape(CornerRadius.card))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(Spacing.sm),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
            Text(
                String.format(java.util.Locale.ROOT, "Contrast ratio %.1f:1 — below WCAG AA (4.5:1)", ratio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        TextButton(onClick = onFix) { Text("Fix") }
    }
}
