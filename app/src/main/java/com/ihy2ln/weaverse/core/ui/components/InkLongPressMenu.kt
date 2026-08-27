package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

data class LongPressMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/**
 * Wraps [content] with long-press handling and a standard menu that always offers
 * quick remove and select-to-remove when [onRemove] is provided.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkLongPressMenuBox(
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onEnterSelectMode: (() -> Unit)? = null,
    extraItems: List<LongPressMenuItem> = emptyList(),
    selected: Boolean = false,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.combinedClickable(
            onClick = {
                if (selectionMode) {
                    onClick()
                } else {
                    onClick()
                }
            },
            onLongClick = {
                if (!selectionMode) menuOpen = true
            },
        ),
    ) {
        content()
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            extraItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            fontWeight = if (item.destructive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    enabled = item.enabled,
                    onClick = {
                        menuOpen = false
                        item.onClick()
                    },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Remove", fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
            if (onEnterSelectMode != null) {
                DropdownMenuItem(
                    text = { Text("Select to remove…") },
                    onClick = {
                        menuOpen = false
                        onEnterSelectMode()
                    },
                )
            }
        }
    }
}
