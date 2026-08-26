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

/**
 * Difficulty rather than a raw sampler setting: [directive] is injected into the
 * system prompt so the world actually pushes back the chosen amount, and
 * [temperature] follows it (harsher settings stay more disciplined).
 */
data class RpPreset(
    val id: String,
    val name: String,
    val description: String,
    val temperature: Float,
    val directive: String,
)

val defaultPresets = listOf(
    RpPreset(
        id = "preset-slice",
        name = "Slice of life",
        description = "Warm and low-stakes. Setbacks are small and nobody really loses.",
        temperature = 0.95f,
        directive = "Keep the stakes gentle. Conflicts resolve kindly, injuries are minor, " +
            "and the story favours warmth, humour and everyday moments over danger.",
    ),
    RpPreset(
        id = "preset-balanced",
        name = "Normal",
        description = "A fair world. Effort is usually rewarded, mistakes usually recoverable.",
        temperature = 0.8f,
        directive = "Let outcomes follow effort. Reasonable plans tend to work, mistakes cost " +
            "something but are recoverable, and danger is real without being punishing.",
    ),
    RpPreset(
        id = "preset-hard",
        name = "Hard",
        description = "The world pushes back. Plans need thought and mistakes hurt.",
        temperature = 0.7f,
        directive = "Make the world push back. Careless choices fail, resources run short, " +
            "and opponents act intelligently. Success must be earned.",
    ),
    RpPreset(
        id = "preset-ruthless",
        name = "Ruthless",
        description = "Unforgiving. Bad decisions can end the run.",
        temperature = 0.6f,
        directive = "Be unforgiving. Enemies exploit every weakness, luck does not rescue bad " +
            "decisions, and lasting loss — including death — is on the table. Never soften an " +
            "outcome to spare the player.",
    ),
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
