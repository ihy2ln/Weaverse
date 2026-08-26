package com.ihy2ln.weaverse.feature.roleplay.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkHairline
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

data class RpPreset(val id: String, val name: String, val description: String, val temperature: Float)

val defaultPresets = listOf(
    RpPreset("preset-balanced", "Balanced", "General roleplay — temp 0.8", 0.8f),
    RpPreset("preset-creative", "Creative", "More varied replies — temp 1.0", 1.0f),
    RpPreset("preset-precise", "Precise", "Stays on-script — temp 0.5", 0.5f),
)

@Composable
fun PresetsScreen(viewModel: PresetsViewModel = hiltViewModel()) {
    val selectedId by viewModel.selectedPresetId.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Presets", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sampler settings for roleplay chats — tap to select",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = InkSpacing.md),
        )
        LazyColumn {
            items(defaultPresets, key = { it.id }) { preset ->
                val selected = selectedId == preset.id
                val hue = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.16f else 0.04f)
                val border = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                } else {
                    InkHairline
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = InkSpacing.sm)
                        .clip(RoundedCornerShape(inkRadiusMd()))
                        .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(inkRadiusMd()))
                        .background(hue)
                        .clickable { viewModel.selectPreset(preset.id) }
                        .padding(InkSpacing.lg),
                ) {
                    Text(
                        if (selected) "✓ ${preset.name}" else preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(preset.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Temperature ${preset.temperature}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
