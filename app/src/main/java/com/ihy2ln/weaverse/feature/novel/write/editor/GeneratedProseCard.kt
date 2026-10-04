package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
data class DraftPicture(val path: String, val caption: String, val afterParagraph: Int, val mediaId: String = "")

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
    /** Removes a picture from the draft, by its place in [GeneratedProse.pictures]. */
    onRemovePicture: (Int) -> Unit = {},
    /** Moves a picture up or down a paragraph. */
    onMovePicture: (index: Int, delta: Int) -> Unit = { _, _ -> },
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
            IllustratedDraft(
                prose = prose,
                onTextChange = onTextChange,
                onRemovePicture = onRemovePicture,
                onMovePicture = onMovePicture,
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            )
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
                if (prose.pictures.isNotEmpty()) ProseAction(if (editingText) "With pictures" else "Plain text") { editingText = !editingText }
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

/**
 * The draft as it will land, and editable as such: each paragraph is a text field, and each picture
 * (side by side where several share a spot) can be moved up or down a paragraph or removed.
 */
@Composable
private fun IllustratedDraft(
    prose: GeneratedProse,
    onTextChange: (String) -> Unit,
    onRemovePicture: (Int) -> Unit,
    onMovePicture: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    val paragraphs = com.ihy2ln.weaverse.core.media.splitIntoParagraphs(prose.text)
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    val accent = MaterialTheme.colorScheme.primary
    @Composable
    fun pictures(at: Int) {
        val here = prose.pictures.withIndex().filter { (_, p) ->
            p.afterParagraph.coerceAtMost(paragraphs.lastIndex) == at
        }
        if (here.isEmpty()) return
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            here.forEach { (index, picture) ->
                Column(
                    Modifier.weight(1f, fill = false).widthIn(max = if (here.size == 1) 220.dp else 160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Box {
                        coil3.compose.AsyncImage(
                            model = java.io.File(picture.path),
                            contentDescription = picture.caption,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)),
                        )
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(4.dp)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
                        ) {
                            PictureControl("\u2191", "Move up") { onMovePicture(index, -1) }
                            PictureControl("\u2193", "Move down") { onMovePicture(index, 1) }
                            PictureControl("\u2715", "Remove picture") { onRemovePicture(index) }
                        }
                    }
                    Text(picture.caption, fontSize = 11.sp, maxLines = 1, color = color.copy(alpha = 0.8f))
                }
            }
        }
    }
    Column(modifier.padding(vertical = 4.dp)) {
        pictures(-1)
        paragraphs.forEachIndexed { i, p ->
            // Typing here edits the draft itself; the paragraphs are rejoined with blank lines.
            BasicTextField(
                value = p,
                onValueChange = { next ->
                    onTextChange(paragraphs.toMutableList().also { it[i] = next }.joinToString("\n\n"))
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
            pictures(i)
        }
    }
}

@Composable
private fun PictureControl(symbol: String, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(50))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
}

@Composable
private fun ProseAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
}
