package com.ihy2ln.weaverse.core.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gets an exported file out of the app's private storage: the share sheet (email, Drive,
 * messaging) or the system "Save to…" picker (Downloads, SD card, any folder).
 */
object FileSharing {
    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    fun share(context: Context, file: File, title: String = "Share ${file.name}") {
        // The app's FileProvider exposes only the cache, so the file is copied there first.
        val shared = File(File(context.cacheDir, "shared").apply { mkdirs() }, file.name)
        file.copyTo(shared, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".extension-files", shared)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun copyTo(context: Context, file: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Couldn't open the chosen location")
    }
}

/** Share and Save-to buttons for a file an export just wrote. */
@Composable
fun ExportedFileActions(path: String, modifier: Modifier = Modifier, onResult: (String) -> Unit = {}) {
    val context = LocalContext.current
    val file = File(path)
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileSharing.mimeOf(file))) { uri ->
        if (uri != null) {
            runCatching { FileSharing.copyTo(context, file, uri) }
                .onSuccess { onResult("Saved ${file.name}") }
                .onFailure { onResult("Save failed: ${it.message}") }
        }
    }
    Row(modifier) {
        InkOutlinedButton(
            label = "Share",
            onClick = { runCatching { FileSharing.share(context, file) }.onFailure { onResult("Share failed: ${it.message}") } },
        )
        InkOutlinedButton(
            label = "Save to…",
            onClick = { saveTo.launch(file.name) },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
