package com.ihy2ln.weaverse.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

/**
 * Settings → PC harnesses: Claude Code, Codex (ChatGPT) and ComfyUI on the user's PC, reached
 * through Weaverse Desktop with the sync address and password. The models themselves are picked
 * in Models (Writing / Vision / Image generation) or in Manga Studio → AI settings.
 */
@Composable
internal fun PcHarnessBody(state: SettingsUiState, onCheck: () -> Unit) {
    val pc = state.prefs.syncWebUrl.trim()
    Column {
        Text(
            "Use the AI tools on your PC from this phone: Claude Code and ChatGPT (Codex CLI) run on your own " +
                "subscriptions, Ollama runs local models on your PC for free, and ComfyUI colors and edits " +
                "pictures on your PC's graphics card with no content filter. The phone talks to them through " +
                "Weaverse Desktop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (pc.isBlank()) "PC: not set — enter it under Sync through the web version (address and sync password)."
            else "PC: $pc (sync password from Sync through the web version)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = InkSpacing.sm)) {
            OutlinedButton(onClick = onCheck, enabled = !state.pcChecking && pc.isNotBlank()) { Text("Check PC") }
            if (state.pcChecking) CircularProgressIndicator(Modifier.padding(start = InkSpacing.sm).size(20.dp), strokeWidth = 2.dp)
        }
        if (state.pcStatus.isNotBlank()) {
            Text(state.pcStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = InkSpacing.sm))
        }
        Text(
            "On the PC: start Weaverse Desktop (START-DESKTOP.bat) and keep it open.\n" +
                "• Claude Code: run `claude` once in a terminal and sign in with /login.\n" +
                "• ChatGPT: install the Codex CLI (`npm install -g @openai/codex`), then run `codex login` and " +
                "sign in with your ChatGPT account.\n" +
                "• Ollama: install it and pull models (`ollama pull <model>`); Weaverse Desktop starts it when " +
                "needed and lists every installed model. Models that read pictures also appear under Vision.\n" +
                "• ComfyUI: keep it running. Coloring uses the Qwen Image Edit 2.1 workflow; extra API-format " +
                "workflows dropped into Weaverse/data/comfy-workflows appear as more models " +
                "(placeholders __IMAGE__, __PROMPT__, __NEGATIVE__, __SEED__, __STEPS__).\n\n" +
                "Then pick them in Models (Writing, Vision and Image generation tabs) or in Manga Studio → AI → AI settings. " +
                "Ollama answers stream in as they are written; Claude Code and Codex answers arrive whole, " +
                "and ComfyUI takes a few minutes per page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
    }
}
