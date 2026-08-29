package com.ihy2ln.weaverse.feature.prompt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.components.InkCheckIconButton
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.VoiceInputButton
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

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
    onSpoken: (String) -> Unit,
    onAdd: (() -> Unit)? = null,
    addSelected: Boolean = false,
    compactSingleLine: Boolean = false,
    extraActionLabel: String = "",
    onExtraAction: (() -> Unit)? = null,
    extraActionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(inkRadiusMd())
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, InkAccentBlue, shape)
            .padding(horizontal = if (collapsed) 4.dp else InkSpacing.xs, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PROMPT ${if (collapsed) "▴" else "▾"}",
                modifier = Modifier.clickable { onCollapsedChange(!collapsed) }.padding(2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = InkAccentBlue,
                maxLines = 1,
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
        if (collapsed) return@Column

        if (compactSingleLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (onExtraAction != null && extraActionLabel.isNotBlank()) {
                    Text(
                        extraActionLabel,
                        modifier = Modifier
                            .width(38.dp)
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(Color(0xFF7341A8))
                            .clickable(enabled = extraActionEnabled && !streaming, onClick = onExtraAction)
                            .padding(vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
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
                        value = value,
                        onValueChange = onValueChange,
                        enabled = !streaming,
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = tokens.primaryText),
                        cursorBrush = SolidColor(tokens.primaryText),
                        singleLine = true,
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
                Row(
                    modifier = Modifier
                        .width(62.dp)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .clickable(enabled = !streaming, onClick = onModelClick)
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("M·", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(
                        modelLabel,
                        modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        softWrap = false,
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
                    InkCheckIconButton(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        contentDescription = if (aiMode) "Generate" else "Accept",
                        modifier = Modifier.size(23.dp),
                    )
                }
                InkClearIconButton(onClick = onClear, enabled = canClear, modifier = Modifier.size(23.dp))
                VoiceInputButton(
                    onSpoken = onSpoken,
                    enabled = !streaming,
                    compact = true,
                    modifier = Modifier.size(23.dp),
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onAdd != null) {
                Text(
                    if (addSelected) "▣" else "+",
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp))
                        .background(tokens.hover).clickable(enabled = !streaming, onClick = onAdd)
                        .padding(5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (addSelected) InkAccentBlue else tokens.primaryText,
                    textAlign = TextAlign.Center,
                )
            }
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
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !streaming,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryText),
                    minLines = 1,
                    maxLines = 3,
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
                InkCheckIconButton(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    contentDescription = if (aiMode) "Generate" else "Accept",
                    modifier = Modifier.size(26.dp),
                )
            }
            InkClearIconButton(
                onClick = onClear,
                enabled = canClear,
                modifier = Modifier.size(26.dp),
            )
            VoiceInputButton(
                onSpoken = onSpoken,
                enabled = !streaming,
                compact = true,
                modifier = Modifier.size(26.dp),
            )
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
