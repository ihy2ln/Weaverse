package com.ihy2ln.weaverse.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

/**
 * Settings → fal.ai: one key for fal's picture models (FLUX Kontext, Qwen Image Edit, Seedream,
 * Nano Banana…) and its text router. The models are picked in Models (Image generation / Writing)
 * or in Manga Studio → AI settings, like any other.
 */
@Composable
internal fun FalSettingsBody(state: SettingsUiState, onKey: (String) -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    Column {
        Text(
            "fal.ai runs hundreds of image models on pay-as-you-go credit. Weaverse turns each model's own " +
                "safety checker off where the model allows it, so pages one provider declines can be colored here. " +
                "Text models are reached through fal's router on the same key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.falKey,
            onValueChange = onKey,
            label = { Text("fal.ai API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = InkSpacing.sm)) {
            OutlinedButton(onClick = onSave, enabled = !state.falChecking) { Text("Save & check") }
            if (state.falChecking) CircularProgressIndicator(Modifier.padding(start = InkSpacing.sm).size(20.dp), strokeWidth = 2.dp)
            TextButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fal.ai/dashboard/keys"))) } },
                modifier = Modifier.padding(start = InkSpacing.sm),
            ) { Text("Get a key") }
        }
        if (state.falStatus.isNotBlank()) {
            Text(state.falStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = InkSpacing.sm))
        }
        Text(
            "Then pick fal models (\"fal · …\") in Models → Image generation, or in Manga Studio → AI → AI settings. " +
                "FLUX Kontext and Qwen Image Edit suit coloring manga pages; text-to-image models make new pictures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
    }
}
