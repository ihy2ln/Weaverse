package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** What the user typed when creating a novel, campaign or storyboard. */
data class NewWorkDetails(
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
)

/**
 * How a workspace words its "new work" dialog. The fields are the same because
 * they all end up on the same manuscript record — only the language differs.
 */
data class CreateWorkVocabulary(
    val what: String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val genreLabel: String,
    val povLabel: String,
    val styleLabel: String,
    val styleHint: String,
    val storyboardSpecific: Boolean = false,
) {
    companion object {
        val Novel = CreateWorkVocabulary(
            what = "novel",
            titleLabel = "Title",
            titlePlaceholder = "Untitled Book",
            genreLabel = "Genre",
            povLabel = "Point of view",
            styleLabel = "Style guide",
            styleHint = "Voice, pacing, anything the AI should keep to.",
        )
        val Campaign = CreateWorkVocabulary(
            what = "campaign",
            titleLabel = "Adventure",
            titlePlaceholder = "Untitled Adventure",
            genreLabel = "Setting",
            povLabel = "Whose eyes",
            styleLabel = "House rules",
            styleHint = "Tone, danger level, anything the game master should honour.",
        )
        val Storyboard = CreateWorkVocabulary(
            what = "storyboard",
            titleLabel = "Series title",
            titlePlaceholder = "Untitled Manga",
            genreLabel = "Visual genre",
            povLabel = "Reading direction",
            styleLabel = "Art direction",
            styleHint = "Linework, palette, character design, lettering, and panel rhythm.",
            storyboardSpecific = true,
        )
    }
}

/**
 * Details popup shared by every workspace that can start a new work. Only the
 * title is required; everything else can be filled in later from the editor.
 */
@Composable
fun CreateWorkDialog(
    vocabulary: CreateWorkVocabulary,
    onDismiss: () -> Unit,
    onCreate: (NewWorkDetails) -> Unit,
) {
    val tokens = inkTokens()
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var pov by remember { mutableStateOf(if (vocabulary.storyboardSpecific) "Right to left" else "") }
    var tense by remember { mutableStateOf(if (vocabulary.storyboardSpecific) "Manga" else "") }
    var styleGuide by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${vocabulary.what}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(vocabulary.titleLabel) },
                    placeholder = { Text(vocabulary.titlePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vocabulary.storyboardSpecific) {
                    Text(
                        "Set up the visual language of the series. You can change layouts page by page later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                    Text("Format", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Manga", "Manga"),
                            SegmentedOption("Comic", "Comic"),
                            SegmentedOption("Webtoon", "Webtoon"),
                        ),
                        selectedId = tense,
                        onSelect = { tense = it },
                    )
                    Text("Reading direction", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Right to left", "Right → left"),
                            SegmentedOption("Left to right", "Left → right"),
                            SegmentedOption("Vertical", "Vertical"),
                        ),
                        selectedId = pov,
                        onSelect = { pov = it },
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(vocabulary.genreLabel) },
                        placeholder = { Text("Shōnen action, noir, romance, superhero…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(vocabulary.genreLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pov,
                        onValueChange = { pov = it },
                        label = { Text(vocabulary.povLabel) },
                        placeholder = { Text("First person, third limited…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tense,
                        onValueChange = { tense = it },
                        label = { Text("Tense") },
                        placeholder = { Text("Past, present…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = styleGuide,
                    onValueChange = { styleGuide = it },
                    label = { Text(vocabulary.styleLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    vocabulary.styleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
                Text(
                    if (vocabulary.storyboardSpecific) {
                        "Only the series title is required. Main art can be set later and appears in Window."
                    } else {
                        "Only the title is needed now — the rest can be filled in later, " +
                            "and a cover is set from the editor."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(
                    NewWorkDetails(
                        title = title.trim().ifBlank { vocabulary.titlePlaceholder },
                        genre = genre.trim(),
                        pov = pov.trim(),
                        tense = tense.trim(),
                        styleGuide = styleGuide.trim(),
                    ),
                )
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
