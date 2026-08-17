package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun InkConfirmDeleteDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete $itemName?",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Text("This removes it from the project. Tap Undo on the top bar if this was a mistake.")
        },
        confirmButton = {
            InkConfirmButton(
                onClick = onConfirm,
                label = "Delete",
                contentDescription = "Confirm delete",
            )
        },
        dismissButton = {
            InkTextButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/** Minus control that asks before running [onConfirmedDelete]. */
@Composable
fun InkDeleteButton(
    itemName: String,
    onConfirmedDelete: () -> Unit,
    modifier: Modifier = Modifier,
    buttonLabel: String = "−",
) {
    var confirm by remember { mutableStateOf(false) }
    InkTextButton(
        label = buttonLabel,
        onClick = { confirm = true },
        modifier = modifier,
    )
    if (confirm) {
        InkConfirmDeleteDialog(
            itemName = itemName,
            onConfirm = {
                confirm = false
                onConfirmedDelete()
            },
            onDismiss = { confirm = false },
        )
    }
}
