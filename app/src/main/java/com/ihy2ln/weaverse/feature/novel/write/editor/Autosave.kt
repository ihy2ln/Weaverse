package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.util.SaveStatus
import kotlinx.coroutines.delay

/**
 * Debounced autosave (spec §5/§6: "autosave (debounced 800ms) with
 * Saving…/Saved status"). Status flips to [SaveStatus.Saving] the moment
 * [document] changes — honest "there are unsaved edits" feedback for the
 * whole debounce window, not just while [onSave] is actually running — and
 * back to [SaveStatus.Saved] once the (debounced) save completes. Every new
 * edit within the debounce window restarts the timer, since
 * `LaunchedEffect(document)` cancels and relaunches on every key change.
 */
@Composable
fun rememberAutosaveStatus(
    document: Document,
    onSave: suspend (Document) -> Unit,
    debounceMillis: Long = 800L,
): SaveStatus {
    var status by remember { mutableStateOf(SaveStatus.Saved) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(document) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        status = SaveStatus.Saving
        delay(debounceMillis)
        onSave(document)
        status = SaveStatus.Saved
    }

    return status
}
