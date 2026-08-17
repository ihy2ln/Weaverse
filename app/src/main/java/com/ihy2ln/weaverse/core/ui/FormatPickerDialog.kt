package com.ihy2ln.weaverse.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.export.ExportFormat

/** Shared "pick one of the four export/import formats" dialog — Books/Codex/Chats/Snippets
 * export and import all funnel through the same choice (spec follow-up: "the more formats the
 * better, especially JSON, Word, HTML, DOCX, MD"). */
@Composable
fun FormatPickerDialog(title: String, onDismiss: () -> Unit, onSelect: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExportFormat.entries.forEach { format ->
                    TextButton(onClick = { onSelect(format) }, modifier = Modifier.fillMaxWidth()) {
                        Text(format.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
