package com.ihy2ln.weaverse.feature.novel.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.BookEntity

/** Books rail tab (hamburger menu → Books): switch, add, delete, and duplicate stories. */
@Composable
fun BooksSection(modifier: Modifier = Modifier, viewModel: BooksViewModel = hiltViewModel()) {
    val books by viewModel.books.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    var newDialogOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Books", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (books.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MenuBook,
                title = "No books yet",
                subtitle = "Create your first story to start planning and writing.",
                actionLabel = "New book",
                onAction = { newDialogOpen = true },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                books.forEach { book ->
                    BookRow(
                        book = book,
                        selected = book.id == currentBookId,
                        onSelect = { viewModel.selectBook(book.id) },
                        onDuplicate = { viewModel.duplicateBook(book) },
                        onDelete = { pendingDelete = book },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New book", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createBook(name); newDialogOpen = false })
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${book.title}\"?") },
            text = { Text("This permanently deletes every act, chapter, scene, and codex entry in this book. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteBook(book); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookRow(
    book: BookEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    InkCard(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Current book", tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Spacing.sm).weight(1f),
            )
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate ${book.title}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${book.title}")
            }
        }
    }
}
