package com.ihy2ln.weaverse.feature.games

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

private const val HERO_ART = "file:///android_asset/images/adams_haven/battle/boss_arena/boss-arena-01-heartwood-throne.webp"

/**
 * Games mode: Adams Haven, the Godot card game, launched as the real game. This screen only
 * installs the game data and starts it; everything past Play is the Godot build itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(viewModel: GamesViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = inkTokens()
    var confirmRemove by remember { mutableStateOf(false) }

    // Coming back from the game, or from pushing a pack with adb, should show what is there now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val importing = ui.importProgress != null || ui.updateProgress != null
    val installed = ui.installed

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd() * 1.5f)),
        ) {
            AsyncImage(
                model = HERO_ART,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Fades into the profile's page color, with the profile's accent and type, like
            // every other mode's art.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, tokens.background.copy(alpha = 0.9f)))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(InkSpacing.lg),
            ) {
                Text("SILVERWOOD EXPEDITIONS", style = MaterialTheme.typography.labelMedium, color = tokens.activePill, letterSpacing = 1.5.sp)
                Text("Adams Haven", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Text(
                    installed?.manifest?.version?.takeIf { it.isNotBlank() }?.let { "Version $it" } ?: "Card roguelite · Tower tycoon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText.copy(alpha = 0.8f),
                )
            }
        }

        InkCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                when {
                    ui.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    importing -> {
                        Text(
                            if (ui.updateProgress != null) "Updating from GitHub…" else "Installing game data…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val progress = ui.importProgress ?: ui.updateProgress ?: 0f
                        if (progress >= 0f) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(
                            "${formatBytes(if (ui.updateProgress != null) ui.updatedBytes else ui.importedBytes)} processed. Keep Weaverse open until it finishes.",
                            color = tokens.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    installed != null -> {
                        Text("Ready to play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Game data ${formatBytes(installed.sizeOnDisk)} · Godot ${installed.manifest.engine.ifBlank { GodotRuntime.ENGINE_VERSION }}",
                            color = tokens.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { context.startActivity(Intent(context, AdamsHavenGameActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(InkSpacing.xs))
                            Text("Play Adams Haven", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Opens full screen in landscape. Back or Quit in the game returns here.",
                            color = tokens.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {
                        Text("Install the game data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "The game's art, battle videos and scenes are separate from the Weaverse download. " +
                                "Check GitHub for the latest release or import an Adams Haven APK or Weaverse game-pack ZIP.",
                            color = tokens.secondaryText,
                        )
                    }
                }
                if (!ui.loading && !importing) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                        OutlinedButton(
                            onClick = viewModel::checkForUpdates,
                            enabled = !ui.checkingUpdates,
                        ) {
                            if (ui.checkingUpdates) CircularProgressIndicator(Modifier.size(18.dp))
                            else Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(InkSpacing.xs))
                            Text(if (ui.checkingUpdates) "Checking GitHub…" else "Check for updates")
                        }
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "application/vnd.android.package-archive", "application/octet-stream", "*/*")) }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(InkSpacing.xs))
                            Text("Import game")
                        }
                        if (installed != null) {
                            TextButton(onClick = { confirmRemove = true }) { Text("Remove game data") }
                        }
                    }
                }
                if (ui.updateAvailable && ui.release != null && !importing) {
                    Button(onClick = viewModel::updateFromGitHub, modifier = Modifier.fillMaxWidth()) {
                        Text("Download and install Adams Haven ${ui.release?.version}")
                    }
                }
                ui.message?.let { message ->
                    Text(message, color = tokens.activePill, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        InkCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Text("IN THE GAME", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                Feature("Battle Journey", "A roguelite run through Briar Hollow. Start with one hero, recruit on the route, and bring the haul home.")
                Feature("Tower Tycoon", "Build Silverbrook's tower floor by floor. Staff it, farm it and keep it fed.")
                Feature("Party & Deck", "Equip the moves each hero fights with. The deck is whatever your party has equipped.")
                Feature("Character Cards", "The animated character fronts, with their idles, attacks and moves.")
            }
        }

        Text(
            "Game data lives at ${ui.packPath}. Removing it keeps your saves.",
            color = tokens.secondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.widthIn(max = 720.dp),
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove game data?") },
            text = { Text("This frees ${installed?.let { formatBytes(it.sizeOnDisk) } ?: "the space"}. Your saves stay, and importing the pack again brings the game back.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; viewModel.remove() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Feature(title: String, body: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, color = inkTokens().secondaryText, style = MaterialTheme.typography.bodySmall)
    }
}
