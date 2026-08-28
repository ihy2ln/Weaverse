package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import java.io.File

/**
 * The RPG session reads as an illustrated adventure page rather than a chat.
 * Messages remain the persistence/generation engine, but are rendered as story
 * paragraphs and clearly separated player actions.
 */
@Composable
fun AdventurePlayScreen(
    chatId: String,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) {
        viewModel.bindChat(chatId)
        viewModel.setDisplayMode("dungeonMaster")
    }
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val storyState = rememberLazyListState()
    val dictate = rememberSpeechToText { spoken ->
        viewModel.onInputChange(mergeSpokenText(state.input, spoken))
    }

    LaunchedEffect(state.title) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Adventure" },
                displayMode = "dungeonMaster",
                onDisplayMode = {},
                showSwitcher = false,
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { onChromeChange(null) } }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris) else viewModel.clearMediaPickRequest()
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size + if (state.streamingText.isNotBlank()) 1 else 0
        if (target > 0) runCatching { storyState.animateScrollToItem(target - 1) }
    }

    val sceneArt = state.mediaPanels.lastOrNull { it.path.isNotBlank() && !it.isAudio }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .heightIn(min = 190.dp, max = 360.dp)
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm)
                .clip(RoundedCornerShape(inkRadiusMd()))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            tokens.panel,
                        ),
                    ),
                )
                .clickable(onClickLabel = "Choose scene art") { viewModel.requestMediaPick() },
            contentAlignment = Alignment.Center,
        ) {
            if (sceneArt != null) {
                AsyncImage(
                    model = File(sceneArt.path),
                    contentDescription = "Current adventure scene",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))),
                        )
                        .padding(InkSpacing.md),
                ) {
                    Text(
                        "Current scene · tap to change art",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◈", style = MaterialTheme.typography.displaySmall, color = tokens.activePill)
                    Text("Add scene art", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A single illustration anchors the current adventure scene.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.lg),
        ) {
            Text(
                "Adventure story",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = InkSpacing.xs),
            )
            LazyColumn(
                state = storyState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item("empty") {
                        Text(
                            "The scene is waiting for its first action.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.secondaryText,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    if (message.role == "user") {
                        Text(
                            "Your action — ${message.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = tokens.secondaryText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.hover, RoundedCornerShape(inkRadiusMd()))
                                .padding(InkSpacing.sm),
                        )
                    } else {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.primaryText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (state.streamingText.isNotBlank()) {
                    item("streaming") {
                        Text(
                            state.streamingText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.primaryText,
                        )
                    }
                }
            }
        }

        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
        }
        CollapsibleUsageStrip(state.lastUsage, Modifier.padding(horizontal = InkSpacing.lg))
        AdventureActionComposer(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            aiMode = state.entryMode != "nai",
            streaming = state.isStreaming,
            onToggleMode = {
                viewModel.setEntryMode(if (state.entryMode == "nai") "ai" else "nai")
            },
            onAddArt = viewModel::requestMediaPick,
            onDictate = dictate,
            onSend = viewModel::send,
            onCancel = viewModel::cancelGeneration,
        )
    }
}

@Composable
private fun AdventureActionComposer(
    value: String,
    onValueChange: (String) -> Unit,
    aiMode: Boolean,
    streaming: Boolean,
    onToggleMode: () -> Unit,
    onAddArt: () -> Unit,
    onDictate: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.sm)
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(tokens.panel)
            .padding(InkSpacing.sm),
    ) {
        Text("What do you do?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Text(
                "＋",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tokens.hover)
                    .clickable(onClickLabel = "Add scene art", onClick = onAddArt)
                    .padding(8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tokens.hover)
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                if (value.isBlank()) {
                    Text(
                        "Describe your action…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryText),
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                if (aiMode) "/A" else "\\M",
                style = MaterialTheme.typography.labelMedium,
                color = if (aiMode) tokens.activePill else tokens.secondaryText,
                modifier = Modifier.clickable(onClick = onToggleMode).padding(InkSpacing.xs),
            )
            Text(
                if (streaming) "×" else if (value.isNotBlank()) "➤" else "🎙",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.activePill,
                modifier = Modifier
                    .clickable {
                        when {
                            streaming -> onCancel()
                            value.isNotBlank() -> onSend()
                            else -> onDictate()
                        }
                    }
                    .padding(InkSpacing.xs),
            )
        }
    }
}
