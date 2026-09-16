package com.ihy2ln.weaverse.feature.storyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.manga.MangaDownloadTreatment

@Composable
internal fun MangaDownloadOptionsDialog(
    title: String,
    alreadyDownloaded: Boolean,
    initial: MangaDownloadTreatment = MangaDownloadTreatment.Original,
    onConfirm: (MangaDownloadTreatment) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by rememberSaveable(title) { mutableStateOf(initial.name) }
    val mode = MangaDownloadTreatment.decode(selection)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Download options") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("Original pages are always kept. AI results are saved as separate edited versions.", modifier = Modifier.padding(vertical = 12.dp))
            MangaDownloadTreatment.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { selection = option.name }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(mode == option, onClick = null)
                    Text(option.label, Modifier.padding(start = 8.dp))
                }
            }
            if (mode != MangaDownloadTreatment.Original) Text(
                "${if (alreadyDownloaded) "Originals are already downloaded." else "Download originals first."} Then open AI ready to review models/settings and tap Run for the whole chapter. AI may use paid credits; it does not run just by opening this menu.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }, confirmButton = {
        Button(onClick = { onConfirm(mode) }) {
            Text(if (!alreadyDownloaded) "Download originals" else if (mode == MangaDownloadTreatment.Original) "Keep originals only" else "Prepare AI")
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun MangaDownloadOptionsHost(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    state.downloadChoice?.let { chapter ->
        val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
        MangaDownloadOptionsDialog("${chapter.mangaTitle} · ${chapter.title}", existing?.status == "completed",
            state.downloadTreatments[existing?.id] ?: MangaDownloadTreatment.Original,
            viewModel::confirmDownload, viewModel::dismissDownloadOptions)
    }
    if (state.linkDownloadChoice) state.linkPreview?.let { preview ->
        MangaDownloadOptionsDialog(preview.title, false, onConfirm = viewModel::confirmLinkDownloadWithTreatment,
            onDismiss = viewModel::dismissDownloadOptions)
    }
}

/** Waiting plans never navigate away or start paid work as a side effect of a download. */
@Composable
internal fun MangaDownloadFollowUps(state: MangaSourceUiState, viewModel: MangaSourceViewModel, onEditChapter: (MangaEditRequest) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pending = state.downloads.filter { it.id in state.downloadTreatments }
    if (pending.isEmpty()) return
    val ready = pending.filter { it.status == "completed" }
    Surface(tonalElevation = 2.dp) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready.isNotEmpty()) "AI ready · ${ready.size} chapter(s)" else "AI after download · ${pending.size} waiting")
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Download AI follow-ups") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Originals stay in your library. Open the editor to review settings and tap Run. Remove only clears this reminder, not your files.", style = MaterialTheme.typography.bodySmall)
            pending.forEach { chapter ->
                val treatment = state.downloadTreatments.getValue(chapter.id)
                Text("${chapter.mangaTitle} · ${chapter.title}", modifier = Modifier.padding(top = 16.dp))
                Text("${treatment.label} · ${if (chapter.status == "completed") "Ready" else chapter.status}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = chapter.status == "completed", onClick = {
                        expanded = false
                        onEditChapter(MangaEditRequest(chapter.id, action = MangaReaderAction.valueOf(treatment.editorAction!!)))
                    }) { Text("Open AI") }
                    if (chapter.status in listOf("failed", "stopped")) TextButton(onClick = { viewModel.retry(chapter) }) { Text("Retry download") }
                    TextButton(onClick = { viewModel.removeDownloadTreatment(chapter.id) }) { Text("Remove") }
                }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("Close") } })
}
