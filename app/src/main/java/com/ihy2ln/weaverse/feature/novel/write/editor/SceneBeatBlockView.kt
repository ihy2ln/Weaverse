package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.findCodexMentionRanges
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.PromptActionMenuButton
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/**
 * NovelCrafter-style scene beat: the user's AI prompt lives in this collapsible
 * blue box. Generated manuscript prose is rendered as normal blocks *after* it.
 */
@Composable
fun SceneBeatBlockView(
    block: SceneBeatBlock,
    onPromptChange: (String) -> Unit,
    onToggleCollapsed: () -> Unit,
    onGenerate: () -> Unit,
    onClearText: () -> Unit = {},
    onAccept: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRequestImage: () -> Unit = {},
    hasImage: Boolean = false,
    hasResult: Boolean = false,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    codexNames: List<String> = emptyList(),
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(inkRadiusMd())
    val mentionColor = InkAccentBlue
    val transformation = remember(codexNames, mentionColor) {
        CodexMentionVisualTransformation(codexNames, mentionColor)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.panel)
            .border(1.5.dp, InkAccentBlue, shape)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = InkAccentBlue,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "SCENE BEAT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            InkTextButton(
                label = if (block.collapsed) "Show" else "Hide",
                onClick = onToggleCollapsed,
                compact = true,
            )
        }
        if (block.collapsed) {
            if (block.prompt.isNotBlank()) {
                Text(
                    text = highlightCodexMentions(block.prompt, codexNames, mentionColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
        } else {
            VoiceToTextField(
                value = block.prompt,
                onValueChange = onPromptChange,
                placeholder = "Describe the beat…",
                singleLine = true,
                visualTransformation = transformation,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                if (hasResult && !generating) {
                    PromptActionMenuButton(
                        onConfirm = onAccept,
                        onRetry = onRetry,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                InkFilledButton(
                    label = if (generating) "…" else "Generate",
                    onClick = onGenerate,
                    enabled = !generating && block.prompt.isNotBlank(),
                )
                InkModeCapsule(
                    label = "Clear",
                    onClick = onClearText,
                    enabled = !generating && (block.prompt.isNotBlank() || hasResult),
                )
                InkModeCapsule(
                    label = if (hasImage) "Pic ✓" else "Pic",
                    onClick = onRequestImage,
                    selected = hasImage,
                    enabled = !generating,
                )
            }
        }
    }
}

internal fun highlightCodexMentions(
    text: String,
    names: List<String>,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    findCodexMentionRanges(text, names).forEach { range ->
        addStyle(
            SpanStyle(color = color, textDecoration = TextDecoration.Underline),
            range.first,
            range.last + 1,
        )
    }
}

private class CodexMentionVisualTransformation(
    private val names: List<String>,
    private val color: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightCodexMentions(text.text, names, color),
            OffsetMapping.Identity,
        )
    }
}
