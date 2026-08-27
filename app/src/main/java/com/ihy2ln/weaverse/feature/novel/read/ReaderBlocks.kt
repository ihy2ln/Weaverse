package com.ihy2ln.weaverse.feature.novel.read

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.Align
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.CodeBlock
import com.ihy2ln.weaverse.core.text.Divider
import com.ihy2ln.weaverse.core.text.Heading
import com.ihy2ln.weaverse.core.text.ListItem
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaGridBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Quote
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@Composable
internal fun ReaderBlockView(
    block: Block,
    mediaPaths: Map<String, String>,
    stackIndex: Int?,
    palette: ReaderPalette,
    fontSizeSp: Int,
    lineHeight: Float,
    speaking: Boolean,
    onCycleStack: () -> Unit,
) {
    when (block) {
        is Paragraph -> ReaderProse(
            text = block.plainText(),
            palette = palette,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            speaking = speaking,
            align = block.align,
        )
        is Heading -> ReaderProse(
            text = block.plainText(),
            palette = palette,
            fontSizeSp = fontSizeSp + (6 - block.level).coerceIn(2, 8),
            lineHeight = lineHeight,
            speaking = speaking,
            weight = FontWeight.Bold,
        )
        is Quote -> ReaderProse(
            text = block.plainText(),
            palette = palette,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            speaking = speaking,
            italic = true,
        )
        is ListItem -> ReaderProse(
            text = (if (block.ordered) "• " else "• ") + block.plainText(),
            palette = palette,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            speaking = speaking,
        )
        is CodeBlock -> ReaderProse(
            text = block.text,
            palette = palette,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            speaking = speaking,
            family = FontFamily.Monospace,
        )
        is Divider -> androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(vertical = InkSpacing.md),
            color = palette.secondary.copy(alpha = .35f),
        )
        is MediaBlock -> ReaderMediaBlock(block, mediaPaths[block.mediaId], palette)
        is MediaStackBlock -> ReaderMediaStack(
            block = block,
            mediaPaths = mediaPaths,
            cycleIndex = stackIndex,
            palette = palette,
            onCycle = onCycleStack,
        )
        is MediaGridBlock -> ReaderMediaGrid(block, mediaPaths, palette)
        is SceneBeatBlock -> Unit
    }
}

@Composable
private fun ReaderProse(
    text: String,
    palette: ReaderPalette,
    fontSizeSp: Int,
    lineHeight: Float,
    speaking: Boolean,
    align: Align = Align.Start,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    family: FontFamily = FontFamily.Serif,
) {
    if (text.isBlank()) return
    Text(
        text,
        color = palette.text,
        fontFamily = family,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeight).sp,
        fontWeight = if (speaking) FontWeight.SemiBold else weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = when (align) {
            Align.Center -> androidx.compose.ui.text.style.TextAlign.Center
            Align.End -> androidx.compose.ui.text.style.TextAlign.End
            Align.Justify -> androidx.compose.ui.text.style.TextAlign.Justify
            Align.Start -> androidx.compose.ui.text.style.TextAlign.Start
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReaderMediaBlock(
    block: MediaBlock,
    path: String?,
    palette: ReaderPalette,
) {
    if (block.collapsed) return
    val fraction = (block.widthPercent / 100f).coerceIn(0.2f, 1f)
    val boxAlign = when (block.align) {
        Align.Start -> Alignment.CenterStart
        Align.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = boxAlign) {
        Column(modifier = Modifier.fillMaxWidth(fraction)) {
            ReaderMediaSurface(
                path = path,
                isVideo = block.kind == MediaKind.Video,
                isAudio = block.kind == MediaKind.Audio,
                audioLabel = "Audio",
                contentDescription = block.caption.joinToString("") { it.text }
                    .ifBlank { "Scene picture" },
                palette = palette,
                maxHeight = 320.dp,
            )
            val caption = block.caption.joinToString("") { it.text }.trim()
            if (caption.isNotBlank()) {
                Text(
                    caption,
                    color = palette.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ReaderMediaStack(
    block: MediaStackBlock,
    mediaPaths: Map<String, String>,
    cycleIndex: Int?,
    palette: ReaderPalette,
    onCycle: () -> Unit,
) {
    if (block.collapsed || block.mediaIds.isEmpty()) return
    val index = (cycleIndex ?: block.currentIndex).coerceIn(0, block.mediaIds.lastIndex)
    val path = mediaPaths[block.mediaIds[index]]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycle),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReaderMediaSurface(
            path = path,
            isVideo = false,
            isAudio = false,
            audioLabel = "",
            contentDescription = "Stacked picture ${index + 1} of ${block.mediaIds.size}",
            palette = palette,
            maxHeight = 320.dp,
        )
        Text(
            "Picture ${index + 1} / ${block.mediaIds.size} · tap to cycle",
            color = palette.secondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = InkSpacing.xs),
        )
    }
}

@Composable
private fun ReaderMediaGrid(
    block: MediaGridBlock,
    mediaPaths: Map<String, String>,
    palette: ReaderPalette,
) {
    if (block.mediaIds.isEmpty()) return
    val columns = if (block.template.contains("3")) 3 else 2
    val gutter = block.gutterDp.dp
    Column(verticalArrangement = Arrangement.spacedBy(gutter)) {
        block.mediaIds.chunked(columns).forEach { rowIds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gutter),
            ) {
                rowIds.forEach { id ->
                    Box(modifier = Modifier.weight(1f)) {
                        ReaderMediaSurface(
                            path = mediaPaths[id],
                            isVideo = false,
                            isAudio = false,
                            audioLabel = "",
                            contentDescription = "Grid picture",
                            palette = palette,
                            maxHeight = 180.dp,
                        )
                    }
                }
                repeat(columns - rowIds.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReaderMediaSurface(
    path: String?,
    isVideo: Boolean,
    isAudio: Boolean,
    audioLabel: String,
    contentDescription: String,
    palette: ReaderPalette,
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    when {
        path.isNullOrBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Picture unavailable", color = palette.secondary)
            }
        }
        isAudio -> AudioMediaPlayer(
            path = path,
            label = audioLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.xs),
        )
        else -> ZoomableMedia(
            path = path,
            isVideo = isVideo,
            contentDescription = contentDescription,
            maxHeight = maxHeight,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.xs),
        )
    }
}
