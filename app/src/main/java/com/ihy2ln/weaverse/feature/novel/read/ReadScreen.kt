package com.ihy2ln.weaverse.feature.novel.read

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.ScrollGutterBackdrop
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import kotlinx.coroutines.launch

@Composable
fun ReadScreen(
    sceneId: String = "scene-1",
    viewModel: ReadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(sceneId) { viewModel.jumpToScene(sceneId) }
    LaunchedEffect(state.pageIndex, state.keepScrollOnPageChange) {
        listState.jumpToTopIfNeeded(state.keepScrollOnPageChange)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPad),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.page?.title ?: "Read",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                val chapter = state.page?.chapterTitle.orEmpty()
                if (chapter.isNotBlank()) {
                    Text(
                        chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            InkTextButton(label = "Format", onClick = viewModel::toggleFormat, compact = true)
        }
        ScrollGutterBackdrop(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = InkSpacing.sm),
        ) {
            val page = state.page
            when {
                page == null -> Text("No pages yet.", color = tokens.secondaryText)
                page.kind == ReadPage.Kind.Cover && !page.coverPath.isNullOrBlank() ->
                    CoverReader(path = page.coverPath, listState = listState, modifier = Modifier.fillMaxSize())
                else -> DocumentReader(
                    docJson = page.docJson,
                    mediaPaths = state.mediaPaths,
                    fontSizeSp = state.fontSizeSp,
                    lineHeight = state.lineHeight,
                    listState = listState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            state.pageLabel,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = InkSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "Ch ◀", onClick = viewModel::prevChapter, compact = true, enabled = state.canPrev)
            InkTextButton(
                label = "Top",
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                compact = true,
            )
            InkTextButton(label = "Prev", onClick = viewModel::prevPage, compact = true, enabled = state.canPrev)
            InkTextButton(label = "Next", onClick = viewModel::nextPage, compact = true, enabled = state.canNext)
            InkTextButton(
                label = "Bottom",
                onClick = {
                    scope.launch {
                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                    }
                },
                compact = true,
            )
            InkTextButton(label = "Ch ▶", onClick = viewModel::nextChapter, compact = true, enabled = state.canNext)
        }
    }

    if (state.showFormat) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFormat,
            title = { Text("Format") },
            text = {
                Column {
                    Text("Font size: ${state.fontSizeSp}sp")
                    Slider(
                        value = state.fontSizeSp.toFloat(),
                        onValueChange = { viewModel.setFontSize(it.toInt()) },
                        valueRange = 12f..28f,
                        steps = 15,
                    )
                    Text("Line height: ${"%.1f".format(state.lineHeight)}")
                    Slider(
                        value = state.lineHeight,
                        onValueChange = viewModel::setLineHeight,
                        valueRange = 1.2f..2.2f,
                        steps = 9,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.keepScrollOnPageChange,
                            onCheckedChange = viewModel::setKeepScrollOnPageChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        Text(
                            "Keep scroll position when changing pages",
                            modifier = Modifier.padding(start = InkSpacing.sm),
                        )
                    }
                }
            },
            confirmButton = {
                InkTextButton(label = "Done", onClick = viewModel::dismissFormat)
            },
        )
    }
}
