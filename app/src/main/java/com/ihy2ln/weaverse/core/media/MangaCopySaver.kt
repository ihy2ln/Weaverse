package com.ihy2ln.weaverse.core.media

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ihy2ln.weaverse.core.text.TextOverlay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a finished copy of every manga page the studio edits, translates or colors, in
 * the phone's gallery under Pictures/Weaverse/<title>. The English layers stay editable in
 * the studio; the copy has them baked in so it reads like a printed page anywhere else.
 */
@Singleton
class MangaCopySaver @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) {
    /** Saves [path] with its lettering flattened. Returns where it went, or null on failure. */
    suspend fun save(path: String, overlays: List<TextOverlay>, title: String, kind: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = BitmapFactory.decodeFile(path) ?: return@runCatching null
                val page = source.copy(Bitmap.Config.ARGB_8888, true)
                source.recycle()
                val lettering = overlays.filter { it.text.isNotBlank() && it.source == "manga-translation" }
                if (lettering.isNotEmpty()) ImageOps.typesetLayers(page, lettering.map { it.toTypesetLayer() })
                val folder = safeName(title).ifBlank { "Manga" }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
                val name = "$folder-$kind-$stamp.jpg"
                try {
                    write(page, folder, name)
                } finally {
                    page.recycle()
                }
            }.onFailure { android.util.Log.w("MangaCopySaver", "Could not save a copy of $path", it) }
                .getOrNull()
        }

    private fun write(page: Bitmap, folder: String, name: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_PICTURES}/Weaverse/$folder"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relative)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { page.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    ?: error("No output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (failure: Exception) {
                resolver.delete(uri, null, null)
                throw failure
            }
            return "$relative/$name"
        }
        // Before Android 10 the shared gallery needs a storage permission; the app's own
        // Pictures folder does not.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "Weaverse/$folder")
            .also { it.mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { page.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return file.absolutePath
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\n\r\t]+"""), " ").replace(Regex("""\s+"""), " ").trim().take(60)
}
