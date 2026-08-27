package com.ihy2ln.weaverse.feature.novel.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Heading
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Quote
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

/** Read-only rendering of Write's document JSON, including pictures and stacks. */
@Composable
fun DocumentReader(
    docJson: String,
    mediaPaths: Map<String, String>,
    fontSizeSp: Int,
    lineHeight: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val document = remember(docJson) { documentFromJson(docJson) }
    val size = fontSizeSp.sp
    val height = (fontSizeSp * lineHeight).sp
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        items(document.blocks, key = { it.id }) { block ->
            ReadBlock(
                block = block,
                mediaPaths = mediaPaths,
                fontSize = size,
                lineHeight = height,
            )
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun ReadBlock(
    block: Block,
    mediaPaths: Map<String, String>,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val tokens = inkTokens()
    when (block) {
        is Paragraph -> {
            Text(
                text = annotatedFromSpans(block.spans),
                color = tokens.primaryText,
                fontSize = fontSize,
                lineHeight = lineHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is Heading -> {
            Text(
                text = annotatedFromSpans(block.spans),
                color = tokens.primaryText,
                fontSize = (fontSize.value + 4).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.sm),
            )
        }
        is Quote -> {
            Text(
                text = annotatedFromSpans(block.spans),
                color = tokens.secondaryText,
                fontSize = fontSize,
                fontStyle = FontStyle.Italic,
                lineHeight = lineHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = InkSpacing.md),
            )
        }
        is MediaBlock -> ReadMedia(path = mediaPaths[block.mediaId], kind = block.kind, caption = block.caption)
        is MediaStackBlock -> {
            val currentId = block.mediaIds.getOrNull(block.currentIndex) ?: block.mediaIds.firstOrNull()
            ReadMedia(path = currentId?.let { mediaPaths[it] }, kind = MediaKind.Image, caption = emptyList())
        }
        else -> Unit
    }
}

@Composable
private fun ReadMedia(
    path: String?,
    kind: MediaKind,
    caption: List<Span>,
) {
    val tokens = inkTokens()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (path.isNullOrBlank()) {
            Text("Picture missing", color = tokens.secondaryText, style = MaterialTheme.typography.bodySmall)
        } else when (kind) {
            MediaKind.Audio -> AudioMediaPlayer(path = path, modifier = Modifier.fillMaxWidth())
            MediaKind.Video, MediaKind.Image -> ZoomableMedia(
                path = path,
                isVideo = kind == MediaKind.Video,
                minHeight = 160.dp,
                maxHeight = 480.dp,
                contentScale = ContentScale.Fit,
                decodeOriginal = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (caption.isNotEmpty()) {
            Text(
                annotatedFromSpans(caption),
                color = tokens.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
    }
}

private fun annotatedFromSpans(spans: List<Span>) = buildAnnotatedString {
    spans.forEach { span ->
        val bold = Mark.Bold in span.marks
        val italic = Mark.Italic in span.marks
        withStyle(
            SpanStyle(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            ),
        ) {
            append(span.text)
        }
    }
}

@Composable
fun CoverReader(
    path: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item("cover") {
            ZoomableMedia(
                path = path,
                contentDescription = "Cover",
                contentScale = ContentScale.Fit,
                decodeOriginal = true,
                minHeight = 240.dp,
                maxHeight = 640.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.md),
            )
        }
        alwaysScrollEndSpacer()
    }
}

/** Shared scroll-to-top helper used by ReadScreen after a page turn. */
suspend fun androidx.compose.foundation.lazy.LazyListState.jumpToTopIfNeeded(keepScroll: Boolean) {
    if (!keepScroll) {
        animateScrollToItem(0)
    }
}
