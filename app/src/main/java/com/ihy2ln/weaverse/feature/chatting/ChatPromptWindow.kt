package com.ihy2ln.weaverse.feature.chatting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.components.PromptDockChip
import com.ihy2ln.weaverse.core.ui.components.PromptDockGrid
import com.ihy2ln.weaverse.core.ui.components.PromptDockModelRow
import com.ihy2ln.weaverse.core.ui.components.PromptDockOption
import com.ihy2ln.weaverse.core.ui.components.PromptDockShell
import com.ihy2ln.weaverse.core.ui.components.PromptDockTextField
import com.ihy2ln.weaverse.core.ui.components.PromptDockWordsField
import com.ihy2ln.weaverse.core.ui.components.promptDockFieldLines
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection

/**
 * The Chatting prompt window — the same dock the Novel workspace uses, not the slim
 * prompt bar: a draggable shell with the message box, Send, the model row, and the
 * chat-themed quick messages under "More".
 */
@Composable
fun ChatPromptWindow(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    roomName: String,
    onModelClick: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var templateSearch by rememberSaveable { mutableStateOf("") }
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val configuration = LocalConfiguration.current
    val available = (configuration.screenHeightDp.dp - keyboard - 96.dp).coerceAtLeast(80.dp)
    // Landscape leaves far less height, so the dock takes a larger share of it
    // instead of collapsing to a sliver the message box cannot fit in.
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val collapsedShare = if (landscape) .45f else .3f
    val expandedShare = if (landscape) .75f else .6f
    val draggedHeight by viewModel.promptDockHeight.collectAsState()
    val expanded = state.promptExpanded
    val messageLines = promptDockFieldLines(draggedHeight, if (expanded) 5 else 2)
    val canSend = (state.input.isNotBlank() || state.hasPendingMedia) && !state.isStreaming

    PromptDockShell(
        pinnedHeightDp = draggedHeight,
        onPinnedHeightChange = viewModel::setPromptDockHeight,
        autoMaxHeight = (if (expanded) available * expandedShare else available * collapsedShare)
            .coerceAtLeast(132.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PromptDockChip(
                if (state.aiMode) "/A reply" else "\\M manual",
                enabled = !state.isStreaming,
                onClick = viewModel::toggleAiMode,
            )
            Text(
                "to #$roomName",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("W", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            PromptDockWordsField(
                words = state.maximumWords,
                enabled = !state.isStreaming,
                onChange = viewModel::updateMaximumWords,
            )
            PromptDockChip(if (expanded) "Less" else "More") { viewModel.setPromptExpanded(!expanded) }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PromptDockTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                placeholder = "Message #$roomName",
                enabled = !state.isStreaming,
                maxLines = messageLines,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { if (state.isStreaming) viewModel.cancelGeneration() else viewModel.send() },
                enabled = state.isStreaming || canSend,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 34.dp),
            ) { Text(if (state.isStreaming) "Stop" else "Send", fontSize = 13.sp) }
        }

        PromptDockModelRow(
            current = PromptModelSelection.effectiveModelRef(state.selectedModelRef, state.defaultModelRef),
            models = state.writingModels,
            enabled = !state.isStreaming,
            onPick = { id -> viewModel.selectModel(id) },
            onUseDefault = viewModel::useDefaultModel,
        )

        if (expanded) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PromptDockChip("Attach image", enabled = !state.isStreaming, onClick = viewModel::requestMediaPick)
                PromptDockChip("Dictate", enabled = !state.isStreaming, onClick = onMicTap)
                PromptDockChip("Roll d20", enabled = !state.isStreaming, onClick = viewModel::rollDice)
                PromptDockChip("Retry", enabled = !state.isStreaming, onClick = viewModel::retry)
                PromptDockChip("Continue", enabled = !state.isStreaming, onClick = viewModel::continueConversation)
                PromptDockChip("Clear", enabled = state.input.isNotBlank(), onClick = viewModel::clearInput)
                PromptDockChip("All models", onClick = onModelClick)
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PromptDockTextField(
                    value = templateSearch,
                    onValueChange = { templateSearch = it },
                    placeholder = "Search quick messages",
                    modifier = Modifier.weight(1f),
                )
                PromptDockChip(
                    if (state.selectedTemplateIds.isEmpty()) {
                        "Room default"
                    } else {
                        "Clear ${state.selectedTemplateIds.size}"
                    },
                    onClick = viewModel::clearTemplates,
                )
            }
            PromptDockGrid(
                options = state.templates
                    .filter { it.name.contains(templateSearch, true) }
                    .map { PromptDockOption(it.id, it.name) },
                selectedIds = state.selectedTemplateIds,
                onSelect = viewModel::toggleTemplate,
                emptyText = "No quick messages match.",
            )
        }

        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val meter = state.contextMeterLabel
        val usage = state.lastUsage
        if (meter.isNotBlank() || usage.isNotBlank()) {
            Text(
                listOf(meter, usage).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
