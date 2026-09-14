package com.ihy2ln.weaverse.feature.prompt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.ComposerMenuButton
import com.ihy2ln.weaverse.core.ui.components.InkCheckIconButton
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.PromptActionMenuButton
import com.ihy2ln.weaverse.core.ui.components.VoiceInputButton
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.novel.codex.matchingBangCommands
import com.ihy2ln.weaverse.feature.roleplay.chat.RpgTurnCommands

/**
 * One compact prompt control shared by writing, chat, storyboard, and Adventure.
 * The input/action row stays as small as the Adventure composer while the
 * context, word range, and model remain available in the metadata row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedPromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    contextLabel: String,
    minimumWords: String,
    maximumWords: String,
    onMinimumWordsChange: (String) -> Unit,
    onMaximumWordsChange: (String) -> Unit,
    wordRangeValid: Boolean,
    modelLabel: String,
    onModelClick: () -> Unit,
    aiMode: Boolean,
    onToggleMode: () -> Unit,
    streaming: Boolean,
    canSubmit: Boolean,
    canClear: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    /** ⌫ press-and-hold undo of the last deletion. Null disables hold. */
    onUndoClear: (() -> Unit)? = null,
    onSpoken: (String) -> Unit,
    /** Long-press menu actions: ↻ retry/resubmit and » continue. Null hides them. */
    onRetry: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    /** Set false to drop the big clear ✕ (the dock's ↻ reset already covers it). */
    showClear: Boolean = true,
    /** Insert-target chip label, e.g. "⌖¶3" / "→End". Blank hides the chip. */
    targetLabel: String = "",
    onTargetClick: (() -> Unit)? = null,
    /** When set, the compact row's trailing control is the combined +/ROLL/🎤 button. */
    onMicTap: (() -> Unit)? = null,
    /** Roll action surfaced inside the combined composer hold-menu. */
    onRoll: (() -> Unit)? = null,
    /** Roster/inventory quick-adds inside the combined composer hold-menu. */
    onAddCharacter: (() -> Unit)? = null,
    onAddItem: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    addSelected: Boolean = false,
    compactSingleLine: Boolean = false,
    onExtraAction: (() -> Unit)? = null,
    /** Show the command preview popup while a command/hotkey is being typed. */
    showCommandPopup: Boolean = false,
    /** Battle focus: collapse to just the header line, whatever the user chose. */
    forceCollapsed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val isCollapsed = collapsed || forceCollapsed
    val shape = RoundedCornerShape(inkRadiusMd())
    // Composer sizing from the PROMPT long-press menu: 0 Standard, 1 Tall, 2 Full.
    var sizeIndex by rememberSaveable { mutableStateOf(0) }
    val sizeNames = listOf("Standard", "Tall", "Full")
    // Hoisted TextFieldValue keeps the caret where the user left it, even when the
    // parent recomposes or focus moves away to the document and comes back.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, InkAccentBlue, shape)
            .padding(horizontal = if (isCollapsed) 4.dp else InkSpacing.xs, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Press-and-hold "PROMPT" for the layout menu: collapse, expand, resize.
            Box {
                var promptMenuOpen by remember { mutableStateOf(false) }
                Text(
                    "PROMPT ${if (isCollapsed) "▴" else "▾"}",
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onCollapsedChange(!collapsed) },
                            onLongClick = { promptMenuOpen = true },
                            onLongClickLabel = "Prompt layout options",
                        )
                        .padding(2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkAccentBlue,
                    maxLines = 1,
                )
                DropdownMenu(
                    expanded = promptMenuOpen,
                    onDismissRequest = { promptMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Collapse") },
                        onClick = {
                            onCollapsedChange(true)
                            promptMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Expand") },
                        onClick = {
                            onCollapsedChange(false)
                            promptMenuOpen = false
                        },
                    )
                    val nextSize = (sizeIndex + 1) % sizeNames.size
                    DropdownMenuItem(
                        text = { Text("Resize · ${sizeNames[nextSize]}") },
                        onClick = {
                            sizeIndex = nextSize
                            onCollapsedChange(false)
                            promptMenuOpen = false
                        },
                    )
                }
            }
            // Model lives next to the prompt label to keep the entry row short.
            Text(
                "· $modelLabel",
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable(enabled = !streaming, onClick = onModelClick)
                    .padding(horizontal = 3.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .widthIn(max = 110.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = tokens.secondaryText,
                maxLines = 1,
                softWrap = false,
            )
            if (!collapsed && contextLabel.isNotBlank()) {
                Text(
                    contextLabel,
                    modifier = Modifier.weight(1f).padding(start = InkSpacing.xs)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (isCollapsed) return@Column

        if (showCommandPopup) {
            val bangVm: BangCommandsViewModel = hiltViewModel()
            val bangCommands by bangVm.commands.collectAsState()
            val starCommands by bangVm.starCommands.collectAsState()
            val trimmed = value.trimStart()
            val bangMatches = if (trimmed.startsWith("!")) {
                matchingBangCommands(trimmed, bangCommands)
            } else {
                emptyList()
            }
            val starMatches = if (trimmed.startsWith("*")) {
                RpgTurnCommands.matches(trimmed, starCommands)
            } else {
                emptyList()
            }
            when {
                bangMatches.isNotEmpty() -> CommandPreviewPopup(
                    rows = bangMatches.map { CommandPreviewRow(it.title, it.description) },
                    onSelect = { row ->
                        val leading = value.removeSuffix(trimmed)
                        val rest = trimmed.drop(1).dropWhile { it.isLetter() }
                        onValueChange("${leading}${row.trigger}$rest")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
                starMatches.isNotEmpty() -> CommandPreviewPopup(
                    rows = starMatches.map { CommandPreviewRow("*${it.keyword}", it.description) },
                    onSelect = { row ->
                        val leading = value.removeSuffix(trimmed)
                        val rest = trimmed.drop(1).dropWhile { it.isLetter() }
                        onValueChange("${leading}${row.trigger}$rest")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
                trimmed == "/" || trimmed == "\\" -> CommandPreviewPopup(
                    rows = listOf(
                        CommandPreviewRow("/", "AI generate — hand the text to the AI"),
                        CommandPreviewRow("\\", "Manual entry — save it without the AI"),
                        CommandPreviewRow("!…", "Codex quick-add — !character, !location, !object, !lore, !other"),
                    ),
                    onSelect = { row ->
                        if (row.trigger != "!…") {
                            onValueChange(value.removeSuffix(trimmed) + row.trigger)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }
        }

        if (compactSingleLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.hover)
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { next ->
                            fieldValue = next
                            onValueChange(next.text)
                        },
                        enabled = !streaming,
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = tokens.primaryText),
                        cursorBrush = SolidColor(tokens.primaryText),
                        singleLine = sizeIndex == 0,
                        minLines = when (sizeIndex) {
                            1 -> 3
                            2 -> 6
                            else -> 1
                        },
                        maxLines = when (sizeIndex) {
                            1 -> 6
                            2 -> 14
                            else -> 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.width(70.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text("W", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = tokens.secondaryText)
                    UnifiedNumberField(
                        minimumWords,
                        onMinimumWordsChange,
                        "Minimum words",
                        !streaming,
                        wordRangeValid,
                        widthDp = 25,
                    )
                    Text("–", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                    UnifiedNumberField(
                        maximumWords,
                        onMaximumWordsChange,
                        "Maximum words",
                        !streaming,
                        wordRangeValid,
                        widthDp = 25,
                    )
                }
                if (targetLabel.isNotBlank() && onTargetClick != null) {
                    Text(
                        targetLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(InkAccentBlue.copy(alpha = 0.12f))
                            .clickable(enabled = !streaming, onClick = onTargetClick)
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkAccentBlue,
                        maxLines = 1,
                    )
                }
                Text(
                    if (aiMode) "/A" else "\\M",
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(InkAccentBlue.copy(alpha = 0.12f))
                        .clickable(enabled = !streaming, onClick = onToggleMode)
                        .padding(horizontal = 3.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkAccentBlue,
                )
                if (streaming) {
                    Text(
                        "×",
                        modifier = Modifier.size(23.dp).clickable(onClick = onCancel),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    PromptActionMenuButton(
                        onConfirm = onSubmit,
                        enabled = canSubmit,
                        onRetry = onRetry,
                        onContinue = onContinue,
                    )
                }
                if (showClear) {
                    InkClearIconButton(onClick = onClear, enabled = canClear, modifier = Modifier.size(23.dp), onUndo = onUndoClear)
                }
                if (onMicTap != null) {
                    ComposerMenuButton(
                        onMicTap = onMicTap,
                        enabled = !streaming,
                        onAdd = onAdd,
                        onRoll = onRoll ?: onExtraAction,
                        onAddCharacter = onAddCharacter,
                        onAddItem = onAddItem,
                    )
                } else {
                    VoiceInputButton(
                        onSpoken = onSpoken,
                        enabled = !streaming,
                        compact = true,
                        modifier = Modifier.size(23.dp),
                    )
                }
                if (onAdd != null && onMicTap == null) {
                    Text(
                        if (addSelected) "▣" else "+",
                        modifier = Modifier
                            .size(23.dp)
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .clickable(enabled = !streaming, onClick = onAdd)
                            .padding(vertical = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 12.sp,
                        color = if (addSelected) InkAccentBlue else tokens.primaryText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(tokens.hover).padding(horizontal = InkSpacing.sm, vertical = 7.dp),
            ) {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { next ->
                        fieldValue = next
                        onValueChange(next.text)
                    },
                    enabled = !streaming,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryText),
                    minLines = when (sizeIndex) {
                        1 -> 3
                        2 -> 6
                        else -> 1
                    },
                    maxLines = when (sizeIndex) {
                        1 -> 8
                        2 -> 16
                        else -> 3
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                if (aiMode) "/A" else "\\M",
                modifier = Modifier.clip(RoundedCornerShape(inkRadiusSm()))
                    .background(InkAccentBlue.copy(alpha = 0.12f))
                    .clickable(enabled = !streaming, onClick = onToggleMode)
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = InkAccentBlue,
            )
            if (streaming) {
                Text(
                    "×",
                    modifier = Modifier.size(26.dp).clickable(onClick = onCancel),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = tokens.primaryText,
                )
            } else {
                PromptActionMenuButton(
                    onConfirm = onSubmit,
                    enabled = canSubmit,
                    onRetry = onRetry,
                    onContinue = onContinue,
                )
            }
            if (showClear) {
                InkClearIconButton(
                    onClick = onClear,
                    enabled = canClear,
                    modifier = Modifier.size(26.dp),
                    onUndo = onUndoClear,
                )
            }
            VoiceInputButton(
                onSpoken = onSpoken,
                enabled = !streaming,
                compact = true,
                modifier = Modifier.size(26.dp),
            )
            if (onAdd != null) {
                Text(
                    if (addSelected) "▣" else "+",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .clickable(enabled = !streaming, onClick = onAdd)
                        .padding(vertical = 3.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (addSelected) InkAccentBlue else tokens.primaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("W", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            UnifiedNumberField(minimumWords, onMinimumWordsChange, "Minimum words", !streaming, wordRangeValid)
            Text("–", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            UnifiedNumberField(maximumWords, onMaximumWordsChange, "Maximum words", !streaming, wordRangeValid)
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable(enabled = !streaming, onClick = onModelClick)
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Model · ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    modelLabel,
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (targetLabel.isNotBlank() && onTargetClick != null) {
                Text(
                    targetLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(InkAccentBlue.copy(alpha = 0.12f))
                        .clickable(enabled = !streaming, onClick = onTargetClick)
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkAccentBlue,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun UnifiedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    enabled: Boolean,
    valid: Boolean,
    widthDp: Int = 34,
) {
    val tokens = inkTokens()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelSmall.copy(
            color = if (enabled) tokens.primaryText else tokens.secondaryText,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.width(widthDp.dp).semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier.border(
                    1.dp,
                    if (valid) tokens.hairline else MaterialTheme.colorScheme.error,
                    RoundedCornerShape(6.dp),
                ).padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}
