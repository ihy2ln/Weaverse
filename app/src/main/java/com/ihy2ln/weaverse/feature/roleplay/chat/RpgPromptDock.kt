package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.PromptDockChip
import com.ihy2ln.weaverse.core.ui.components.PromptDockGrid
import com.ihy2ln.weaverse.core.ui.components.PromptDockModelRow
import com.ihy2ln.weaverse.core.ui.components.PromptDockOption
import com.ihy2ln.weaverse.core.ui.components.PromptDockShell
import com.ihy2ln.weaverse.core.ui.components.PromptDockTextField
import com.ihy2ln.weaverse.core.ui.components.PromptDockWordsField
import com.ihy2ln.weaverse.core.ui.components.PromptActionMenuButton
import com.ihy2ln.weaverse.core.ui.components.promptDockFieldLines
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.novel.codex.matchingBangCommands
import com.ihy2ln.weaverse.feature.prompt.BangCommandsViewModel
import com.ihy2ln.weaverse.feature.prompt.CommandPreviewPopup
import com.ihy2ln.weaverse.feature.prompt.CommandPreviewRow

/** One tappable turn preset; picking it fills the composer. */
data class RpgPreset(val id: String, val label: String)

/** Actions / Thoughts / Roleplay — the RPG stand-in for the Novel dock's templates. */
data class RpgPresetGroup(val label: String, val presets: List<RpgPreset>)

/**
 * The RPG prompt dock. Same structure as the Novel writing dock — drag-to-resize, a
 * compact control row, one instruction field, an inline model picker and an expandable
 * lower half — with the turn presets and the AI's own suggested moves folded in, so the
 * palette, the choice cards and the composer are one window instead of three.
 */
@Composable
fun RpgPromptDock(
    input: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    streaming: Boolean,
    aiMode: Boolean,
    onToggleMode: () -> Unit,
    contextLabel: String,
    minimumWords: Int,
    maximumWords: Int,
    onMinimumWords: (Int) -> Unit,
    onMaximumWords: (Int) -> Unit,
    wordRangeValid: Boolean,
    modelLabel: String,
    models: List<ModelInfo>,
    onPickModel: (String) -> Unit,
    onUseDefaultModel: () -> Unit,
    /** The moves the AI offered for this beat; empty when it made no suggestions. */
    choices: List<RpgActionChoice>,
    presetGroups: List<RpgPresetGroup>,
    onPreset: (RpgPreset) -> Unit,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onUndoClear: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onMicTap: () -> Unit,
    onRoll: () -> Unit,
    onAdd: () -> Unit,
    onAddCharacter: () -> Unit,
    onAddItem: () -> Unit,
    pinnedHeightDp: Float,
    onPinnedHeightChange: (Float) -> Unit,
    availableHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf(0) }
    val promptLines = promptDockFieldLines(pinnedHeightDp, if (expanded) 5 else 2)
    PromptDockShell(
        pinnedHeightDp = pinnedHeightDp,
        onPinnedHeightChange = onPinnedHeightChange,
        autoMaxHeight = if (expanded) availableHeight * .6f else availableHeight * .35f,
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PromptDockChip(
                label = if (aiMode) "/A" else "\\M",
                enabled = !streaming,
                selected = true,
                onClick = onToggleMode,
            )
            Text(
                contextLabel,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("W", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            PromptDockWordsField(minimumWords, !streaming, onMinimumWords, "Minimum words")
            Text("–", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            PromptDockWordsField(maximumWords, !streaming, onMaximumWords, "Maximum words")
            PromptDockChip(if (expanded) "Less" else "More") { expanded = !expanded }
        }

        if (choices.isNotEmpty()) {
            RpgChoiceStrip(choices = choices, onChoose = onInputChange)
        }

        RpgCommandPopup(input, onInputChange)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PromptDockTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = placeholder,
                enabled = !streaming,
                maxLines = promptLines,
                modifier = Modifier.weight(1f),
            )
            if (streaming) {
                Text(
                    "×",
                    modifier = Modifier.size(30.dp).clickable(onClick = onCancel),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = tokens.primaryText,
                )
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit && wordRangeValid,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) { Text("Send", fontSize = 13.sp) }
            }
            InkClearIconButton(
                onClick = onClear,
                enabled = input.isNotBlank(),
                modifier = Modifier.size(26.dp),
                onUndo = onUndoClear,
            )
            IconButton(onClick = onMicTap, enabled = !streaming, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = if (streaming) tokens.secondaryText else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        PromptDockModelRow(
            current = modelLabel,
            models = models,
            enabled = !streaming,
            onPick = onPickModel,
            onUseDefault = onUseDefaultModel,
        )

        if (expanded) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PromptActionMenuButton(
                    onConfirm = onSubmit,
                    enabled = canSubmit && wordRangeValid && !streaming,
                    onRetry = onRetry,
                    onContinue = onContinue,
                )
                PromptDockChip("Retry", enabled = !streaming, onClick = onRetry)
                PromptDockChip("Continue", enabled = !streaming, onClick = onContinue)
                PromptDockChip("+ Media", enabled = !streaming, onClick = onAdd)
                PromptDockChip("🎲 Roll", enabled = !streaming, onClick = onRoll)
                PromptDockChip("+ Character", enabled = !streaming, onClick = onAddCharacter)
                PromptDockChip("+ Item", enabled = !streaming, onClick = onAddItem)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                presetGroups.forEachIndexed { index, entry ->
                    PromptDockChip(entry.label, selected = index == group) { group = index }
                }
            }
            val shown = presetGroups.getOrNull(group)?.presets.orEmpty()
            PromptDockGrid(
                options = shown.map { PromptDockOption(it.id, it.label) },
                selectedIds = emptyList(),
                onSelect = { id -> shown.firstOrNull { it.id == id }?.let(onPreset) },
                emptyText = "No presets for this group.",
                numbered = false,
            )
        }
    }
}

/** The AI's suggested moves, inside the dock instead of floating above the story. */
@Composable
private fun RpgChoiceStrip(choices: List<RpgActionChoice>, onChoose: (String) -> Unit) {
    val tokens = inkTokens()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Choose the party’s direction",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.activePill,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            choices.forEach { choice ->
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.panel)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(inkRadiusSm()))
                        .clickable(onClickLabel = "Choose ${choice.title}") { onChoose(choice.title) }
                        .padding(InkSpacing.xs),
                ) {
                    Text(choice.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (choice.description.isNotBlank()) {
                        Text(choice.description, style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The `!`, `*` and `/` command preview, kept from the old composer bar. */
@Composable
private fun RpgCommandPopup(value: String, onValueChange: (String) -> Unit) {
    val bangVm: BangCommandsViewModel = hiltViewModel()
    val bangCommands by bangVm.commands.collectAsState()
    val starCommands by bangVm.starCommands.collectAsState()
    val trimmed = value.trimStart()
    val rows = remember(trimmed, bangCommands, starCommands) {
        when {
            trimmed.startsWith("!") ->
                matchingBangCommands(trimmed, bangCommands).map { CommandPreviewRow(it.title, it.description) }
            trimmed.startsWith("*") ->
                RpgTurnCommands.matches(trimmed, starCommands).map { CommandPreviewRow("*${it.keyword}", it.description) }
            trimmed == "/" || trimmed == "\\" -> listOf(
                CommandPreviewRow("/", "AI turn — hand the text to the AI"),
                CommandPreviewRow("\\", "Manual entry — record it without the AI"),
                CommandPreviewRow("!…", "Codex quick-add — !character, !location, !object, !lore, !other"),
            )
            else -> emptyList()
        }
    }
    if (rows.isEmpty()) return
    Box(Modifier.fillMaxWidth()) {
        CommandPreviewPopup(
            rows = rows,
            onSelect = { row ->
                if (row.trigger != "!…") {
                    val leading = value.removeSuffix(trimmed)
                    val rest = trimmed.drop(1).dropWhile { it.isLetter() }
                    onValueChange("$leading${row.trigger}$rest")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
