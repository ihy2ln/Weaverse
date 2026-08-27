package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.feature.library.ItemAdminAction

@Composable
fun ItemAdminMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ItemAdminAction) -> Unit,
    actions: List<ItemAdminAction>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    title: String = "Admin",
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = modifier,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions.forEach { action ->
            if (action == ItemAdminAction.SelectToRemove || action == ItemAdminAction.Delete) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(action.label()) },
                onClick = {
                    onAction(action)
                    onDismiss()
                },
            )
        }
    }
}

fun ItemAdminAction.label(): String = when (this) {
    ItemAdminAction.Export -> "Export"
    ItemAdminAction.Copy -> "Copy"
    ItemAdminAction.AddCover -> "Add cover art"
    ItemAdminAction.Delete -> "Delete"
    ItemAdminAction.SelectToRemove -> "Select to remove"
    ItemAdminAction.Rename -> "Rename"
    ItemAdminAction.Pin -> "Pin / unpin"
}
