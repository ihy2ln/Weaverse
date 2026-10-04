package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd

/** The AI draft as it sits in the manuscript, before the writer keeps or drops it. */
data class GeneratedProse(
    val text: String,
    val original: String,
    val label: String,
    val streaming: Boolean,
    val replacing: Boolean,
    /** Block the draft belongs under; null anchors it at the end of the scene. */
    val afterBlockId: String?,
    val earlierCount: Int,
    /** Pictures of what the draft names (Codex entries, buildings); placed where mentioned on Insert. */
    val pictures: List<DraftPicture> = emptyList(),
)

/** A picture the draft will carry: its file, caption, and the paragraph it follows (-1 = above). */
data class DraftPicture(val path: String, val caption: String, val afterParagraph: Int)

/**
 * Shows a generated draft inline in the story, in a contrasting panel so it can never
 * be mistaken for prose that is already part of the manuscript. Its actions sit on one
 * scrollable line under the text.
 */
@Composable
fun GeneratedProseCard(
    prose: GeneratedProse,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onCopy: () -> Unit,
    onChooseEarlier: (Int) -> Unit,
    /** Edits the draft in place, before it is inserted into the manuscript. */
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOriginal by remember(prose.afterBlockId) { mutableStateOf(false) }
    // With pictures the draft reads as it will be inserted; "Edit text" switches to the text field.
    var editingText by remember(prose.afterBlockId) { mutableStateOf(false) }
    // Hoisted so the caret survives recomposition while the draft is being edited.
    var field by remember(prose.afterBlockId) {
        mutableStateOf(TextFieldValue(prose.text, TextRange(prose.text.length)))
    }
    LaunchedEffect(prose.text) {
        if (field.text != prose.text) {
            val caret = field.selection.start.coerceIn(0, prose.text.length)
            field = TextFieldValue(prose.text, TextRange(caret))
        }
    }
    val shape = RoundedCornerShape(inkRadiusMd())
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = InkSpacing.xs)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(2.dp, accent, shape)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    prose.streaming -> "AI draft · writing…"
                    showOriginal -> "${prose.label.ifBlank { "AI draft" }} · showing original"
                    else -> "${prose.label.ifBlank { "AI draft" }} · not applied"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        if (prose.streaming || showOriginal) {
            SelectionContainer {
                Text(
                    if (showOriginal) prose.original else prose.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                )
            }
        } else if (prose.pictures.isNotEmpty() && !editingText) {
            IllustratedDraft(prose, Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()))
        } else {
            // The draft is editable here, so a near-miss can be fixed by hand instead
            // of being inserted and then corrected in the manuscript.
            BasicTextField(
                value = field,
                onValueChange = { next ->
                    field = next
                    onTextChange(next.text)
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            )
        }
        if (!prose.streaming) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProseAction(if (prose.replacing) "Replace" else "Insert", onAccept)
                ProseAction("Retry", onRetry)
                if (prose.pictures.isNotEmpty()) ProseAction(if (editingText) "Preview" else "Edit text") { editingText = !editingText }
                if (prose.original.isNotBlank()) {
                    ProseAction(if (showOriginal) "Candidate" else "Compare") { showOriginal = !showOriginal }
                }
                ProseAction("Copy", onCopy)
                ProseAction("Discard", onDiscard)
                repeat(prose.earlierCount) { index ->
                    ProseAction("Earlier ${index + 1}") { onChooseEarlier(index) }
                }
            }
        }
    }
}

/** The draft as it will land: its paragraphs, with pictures side by side where they're named. */
@Composable
private fun IllustratedDraft(prose: GeneratedProse, modifier: Modifier) {
    val paragraphs = com.ihy2ln.weaverse.core.media.splitIntoParagraphs(prose.text)
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    @Composable
    fun pictures(at: Int) {
        val here = prose.pictures.filter { it.afterParagraph == at }
        if (here.isEmpty()) return
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            here.forEach { picture ->
                Column(
                    Modifier.weight(1f, fill = false).widthIn(max = if (here.size == 1) 220.dp else 180.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    coil3.compose.AsyncImage(
                        model = java.io.File(picture.path),
                        contentDescription = picture.caption,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)),
                    )
                    Text(picture.caption, fontSize = 11.sp, maxLines = 1, color = color.copy(alpha = 0.8f))
                }
            }
        }
    }
    Column(modifier.padding(vertical = 4.dp)) {
        pictures(-1)
        paragraphs.forEachIndexed { i, p ->
            Text(p, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.padding(bottom = 6.dp))
            pictures(i)
        }
    }
}

@Composable
private fun ProseAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
}
